from typing import Annotated

from fastapi import APIRouter
from fastapi import Depends
from fastapi import HTTPException
from fastapi import Path
from fastapi import status
from sqlalchemy.orm import Session

from app.db.dependencies import get_db
from app.models.user import User
from app.routes.auth import get_current_user
from app.schemas.conversation import ConversationListResponse
from app.schemas.conversation import ConversationSyncRequest
from app.schemas.conversation import ConversationSyncResponse
from app.services.conversation_service import ConversationIdConflictError
from app.services.conversation_service import ConversationService

router = APIRouter(prefix="/conversations", tags=["conversations"])


@router.get("", response_model=ConversationListResponse)
def list_conversations(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = ConversationService()
    conversations = [
        service.to_response(db, conversation)
        for conversation in service.list_for_owner(db, owner_id=str(user.id))
    ]
    return {"conversations": conversations, "total": len(conversations)}


@router.put("/{conversation_id}", response_model=ConversationSyncResponse)
def replace_conversation(
    conversation_id: Annotated[str, Path(min_length=1, max_length=64)],
    request: ConversationSyncRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    service = ConversationService()
    try:
        conversation = service.replace(
            db,
            owner_id=str(user.id),
            conversation_id=conversation_id,
            data=request,
        )
    except ConversationIdConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Conversation id is already in use.",
        ) from exc
    return service.to_response(db, conversation)


@router.delete("/{conversation_id}")
def delete_conversation(
    conversation_id: Annotated[str, Path(min_length=1, max_length=64)],
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    deleted = ConversationService().delete(
        db,
        owner_id=str(user.id),
        conversation_id=conversation_id,
    )
    return {
        "status": "deleted" if deleted else "not_found",
        "id": conversation_id,
    }
