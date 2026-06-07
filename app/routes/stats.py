from fastapi import APIRouter

from app.services.vector_store import VectorStoreService

router = APIRouter()


@router.get("/stats")
def get_stats():
    return VectorStoreService().stats()
