from fastapi import APIRouter
from fastapi import Query
from pydantic import BaseModel

from app.services.vector_store import VectorStoreService

router = APIRouter()


class DeleteArticleRequest(BaseModel):
    url: str


@router.get("/articles")
def list_articles():
    articles = VectorStoreService().list_articles()
    return {
        "articles": articles,
        "total": len(articles),
    }


@router.get("/articles/status")
def article_status(url: str = Query(...)):
    return VectorStoreService().article_status(url)


@router.delete("/articles")
def delete_article(request: DeleteArticleRequest):
    deleted_chunks = VectorStoreService().delete_by_url(request.url)
    return {
        "status": "deleted" if deleted_chunks > 0 else "not_found",
        "url": request.url,
        "deleted_chunks": deleted_chunks,
    }
