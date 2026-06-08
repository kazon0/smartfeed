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
LLM_RERANK_CANDIDATE_LIMIT = 10
ERROR_CODE_BY_SOURCE_TYPE = {
    "need_page_context": "NEED_PAGE_CONTEXT",
    "page_not_found_with_suggestions": "PAGE_CONTENT_NOT_FOUND",
    "unsupported_realtime": "REALTIME_UNSUPPORTED",
    "no_knowledge_found": "NO_KNOWLEDGE_FOUND",
    "unsupported_action": "UNSUPPORTED_ACTION",
}


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
    rewritten_query = _rewrite_query(request.query, request.url)
    if request.url:
        return _chat_with_current_page(
            request.query,
            rewritten_query,
            request.url,
            intent,
            vector_store,
        )

    global_vector_chunks = vector_store.query(rewritten_query)
    global_keyword_chunks = _keyword_retrieve_chunks(
        rewritten_query,
        _get_all_chunks(vector_store),
    )
    global_chunks = _merge_chunks(global_vector_chunks, global_keyword_chunks)
    ranked_chunks = _rank_chunks(rewritten_query, global_chunks)
    ranked_chunks = _llm_rerank_chunks(request.query, ranked_chunks)
    relevant_chunks = _high_relevance_chunks(ranked_chunks)
    answer, source_type, selected_chunks = _answer_with_policy(
        request.query,
        intent["fallback_policy"],
        relevant_chunks,
        [],
        global_chunks,
    )

    return _chat_response(
        answer=answer,
        sources=_build_sources(selected_chunks, request.query),
        source_type=source_type,
        intent=intent,
        retrieval_scope=intent["retrieval_scope"],
    )


