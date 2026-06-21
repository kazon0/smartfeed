from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from app.db.dependencies import get_db
from app.main import app
from app.routes.auth import get_current_user
from app.services.article_service import ArticleService
from app.services.chat_service import ChatService
from app.services.langchain_rag_pipeline import LangChainRAGPipeline
from app.services.llm_service import LLMService
from app.services.rag_pipeline import RAGPipeline
from app.services.rag_pipeline_factory import create_rag_pipeline
from app.services.vector_store import VectorStoreService
from app.services.web_parser import WebParserService


client = TestClient(app)
TEST_USER = SimpleNamespace(id="test-user-id")


def empty_db_override():
    yield None


@pytest.fixture(autouse=True)
def authenticated_user():
    app.dependency_overrides[get_current_user] = lambda: TEST_USER
    app.dependency_overrides[get_db] = empty_db_override
    yield
    app.dependency_overrides.pop(get_current_user, None)
    app.dependency_overrides.pop(get_db, None)


def test_root_status_ok():
    response = client.get("/")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_health_status_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_business_api_requires_authentication():
    def override_get_db():
        yield None

    app.dependency_overrides.pop(get_current_user, None)
    app.dependency_overrides[get_db] = override_get_db
    try:
        response = client.get("/stats")
    finally:
        app.dependency_overrides.pop(get_db, None)
        app.dependency_overrides[get_current_user] = lambda: TEST_USER

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or missing access token."


def test_auth_register_login_and_current_user(monkeypatch):
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from sqlalchemy.pool import StaticPool

    from app.db.base import Base
    from app.db.dependencies import get_db

    monkeypatch.setenv("JWT_SECRET", "test-secret-with-at-least-32-characters")
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)

    def override_get_db():
        session = session_factory()
        try:
            yield session
        finally:
            session.close()

    app.dependency_overrides.pop(get_current_user, None)
    app.dependency_overrides[get_db] = override_get_db
    try:
        register_response = client.post(
            "/auth/register",
            json={
                "email": "User@Example.com",
                "password": "strong-password",
                "display_name": "SmartFeed User",
            },
        )
        assert register_response.status_code == 201
        register_data = register_response.json()
        assert register_data["user"]["email"] == "user@example.com"
        assert register_data["token_type"] == "bearer"

        duplicate_response = client.post(
            "/auth/register",
            json={"email": "user@example.com", "password": "strong-password"},
        )
        assert duplicate_response.status_code == 409

        invalid_login = client.post(
            "/auth/login",
            json={"email": "user@example.com", "password": "wrong-password"},
        )
        assert invalid_login.status_code == 401

        login_response = client.post(
            "/auth/login",
            json={"email": "user@example.com", "password": "strong-password"},
        )
        assert login_response.status_code == 200
        access_token = login_response.json()["access_token"]

        me_response = client.get(
            "/auth/me",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        assert me_response.status_code == 200
        assert me_response.json()["display_name"] == "SmartFeed User"
        assert client.get("/auth/me").status_code == 401
    finally:
        app.dependency_overrides.pop(get_db, None)
        Base.metadata.drop_all(engine)
        engine.dispose()


def test_websocket_chat_requires_token():
    with client.websocket_connect("/ws/chat") as websocket:
        assert websocket.receive_json()["stage"] == "connected"
        error = websocket.receive_json()

    assert error["type"] == "error"
    assert error["error_code"] == "UNAUTHORIZED"


def test_websocket_chat_streams_status_and_final_response(monkeypatch):
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from sqlalchemy.pool import StaticPool

    from app.db.base import Base

    class FakeVectorStoreService:
        def __init__(self):
            self.user_id = None

        def query(self, text, top_k=5, metadata_filter=None):
            assert self.user_id
            return []

    class FakeLLMService:
        def answer_without_context(self, question, reason):
            return f"{reason}。websocket answer"

        def answer_without_context_stream(self, question, reason):
            yield f"{reason}。"
            yield "websocket answer"

    monkeypatch.setenv("JWT_SECRET", "test-secret-with-at-least-32-characters")
    monkeypatch.setattr("app.routes.ws.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.ws.LLMService", FakeLLMService)

    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)

    def override_get_db():
        session = session_factory()
        try:
            yield session
        finally:
            session.close()

    app.dependency_overrides.pop(get_current_user, None)
    app.dependency_overrides[get_db] = override_get_db
    try:
        register_response = client.post(
            "/auth/register",
            json={
                "email": "ws@example.com",
                "password": "strong-password",
                "display_name": "WebSocket User",
            },
        )
        access_token = register_response.json()["access_token"]

        with client.websocket_connect(f"/ws/chat?token={access_token}") as websocket:
            assert websocket.receive_json()["stage"] == "connected"
            assert websocket.receive_json()["stage"] == "authenticated"

            websocket.send_json({"query": "unknown websocket question"})

            assert websocket.receive_json()["stage"] == "retrieving"
            assert websocket.receive_json()["stage"] == "answering"
            first_delta = websocket.receive_json()
            second_delta = websocket.receive_json()
            completed = websocket.receive_json()

        assert first_delta == {"type": "delta", "text": "知识库中未找到足够相关内容，以下是通用回答。"}
        assert second_delta == {"type": "delta", "text": "websocket answer"}
        assert completed["type"] == "completed"
        assert completed["stage"] == "completed"
        assert completed["response"]["status"] == "ok"
        assert completed["response"]["answer"].endswith("websocket answer")
    finally:
        app.dependency_overrides[get_current_user] = lambda: TEST_USER
        app.dependency_overrides[get_db] = empty_db_override
        Base.metadata.drop_all(engine)
        engine.dispose()


