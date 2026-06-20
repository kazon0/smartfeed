from fastapi import APIRouter, Depends
from fastapi import Query
from pydantic import BaseModel

from app.models.user import User
from app.routes.auth import get_current_user
from app.services.vector_store import VectorStoreService

router = APIRouter()


class DeleteArticleRequest(BaseModel):
    url: str


@router.get("/articles")
def list_articles(user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    articles = vector_store.list_articles()
    return {
        "articles": articles,
        "total": len(articles),
    }


@router.get("/articles/status")
def article_status(url: str = Query(...), user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    return vector_store.article_status(url)


@router.delete("/articles")
def delete_article(request: DeleteArticleRequest, user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    deleted_chunks = vector_store.delete_by_url(request.url)
    return {
        "status": "deleted" if deleted_chunks > 0 else "not_found",
        "url": request.url,
        "deleted_chunks": deleted_chunks,
    }
