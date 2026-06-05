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