def test_websocket_upload_streams_summary_and_final_response(monkeypatch):
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from sqlalchemy.pool import StaticPool

    from app.db.base import Base

    class FakeWebParserService:
        def prepare(self, url):
            return {
                "url": url,
                "title": "Streaming Upload",
                "content": "article content",
                "chunks": ["article chunk"],
                "chunk_metadata": [{"chunk_index": 0}],
                "metadata": {"parser": "fake", "length": 15},
            }

    class FakeLLMService:
        def summarize_stream(self, text):
            yield "summary "
            yield "delta"

        def classify_topic(self, title, url, summary, content):
            return {
                "topic": "科技",
                "confidence": 0.9,
                "reason": "fake",
                "source": "llm",
            }

    class FakeVectorStoreService:
        def __init__(self):
            self.user_id = None

        def delete_by_url(self, url):
            assert self.user_id

        def add_chunks(self, chunks, metadata, chunk_metadata=None):
            assert self.user_id
            return len(chunks)

    monkeypatch.setenv("JWT_SECRET", "test-secret-with-at-least-32-characters")
    monkeypatch.setattr("app.routes.ws.WebParserService", FakeWebParserService)
    monkeypatch.setattr("app.routes.ws.LLMService", FakeLLMService)
    monkeypatch.setattr("app.routes.ws.VectorStoreService", FakeVectorStoreService)

    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)

    def override_get_db():
        session = session_factory()
        try:
            yield session
        finally:
            session.close()

    app.dependency_overrides.pop(get_current_user, None)
    app.dependency_overrides[get_db] = override_get_db
    try:
        register_response = client.post(
            "/auth/register",
            json={
                "email": "ws-upload@example.com",
                "password": "strong-password",
                "display_name": "Upload User",
            },
        )
        access_token = register_response.json()["access_token"]

        with client.websocket_connect(f"/ws/upload?token={access_token}") as websocket:
            assert websocket.receive_json()["stage"] == "connected"
            assert websocket.receive_json()["stage"] == "authenticated"

            websocket.send_json({"url": "https://example.com/article"})

            events = [websocket.receive_json() for _ in range(7)]

        assert [event.get("stage") for event in (events[0], events[1], events[4], events[5])] == [
            "parsing",
            "summarizing",
            "classifying",
            "storing",
        ]
        assert events[2] == {"type": "delta", "target": "summary", "text": "summary "}
        assert events[3] == {"type": "delta", "target": "summary", "text": "delta"}
        assert events[6]["type"] == "completed"
        assert events[6]["response"]["summary"] == "summary delta"
        assert events[6]["response"]["stored_chunks"] == 1
    finally:
        app.dependency_overrides[get_current_user] = lambda: TEST_USER
        app.dependency_overrides[get_db] = empty_db_override
        Base.metadata.drop_all(engine)
        engine.dispose()


def test_article_service_isolates_same_url_by_owner():
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from app.db.base import Base

    engine = create_engine("sqlite://")
    Base.metadata.create_all(engine)
    db = sessionmaker(bind=engine, expire_on_commit=False)()
    service = ArticleService()
    url = "https://example.com/shared"
    try:
        service.upsert(
            db,
            owner_id="user-a",
            url=url,
            title="A",
            topic="科技",
            summary="A summary",
            chunk_count=2,
        )
        service.upsert(
            db,
            owner_id="user-b",
            url=url,
            title="B",
            topic="学习",
            summary="B summary",
            chunk_count=3,
        )
        service.upsert(
            db,
            owner_id="user-a",
            url=url,
            title="A updated",
            topic="科技",
            summary="A updated summary",
            chunk_count=4,
        )

        articles_a = service.list(db, owner_id="user-a")
        articles_b = service.list(db, owner_id="user-b")
        assert len(articles_a) == 1
        assert articles_a[0].title == "A updated"
        assert articles_a[0].chunk_count == 4
        assert len(articles_b) == 1
        assert articles_b[0].title == "B"

        assert service.delete(db, owner_id="user-a", url=url) is True
        assert service.list(db, owner_id="user-a") == []
        assert len(service.list(db, owner_id="user-b")) == 1
    finally:
        db.close()
        Base.metadata.drop_all(engine)
        engine.dispose()


