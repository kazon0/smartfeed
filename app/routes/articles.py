from fastapi import APIRouter, Depends
from fastapi import Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.db.dependencies import get_db
from app.models.user import User
from app.routes.auth import get_current_user
from app.services.article_service import ArticleService
from app.services.vector_store import VectorStoreService

router = APIRouter()


class DeleteArticleRequest(BaseModel):
    url: str


@router.get("/articles")
def list_articles(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = ArticleService()
    articles = [
        service.to_list_item(article)
        for article in service.list(db, owner_id=str(user.id))
    ]
    return {
        "articles": articles,
        "total": len(articles),
    }


@router.get("/articles/status")
def article_status(
    url: str = Query(...),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = ArticleService()
    article = service.get(db, owner_id=str(user.id), url=url)
    return service.status(article, url=url)


@router.delete("/articles")
def delete_article(
    request: DeleteArticleRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    deleted_chunks = vector_store.delete_by_url(request.url)
    deleted_article = ArticleService().delete(
        db,
        owner_id=str(user.id),
        url=request.url,
    )
    return {
        "status": "deleted" if deleted_article or deleted_chunks > 0 else "not_found",
        "url": request.url,
        "deleted_chunks": deleted_chunks,
    }
