from fastapi.testclient import TestClient

from app.main import app
from app.services.llm_service import LLMService
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


def test_llm_classify_topic_parses_json_response(monkeypatch):
    service = LLMService()
    monkeypatch.setattr(
        service,
        "_chat",
        lambda prompt: '{"topic":"新闻","confidence":0.91,"reason":"央视新闻政策报道"}',
    )

    result = service.classify_topic(
        title="住房公积金条例修订",
        url="https://mbd.baidu.com/newspage/data/article",
        summary="央视新闻报道住房公积金条例修订。",
        content="近日，公开征求意见。",
    )

    assert result["topic"] == "新闻"
    assert result["confidence"] == 0.91
    assert result["source"] == "llm"


def test_llm_rewrite_query_parses_json_response(monkeypatch):
    service = LLMService()
    monkeypatch.setattr(
        service,
        "_chat",
        lambda prompt: '{"query":"Kotlin 协程 学习 方法","reason":"补充检索关键词"}',
    )

    result = service.rewrite_query("如何学习 Kotlin")

    assert result["query"] == "Kotlin 协程 学习 方法"
    assert result["source"] == "llm"


def test_llm_rerank_chunks_parses_json_array(monkeypatch):
    service = LLMService()
    monkeypatch.setattr(service, "_chat", lambda prompt: "[1,0]")

    indexes = service.rerank_chunks(
        "快速排序怎么理解",
        ["冒泡排序内容", "快速排序内容"],
    )

    assert indexes == [1, 0]


def test_llm_compress_context_returns_text(monkeypatch):
    service = LLMService()
    monkeypatch.setattr(service, "_chat", lambda prompt: "[1] 快速排序核心上下文")

    compressed = service.compress_context(
        "快速排序怎么理解",
        ["[1] title: 算法\n快速排序内容和广告噪声"],
    )

    assert compressed == "[1] 快速排序核心上下文"


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


def test_stats_returns_content_distribution(monkeypatch):
    class FakeVectorStoreService:
        def stats(self):
            return {
                "total_chunks": 3,
                "total_articles": 2,
                "topics": [
                    {"topic": "科技", "chunk_count": 2, "percentage": 66.67}
                ],
                "domains": [
                    {"domain": "example.com", "chunk_count": 2, "percentage": 66.67}
                ],
                "articles": [
                    {
                        "url": "https://example.com/a",
                        "title": "A",
                        "domain": "example.com",
                        "chunk_count": 2,
                        "percentage": 66.67,
                    }
                ],
            }

    monkeypatch.setattr("app.routes.stats.VectorStoreService", FakeVectorStoreService)

    response = client.get("/stats")
    data = response.json()

    assert response.status_code == 200
    assert data["total_chunks"] == 3
    assert data["total_articles"] == 2
    assert data["topics"][0]["topic"] == "科技"
    assert data["domains"][0]["domain"] == "example.com"


def test_articles_list_returns_saved_articles(monkeypatch):
    class FakeVectorStoreService:
        def list_articles(self):
            return [
                {
                    "url": "https://example.com/a",
                    "title": "A",
                    "domain": "example.com",
                    "chunk_count": 3,
                    "topic": "科技",
                }
            ]

    monkeypatch.setattr("app.routes.articles.VectorStoreService", FakeVectorStoreService)

    response = client.get("/articles")
    data = response.json()

    assert response.status_code == 200
    assert data["total"] == 1
    assert data["articles"][0]["url"] == "https://example.com/a"
    assert data["articles"][0]["chunk_count"] == 3
    assert data["articles"][0]["topic"] == "科技"


def test_upload_stores_llm_topic_metadata(monkeypatch):
    captured = {}

    class FakeWebParserService:
        def prepare(self, url):
            return {
                "url": url,
                "title": "住房公积金条例修订",
                "content": "央视新闻报道住房公积金条例修订，公开征求意见。",
                "chunks": ["央视新闻报道住房公积金条例修订，公开征求意见。"],
                "chunk_metadata": [{}],
                "metadata": {"source": "web", "parser": "jina", "length": 24},
            }

    class FakeLLMService:
        def summarize(self, text):
            return "这是一篇关于住房公积金政策修订的新闻。"

        def classify_topic(self, *, title, url, summary, content):
            return {
                "topic": "新闻",
                "confidence": 0.91,
                "reason": "央视新闻政策报道",
                "source": "llm",
            }

    class FakeVectorStoreService:
        def delete_by_url(self, url):
            captured["deleted_url"] = url
            return 0

        def classify_topic(self, url, title, content):
            return "新闻"

        def add_chunks(self, chunks, metadata, chunk_metadata=None):
            captured["metadata"] = metadata
            return len(chunks)

    monkeypatch.setattr("app.routes.upload.WebParserService", FakeWebParserService)
    monkeypatch.setattr("app.routes.upload.LLMService", FakeLLMService)
    monkeypatch.setattr("app.routes.upload.VectorStoreService", FakeVectorStoreService)

    response = client.post(
        "/upload",
        json={"url": "https://mbd.baidu.com/newspage/data/article"},
    )
    data = response.json()

    assert response.status_code == 200
    assert data["stored_chunks"] == 1
    assert data["data"]["metadata"]["topic"] == "新闻"
    assert captured["metadata"]["topic"] == "新闻"
    assert captured["metadata"]["topic_source"] == "llm"