def test_conversation_sync_round_trip_and_owner_isolation():
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from sqlalchemy.pool import StaticPool

    from app.db.base import Base

    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, expire_on_commit=False)

    def override_get_db():
        session = session_factory()
        try:
            yield session
        finally:
            session.close()

    payload = {
        "title": "RAG article",
        "source_url": "https://example.com/rag",
        "summary": "Article summary",
        "status": "ready",
        "topic": "科技",
        "stored_chunks": 4,
        "created_at_millis": 1_700_000_000_000,
        "updated_at_millis": 1_700_000_001_000,
        "messages": [
            {"type": "user", "text": "Explain RAG"},
            {
                "type": "assistant",
                "response": {
                    "status": "success",
                    "answer": "RAG combines retrieval and generation.",
                    "sources": [{"url": "https://example.com/rag"}],
                },
            },
        ],
    }
    app.dependency_overrides[get_db] = override_get_db
    try:
        saved = client.put("/conversations/conversation-1", json=payload)
        assert saved.status_code == 200
        assert saved.json()["messages"][1]["response"]["sources"][0]["url"] == payload["source_url"]

        restored = client.get("/conversations")
        assert restored.status_code == 200
        assert restored.json()["total"] == 1
        assert restored.json()["conversations"][0]["stored_chunks"] == 4

        stale_payload = {**payload, "title": "Stale title", "updated_at_millis": 1}
        stale = client.put("/conversations/conversation-1", json=stale_payload)
        assert stale.json()["title"] == "RAG article"

        app.dependency_overrides[get_current_user] = lambda: SimpleNamespace(id="user-b")
        assert client.get("/conversations").json()["total"] == 0
        assert client.delete("/conversations/conversation-1").json()["status"] == "not_found"
        assert client.put("/conversations/conversation-1", json=payload).status_code == 409
    finally:
        app.dependency_overrides[get_current_user] = lambda: TEST_USER
        app.dependency_overrides[get_db] = empty_db_override
        Base.metadata.drop_all(engine)
        engine.dispose()


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


def test_web_parser_uses_html_meta_charset_for_title(monkeypatch):
    class FakeResponse:
        status_code = 200
        content = (
            '<html><head><meta charset="UTF-8"><title>百度安全验证</title></head>'
            '<body><article><p>测试正文内容。</p></article></body></html>'
        ).encode("utf-8")
        headers = {"content-type": "text/html"}
        apparent_encoding = None
        encoding = "ISO-8859-1"

    parser = WebParserService()
    monkeypatch.setattr(parser, "_prepare_with_jina", lambda url: None)
    monkeypatch.setattr(parser.session, "get", lambda url, timeout: FakeResponse())

    result = parser.prepare("https://zhidao.baidu.com/question/1.html")

    assert result["title"] == "百度安全验证"


def test_web_parser_repairs_utf8_title_decoded_as_latin1():
    parser = WebParserService()
    mojibake_title = "百度知道".encode("utf-8").decode("latin-1")

    assert parser._normalize_title(mojibake_title) == "百度知道"


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


def test_llm_generate_search_queries_parses_json_array(monkeypatch):
    service = LLMService()
    monkeypatch.setattr(
        service,
        "_chat",
        lambda prompt: '["十个基础算法 清单","排序算法 搜索算法 图算法 动态规划"]',
    )

    queries = service.generate_search_queries(
        "十种算法有哪些",
        "程序员基础算法",
    )

    assert queries == ["十个基础算法 清单", "排序算法 搜索算法 图算法 动态规划"]


def test_rag_pipeline_builds_search_queries_from_rewrite_and_multi_query():
    class FakeLLMService:
        def generate_search_queries(self, question, rewritten_query, url=None):
            return ["十个基础算法 清单", rewritten_query, "排序算法 搜索算法"]

    pipeline = RAGPipeline(llm_service_factory=FakeLLMService)

    queries = pipeline.search_queries(
        "十种算法有哪些",
        "程序员基础算法",
        url="https://example.com/algorithms",
    )

    assert queries == [
        "程序员基础算法",
        "十种算法有哪些",
        "十个基础算法 清单",
        "排序算法 搜索算法",
    ]


def test_rag_pipeline_run_returns_unified_retrieval_result():
    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            return {"query": "动态规划 状态复用"}

        def generate_search_queries(self, question, rewritten_query, url=None):
            return ["动态规划 重叠子问题"]

        def rerank_chunks(self, question, candidates):
            return [0]

    class FakeVectorStore:
        def query(self, query, metadata_filter=None):
            return [
                {
                    "content": "该方法保存重叠子问题的结果。",
                    "metadata": {
                        "url": "https://example.com/algorithms",
                        "title": "算法",
                        "section_title": "动态规划",
                        "section_index": 1,
                        "chunk_index": 2,
                    },
                    "score": 0.9,
                }
            ]

    debug = {"retrieval_steps": []}
    result = RAGPipeline(llm_service_factory=FakeLLMService).run(
        "这个方法怎么实现",
        FakeVectorStore(),
        rerank_query="动态规划怎么实现",
        url="https://example.com/algorithms",
        metadata_filter={"url": "https://example.com/algorithms"},
        scope="page",
        relevance_threshold=0.25,
        debug=debug,
    )

    assert result["rewritten_query"] == "动态规划 状态复用"
    assert result["search_queries"] == [
        "动态规划 状态复用",
        "这个方法怎么实现",
        "动态规划 重叠子问题",
    ]
    assert result["relevant_chunks"][0]["metadata"]["section_title"] == "动态规划"
    assert debug["ranking_query"] == " ".join(result["search_queries"])
    assert len(debug["retrieval_steps"]) == 3