def _chat_with_current_page(
    query: str,
    rewritten_query: str,
    url: str,
    intent: dict,
    vector_store: VectorStoreService,
) -> dict:
    page_chunks = vector_store.query(
        rewritten_query,
        metadata_filter={"url": url},
    )
    ranked_page_chunks = _rank_chunks(rewritten_query, page_chunks)
    all_page_chunks = vector_store.get_chunks_by_url(url)
    keyword_page_chunks = _keyword_retrieve_chunks(rewritten_query, all_page_chunks)
    ranked_page_chunks = _rank_chunks(
        rewritten_query,
        _merge_chunks(ranked_page_chunks, keyword_page_chunks),
    )
    ranked_page_chunks = _llm_rerank_chunks(query, ranked_page_chunks)
    relevant_page_chunks = _high_relevance_chunks(ranked_page_chunks)

    if all_page_chunks and _is_page_wide_query(query, intent):
        selected_page_chunks = _select_page_context(all_page_chunks)
        answer, source_type, selected_chunks = _answer_with_policy(
            query,
            intent["fallback_policy"],
            selected_page_chunks,
            selected_page_chunks,
            [],
        )
        return _chat_response(
            answer=answer,
            sources=_build_sources(selected_chunks, query),
            source_type=source_type,
            intent=intent,
            retrieval_scope="page_first",
        )

    if relevant_page_chunks:
        expanded_page_chunks = _expand_page_context(
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
        return _chat_response(
            answer=answer,
            sources=_build_sources(selected_chunks, query),
            source_type=source_type,
            intent=intent,
            retrieval_scope="page_first",
        )

    saved_sources = [
        source
        for source in vector_store.list_sources()
        if source.get("url") != url
    ]

    answer = (
            "当前网页没有找到可用于回答的内容。"
            "你可以改为询问知识库中已有内容，或从下方已保存文章中选择一个继续提问。"
        )

    return _chat_response(
        answer=answer,
        sources=saved_sources,
        source_type="page_not_found_with_suggestions",
        intent=intent,
        retrieval_scope="page_first",
    )


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

    return _chat_response(
        answer=answer,
        sources=[],
        source_type=source_type,
        intent=intent,
        retrieval_scope=intent["retrieval_scope"],
    )


def _rewrite_query(query: str, url: str | None = None) -> str:
    try:
        result = LLMService().rewrite_query(query, url=url)
    except Exception:
        return query

    rewritten_query = result.get("query", "")
    if not isinstance(rewritten_query, str) or not rewritten_query.strip():
        return query
    return rewritten_query.strip()


def _chat_response(
    answer: str,
    sources: list[dict],
    source_type: str,
    intent: dict,
    retrieval_scope: str,
) -> dict:
    error_code = _error_code(source_type, answer)
    return {
        "status": "failed" if error_code else "ok",
        "error_code": error_code,
        "message": _response_message(error_code, answer),
        "answer": answer,
        "sources": sources,
        "source_type": source_type,
        "intent": intent["intent"],
        "intent_reason": intent["reason"],
        "retrieval_scope": retrieval_scope,
        "fallback_policy": intent["fallback_policy"],
    }


def _error_code(source_type: str, answer: str) -> str | None:
    if answer == "LLM unavailable":
        return "LLM_UNAVAILABLE"
    return ERROR_CODE_BY_SOURCE_TYPE.get(source_type)


def _response_message(error_code: str | None, answer: str) -> str:
    if not error_code:
        return ""
    if error_code == "LLM_UNAVAILABLE":
        return "LLM service is unavailable."
    return answer


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


def _expand_page_context(
    relevant_chunks: list[dict],
    all_page_chunks: list[dict],
) -> list[dict]:
    section_chunks = _expand_section_chunks(relevant_chunks, all_page_chunks)
    if section_chunks:
        return section_chunks
    return _expand_page_chunks(relevant_chunks, all_page_chunks)


def _expand_section_chunks(
    relevant_chunks: list[dict],
    all_page_chunks: list[dict],
    limit: int = 12,
) -> list[dict]:
    if not all_page_chunks:
        return []

    selected_sections = []
    for chunk in relevant_chunks:
        metadata = chunk.get("metadata", {})
        section_index = metadata.get("section_index")
        if section_index is None or section_index in selected_sections:
            continue
        selected_sections.append(section_index)

    if not selected_sections:
        return []

    selected_chunks = [
        chunk
        for chunk in all_page_chunks
        if chunk.get("metadata", {}).get("section_index") in selected_sections
    ]
    return sorted(
        selected_chunks,
        key=lambda chunk: chunk.get("metadata", {}).get("chunk_index", 0),
    )[:limit]


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


def _keyword_retrieve_chunks(
    query: str,
    chunks: list[dict],
    limit: int = 5,
) -> list[dict]:
    terms = _query_terms(query)
    if not terms or not chunks:
        return []

    scored_chunks = []
    for chunk in chunks:
        searchable_text = _chunk_search_text(chunk)
        hits = sum(1 for term in terms if term in searchable_text)
        if hits <= 0:
            continue

        keyword_score = min(1.0, 0.3 + hits * 0.08)
        scored_chunk = {
            **chunk,
            "score": max(chunk.get("score", 0), keyword_score),
        }
        scored_chunks.append((hits, scored_chunk))

    scored_chunks.sort(
        key=lambda item: (
            item[0],
            item[1].get("score", 0),
            -item[1].get("metadata", {}).get("chunk_index", 0),
        ),
        reverse=True,
    )
    return [chunk for _, chunk in scored_chunks[:limit]]


def _get_all_chunks(vector_store: VectorStoreService) -> list[dict]:
    get_all_chunks = getattr(vector_store, "get_all_chunks", None)
    if not get_all_chunks:
        return []
    return get_all_chunks()


def _chunk_search_text(chunk: dict) -> str:
    metadata = chunk.get("metadata", {})
    return "".join(
        [
            str(metadata.get("title", "")),
            str(metadata.get("section_title", "")),
            str(chunk.get("content", "")),
        ]
    )


def _rank_chunks(query: str, chunks: list[dict]) -> list[dict]:
    terms = _query_terms(query)

    def rank_score(chunk: dict) -> float:
        content = chunk.get("content", "")
        keyword_hits = sum(1 for term in terms if term and term in content)
        return chunk.get("score", 0) + keyword_hits

    return sorted(chunks, key=rank_score, reverse=True)


def _llm_rerank_chunks(query: str, chunks: list[dict]) -> list[dict]:
    if len(chunks) <= 1:
        return chunks

    candidates = chunks[:LLM_RERANK_CANDIDATE_LIMIT]
    try:
        candidate_texts = [
            _format_context_chunk(index, chunk)
            for index, chunk in enumerate(candidates)
        ]
        indexes = LLMService().rerank_chunks(query, candidate_texts)
    except Exception:
        return chunks

    if not indexes:
        return chunks

    selected = [candidates[index] for index in indexes]
    selected_ids = set(indexes)
    remaining_candidates = [
        chunk
        for index, chunk in enumerate(candidates)
        if index not in selected_ids
    ]
    return selected + remaining_candidates + chunks[LLM_RERANK_CANDIDATE_LIMIT:]


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
        llm_service = LLMService()
        context_chunks = [
            _format_context_chunk(index, chunk)
            for index, chunk in enumerate(chunks)
        ]
        compressed_context = _compress_context(query, context_chunks, llm_service)
        answer_context = [compressed_context] if compressed_context else context_chunks
        answer = llm_service.answer(question=query, context_chunks=answer_context)
        if answer.startswith("LLM unavailable"):
            return "LLM unavailable"
        return answer
    except Exception:
        return "LLM unavailable"


def _compress_context(
    query: str,
    context_chunks: list[str],
    llm_service: LLMService,
) -> str:
    try:
        compressed_context = llm_service.compress_context(query, context_chunks)
    except Exception:
        return ""

    if not compressed_context or compressed_context.startswith("LLM unavailable"):
        return ""
    return compressed_context


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
