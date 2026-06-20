from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.models.user import User
from app.routes.auth import get_current_user
from app.services.vector_store import VectorStoreService

router = APIRouter()


class SearchRequest(BaseModel):
    query: str


@router.post("/search")
def search_articles(request: SearchRequest, user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    results = vector_store.query(request.query)
    return {
        "query": request.query,
        "results": results,
    }
