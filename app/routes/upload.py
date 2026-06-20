from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.db.dependencies import get_db
from app.models.user import User
from app.routes.auth import get_current_user
from app.services.article_service import ArticleService
from app.services.llm_service import LLMService
from app.services.vector_store import VectorStoreService
from app.services.web_parser import WebParserService

router = APIRouter()


class UploadRequest(BaseModel):
    url: str


@router.post("/upload")
def upload_article(
    request: UploadRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
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
        summary = llm_service.summarize(data["content"])
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
