from fastapi.testclient import TestClient

from app.main import app
from app.services.vector_store import VectorStoreService
from app.services.web_parser import WebParserService


client = TestClient(app)


def test_root_status_ok():
    response = client.get("/")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_upload_invalid_url_does_not_crash():
    response = client.post("/upload", json={"url": "http://invalid.localhost.test"})
    data = response.json()

    assert response.status_code == 200
    assert data["stored_chunks"] == 0
    assert data["summary"] == ""
    assert "data" in data
    assert "error" in data["data"]


def test_web_parser_non_article_page_returns_empty_chunks(monkeypatch):
    class FakeResponse:
        status_code = 200
        text = """
        <html>
          <head><title>Navigation Only</title></head>
          <body>
            <nav>首页 登录 注册 分享</nav>
            <footer>Copyright ICP备</footer>
          </body>
        </html>
        """
        apparent_encoding = "utf-8"
        encoding = "utf-8"

    parser = WebParserService()
    monkeypatch.setattr(parser, "_prepare_with_jina", lambda url: None)
    monkeypatch.setattr(parser.session, "get", lambda url, timeout: FakeResponse())

    result = parser.prepare("https://example.com/nav-only")

    assert "error" not in result
    assert result["chunks"] == []


def test_web_parser_jina_extracts_sections(monkeypatch):
    class FakeResponse:
        status_code = 200
        text = """
Title: 算法文章
URL Source: https://example.com/algorithms
Markdown Content:
# 排序算法
冒泡排序用于比较相邻元素。
快速排序使用基准元素分治。

# 搜索算法
二分查找适用于有序数组。
"""
        apparent_encoding = "utf-8"
        encoding = "utf-8"

    parser = WebParserService()
    monkeypatch.setattr(parser.session, "get", lambda url, timeout: FakeResponse())

    result = parser.prepare("https://example.com/algorithms")

    assert result["metadata"]["parser"] == "jina"
    assert [section["title"] for section in result["sections"]] == ["排序算法", "搜索算法"]
    assert result["chunk_metadata"][0]["section_title"] == "排序算法"
    assert "冒泡排序" in result["content"]


def test_vector_store_add_chunks_keeps_section_metadata():
    captured = {}

    class FakeCollection:
        def upsert(self, **kwargs):
            captured.update(kwargs)

    service = VectorStoreService.__new__(VectorStoreService)
    service.collection = FakeCollection()
    service.embedding_model = None

    stored = service.add_chunks(
        ["冒泡排序内容"],
        {"url": "https://example.com", "title": "算法文章"},
        [{"section_index": 1, "section_title": "排序算法"}],
    )

    assert stored == 1
    assert captured["metadatas"][0]["section_index"] == 1
    assert captured["metadatas"][0]["section_title"] == "排序算法"


def test_vector_store_delete_by_url_does_not_crash():
    class FakeCollection:
        def get(self, where):
            return {"ids": []}

        def delete(self, ids):
            raise AssertionError("delete should not be called when there are no ids")

    service = VectorStoreService.__new__(VectorStoreService)
    service.collection = FakeCollection()

    service.delete_by_url("https://example.com")


def test_chat_without_data_returns_answer_or_fallback(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return []

    class FakeLLMService:
        def answer_without_context(self, question, reason):
            return f"{reason}。fallback answer"

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "unknown question"})
    data = response.json()

    assert response.status_code == 200
    assert data["source_type"] == "llm_fallback"
    assert data["sources"] == []
    assert data["answer"]


def test_chat_article_reference_without_url_does_not_search(monkeypatch):
    class FakeVectorStoreService:
        def query(self, *args, **kwargs):
            raise AssertionError("article reference without url should not search")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)

    response = client.post("/chat", json={"query": "这篇文章讲了什么"})
    data = response.json()

    assert response.status_code == 200
    assert data["intent"] == "page_reference"
    assert data["source_type"] == "need_page_context"
    assert data["sources"] == []
    assert data["answer"] == "我无法确定你指的是哪篇文章，请先分享网页，或提供文章链接/标题。"