def test_rag_pipeline_merges_duplicate_chunks_using_highest_score():
    pipeline = RAGPipeline()
    low_score = {
        "content": "快速排序通过分区处理数据",
        "metadata": {"url": "https://example.com", "chunk_index": 2},
        "score": 0.4,
    }
    high_score = {**low_score, "score": 0.9}

    merged = pipeline.merge_chunks([low_score], [high_score])

    assert merged == [high_score]


def test_rag_pipeline_keyword_retrieval_prefers_more_term_hits():
    pipeline = RAGPipeline()
    chunks = [
        {
            "content": "排序算法概览",
            "metadata": {"title": "算法", "chunk_index": 0},
            "score": 0.1,
        },
        {
            "content": "快速排序使用分区算法",
            "metadata": {"title": "算法", "chunk_index": 1},
            "score": 0.1,
        },
    ]

    results = pipeline.keyword_retrieve_chunks("快速排序算法", chunks)

    assert results[0]["metadata"]["chunk_index"] == 1
    assert results[0]["score"] > chunks[0]["score"]


def test_rag_pipeline_ranking_uses_section_title_for_pronoun_heavy_chunks():
    pipeline = RAGPipeline()
    chunks = [
        {
            "content": "本文只是顺带提到动态规划，重点讨论其他算法。",
            "metadata": {
                "title": "算法指南",
                "section_title": "延伸阅读",
                "chunk_index": 4,
            },
            "score": 0.9,
        },
        {
            "content": "该方法会保存重叠子问题的结果，并复用已经计算的状态。",
            "metadata": {
                "title": "算法指南",
                "section_title": "动态规划",
                "chunk_index": 8,
            },
            "score": 0.7,
        },
    ]

    ranked = pipeline.rank_chunks("动态规划怎么实现", chunks)

    assert ranked[0]["metadata"]["section_title"] == "动态规划"


def test_rag_pipeline_keeps_ranked_chunks_when_llm_rerank_fails():
    class FailingLLMService:
        def rerank_chunks(self, question, candidates):
            raise RuntimeError("rerank unavailable")

    pipeline = RAGPipeline(llm_service_factory=FailingLLMService)
    chunks = [
        {"content": "第一段", "metadata": {"chunk_index": 0}, "score": 0.8},
        {"content": "第二段", "metadata": {"chunk_index": 1}, "score": 0.7},
    ]

    assert pipeline.llm_rerank_chunks("问题", chunks) == chunks


def test_rag_pipeline_factory_can_create_langchain_pipeline(monkeypatch):
    monkeypatch.setenv("SMARTFEED_RAG_PIPELINE", "langchain")

    pipeline = create_rag_pipeline(llm_service_factory=LLMService)

    assert isinstance(pipeline, LangChainRAGPipeline)


def test_langchain_pipeline_run_records_runnable_stages():
    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            return {"query": "Kotlin 协程"}

        def generate_search_queries(self, question, rewritten_query, url=None):
            return ["Kotlin Flow"]

        def rerank_chunks(self, question, candidates):
            return [0]

    class FakeVectorStore:
        def query(self, query, metadata_filter=None):
            return [
                {
                    "content": "Flow 用于异步数据流。",
                    "metadata": {"url": "https://example.com/kotlin", "chunk_index": 0},
                    "score": 0.9,
                }
            ]

    debug = {"retrieval_steps": []}
    result = LangChainRAGPipeline(llm_service_factory=FakeLLMService).run(
        "怎么学习协程",
        FakeVectorStore(),
        debug=debug,
    )

    assert debug["langchain_stages"] == [
        "rewrite",
        "multi_query",
        "retrieve",
        "rank",
        "rerank",
    ]
    assert set(debug["langchain_timings_ms"]) == {
        "rewrite",
        "multi_query",
        "retrieve",
        "rank",
        "rerank",
    }
    assert all(value >= 0 for value in debug["langchain_timings_ms"].values())
    assert result["rewritten_query"] == "Kotlin 协程"
    assert result["relevant_chunks"]


def test_langchain_pipeline_runs_compression_and_answer_stages():
    class FakeLLMService:
        def compress_context(self, question, context_chunks):
            return "[1] section_title: 协程\nFlow 是异步数据流。"

        def answer(self, question, context_chunks):
            assert context_chunks == ["[1] section_title: 协程\nFlow 是异步数据流。"]
            return "Flow 可以表示异步数据流。"

    debug = {"langchain_stages": ["rewrite", "multi_query", "retrieve", "rank", "rerank"]}
    pipeline = LangChainRAGPipeline(llm_service_factory=FakeLLMService)

    answer = pipeline.answer_with_context(
        "Flow 是什么",
        ["[1] section_title: 协程\nFlow 用于异步数据流。"],
        FakeLLMService(),
        debug,
    )

    assert answer == "Flow 可以表示异步数据流。"
    assert debug["langchain_stages"] == [
        "rewrite",
        "multi_query",
        "retrieve",
        "rank",
        "rerank",
        "compress",
        "answer",
    ]
    assert debug["context"]["compressed"] is True
    assert debug["langchain_timings_ms"]["compress"] >= 0
    assert debug["langchain_timings_ms"]["answer"] >= 0


