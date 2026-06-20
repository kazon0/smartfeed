from fastapi import APIRouter, Depends

from app.models.user import User
from app.routes.auth import get_current_user
from app.services.vector_store import VectorStoreService

router = APIRouter()


@router.get("/stats")
def get_stats(user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    return vector_store.stats()
