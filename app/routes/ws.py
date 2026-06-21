import asyncio
from typing import Literal
from collections.abc import Callable

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
from app.services.article_service import ArticleService
from app.services.auth_service import AuthService
from app.services.chat_service import ChatService
from app.services.llm_service import LLMService
from app.services.query_intent import QueryIntentService
from app.services.vector_store import VectorStoreService
from app.services.web_parser import WebParserService

router = APIRouter()


class WebSocketChatRequest(BaseModel):
    query: str
    mode: Literal["page", "global"] = "global"
    url: str | None = None
    history: list[dict[str, str]] = Field(default_factory=list)


class WebSocketUploadRequest(BaseModel):
    url: str


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
                response = await _stream_chat_response(
                    websocket=websocket,
                    request=request,
                    user=user,
                )
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


@router.websocket("/ws/upload")
async def websocket_upload(websocket: WebSocket, db: Session = Depends(get_db)):
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
                request = WebSocketUploadRequest.model_validate(payload)
            except ValidationError as exc:
                await websocket.send_json(
                    {
                        "type": "error",
                        "error_code": "INVALID_REQUEST",
                        "message": "Invalid upload request.",
                        "details": exc.errors(),
                    }
                )
                continue

            try:
                response = await _stream_upload_response(
                    websocket=websocket,
                    request=request,
                    user=user,
                    db=db,
                )
            except Exception:
                await websocket.send_json(
                    {
                        "type": "error",
                        "error_code": "UPLOAD_FAILED",
                        "message": "Failed to upload article.",
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


async def _stream_chat_response(
    websocket: WebSocket,
    request: WebSocketChatRequest,
    user: User,
) -> dict:
    loop = asyncio.get_running_loop()
    queue: asyncio.Queue[dict | None] = asyncio.Queue()

    def on_delta(text: str) -> None:
        loop.call_soon_threadsafe(
            queue.put_nowait,
            {"type": "delta", "text": text},
        )

    chat_task = asyncio.create_task(
        asyncio.to_thread(
            _chat_response,
            request,
            user,
            on_delta,
        )
    )

    while True:
        if chat_task.done() and queue.empty():
            break

        try:
            event = await asyncio.wait_for(queue.get(), timeout=0.1)
        except asyncio.TimeoutError:
            continue

        if event is not None:
            await websocket.send_json(event)

    return await chat_task


async def _stream_upload_response(
    websocket: WebSocket,
    request: WebSocketUploadRequest,
    user: User,
    db: Session,
) -> dict:
    loop = asyncio.get_running_loop()
    queue: asyncio.Queue[dict | None] = asyncio.Queue()

    def emit(event: dict) -> None:
        loop.call_soon_threadsafe(queue.put_nowait, event)

    upload_task = asyncio.create_task(
        asyncio.to_thread(
            _upload_response,
            request,
            user,
            db,
            emit,
        )
    )

    while True:
        if upload_task.done() and queue.empty():
            break

        try:
            event = await asyncio.wait_for(queue.get(), timeout=0.1)
        except asyncio.TimeoutError:
            continue

        if event is not None:
            await websocket.send_json(event)

    return await upload_task


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


def _chat_response(
    request: WebSocketChatRequest,
    user: User,
    answer_delta_callback: Callable[[str], None] | None = None,
) -> dict:
    vector_store = VectorStoreService()
    vector_store.user_id = str(user.id)
    return ChatService(
        vector_store_factory=lambda: vector_store,
        llm_service_factory=LLMService,
        intent_service_factory=QueryIntentService,
        answer_delta_callback=answer_delta_callback,
    ).chat(
        query=request.query,
        url=request.url,
        mode=request.mode,
        history=request.history,
    )


def _upload_response(
    request: WebSocketUploadRequest,
    user: User,
    db: Session,
    emit: Callable[[dict], None],
) -> dict:
    emit({"type": "status", "stage": "parsing"})
    data = WebParserService().prepare(request.url)
    stored_chunks = 0
    summary = ""

    if "error" not in data:
        if not data["chunks"]:
            return {
                "status": "failed",
                "error": "No readable article content extracted",
                "url": request.url,
                "stored_chunks": 0,
            }

        llm_service = LLMService()
        emit({"type": "status", "stage": "summarizing"})
        if hasattr(llm_service, "summarize_stream"):
            summary_parts = []
            for delta in llm_service.summarize_stream(data["content"]):
                summary_parts.append(delta)
                emit({"type": "delta", "target": "summary", "text": delta})
            summary = "".join(summary_parts)
        else:
            summary = llm_service.summarize(data["content"])

        emit({"type": "status", "stage": "classifying"})
        vector_store = VectorStoreService()
        vector_store.user_id = str(user.id)
        topic_result = llm_service.classify_topic(
            title=data["title"],
            url=data["url"],
            summary=summary,
            content=data["content"],
        )
        topic = topic_result["topic"]
        if topic_result["source"] != "llm" or topic_result["confidence"] < 0.55:
            topic = vector_store.classify_topic(
                url=data["url"],
                title=data["title"],
                content=data["content"],
            )
            topic_result = {
                **topic_result,
                "topic": topic,
                "source": "rule_fallback",
            }

        data["metadata"]["topic"] = topic
        data["metadata"]["topic_source"] = topic_result["source"]
        data["metadata"]["topic_confidence"] = topic_result["confidence"]
        data["metadata"]["topic_reason"] = topic_result["reason"]
        metadata = {
            **data["metadata"],
            "url": data["url"],
            "title": data["title"],
        }
        emit({"type": "status", "stage": "storing"})
        vector_store.delete_by_url(data["url"])
        stored_chunks = vector_store.add_chunks(
            data["chunks"],
            metadata,
            data.get("chunk_metadata"),
        )
        ArticleService().upsert(
            db,
            owner_id=str(user.id),
            url=data["url"],
            title=data["title"],
            topic=topic,
            summary=summary,
            chunk_count=stored_chunks,
        )

    return {
        "status": "received",
        "data": data,
        "stored_chunks": stored_chunks,
        "summary": summary,
    }
