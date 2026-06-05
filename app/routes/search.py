from fastapi import APIRouter
from pydantic import BaseModel

from app.services.vector_store import VectorStoreService

router = APIRouter()


class SearchRequest(BaseModel):
    query: str


@router.post("/search")
def search_articles(request: SearchRequest):
    results = VectorStoreService().query(request.query)
    return {
        "query": request.query,
        "results": results,
    }
