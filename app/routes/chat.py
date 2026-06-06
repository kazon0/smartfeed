import re
from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from app.services.llm_service import LLMService
from app.services.query_intent import QueryIntentService
from app.services.vector_store import VectorStoreService

router = APIRouter()

RELEVANCE_THRESHOLD = 0.25
SOURCE_PREVIEW_LENGTH = 1200
FULL_PAGE_CONTEXT_CHUNK_LIMIT = 30


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
    if request.url:
        return _chat_with_current_page(
            request.query,
            request.url,
            intent,
            vector_store,
        )

    global_chunks = vector_store.query(request.query)
    ranked_chunks = _rank_chunks(request.query, global_chunks)
    relevant_chunks = _high_relevance_chunks(ranked_chunks)
    answer, source_type, selected_chunks = _answer_with_policy(
        request.query,
        intent["fallback_policy"],
        relevant_chunks,
        [],
        global_chunks,
    )

    return {
        "answer": answer,
        "sources": _build_sources(selected_chunks, request.query),
        "source_type": source_type,
        "intent": intent["intent"],
        "intent_reason": intent["reason"],
        "retrieval_scope": intent["retrieval_scope"],
        "fallback_policy": intent["fallback_policy"],
    }


def _chat_with_current_page(
    query: str,
    url: str,
    intent: dict,
    vector_store: VectorStoreService,
) -> dict:
    page_chunks = vector_store.query(
        query,
        metadata_filter={"url": url},
    )
    ranked_page_chunks = _rank_chunks(query, page_chunks)
    relevant_page_chunks = _high_relevance_chunks(ranked_page_chunks)
    all_page_chunks = vector_store.get_chunks_by_url(url)

    if all_page_chunks and _is_page_wide_query(query, intent):
        selected_page_chunks = _select_page_context(all_page_chunks)
        answer, source_type, selected_chunks = _answer_with_policy(
            query,
            intent["fallback_policy"],
            selected_page_chunks,
            selected_page_chunks,
            [],
        )
        return {
            "answer": answer,
            "sources": _build_sources(selected_chunks, query),
            "source_type": source_type,
            "intent": intent["intent"],
            "intent_reason": intent["reason"],
            "retrieval_scope": "page_first",
            "fallback_policy": intent["fallback_policy"],
        }

    if relevant_page_chunks:
        expanded_page_chunks = _expand_page_chunks(
            relevant_page_chunks,
            all_page_chunks,
        )
        answer, source_type, selected_chunks = _answer_with_policy(
            query,
            intent["fallback_policy"],
            expanded_page_chunks,
            expanded_page_chunks,
            [],
        )
        return {
            "answer": answer,
            "sources": _build_sources(selected_chunks, query),
            "source_type": source_type,
            "intent": intent["intent"],
            "intent_reason": intent["reason"],
            "retrieval_scope": "page_first",
            "fallback_policy": intent["fallback_policy"],
        }

    saved_sources = [
        source
        for source in vector_store.list_sources()
        if source.get("url") != url
    ]

    return {
        "answer": (
            "当前网页没有找到可用于回答的内容。"
            "你可以改为询问知识库中已有内容，或从下方已保存文章中选择一个继续提问。"
        ),
        "sources": saved_sources,
        "source_type": "page_not_found_with_suggestions",
        "intent": intent["intent"],
        "intent_reason": intent["reason"],
        "retrieval_scope": "page_first",
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


def _is_page_wide_query(query: str, intent: dict) -> bool:
    normalized = re.sub(r"\s+", "", query.strip())
    page_wide_terms = (
        "这篇",
        "本文",
        "文章",
        "网页",
        "总结",
        "概括",
        "讲了什么",
        "主要内容",
        "有哪些",
        "哪几种",
        "几种",
        "多少种",
        "十种",
        "全部",
        "所有",
        "列出",
        "清单",
        "方法",
        "算法",
        "步骤",
    )
    return intent.get("intent") == "page_reference" or any(
        term in normalized for term in page_wide_terms
    )


def _select_page_context(chunks: list[dict]) -> list[dict]:
    sorted_chunks = sorted(
        chunks,
        key=lambda chunk: chunk.get("metadata", {}).get("chunk_index", 0),
    )
    return sorted_chunks[:FULL_PAGE_CONTEXT_CHUNK_LIMIT]


def _expand_page_chunks(
    relevant_chunks: list[dict],
    all_page_chunks: list[dict],
    neighbor_window: int = 1,
    limit: int = 8,
) -> list[dict]:
    if not all_page_chunks:
        return relevant_chunks[:limit]

    by_index = {
        chunk.get("metadata", {}).get("chunk_index"): chunk
        for chunk in all_page_chunks
    }
    selected_indexes = []

    for chunk in relevant_chunks:
        chunk_index = chunk.get("metadata", {}).get("chunk_index")
        if chunk_index is None:
            continue

        for index in range(chunk_index - neighbor_window, chunk_index + neighbor_window + 1):
            if index in by_index and index not in selected_indexes:
                selected_indexes.append(index)

        if len(selected_indexes) >= limit:
            break

    selected_indexes = sorted(selected_indexes)[:limit]
    return [by_index[index] for index in selected_indexes]


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
            _format_context_chunk(index, chunk)
            for index, chunk in enumerate(chunks)
        ]
        answer = LLMService().answer(question=query, context_chunks=context_chunks)
        if answer.startswith("LLM unavailable"):
            return "LLM unavailable"
        return answer
    except Exception:
        return "LLM unavailable"


