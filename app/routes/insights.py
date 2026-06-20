from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.dependencies import get_db
from app.models.user import User
from app.routes.auth import get_current_user
from app.services.article_service import ArticleService
from app.services.llm_service import LLMService

router = APIRouter()


@router.get("/insights")
def get_insights(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    article_service = ArticleService()
    articles = [
        article_service.to_list_item(article)
        for article in article_service.list(db, owner_id=str(user.id))
    ]
    insight = LLMService().summarize_knowledge_base(articles)
    return {
        "status": "ok",
        "total_articles": len(articles),
        **insight,
    }
