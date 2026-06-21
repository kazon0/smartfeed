import importlib
from collections.abc import Callable
from contextlib import contextmanager
from time import perf_counter

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

        self._pipeline_chain = (
            RunnableLambda(self._rewrite_step)
            | RunnableLambda(self._search_step)
            | RunnableLambda(self._retrieve_step)
            | RunnableLambda(self._rank_step)
            | RunnableLambda(self._rerank_step)
        )
        self._answer_chain = (
            RunnableLambda(self._compress_step)
            | RunnableLambda(self._answer_step)
        )

    def _ensure_langchain_available(self) -> None:
        try:
            importlib.import_module("langchain_core")
        except ImportError as exc:
            raise RuntimeError("LangChain is not installed") from exc

    def run(
        self,
        query: str,
        vector_store: VectorStoreService,
        *,
        rerank_query: str | None = None,
        url: str | None = None,
        metadata_filter: dict | None = None,
        all_chunks: list[dict] | None = None,
        scope: str = "global",
        relevance_threshold: float = 0.25,
        debug: dict | None = None,
        use_llm_preprocessing: bool = True,
    ) -> dict:
        if not use_llm_preprocessing:
            return super().run(
                query,
                vector_store,
                rerank_query=rerank_query,
                url=url,
                metadata_filter=metadata_filter,
                all_chunks=all_chunks,
                scope=scope,
                relevance_threshold=relevance_threshold,
                debug=debug,
                use_llm_preprocessing=False,
            )

        payload = {
            "query": query,
            "rerank_query": rerank_query or query,
            "url": url,
            "vector_store": vector_store,
            "metadata_filter": metadata_filter,
            "all_chunks": all_chunks,
            "scope": scope,
            "relevance_threshold": relevance_threshold,
            "debug": debug,
        }
        try:
            return self._pipeline_chain.invoke(payload)
        except Exception as exc:
            if debug is not None:
                debug["langchain_fallback"] = {
                    "stage": "retrieval_chain",
                    "reason": str(exc),
                }
            return super().run(
                query,
                vector_store,
                rerank_query=rerank_query,
                url=url,
                metadata_filter=metadata_filter,
                all_chunks=all_chunks,
                scope=scope,
                relevance_threshold=relevance_threshold,
                debug=debug,
                use_llm_preprocessing=use_llm_preprocessing,
            )

    def answer_with_context(
        self,
        query: str,
        context_chunks: list[str],
        llm_service: LLMService,
        debug: dict | None = None,
        compress_context: bool = True,
    ) -> str:
        if not compress_context:
            return super().answer_with_context(
                query,
                context_chunks,
                llm_service,
                debug,
                compress_context=False,
            )

        payload = {
            "query": query,
            "context_chunks": context_chunks,
            "llm_service": llm_service,
            "debug": debug,
        }
        try:
            return self._answer_chain.invoke(payload)
        except Exception as exc:
            if debug is not None:
                debug["langchain_fallback"] = {
                    "stage": "answer_chain",
                    "reason": str(exc),
                }
            return super().answer_with_context(
                query,
                context_chunks,
                llm_service,
                debug,
                compress_context=compress_context,
            )

    def answer_with_context_stream(
        self,
        query: str,
        context_chunks: list[str],
        llm_service: LLMService,
        debug: dict | None = None,
        compress_context: bool = True,
    ):
        yield from super().answer_with_context_stream(
            query,
            context_chunks,
            llm_service,
            debug,
            compress_context=compress_context,
        )

    def _rewrite_step(self, payload: dict) -> dict:
        with self._measure_stage(payload, "rewrite"):
            return {
                **payload,
                "rewritten_query": super().rewrite_query(
                    payload["query"],
                    url=payload.get("url"),
                ),
            }

    def _search_step(self, payload: dict) -> dict:
        with self._measure_stage(payload, "multi_query"):
            search_queries = super().search_queries(
                payload["query"],
                payload["rewritten_query"],
                url=payload.get("url"),
            )
            return {
                **payload,
                "search_queries": search_queries,
                "ranking_query": " ".join(search_queries),
            }

    def _retrieve_step(self, payload: dict) -> dict:
        with self._measure_stage(payload, "retrieve"):
            retrieved_chunks = super().retrieve_chunks(
                payload["vector_store"],
                payload["search_queries"],
                metadata_filter=payload.get("metadata_filter"),
                all_chunks=payload.get("all_chunks"),
                scope=payload.get("scope", "global"),
                debug=payload.get("debug"),
            )
            return {**payload, "retrieved_chunks": retrieved_chunks}

    def _rank_step(self, payload: dict) -> dict:
        with self._measure_stage(payload, "rank"):
            ranked_chunks = super().rank_chunks(
                payload["ranking_query"],
                payload["retrieved_chunks"],
            )
            debug = payload.get("debug")
            if debug is not None:
                debug["rewritten_query"] = payload["rewritten_query"]
                debug["search_queries"] = payload["search_queries"]
                debug["ranking_query"] = payload["ranking_query"]
                debug["ranked_chunks"] = self.debug_chunk_refs(ranked_chunks)
            return {**payload, "ranked_chunks": ranked_chunks}

    def _rerank_step(self, payload: dict) -> dict:
        with self._measure_stage(payload, "rerank"):
            reranked_chunks = super().llm_rerank_chunks(
                payload["rerank_query"],
                payload["ranked_chunks"],
                payload.get("debug"),
            )
            relevant_chunks = self.high_relevance_chunks(
                reranked_chunks,
                payload["relevance_threshold"],
            )
            debug = payload.get("debug")
            if debug is not None:
                debug["relevant_chunks"] = self.debug_chunk_refs(relevant_chunks)
            return {
                "rewritten_query": payload["rewritten_query"],
                "search_queries": payload["search_queries"],
                "ranking_query": payload["ranking_query"],
                "retrieved_chunks": payload["retrieved_chunks"],
                "ranked_chunks": reranked_chunks,
                "relevant_chunks": relevant_chunks,
            }

    def _compress_step(self, payload: dict) -> dict:
        with self._measure_stage(payload, "compress"):
            answer_context = super().prepare_answer_context(
                payload["query"],
                payload["context_chunks"],
                payload["llm_service"],
                payload.get("debug"),
            )
            return {**payload, "answer_context": answer_context}

    def _answer_step(self, payload: dict) -> str:
        with self._measure_stage(payload, "answer"):
            return payload["llm_service"].answer(
                question=payload["query"],
                context_chunks=payload["answer_context"],
            )

    def _record_stage(self, payload: dict, stage: str) -> None:
        debug = payload.get("debug")
        if debug is not None:
            debug.setdefault("langchain_stages", []).append(stage)

    @contextmanager
    def _measure_stage(self, payload: dict, stage: str):
        self._record_stage(payload, stage)
        started = perf_counter()
        try:
            yield
        finally:
            debug = payload.get("debug")
            if debug is not None:
                debug.setdefault("langchain_timings_ms", {})[stage] = round(
                    (perf_counter() - started) * 1000,
                    2,
                )
