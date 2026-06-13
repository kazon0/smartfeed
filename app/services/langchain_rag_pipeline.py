import importlib
from collections.abc import Callable

from app.services.llm_service import LLMService
from app.services.rag_pipeline import RAGPipeline
from app.services.vector_store import VectorStoreService


class LangChainRAGPipeline(RAGPipeline):
    def __init__(
        self,
        llm_service_factory: Callable[[], LLMService] = LLMService,
    ):
        self._ensure_langchain_available()
        super().__init__(llm_service_factory=llm_service_factory)
        from langchain_core.runnables import RunnableLambda

        self._rewrite_query_chain = RunnableLambda(self._rewrite_query_step)
        self._search_queries_chain = RunnableLambda(self._search_queries_step)
        self._retrieve_chunks_chain = RunnableLambda(self._retrieve_chunks_step)
        self._rank_chunks_chain = RunnableLambda(self._rank_chunks_step)
        self._rerank_chunks_chain = RunnableLambda(self._rerank_chunks_step)

    def _ensure_langchain_available(self) -> None:
        try:
            importlib.import_module("langchain_core")
        except ImportError as exc:
            raise RuntimeError("LangChain is not installed") from exc

    def rewrite_query(self, query: str, url: str | None = None) -> str:
        return self._rewrite_query_chain.invoke({"query": query, "url": url})

    def search_queries(
        self,
        query: str,
        rewritten_query: str,
        url: str | None = None,
    ) -> list[str]:
        return self._search_queries_chain.invoke(
            {
                "query": query,
                "rewritten_query": rewritten_query,
                "url": url,
            }
        )

    def retrieve_chunks(
        self,
        vector_store: VectorStoreService,
        queries: list[str],
        *,
        metadata_filter: dict | None = None,
        all_chunks: list[dict] | None = None,
        scope: str = "global",
        debug: dict | None = None,
    ) -> list[dict]:
        return self._retrieve_chunks_chain.invoke(
            {
                "vector_store": vector_store,
                "queries": queries,
                "metadata_filter": metadata_filter,
                "all_chunks": all_chunks,
                "scope": scope,
                "debug": debug,
            }
        )

    def rank_chunks(self, query: str, chunks: list[dict]) -> list[dict]:
        return self._rank_chunks_chain.invoke({"query": query, "chunks": chunks})

    def llm_rerank_chunks(
        self,
        query: str,
        chunks: list[dict],
        debug: dict | None = None,
    ) -> list[dict]:
        return self._rerank_chunks_chain.invoke(
            {
                "query": query,
                "chunks": chunks,
                "debug": debug,
            }
        )

    def _rewrite_query_step(self, payload: dict) -> str:
        return super().rewrite_query(payload["query"], url=payload.get("url"))

    def _search_queries_step(self, payload: dict) -> list[str]:
        return super().search_queries(
            payload["query"],
            payload["rewritten_query"],
            url=payload.get("url"),
        )

    def _retrieve_chunks_step(self, payload: dict) -> list[dict]:
        return super().retrieve_chunks(
            payload["vector_store"],
            payload["queries"],
            metadata_filter=payload.get("metadata_filter"),
            all_chunks=payload.get("all_chunks"),
            scope=payload.get("scope", "global"),
            debug=payload.get("debug"),
        )

    def _rank_chunks_step(self, payload: dict) -> list[dict]:
        return super().rank_chunks(payload["query"], payload["chunks"])

    def _rerank_chunks_step(self, payload: dict) -> list[dict]:
        return super().llm_rerank_chunks(
            payload["query"],
            payload["chunks"],
            payload.get("debug"),
        )
