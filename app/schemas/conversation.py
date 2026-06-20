from typing import Any, Literal

from pydantic import BaseModel
from pydantic import Field


class MessageSync(BaseModel):
    type: Literal["user", "summary", "assistant", "error"]
    text: str = ""
    response: dict[str, Any] | None = None


class ConversationSyncRequest(BaseModel):
    title: str = Field(default="新聊天", max_length=500)
    source_url: str = ""
    summary: str = ""
    status: str = Field(default="", max_length=40)
    topic: str = Field(default="", max_length=80)
    stored_chunks: int = Field(default=0, ge=0)
    created_at_millis: int = Field(ge=0)
    updated_at_millis: int = Field(ge=0)
    messages: list[MessageSync] = Field(default_factory=list)


class ConversationSyncResponse(ConversationSyncRequest):
    id: str
    url: str = ""


class ConversationListResponse(BaseModel):
    conversations: list[ConversationSyncResponse]
    total: int
