from fastapi import APIRouter

from app.services.llm_service import LLMService
from app.services.vector_store import VectorStoreService

router = APIRouter()


@router.get("/insights")
def get_insights():
    vector_store = VectorStoreService()
    articles = vector_store.list_articles()
    insight = LLMService().summarize_knowledge_base(articles)
    return {
        "status": "ok",
        "total_articles": len(articles),
        **insight,
    }
