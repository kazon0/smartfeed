from typing import Literal

from fastapi import APIRouter
from fastapi import Depends
from fastapi import WebSocket
from fastapi import WebSocketDisconnect
from pydantic import BaseModel
from pydantic import Field
from pydantic import ValidationError
from sqlalchemy.orm import Session

from app.db.dependencies import get_db
from app.models.user import User
from app.security.tokens import decode_access_token
from app.services.auth_service import AuthService
from app.services.chat_service import ChatService
from app.services.llm_service import LLMService
from app.services.query_intent import QueryIntentService
from app.services.vector_store import VectorStoreService

router = APIRouter()


class WebSocketChatRequest(BaseModel):
    query: str
    mode: Literal["page", "global"] = "global"
    url: str | None = None
    history: list[dict[str, str]] = Field(default_factory=list)


@router.websocket("/ws/chat")
async def websocket_chat(websocket: WebSocket, db: Session = Depends(get_db)):
    await websocket.accept()
    await websocket.send_json({"type": "status", "stage": "connected"})

    user = _authenticate_websocket(websocket, db)
    if user is None:
        await websocket.send_json(
            {
                "type": "error",
                "error_code": "UNAUTHORIZED",
                "message": "Invalid or missing access token.",
            }
        )
        await websocket.close(code=1008)
        return

    await websocket.send_json({"type": "status", "stage": "authenticated"})

    try:
        while True:
            payload = await websocket.receive_json()
            try:
                request = WebSocketChatRequest.model_validate(payload)
            except ValidationError as exc:
                await websocket.send_json(
                    {
                        "type": "error",
                        "error_code": "INVALID_REQUEST",
                        "message": "Invalid chat request.",
                        "details": exc.errors(),
                    }
                )
                continue

            await websocket.send_json({"type": "status", "stage": "retrieving"})
            await websocket.send_json({"type": "status", "stage": "answering"})
            try:
                response = _chat_response(request=request, user=user)
            except Exception:
                await websocket.send_json(
                    {
                        "type": "error",
                        "error_code": "CHAT_FAILED",
                        "message": "Failed to generate chat response.",
                    }
                )
                continue

            await websocket.send_json(
                {
                    "type": "completed",
                    "stage": "completed",
                    "response": response,
                }
            )
    except WebSocketDisconnect:
        return


def _authenticate_websocket(websocket: WebSocket, db: Session) -> User | None:
    token = websocket.query_params.get("token") or _authorization_token(websocket)
    if not token:
        return None
    user_id = decode_access_token(token)
    if user_id is None:
        return None
    return AuthService().get_user(db, user_id)


def _authorization_token(websocket: WebSocket) -> str | None:
    header = websocket.headers.get("authorization", "")
    scheme, _, token = header.partition(" ")
    if scheme.lower() != "bearer" or not token:
        return None
    return token


def _chat_response(request: WebSocketChatRequest, user: User) -> dict:
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