def test_langchain_pipeline_run_falls_back_to_classic_on_chain_error():
    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            return {"query": question}

        def generate_search_queries(self, question, rewritten_query, url=None):
            return []

    class FakeVectorStore:
        def query(self, query, metadata_filter=None):
            return [
                {
                    "content": "回退后仍可检索。",
                    "metadata": {"url": "https://example.com", "chunk_index": 0},
                    "score": 0.8,
                }
            ]

    class BrokenChain:
        def invoke(self, payload):
            raise RuntimeError("chain failed")

    pipeline = LangChainRAGPipeline(llm_service_factory=FakeLLMService)
    pipeline._pipeline_chain = BrokenChain()
    debug = {"retrieval_steps": []}

    result = pipeline.run("回退测试", FakeVectorStore(), debug=debug)

    assert result["relevant_chunks"]
    assert debug["langchain_fallback"] == {
        "stage": "retrieval_chain",
        "reason": "chain failed",
    }


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


def test_llm_compress_context_balances_input_across_long_chunks(monkeypatch):
    service = LLMService()
    captured = {}

    def fake_chat(prompt):
        captured["prompt"] = prompt
        return "压缩结果"

    monkeypatch.setattr(service, "_chat", fake_chat)
    context_chunks = [
        f"[{index}] section_title: 第{index}章\n" + character * 6000
        for index, character in ((1, "甲"), (2, "乙"), (3, "丙"))
    ]

    service.compress_context("总结三章", context_chunks)

    prompt = captured["prompt"]
    assert "section_title: 第1章" in prompt
    assert "section_title: 第2章" in prompt
    assert "section_title: 第3章" in prompt
    assert "丙" * 100 in prompt


def test_chat_context_compression_falls_back_when_section_is_missing():
    captured_context = []

    class IncompleteCompressionLLM:
        def compress_context(self, question, context_chunks):
            return context_chunks[0]

        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "综合回答"

    service = ChatService(llm_service_factory=IncompleteCompressionLLM)
    chunks = [
        {
            "content": "排序算法通过比较元素完成排序。",
            "metadata": {
                "url": "https://example.com/algorithms",
                "title": "算法",
                "section_title": "排序算法",
                "section_index": 0,
                "chunk_index": 0,
            },
        },
        {
            "content": "动态规划复用重叠子问题的计算结果。",
            "metadata": {
                "url": "https://example.com/algorithms",
                "title": "算法",
                "section_title": "动态规划",
                "section_index": 1,
                "chunk_index": 1,
            },
        },
    ]

    answer = service._build_context_answer("比较两种方法", chunks)

    assert answer == "综合回答"
    assert len(captured_context) == 2
    assert "section_title: 排序算法" in captured_context[0]
    assert "section_title: 动态规划" in captured_context[1]


def test_llm_answer_prompt_requires_synthesized_explanation(monkeypatch):
    service = LLMService()
    captured = {}

    def fake_chat(prompt):
        captured["prompt"] = prompt
        return "根据当前网页，二分查找适用于有序数组。"

    monkeypatch.setattr(service, "_chat", fake_chat)

    answer = service.answer(
        "二分查找怎么理解",
        [
            "[1] title: 算法文章 section_title: 搜索算法 chunk_index: 2\n"
            "二分查找适用于有序数组。",
            "[2] title: 算法文章 section_title: 搜索算法 chunk_index: 3\n"
            "广度优先搜索按层次遍历。",
        ],
    )

    prompt = captured["prompt"]
    assert answer.startswith("根据当前网页")
    assert "综合相关片段的上下文关系" in prompt
    assert "不要按 chunk 顺序机械拼接摘要" in prompt
    assert "不要使用“来源[1]”" in prompt


def test_chat_context_trims_trailing_noise_without_removing_article_urls():
    service = ChatService()
    context = service._format_context_chunk(
        0,
        {
            "content": (
                "项目文档见 https://example.com/docs ，核心结论是保留正文。\n"
                "原创声明：未经许可不得转载。相关推荐：另一篇文章。"
            ),
            "metadata": {
                "url": "https://example.com/article",
                "title": "测试文章",
                "chunk_index": 3,
                "section_index": 1,
                "section_title": "结论",
            },
        },
    )

    assert "https://example.com/docs" in context
    assert "核心结论是保留正文" in context
    assert "原创声明" not in context
    assert "相关推荐" not in context