def test_chat_general_query_searches_first_then_llm(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return []

    class FakeLLMService:
        def answer_without_context(self, question, reason):
            return f"{reason}。general answer"

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "如何学习 Kotlin"})
    data = response.json()

    assert response.status_code == 200
    assert data["intent"] == "knowledge_or_general_query"
    assert data["source_type"] == "llm_fallback"
    assert data["sources"] == []
    assert data["fallback_policy"] == "llm_allowed"


def test_chat_article_reference_with_url_uses_page_first(monkeypatch):
    calls = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            calls.append(metadata_filter)
            if metadata_filter:
                return [
                    {
                        "content": "这篇文章介绍了 RAG 的基本流程。",
                        "metadata": {
                            "url": "https://example.com/rag",
                            "title": "RAG",
                            "chunk_index": 0,
                        },
                        "score": 0.9,
                    }
                ]
            return []

        def get_chunks_by_url(self, url):
            return [
                {
                    "content": "这篇文章介绍了 RAG 的基本流程。",
                    "metadata": {
                        "url": url,
                        "title": "RAG",
                        "chunk_index": 0,
                    },
                    "score": 1.0,
                }
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            return "page answer"

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={"query": "这篇文章讲了什么", "url": "https://example.com/rag"},
    )
    data = response.json()

    assert response.status_code == 200
    assert data["intent"] == "page_reference"
    assert data["retrieval_scope"] == "page_first"
    assert data["source_type"] == "page"
    assert calls[0] == {"url": "https://example.com/rag"}


def test_chat_realtime_without_knowledge_does_not_guess(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return []

    class FakeLLMService:
        def answer_without_context(self, *args, **kwargs):
            raise AssertionError("realtime fallback should not call LLM")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "今天星期几"})
    data = response.json()

    assert response.status_code == 200
    assert data["intent"] == "realtime_or_current_query"
    assert data["source_type"] == "unsupported_realtime"
    assert data["sources"] == []


def test_chat_search_history_without_results_no_general_answer(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return []

    class FakeLLMService:
        def answer_without_context(self, *args, **kwargs):
            raise AssertionError("history search should not call general LLM")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "我之前看过关于人工智能的文章吗"})
    data = response.json()

    assert response.status_code == 200
    assert data["intent"] == "search_history"
    assert data["source_type"] == "no_knowledge_found"
    assert data["answer"] == "没有在知识库中找到相关内容。"


def test_chat_unsupported_action_returns_directly(monkeypatch):
    class FakeVectorStoreService:
        def query(self, *args, **kwargs):
            raise AssertionError("unsupported action should not search")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)

    response = client.post("/chat", json={"query": "删除这篇文章"})
    data = response.json()

    assert response.status_code == 200
    assert data["intent"] == "unsupported_action"
    assert data["source_type"] == "unsupported_action"
    assert data["answer"] == "当前版本还不支持这个操作。"


def test_chat_sources_use_content_preview(monkeypatch):
    long_content = "人工智能" * 80

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return [
                {
                    "content": long_content,
                    "metadata": {
                        "url": "https://example.com/ai",
                        "title": "AI",
                        "chunk_index": 0,
                    },
                    "score": 0.9,
                }
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            assert context_chunks[0].startswith("[1] title: AI")
            return "根据来源[1]，这是回答。"

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "什么是人工智能"})
    data = response.json()

    assert response.status_code == 200
    assert "content" not in data["sources"][0]
    assert data["sources"][0]["display_title"] == "AI"
    assert data["sources"][0]["content_preview"] == long_content[:1200]
    assert len(data["sources"][0]["content_preview"]) == 320


