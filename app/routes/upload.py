from fastapi import APIRouter
from pydantic import BaseModel

from app.services.web_parser import WebParserService

router = APIRouter()


class UploadRequest(BaseModel):
    url: str


@router.post("/upload")
def upload_article(request: UploadRequest):
    data = WebParserService().prepare(request.url)
    return {
        "status": "received",
        "data": data,
    }