def test_vector_store_add_chunks_keeps_section_metadata():
    captured = {}

    class FakeCollection:
        def upsert(self, **kwargs):
            captured.update(kwargs)

    service = VectorStoreService.__new__(VectorStoreService)
    service.collection = FakeCollection()
    service.embedding_model = None
    service.user_id = "user-a"

    stored = service.add_chunks(
        ["冒泡排序内容"],
        {"url": "https://example.com", "title": "算法文章"},
        [{"section_index": 1, "section_title": "排序算法"}],
    )

    assert stored == 1
    assert captured["metadatas"][0]["section_index"] == 1
    assert captured["metadatas"][0]["section_title"] == "排序算法"
    assert captured["metadatas"][0]["user_id"] == "user-a"


def test_vector_store_delete_by_url_does_not_crash():
    class FakeCollection:
        def get(self, where):
            return {"ids": []}

        def delete(self, ids):
            raise AssertionError("delete should not be called when there are no ids")

    service = VectorStoreService.__new__(VectorStoreService)
    service.collection = FakeCollection()
    service.user_id = "user-a"

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
    class FakeArticleService:
        def list(self, db, *, owner_id):
            assert owner_id == TEST_USER.id
            return [
                {
                    "url": "https://example.com/a",
                    "title": "A",
                    "domain": "example.com",
                    "chunk_count": 3,
                    "topic": "科技",
                }
            ]

        def to_list_item(self, article):
            return article

    monkeypatch.setattr("app.routes.articles.ArticleService", FakeArticleService)

    response = client.get("/articles")
    data = response.json()

    assert response.status_code == 200
    assert data["total"] == 1
    assert data["articles"][0]["url"] == "https://example.com/a"
    assert data["articles"][0]["chunk_count"] == 3
    assert data["articles"][0]["topic"] == "科技"


def test_article_status_returns_existing_article(monkeypatch):
    class FakeArticleService:
        def get(self, db, *, owner_id, url):
            assert owner_id == TEST_USER.id
            assert url == "https://example.com/a"
            return {
                "url": url,
                "title": "A",
                "domain": "example.com",
                "topic": "科技",
                "chunk_count": 3,
            }

        def status(self, article, *, url):
            return {"exists": True, **article}

    monkeypatch.setattr("app.routes.articles.ArticleService", FakeArticleService)

    response = client.get("/articles/status", params={"url": "https://example.com/a"})
    data = response.json()

    assert response.status_code == 200
    assert data["exists"] is True
    assert data["chunk_count"] == 3
    assert data["topic"] == "科技"


def test_vector_store_article_status_returns_missing_for_unknown_url():
    class FakeCollection:
        def get(self, where, include):
            assert where == {
                "$and": [
                    {"user_id": "user-a"},
                    {"url": "https://example.com/missing"},
                ]
            }
            return {"documents": [], "metadatas": []}

    service = VectorStoreService.__new__(VectorStoreService)
    service.collection = FakeCollection()
    service.user_id = "user-a"

    status = service.article_status("https://example.com/missing")

    assert status == {
        "exists": False,
        "url": "https://example.com/missing",
        "title": "",
        "domain": "example.com",
        "topic": "",
        "chunk_count": 0,
    }


def test_insights_returns_smart_summary(monkeypatch):
    class FakeArticleService:
        def list(self, db, *, owner_id):
            return [
                {
                    "url": "https://example.com/a",
                    "title": "A",
                    "domain": "example.com",
                    "chunk_count": 3,
                    "topic": "科技",
                }
            ]

        def to_list_item(self, article):
            return article

    class FakeLLMService:
        def summarize_knowledge_base(self, articles):
            assert articles[0]["topic"] == "科技"
            return {
                "summary": "你的知识库主要关注科技内容。",
                "highlights": ["科技内容占比较高"],
                "suggestions": ["可以继续围绕重点文章提问"],
                "source": "llm",
            }

    monkeypatch.setattr("app.routes.insights.ArticleService", FakeArticleService)
    monkeypatch.setattr("app.routes.insights.LLMService", FakeLLMService)

    response = client.get("/insights")
    data = response.json()

    assert response.status_code == 200
    assert data["status"] == "ok"
    assert data["total_articles"] == 1
    assert data["summary"] == "你的知识库主要关注科技内容。"
    assert data["source"] == "llm"


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

    class FakeArticleService:
        def upsert(self, db, **values):
            captured["article"] = values

    monkeypatch.setattr("app.routes.upload.WebParserService", FakeWebParserService)
    monkeypatch.setattr("app.routes.upload.LLMService", FakeLLMService)
    monkeypatch.setattr("app.routes.upload.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.upload.ArticleService", FakeArticleService)

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
    assert captured["article"]["owner_id"] == TEST_USER.id
    assert captured["article"]["chunk_count"] == 1


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

    class FakeArticleService:
        def delete(self, db, *, owner_id, url):
            assert owner_id == TEST_USER.id
            assert url == "https://example.com/a"
            return True

    monkeypatch.setattr("app.routes.articles.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.articles.ArticleService", FakeArticleService)

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
    assert data["debug"]["timings_ms"]["intent"] >= 0
    assert data["debug"]["timings_ms"]["total"] >= 0


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


