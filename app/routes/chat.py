import re
from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.llm_service import LLMService
from app.services.query_intent import QueryIntentService
from app.services.vector_store import VectorStoreService

router = APIRouter()

RELEVANCE_THRESHOLD = 0.25


class ChatRequest(BaseModel):
    query: str
    mode: Literal["page", "global"] = "global"
    url: str | None = None


@router.post("/chat")
def chat(request: ChatRequest):
    intent = QueryIntentService().classify(
        request.query,
        has_url=bool(request.url),
    )

    if intent["retrieval_scope"] == "none":
        return _no_retrieval_response(intent)

    vector_store = VectorStoreService()
    page_chunks = []
    global_chunks = []

    if intent["retrieval_scope"] == "page_first":
        page_chunks = vector_store.query(
            request.query,
            metadata_filter={"url": request.url},
        )
        relevant_page_chunks = _high_relevance_chunks(page_chunks)

        if len(relevant_page_chunks) < 2:
            global_chunks = vector_store.query(request.query)
    else:
        global_chunks = vector_store.query(request.query)

    ranked_chunks = _rank_chunks(
        request.query,
        _merge_chunks(page_chunks, global_chunks),
    )
    relevant_chunks = _high_relevance_chunks(ranked_chunks)
    answer, source_type, selected_chunks = _answer_with_policy(
        request.query,
        intent["fallback_policy"],
        relevant_chunks,
        page_chunks,
        global_chunks,
    )

    return {
        "answer": answer,
        "sources": _build_sources(selected_chunks),
        "source_type": source_type,
        "intent": intent["intent"],
        "intent_reason": intent["reason"],
        "retrieval_scope": intent["retrieval_scope"],
        "fallback_policy": intent["fallback_policy"],
    }


def _no_retrieval_response(intent: dict) -> dict:
    if intent["fallback_policy"] == "ask_for_page":
        answer = "我无法确定你指的是哪篇文章，请先分享网页，或提供文章链接/标题。"
        source_type = "need_page_context"
    elif intent["fallback_policy"] == "unsupported_action":
        answer = "当前版本还不支持这个操作。"
        source_type = "unsupported_action"
    else:
        answer = "当前请求无法处理。"
        source_type = "unsupported_action"

    return {
        "answer": answer,
        "sources": [],
        "source_type": source_type,
        "intent": intent["intent"],
        "intent_reason": intent["reason"],
        "retrieval_scope": intent["retrieval_scope"],
        "fallback_policy": intent["fallback_policy"],
    }


def _answer_with_policy(
    query: str,
    fallback_policy: str,
    relevant_chunks: list[dict],
    page_chunks: list[dict],
    global_chunks: list[dict],
) -> tuple[str, str, list[dict]]:
    source_type = _source_type(page_chunks, global_chunks, relevant_chunks)

    if relevant_chunks:
        prefix = ""
        if fallback_policy == "no_guess_realtime":
            prefix = "以下回答基于知识库中已保存内容，可能不是实时最新。\n\n"
        return prefix + _build_context_answer(query, relevant_chunks), source_type, relevant_chunks

    if fallback_policy == "llm_allowed":
        return (
            _build_general_answer(
                query,
                "知识库中未找到足够相关内容，以下是通用回答",
            ),
            "llm_fallback",
            [],
        )

    if fallback_policy == "knowledge_then_llm":
        return (
            _build_general_answer(
                query,
                "知识库中未找到足够相关内容，以下是通用回答",
            ),
            "llm_fallback",
            [],
        )

    if fallback_policy == "no_guess_realtime":
        return (
            "这个问题需要实时信息。当前系统尚未接入实时工具，知识库中也没有足够相关内容。",
            "unsupported_realtime",
            [],
        )

    if fallback_policy == "no_llm_general_answer":
        return "没有在知识库中找到相关内容。", "no_knowledge_found", []

    return "当前请求无法处理。", "unsupported_action", []


def _high_relevance_chunks(chunks: list[dict]) -> list[dict]:
    return [chunk for chunk in chunks if chunk.get("score", 0) >= RELEVANCE_THRESHOLD]


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


def _source_type(
    page_chunks: list[dict],
    global_chunks: list[dict],
    relevant_chunks: list[dict],
) -> str:
    if not relevant_chunks:
        return "llm_fallback"

    has_page = any(chunk in relevant_chunks for chunk in page_chunks)
    has_global = any(chunk in relevant_chunks for chunk in global_chunks)

    if has_page and has_global:
        return "mixed"
    if has_page:
        return "page"
    return "knowledge_base"


def _build_general_answer(query: str, reason: str) -> str:
    try:
        answer = LLMService().answer_without_context(
            question=query,
            reason=reason,
        )
        if answer.startswith("LLM unavailable"):
            return "LLM unavailable"
        return answer
    except Exception:
        return "LLM unavailable"


def _build_context_answer(query: str, chunks: list[dict]) -> str:
    try:
        context_chunks = [
            f"[片段{index + 1}]\n{chunk['content']}"
            for index, chunk in enumerate(chunks)
        ]
        answer = LLMService().answer(question=query, context_chunks=context_chunks)
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