def test_chat_with_missing_page_returns_suggestions_not_page_answer(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            if metadata_filter:
                return []
            raise AssertionError("missing page should not query global chunks for answer")

        def list_sources(self, limit=10):
            return [
                {
                    "url": "https://example.com/saved",
                    "title": "Saved Article",
                    "chunk_index": None,
                    "score": None,
                    "content_preview": "",
                }
            ]

        def get_chunks_by_url(self, url):
            return []

    class FakeLLMService:
        def answer(self, *args, **kwargs):
            raise AssertionError("missing page should not call LLM answer")

        def answer_without_context(self, *args, **kwargs):
            raise AssertionError("missing page should not call LLM fallback")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={"query": "这篇文章讲了什么", "url": "https://example.com/not-uploaded"},
    )
    data = response.json()

    assert response.status_code == 200
    assert data["source_type"] == "page_not_found_with_suggestions"
    assert data["answer"].startswith("当前网页没有找到可用于回答的内容")
    assert data["sources"][0]["url"] == "https://example.com/saved"


def test_chat_page_answer_expands_neighbor_chunks(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            if metadata_filter:
                return [
                    {
                        "content": "方法2：使用位运算 n &= n - 1。",
                        "metadata": {
                            "url": "https://example.com/code",
                            "title": "Code",
                            "chunk_index": 1,
                        },
                        "score": 0.9,
                    }
                ]
            return []

        def get_chunks_by_url(self, url):
            return [
                {
                    "content": "方法1：逐位判断最低位是否为1。",
                    "metadata": {"url": url, "title": "Code", "chunk_index": 0},
                    "score": 1.0,
                },
                {
                    "content": "方法2：使用位运算 n &= n - 1。",
                    "metadata": {"url": url, "title": "Code", "chunk_index": 1},
                    "score": 1.0,
                },
                {
                    "content": "方法3：查表法提前缓存0到255的结果。",
                    "metadata": {"url": url, "title": "Code", "chunk_index": 2},
                    "score": 1.0,
                },
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "根据来源[1]、[2]、[3]回答。"

        def describe_sources(self, question, source_texts):
            return ["这段来源汇总了三种位计数方法。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={
            "query": "这个算法怎么解",
            "url": "https://example.com/code",
        },
    )
    data = response.json()

    assert response.status_code == 200
    assert data["source_type"] == "page"
    assert len(data["sources"]) == 1
    assert data["sources"][0]["display_title"] == "Code"
    assert data["sources"][0]["chunk_indexes"] == [0, 1, 2]
    assert data["sources"][0]["source_note"] == "这段来源汇总了三种位计数方法。"
    assert data["sources"][0]["source_summary"] == "这段来源汇总了三种位计数方法。"
    assert "方法1" in data["sources"][0]["content_preview"]
    assert "方法2" in data["sources"][0]["content_preview"]
    assert "方法3" in data["sources"][0]["content_preview"]
    assert "方法1" in "\n".join(captured_context)
    assert "方法2" in "\n".join(captured_context)
    assert "方法3" in "\n".join(captured_context)


def test_chat_page_answer_expands_full_section_when_metadata_exists(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            if metadata_filter:
                return [
                    {
                        "content": "快速排序使用基准元素分治。",
                        "metadata": {
                            "url": "https://example.com/algorithms",
                            "title": "Algorithms",
                            "section_index": 0,
                            "section_title": "排序算法",
                            "chunk_index": 1,
                        },
                        "score": 0.9,
                    }
                ]
            return []

        def get_chunks_by_url(self, url):
            return [
                {
                    "content": "冒泡排序通过相邻元素交换完成排序。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 0,
                        "section_title": "排序算法",
                        "chunk_index": 0,
                    },
                    "score": 1.0,
                },
                {
                    "content": "快速排序使用基准元素分治。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 0,
                        "section_title": "排序算法",
                        "chunk_index": 1,
                    },
                    "score": 1.0,
                },
                {
                    "content": "二分查找适用于有序数组。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 1,
                        "section_title": "搜索算法",
                        "chunk_index": 2,
                    },
                    "score": 1.0,
                },
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "根据《Algorithms》的排序算法章节回答。"

        def describe_sources(self, question, source_texts):
            return ["这段来源来自排序算法章节。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={
            "query": "快速排序怎么理解",
            "url": "https://example.com/algorithms",
        },
    )
    data = response.json()

    joined_context = "\n".join(captured_context)

    assert response.status_code == 200
    assert data["source_type"] == "page"
    assert len(data["sources"]) == 1
    assert data["sources"][0]["section_title"] == "排序算法"
    assert data["sources"][0]["chunk_indexes"] == [0, 1]
    assert "冒泡排序" in joined_context
    assert "快速排序" in joined_context
    assert "二分查找" not in joined_context


