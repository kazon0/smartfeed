import re
from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.llm_service import LLMService
from app.services.vector_store import VectorStoreService

router = APIRouter()


class ChatRequest(BaseModel):
    query: str
    mode: Literal["page", "global"] = "global"
    url: str | None = None


@router.post("/chat")
def chat(request: ChatRequest):
    vector_store = VectorStoreService()
    page_chunks = []
    global_chunks = []

    if request.url:
        page_chunks = vector_store.query(
            request.query,
            metadata_filter={"url": request.url},
        )

        if len(page_chunks) < 2:
            global_chunks = vector_store.query(request.query)
    else:
        global_chunks = vector_store.query(request.query)

    chunks = _rank_chunks(request.query, _merge_chunks(page_chunks, global_chunks))
    source_type = _source_type(page_chunks, global_chunks)
    answer = _build_answer(request.query, chunks, source_type)

    return {
        "answer": answer,
        "sources": _build_sources(chunks),
        "source_type": source_type,
    }


def _merge_chunks(primary_chunks: list[dict], secondary_chunks: list[dict]) -> list[dict]:
    merged = []
    seen = set()

    for chunk in primary_chunks + secondary_chunks:
        key = (
            chunk.get("metadata", {}).get("url", ""),
            chunk.get("metadata", {}).get("chunk_index", ""),
            chunk.get("content", ""),
        )
        if key in seen:
            continue
        seen.add(key)
        merged.append(chunk)

    return merged


def _rank_chunks(query: str, chunks: list[dict]) -> list[dict]:
    terms = _query_terms(query)

    def rank_score(chunk: dict) -> float:
        content = chunk.get("content", "")
        keyword_hits = sum(1 for term in terms if term and term in content)
        return chunk.get("score", 0) + keyword_hits

    return sorted(chunks, key=rank_score, reverse=True)


def _query_terms(query: str) -> list[str]:
    normalized = re.sub(r"\s+", "", query.strip())
    terms = {normalized}

    for size in (2, 3, 4):
        for index in range(0, max(0, len(normalized) - size + 1)):
            terms.add(normalized[index : index + size])

    return [term for term in terms if len(term) >= 2]


def _source_type(page_chunks: list[dict], global_chunks: list[dict]) -> str:
    if page_chunks:
        return "page"
    if global_chunks:
        return "knowledge_base"
    return "llm_fallback"


def _build_answer(query: str, chunks: list[dict], source_type: str) -> str:
    llm = LLMService()

    try:
        if chunks:
            context_chunks = [
                f"[片段{index + 1}]\n{chunk['content']}"
                for index, chunk in enumerate(chunks)
            ]
            answer = llm.answer(question=query, context_chunks=context_chunks)
        else:
            answer = llm.answer_without_context(
                question=query,
                reason="知识库中未找到相关内容，本回答未基于知识库内容",
            )

        if answer.startswith("LLM unavailable"):
            return "LLM unavailable"
        return answer
    except Exception:
        return "LLM unavailable"


def _build_sources(chunks: list[dict]) -> list[dict]:
    sources = []
    for chunk in chunks:
        metadata = chunk.get("metadata", {})
        sources.append(
            {
                "url": metadata.get("url", ""),
                "title": metadata.get("title", ""),
                "chunk_index": metadata.get("chunk_index"),
                "score": chunk.get("score", 0),
                "content": chunk.get("content", ""),
            }
        )
    return sources
