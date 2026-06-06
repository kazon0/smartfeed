from fastapi import APIRouter
from pydantic import BaseModel

from app.services.llm_service import LLMService
from app.services.vector_store import VectorStoreService
from app.services.web_parser import WebParserService

router = APIRouter()


class UploadRequest(BaseModel):
    url: str


@router.post("/upload")
def upload_article(request: UploadRequest):
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

        metadata = {
            **data["metadata"],
            "url": data["url"],
            "title": data["title"],
        }
        vector_store = VectorStoreService()
        vector_store.delete_by_url(data["url"])
        stored_chunks = vector_store.add_chunks(
            data["chunks"],
            metadata,
            data.get("chunk_metadata"),
        )
        summary = LLMService().summarize(data["content"])

    return {
        "status": "received",
        "data": data,
        "stored_chunks": stored_chunks,
        "summary": summary,
    }