def test_chat_uses_history_for_follow_up_retrieval(monkeypatch):
    captured_rewrite_question = ""

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return []

        def get_all_chunks(self):
            return []

    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            nonlocal captured_rewrite_question
            captured_rewrite_question = question
            return {"query": "野生菌中毒 症状", "source": "llm", "reason": ""}

        def answer_without_context(self, question, reason):
            return f"{reason}。general answer"

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={
            "query": "继续说",
            "history": [
                {"role": "user", "content": "野生菌中毒会怎么样"},
                {"role": "assistant", "content": "可能出现恶心、幻觉等症状。"},
            ],
        },
    )

    assert response.status_code == 200
    assert "野生菌中毒会怎么样" in captured_rewrite_question
    assert "当前问题: 继续说" in captured_rewrite_question


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


def test_chat_multi_query_retrieves_generated_query(monkeypatch):
    captured_queries = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            captured_queries.append(text)
            if text == "十个基础算法 清单":
                return [
                    {
                        "content": "十个基础算法包括冒泡排序、快速排序、归并排序、二分查找、广度优先搜索、深度优先搜索、Dijkstra、Floyd、Prim、Kruskal。",
                        "metadata": {
                            "url": "https://example.com/algorithms",
                            "title": "基础算法",
                            "chunk_index": 2,
                        },
                        "score": 0.86,
                    }
                ]
            return []

        def get_all_chunks(self):
            return []

    class FakeLLMService:
        def rewrite_query(self, question, url=None):
            return {"query": "程序员算法", "source": "llm", "reason": ""}

        def generate_search_queries(self, question, rewritten_query, url=None):
            return ["十个基础算法 清单"]

        def compress_context(self, question, context_chunks):
            return ""

        def answer(self, question, context_chunks):
            return "十个基础算法包括排序、搜索和图算法相关内容。"

        def describe_sources(self, question, source_texts):
            return []

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "十种算法有哪些"})
    data = response.json()

    assert response.status_code == 200
    assert "程序员算法" in captured_queries
    assert "十种算法有哪些" in captured_queries
    assert "十个基础算法 清单" in captured_queries
    assert data["debug"]["search_queries"] == [
        "程序员算法",
        "十种算法有哪些",
        "十个基础算法 清单",
    ]
    assert data["debug"]["retrieval_steps"][2]["query"] == "十个基础算法 清单"
    assert data["debug"]["selected_chunks"][0]["chunk_index"] == 2
    assert data["source_type"] == "knowledge_base"
    assert data["sources"][0]["chunk_index"] == 2
    assert data["answer"] == "十个基础算法包括排序、搜索和图算法相关内容。"


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


def test_chat_page_wide_filters_noisy_chunks_and_limits_sources(monkeypatch):
    captured_context = []

    def chunk(index, content, score=1.0):
        return {
            "content": content,
            "metadata": {
                "url": "https://example.com/algorithms",
                "title": "程序员应该知道的十个基础算法",
                "section_title": "正文",
                "section_index": 2,
                "chunk_index": index,
            },
            "score": score,
        }

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return [chunk(2, "一. 排序算法 1.冒泡排序 2.快速排序 3.归并排序")]

        def get_chunks_by_url(self, url):
            return [
                chunk(0, "[](https://ad.example.com?adtrace=1&fromSource=x) 登录 关注作者"),
                chunk(1, "原创 关注作者 关联问题 换一批 登录 控制台"),
                chunk(2, "一. 排序算法 1.冒泡排序 2.快速排序 3.归并排序"),
                chunk(3, "二. 搜索算法 1.二分查找 2.广度优先搜索 3.深度优先搜索"),
                chunk(4, "三. 图算法 1.最短路径算法 2.最小生成树算法 四. 动态规划 1.背包问题 2.最长公共子序列"),
                chunk(5, "社区规范 联系我们 友情链接"),
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "十种基础算法包括排序、搜索、图算法和动态规划。"

        def describe_sources(self, question, source_texts):
            return ["来源[3]列出了算法分类和具体名称。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={"query": "十种算法有哪些", "url": "https://example.com/algorithms"},
    )
    data = response.json()

    assert response.status_code == 200
    assert len(data["sources"]) <= 3
    assert "adtrace" not in captured_context[0]
    assert "关注作者" not in captured_context[0]
    assert data["debug"]["selected_chunks"][0]["chunk_index"] == 2
    assert "来源[" not in data["sources"][0]["source_summary"]


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


def test_chat_realtime_ignores_weak_unrelated_knowledge(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return [
                {
                    "content": "今天我们继续学习排序算法，快速排序使用基准元素分治。",
                    "metadata": {
                        "url": "https://example.com/sort",
                        "title": "排序算法",
                        "chunk_index": 0,
                    },
                    "score": 0.9,
                }
            ]

        def get_all_chunks(self):
            return []

    class FakeLLMService:
        def answer(self, *args, **kwargs):
            raise AssertionError("weak realtime match should not call context LLM")

        def answer_without_context(self, *args, **kwargs):
            raise AssertionError("realtime fallback should not call general LLM")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "今天星期几"})
    data = response.json()

    assert response.status_code == 200
    assert data["status"] == "failed"
    assert data["source_type"] == "unsupported_realtime"
    assert data["sources"] == []