def test_chat_page_hybrid_retrieval_uses_keyword_match_when_vector_misses(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            if metadata_filter:
                return []
            return []

        def get_chunks_by_url(self, url):
            return [
                {
                    "content": "快速排序使用基准元素把数组划分为左右两部分。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 0,
                        "section_title": "排序算法",
                        "chunk_index": 0,
                    },
                    "score": 1.0,
                },
                {
                    "content": "二分查找每次缩小一半搜索范围。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 1,
                        "section_title": "搜索算法",
                        "chunk_index": 1,
                    },
                    "score": 1.0,
                },
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "根据《Algorithms》的排序算法章节，快速排序使用分治。"

        def describe_sources(self, question, source_texts):
            return ["这段来源解释了快速排序。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={
            "query": "快速排序怎么理解",
            "url": "https://example.com/algorithms",
        },
    )
    data = response.json()
    joined_context = "\n".join(captured_context)

    assert response.status_code == 200
    assert data["source_type"] == "page"
    assert data["sources"][0]["section_title"] == "排序算法"
    assert "快速排序" in joined_context
    assert "二分查找" not in joined_context


def test_chat_page_wide_query_uses_page_context_even_without_high_score(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            if metadata_filter:
                return [
                    {
                        "content": "局部片段，没有完整列表。",
                        "metadata": {
                            "url": "https://example.com/algorithms",
                            "title": "Algorithms",
                            "chunk_index": 5,
                        },
                        "score": 0.1,
                    }
                ]
            return []

        def get_chunks_by_url(self, url):
            return [
                {
                    "content": "方法1：枚举。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 0,
                        "section_title": "排序算法",
                        "chunk_index": 0,
                    },
                    "score": 1.0,
                },
                {
                    "content": "方法2：动态规划。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 1,
                        "section_title": "动态规划",
                        "chunk_index": 1,
                    },
                    "score": 1.0,
                },
                {
                    "content": "方法3：贪心。",
                    "metadata": {
                        "url": url,
                        "title": "Algorithms",
                        "section_index": 2,
                        "section_title": "贪心算法",
                        "chunk_index": 2,
                    },
                    "score": 1.0,
                },
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "根据来源整理：方法1、方法2、方法3。"

        def describe_sources(self, question, source_texts):
            return ["这段来源包含算法方法列表。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={
            "query": "十种算法有哪些",
            "url": "https://example.com/algorithms",
        },
    )
    data = response.json()

    assert response.status_code == 200
    assert data["source_type"] == "page"
    assert len(data["sources"]) == 3
    assert data["sources"][0]["section_title"] == "排序算法"
    assert data["sources"][1]["section_title"] == "动态规划"
    assert data["sources"][2]["section_title"] == "贪心算法"
    assert data["sources"][0]["source_summary"] == "这段来源包含算法方法列表。"
    assert "section_title: 排序算法" in "\n".join(captured_context)
    assert "方法1" in "\n".join(captured_context)
    assert "方法2" in "\n".join(captured_context)
    assert "方法3" in "\n".join(captured_context)


def test_chat_source_display_title_removes_tencent_suffix(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return [
                {
                    "content": "算法内容",
                    "metadata": {
                        "url": "https://cloud.tencent.com/developer/article/1",
                        "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                        "chunk_index": 0,
                    },
                    "score": 0.9,
                }
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            assert "title: 程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云" in context_chunks[0]
            return "根据《程序员应该知道的十个基础算法》，回答。"

        def describe_sources(self, question, source_texts):
            return ["这篇文章列出了程序员常见基础算法。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "基础算法有哪些"})
    data = response.json()

    assert response.status_code == 200
    assert data["sources"][0]["display_title"] == "程序员应该知道的十个基础算法"
    assert data["sources"][0]["source_summary"] == "这篇文章列出了程序员常见基础算法。"
