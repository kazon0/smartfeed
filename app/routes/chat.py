from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.models.user import User
from app.routes.auth import get_current_user
from app.services.chat_service import ChatService
from app.services.llm_service import LLMService
from app.services.query_intent import QueryIntentService
from app.services.vector_store import VectorStoreService

router = APIRouter()


class ChatRequest(BaseModel):
    query: str
    mode: Literal["page", "global"] = "global"
    url: str | None = None
    history: list[dict[str, str]] = []


@router.post("/chat")
def chat(request: ChatRequest, user: User = Depends(get_current_user)):
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    return ChatService(
        vector_store_factory=lambda: vector_store,
        llm_service_factory=LLMService,
        intent_service_factory=QueryIntentService,
    ).chat(
        query=request.query,
        url=request.url,
        mode=request.mode,
        history=request.history,
    )