def test_vector_store_classifies_baidu_cctv_article_as_news():
    service = VectorStoreService.__new__(VectorStoreService)

    topic = service._classify_topic(
        "https://mbd.baidu.com/newspage/data/article\n"
        "主播说联播｜住房公积金条例修订，升级的不只是用途\n"
        "央视新闻 近日 公开征求意见 政策 民生"
    )

    assert topic == "新闻"


def test_articles_delete_by_url(monkeypatch):
    class FakeVectorStoreService:
        def delete_by_url(self, url):
            assert url == "https://example.com/a"
            return 3

    monkeypatch.setattr("app.routes.articles.VectorStoreService", FakeVectorStoreService)

    response = client.request(
        "DELETE",
        "/articles",
        json={"url": "https://example.com/a"},
    )
    data = response.json()

    assert response.status_code == 200
    assert data["status"] == "deleted"
    assert data["deleted_chunks"] == 3


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
    assert data["status"] == "ok"
    assert data["error_code"] is None
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
    assert data["status"] == "failed"
    assert data["error_code"] == "NEED_PAGE_CONTEXT"
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


def test_chat_uses_rewritten_query_for_global_retrieval(monkeypatch):
    captured_queries = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            captured_queries.append(text)
            return []

        def get_all_chunks(self):
            return []

    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            return {
                "query": "Kotlin 协程 学习 路线",
                "source": "llm",
                "reason": "补充检索关键词",
            }

        def answer_without_context(self, question, reason):
            assert question == "如何学习 Kotlin"
            return f"{reason}。general answer"

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "如何学习 Kotlin"})
    data = response.json()

    assert response.status_code == 200
    assert captured_queries[0] == "Kotlin 协程 学习 路线"
    assert data["answer"]


def test_chat_reranks_and_compresses_context(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return [
                {
                    "content": "冒泡排序是相邻元素交换。",
                    "metadata": {
                        "url": "https://example.com/algorithms",
                        "title": "算法",
                        "chunk_index": 0,
                    },
                    "score": 0.8,
                },
                {
                    "content": "快速排序使用基准元素和分治思想。",
                    "metadata": {
                        "url": "https://example.com/algorithms",
                        "title": "算法",
                        "chunk_index": 1,
                    },
                    "score": 0.7,
                },
            ]

        def get_all_chunks(self):
            return []

    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            return {"query": question, "source": "llm", "reason": ""}

        def rerank_chunks(self, question, candidates):
            return [1, 0]

        def compress_context(self, question, context_chunks):
            return "[1] 快速排序使用基准元素和分治思想。"

        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "快速排序使用基准元素和分治思想。"

        def describe_sources(self, question, source_texts):
            return []

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "算法怎么理解"})
    data = response.json()

    assert response.status_code == 200
    assert data["sources"][0]["chunk_index"] == 1
    assert captured_context == ["[1] 快速排序使用基准元素和分治思想。"]


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
    assert data["status"] == "failed"
    assert data["error_code"] == "REALTIME_UNSUPPORTED"
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
    assert data["status"] == "failed"
    assert data["error_code"] == "NO_KNOWLEDGE_FOUND"
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
    assert data["status"] == "failed"
    assert data["error_code"] == "UNSUPPORTED_ACTION"
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


def test_chat_global_hybrid_retrieval_uses_keyword_match_when_vector_misses(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return []

        def get_all_chunks(self):
            return [
                {
                    "content": "Kotlin 协程可以用 suspend、CoroutineScope 和 Flow 组织异步任务。",
                    "metadata": {
                        "url": "https://example.com/kotlin",
                        "title": "Kotlin 学习",
                        "section_index": 0,
                        "section_title": "协程",
                        "chunk_index": 0,
                    },
                    "score": 1.0,
                }
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "根据《Kotlin 学习》，Kotlin 协程适合处理异步任务。"

        def describe_sources(self, question, source_texts):
            return ["这段来源说明了 Kotlin 协程。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "如何学习 Kotlin 协程"})
    data = response.json()

    assert response.status_code == 200
    assert data["status"] == "ok"
    assert data["error_code"] is None
    assert data["source_type"] == "knowledge_base"
    assert data["sources"][0]["section_title"] == "协程"
    assert "Kotlin 协程" in "\n".join(captured_context)


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
