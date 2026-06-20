import json
from datetime import datetime
from datetime import timezone

from sqlalchemy import delete
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.article import Article
from app.models.conversation import Conversation
from app.models.message import Message
from app.models.user import utc_now
from app.schemas.conversation import ConversationSyncRequest


class ConversationIdConflictError(Exception):
    pass


class ConversationService:
    def replace(
        self,
        db: Session,
        *,
        owner_id: str,
        conversation_id: str,
        data: ConversationSyncRequest,
    ) -> Conversation:
        conversation = db.get(Conversation, conversation_id)
        if conversation is not None and conversation.owner_id != owner_id:
            raise ConversationIdConflictError
        incoming_updated_at = self._from_millis(data.updated_at_millis)
        if conversation is not None and self._as_utc(conversation.updated_at) > incoming_updated_at:
            return conversation
        is_new = conversation is None
        if conversation is None:
            conversation = Conversation(id=conversation_id, owner_id=owner_id)
            db.add(conversation)

        conversation.article_id = self._article_id(
            db,
            owner_id=owner_id,
            source_url=data.source_url,
        )
        conversation.title = data.title.strip() or "新聊天"
        conversation.source_url = data.source_url.strip()
        conversation.summary = data.summary
        conversation.status = data.status
        conversation.topic = data.topic
        conversation.stored_chunks = data.stored_chunks
        if is_new:
            conversation.created_at = self._from_millis(data.created_at_millis)
        conversation.updated_at = incoming_updated_at

        db.flush()
        db.execute(delete(Message).where(Message.conversation_id == conversation.id))
        for position, item in enumerate(data.messages):
            db.add(
                Message(
                    id=f"{conversation.id}:{position}",
                    conversation_id=conversation.id,
                    role=item.type,
                    content=item.text,
                    payload=json.dumps(item.response or {}, ensure_ascii=False),
                    position=position,
                )
            )
        db.commit()
        db.refresh(conversation)
        return conversation

    def list_for_owner(self, db: Session, *, owner_id: str) -> list[Conversation]:
        return list(
            db.scalars(
                select(Conversation)
                .where(Conversation.owner_id == owner_id)
                .order_by(Conversation.updated_at.desc())
            )
        )

    def messages(self, db: Session, *, conversation_id: str) -> list[Message]:
        return list(
            db.scalars(
                select(Message)
                .where(Message.conversation_id == conversation_id)
                .order_by(Message.position)
            )
        )

    def delete(self, db: Session, *, owner_id: str, conversation_id: str) -> bool:
        conversation = db.scalar(
            select(Conversation).where(
                Conversation.id == conversation_id,
                Conversation.owner_id == owner_id,
            )
        )
        if conversation is None:
            return False
        db.delete(conversation)
        db.commit()
        return True

    def to_response(self, db: Session, conversation: Conversation) -> dict:
        return {
            "id": conversation.id,
            "title": conversation.title,
            "url": conversation.source_url,
            "source_url": conversation.source_url,
            "summary": conversation.summary,
            "status": conversation.status,
            "topic": conversation.topic,
            "stored_chunks": conversation.stored_chunks,
            "created_at_millis": self._to_millis(conversation.created_at),
            "updated_at_millis": self._to_millis(conversation.updated_at),
            "messages": [
                self._message_response(item)
                for item in self.messages(db, conversation_id=conversation.id)
            ],
        }

    def _article_id(self, db: Session, *, owner_id: str, source_url: str) -> str | None:
        if not source_url:
            return None
        return db.scalar(
            select(Article.id).where(
                Article.owner_id == owner_id,
                Article.url == source_url,
            )
        )

    def _message_response(self, message: Message) -> dict:
        try:
            response = json.loads(message.payload) if message.payload else {}
        except json.JSONDecodeError:
            response = {}
        return {
            "type": message.role,
            "text": message.content,
            "response": response or None,
        }

    def _from_millis(self, value: int) -> datetime:
        if value <= 0:
            return utc_now()
        return datetime.fromtimestamp(value / 1000, tz=timezone.utc)

    def _to_millis(self, value: datetime) -> int:
        return int(self._as_utc(value).timestamp() * 1000)

    def _as_utc(self, value: datetime) -> datetime:
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)