def _format_context_chunk(index: int, chunk: dict) -> str:
    metadata = chunk.get("metadata", {})
    title = metadata.get("title", "")
    url = metadata.get("url", "")
    chunk_index = metadata.get("chunk_index")
    header = f"[{index + 1}]"
    if title:
        header += f" title: {title}"
    if url:
        header += f" url: {url}"
    if chunk_index is not None:
        header += f" chunk_index: {chunk_index}"
    section_title = metadata.get("section_title", "")
    section_index = metadata.get("section_index")
    if section_title:
        header += f" section_title: {section_title}"
    if section_index is not None:
        header += f" section_index: {section_index}"
    return f"{header}\n{chunk.get('content', '')}"


def _build_sources(chunks: list[dict], query: str | None = None) -> list[dict]:
    sources = _group_source_chunks(chunks)
    if query and sources:
        _attach_source_notes(query, sources)
    return sources


def _group_source_chunks(chunks: list[dict]) -> list[dict]:
    sources = []
    current_source = None
    previous_key = None
    previous_index = None

    for chunk in chunks:
        metadata = chunk.get("metadata", {})
        url = metadata.get("url", "")
        title = metadata.get("title", "")
        section_title = metadata.get("section_title", "")
        section_index = metadata.get("section_index")
        chunk_index = metadata.get("chunk_index")
        source_key = (url, title, section_index, section_title)
        is_contiguous = (
            current_source is not None
            and source_key == previous_key
            and isinstance(chunk_index, int)
            and isinstance(previous_index, int)
            and chunk_index == previous_index + 1
        )

        if not is_contiguous:
            display_title = _display_title(title, url)
            current_source = {
                "url": url,
                "title": title,
                "display_title": display_title,
                "section_title": section_title,
                "section_index": section_index,
                "chunk_index": chunk_index,
                "chunk_indexes": [],
                "score": chunk.get("score", 0),
                "content_preview": "",
                "source_summary": "",
                "_content_parts": [],
            }
            sources.append(current_source)

        current_source["score"] = max(
            current_source.get("score") or 0,
            chunk.get("score", 0) or 0,
        )
        if chunk_index is not None:
            current_source["chunk_indexes"].append(chunk_index)
        current_source["_content_parts"].append(chunk.get("content", ""))

        previous_key = source_key
        previous_index = chunk_index

    for source in sources:
        content = "\n\n".join(part.strip() for part in source["_content_parts"] if part.strip())
        source["content_preview"] = _readable_preview(content)
        del source["_content_parts"]

    return sources


def _readable_preview(content: str) -> str:
    normalized = re.sub(r"\n{3,}", "\n\n", content.strip())
    if len(normalized) <= SOURCE_PREVIEW_LENGTH:
        return normalized
    return normalized[:SOURCE_PREVIEW_LENGTH].rstrip() + "..."


def _display_title(title: str, url: str) -> str:
    cleaned = re.sub(r"[-_|]腾讯云开发者社区[-_]?.*$", "", title or "").strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    if cleaned:
        return cleaned
    return url or "Untitled source"


def _attach_source_notes(query: str, sources: list[dict]) -> None:
    source_texts = [source["content_preview"] for source in sources]

    try:
        describe_sources = getattr(LLMService(), "describe_sources")
        notes = describe_sources(query, source_texts)
    except Exception:
        notes = []

    for index, source in enumerate(sources):
        note = notes[index].strip() if index < len(notes) and notes[index] else ""
        if note and not note.startswith("LLM unavailable"):
            source["source_note"] = note
            source["source_summary"] = note