def test_chat_weekday_query_requires_direct_weekday_knowledge(monkeypatch):
    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            return [
                {
                    "content": "这篇财经文章包含发布日期、交易时间和市场走势，但没有提供实时日历信息。",
                    "metadata": {
                        "url": "https://example.com/finance",
                        "title": "财经新闻",
                        "chunk_index": 0,
                    },
                    "score": 0.9,
                }
            ]

        def get_all_chunks(self):
            return []

    class FakeLLMService:
        def answer(self, *args, **kwargs):
            raise AssertionError("weekday query should require direct weekday knowledge")

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post("/chat", json={"query": "今天星期几"})
    data = response.json()

    assert response.status_code == 200
    assert data["status"] == "failed"
    assert data["source_type"] == "unsupported_realtime"


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


def test_chat_page_wide_query_trims_related_content_from_sources(monkeypatch):
    captured_context = []

    class FakeVectorStoreService:
        def query(self, text, top_k=5, metadata_filter=None):
            if metadata_filter:
                return [
                    {
                        "content": "常用的算法类别及其应用如下：一. 排序算法 1.冒泡排序 2.快速排序 3.归并排序。",
                        "metadata": {
                            "url": "https://cloud.tencent.com/developer/article/2352039",
                            "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                            "section_index": 2,
                            "section_title": "程序员应该知道的十个基础算法",
                            "chunk_index": 2,
                        },
                        "score": 0.9,
                    }
                ]
            return []

        def get_chunks_by_url(self, url):
            return [
                {
                    "content": "原创 小齐来了 关注作者 关联问题 换一批 程序员常用的基础算法有哪些？",
                    "metadata": {
                        "url": url,
                        "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                        "section_index": 2,
                        "section_title": "程序员应该知道的十个基础算法",
                        "chunk_index": 1,
                    },
                    "score": 1.0,
                },
                {
                    "content": "作为一名程序员，掌握各种算法可以帮助我们解决复杂问题。常用的算法类别及其应用如下：一. 排序算法 1.冒泡排序 2.快速排序 3.归并排序。",
                    "metadata": {
                        "url": url,
                        "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                        "section_index": 2,
                        "section_title": "程序员应该知道的十个基础算法",
                        "chunk_index": 2,
                    },
                    "score": 1.0,
                },
                {
                    "content": "二. 搜索算法 1.二分查找 2.广度优先搜索 3.深度优先搜索。三. 图算法 1.最短路径算法 2.最小生成树算法。",
                    "metadata": {
                        "url": url,
                        "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                        "section_index": 2,
                        "section_title": "程序员应该知道的十个基础算法",
                        "chunk_index": 3,
                    },
                    "score": 1.0,
                },
                {
                    "content": "四. 动态规划 1.背包问题 2.最长公共子序列。喜欢点赞收藏，下期再见。原创声明：本文系作者授权腾讯云开发者社区发表。",
                    "metadata": {
                        "url": url,
                        "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                        "section_index": 2,
                        "section_title": "程序员应该知道的十个基础算法",
                        "chunk_index": 4,
                    },
                    "score": 1.0,
                },
                {
                    "content": "作者相关精选 [](https://cloud.tencent.com/developer/user/1) 推荐文章 JavaScript 算法与数据结构。",
                    "metadata": {
                        "url": url,
                        "title": "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
                        "section_index": 2,
                        "section_title": "程序员应该知道的十个基础算法",
                        "chunk_index": 5,
                    },
                    "score": 1.0,
                },
            ]

    class FakeLLMService:
        def answer(self, question, context_chunks):
            captured_context.extend(context_chunks)
            return "根据《程序员应该知道的十个基础算法》，十大算法包括排序、搜索、图算法和动态规划相关算法。"

        def describe_sources(self, question, source_texts):
            return ["这段来源列出了文章中的十大基础算法。"]

    monkeypatch.setattr("app.routes.chat.VectorStoreService", FakeVectorStoreService)
    monkeypatch.setattr("app.routes.chat.LLMService", FakeLLMService)

    response = client.post(
        "/chat",
        json={
            "query": "十大算法是什么",
            "url": "https://cloud.tencent.com/developer/article/2352039",
        },
    )
    data = response.json()

    assert response.status_code == 200
    assert data["source_type"] == "page"
    assert len(data["sources"]) == 1
    assert data["sources"][0]["display_title"] == "程序员应该知道的十个基础算法"
    assert "冒泡排序" in data["sources"][0]["content_preview"]
    assert "最长公共子序列" in data["sources"][0]["content_preview"]
    assert "作者相关精选" not in data["sources"][0]["content_preview"]
    assert "原创声明" not in data["sources"][0]["content_preview"]
    assert "推荐文章" not in "\n".join(captured_context)


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
