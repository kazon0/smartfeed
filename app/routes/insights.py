from fastapi import APIRouter, Depends

from app.models.user import User
from app.routes.auth import get_current_user
from app.services.llm_service import LLMService
from app.services.vector_store import VectorStoreService

router = APIRouter()


@router.get("/insights")
def get_insights(user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    articles = vector_store.list_articles()
    insight = LLMService().summarize_knowledge_base(articles)
    return {
        "status": "ok",
        "total_articles": len(articles),
        **insight,
    }
