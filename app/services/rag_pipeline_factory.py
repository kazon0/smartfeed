import os
from collections.abc import Callable

from app.services.llm_service import LLMService
from app.services.rag_pipeline import RAGPipeline


def create_rag_pipeline(
    llm_service_factory: Callable[[], LLMService] = LLMService,
) -> RAGPipeline:
    pipeline_name = os.getenv("SMARTFEED_RAG_PIPELINE", "classic").strip().lower()

    if pipeline_name in {"", "classic", "default"}:
        return RAGPipeline(llm_service_factory=llm_service_factory)

    if pipeline_name == "langchain":
        try:
            from app.services.langchain_rag_pipeline import LangChainRAGPipeline

            return LangChainRAGPipeline(llm_service_factory=llm_service_factory)
        except Exception:
            return RAGPipeline(llm_service_factory=llm_service_factory)

    return RAGPipeline(llm_service_factory=llm_service_factory)
