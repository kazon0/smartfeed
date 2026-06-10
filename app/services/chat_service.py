import re
from collections.abc import Callable

from app.services.llm_service import LLMService
from app.services.query_intent import QueryIntentService
from app.services.vector_store import VectorStoreService


class ChatService:
    RELEVANCE_THRESHOLD = 0.25
    SOURCE_PREVIEW_LENGTH = 1200
    FULL_PAGE_CONTEXT_CHUNK_LIMIT = 30
    PAGE_WIDE_CONTEXT_CHUNK_LIMIT = 10
    LLM_RERANK_CANDIDATE_LIMIT = 10
    SOURCE_LIMIT = 3
    ERROR_CODE_BY_SOURCE_TYPE = {
        "need_page_context": "NEED_PAGE_CONTEXT",
        "page_not_found_with_suggestions": "PAGE_CONTENT_NOT_FOUND",
        "unsupported_realtime": "REALTIME_UNSUPPORTED",
        "no_knowledge_found": "NO_KNOWLEDGE_FOUND",
        "unsupported_action": "UNSUPPORTED_ACTION",
    }

    def __init__(
        self,
        vector_store_factory: Callable[[], VectorStoreService] = VectorStoreService,
        llm_service_factory: Callable[[], LLMService] = LLMService,
        intent_service_factory: Callable[[], QueryIntentService] = QueryIntentService,
    ):
        self.vector_store_factory = vector_store_factory
        self.llm_service_factory = llm_service_factory
        self.intent_service_factory = intent_service_factory

    def chat(
        self,
        query: str,
        url: str | None = None,
        mode: str = "global",
        history: list[dict[str, str]] | None = None,
    ) -> dict:
        history_context = self._history_context(history or [])
        contextual_query = self._contextual_query(query, history_context)
        intent = self.intent_service_factory().classify(
            query,
            has_url=bool(url),
        )
        debug = self._new_debug(query, url, mode, intent)
        debug["history_count"] = len(history or [])
        debug["history_used"] = bool(history_context)

        if intent["retrieval_scope"] == "none":
            return self._no_retrieval_response(intent, debug)

        vector_store = self.vector_store_factory()
        rewritten_query = self._rewrite_query(contextual_query, url)
        search_queries = self._search_queries(contextual_query, rewritten_query, url)
        ranking_query = " ".join(search_queries)
        debug["rewritten_query"] = rewritten_query
        debug["search_queries"] = search_queries
        debug["ranking_query"] = ranking_query
        if url:
            return self._chat_with_current_page(
                query,
                contextual_query,
                search_queries,
                ranking_query,
                url,
                intent,
                vector_store,
                debug,
            )

        global_chunks = self._retrieve_chunks(
            vector_store,
            search_queries,
            all_chunks=self._get_all_chunks(vector_store),
            scope="global",
            debug=debug,
        )
        ranked_chunks = self._rank_chunks(ranking_query, global_chunks)
        debug["ranked_chunks"] = self._debug_chunk_refs(ranked_chunks)
        ranked_chunks = self._llm_rerank_chunks(query, ranked_chunks, debug)
        relevant_chunks = self._high_relevance_chunks(ranked_chunks)
        debug["relevant_chunks"] = self._debug_chunk_refs(relevant_chunks)
        answer, source_type, selected_chunks = self._answer_with_policy(
            contextual_query,
            intent["fallback_policy"],
            relevant_chunks,
            [],
            global_chunks,
            debug,
        )
        debug["selected_chunks"] = self._debug_chunk_refs(selected_chunks)

        return self._chat_response(
            answer=answer,
            sources=self._build_sources(selected_chunks, query),
            source_type=source_type,
            intent=intent,
            retrieval_scope=intent["retrieval_scope"],
            debug=debug,
        )

    def _chat_with_current_page(
        self,
        query: str,
        contextual_query: str,
        search_queries: list[str],
        ranking_query: str,
        url: str,
        intent: dict,
        vector_store: VectorStoreService,
        debug: dict,
    ) -> dict:
        all_page_chunks = vector_store.get_chunks_by_url(url)
        debug["page_chunk_count"] = len(all_page_chunks)
        page_chunks = self._retrieve_chunks(
            vector_store,
            search_queries,
            metadata_filter={"url": url},
            all_chunks=all_page_chunks,
            scope="page",
            debug=debug,
        )
        ranked_page_chunks = self._rank_chunks(ranking_query, page_chunks)
        debug["ranked_chunks"] = self._debug_chunk_refs(ranked_page_chunks)
        ranked_page_chunks = self._llm_rerank_chunks(query, ranked_page_chunks, debug)
        relevant_page_chunks = self._high_relevance_chunks(ranked_page_chunks)
        debug["relevant_chunks"] = self._debug_chunk_refs(relevant_page_chunks)

        if all_page_chunks and self._is_page_wide_query(query, intent):
            selected_page_chunks = self._select_page_context(
                all_page_chunks,
                relevant_page_chunks,
            )
            debug["page_wide_query"] = True
            debug["quality_selected_page_chunks"] = self._debug_chunk_refs(selected_page_chunks)
            answer, source_type, selected_chunks = self._answer_with_policy(
                contextual_query,
                intent["fallback_policy"],
                selected_page_chunks,
                selected_page_chunks,
                [],
                debug,
            )
            debug["selected_chunks"] = self._debug_chunk_refs(selected_chunks)
            return self._chat_response(
                answer=answer,
                sources=self._build_sources(selected_chunks, query),
                source_type=source_type,
                intent=intent,
                retrieval_scope="page_first",
                debug=debug,
            )

        if relevant_page_chunks:
            expanded_page_chunks = self._expand_page_context(
                relevant_page_chunks,
                all_page_chunks,
            )
            debug["expanded_chunks"] = self._debug_chunk_refs(expanded_page_chunks)
            answer, source_type, selected_chunks = self._answer_with_policy(
                contextual_query,
                intent["fallback_policy"],
                expanded_page_chunks,
                expanded_page_chunks,
                [],
                debug,
            )
            debug["selected_chunks"] = self._debug_chunk_refs(selected_chunks)
            return self._chat_response(
                answer=answer,
                sources=self._build_sources(selected_chunks, query),
                source_type=source_type,
                intent=intent,
                retrieval_scope="page_first",
                debug=debug,
            )

        saved_sources = [
            source
            for source in vector_store.list_sources()
            if source.get("url") != url
        ]
        debug["suggested_source_count"] = len(saved_sources)
        answer = (
            "当前网页没有找到可用于回答的内容。"
            "你可以改为询问知识库中已有内容，或从下方已保存文章中选择一个继续提问。"
        )

        return self._chat_response(
            answer=answer,
            sources=saved_sources,
            source_type="page_not_found_with_suggestions",
            intent=intent,
            retrieval_scope="page_first",
            debug=debug,
        )

    def _no_retrieval_response(self, intent: dict, debug: dict | None = None) -> dict:
        if intent["fallback_policy"] == "ask_for_page":
            answer = "我无法确定你指的是哪篇文章，请先分享网页，或提供文章链接/标题。"
            source_type = "need_page_context"
        elif intent["fallback_policy"] == "unsupported_action":
            answer = "当前版本还不支持这个操作。"
            source_type = "unsupported_action"
        else:
            answer = "当前请求无法处理。"
            source_type = "unsupported_action"

        return self._chat_response(
            answer=answer,
            sources=[],
            source_type=source_type,
            intent=intent,
            retrieval_scope=intent["retrieval_scope"],
            debug=debug,
        )

    def _new_debug(
        self,
        query: str,
        url: str | None,
        mode: str,
        intent: dict,
    ) -> dict:
        return {
            "query": query,
            "url": url or "",
            "mode": mode,
            "intent": intent["intent"],
            "retrieval_scope": intent["retrieval_scope"],
            "fallback_policy": intent["fallback_policy"],
            "rewritten_query": "",
            "search_queries": [],
            "ranking_query": "",
            "retrieval_steps": [],
            "retrieved_chunk_count": 0,
            "page_chunk_count": 0,
            "ranked_chunks": [],
            "relevant_chunks": [],
            "expanded_chunks": [],
            "selected_chunks": [],
            "context": {},
        }

    def _history_context(self, history: list[dict[str, str]], limit: int = 8) -> str:
        clean_items = []
        for item in history[-limit:]:
            role = str(item.get("role", "")).strip().lower()
            content = str(item.get("content", "")).strip()
            if role not in {"user", "assistant", "summary"} or not content:
                continue
            label = {
                "user": "用户",
                "assistant": "助手",
                "summary": "文章总结",
            }[role]
            clean_items.append(f"{label}: {content[:600]}")
        return "\n".join(clean_items)

    def _contextual_query(self, query: str, history_context: str) -> str:
        clean_query = query.strip()
        if not history_context:
            return clean_query
        return (
            "以下是当前对话最近上下文，用于理解用户当前问题中的指代。"
            "回答时仍应以当前问题为准。\n"
            f"{history_context}\n\n"
            f"当前问题: {clean_query}"
        )

    def _rewrite_query(self, query: str, url: str | None = None) -> str:
        try:
            result = self.llm_service_factory().rewrite_query(query, url=url)
        except Exception:
            return query

        rewritten_query = result.get("query", "")
        if not isinstance(rewritten_query, str) or not rewritten_query.strip():
            return query
        return rewritten_query.strip()

    def _search_queries(
        self,
        query: str,
        rewritten_query: str,
        url: str | None = None,
    ) -> list[str]:
        queries = []
        for candidate in (rewritten_query, query):
            clean_candidate = candidate.strip()
            if clean_candidate and clean_candidate not in queries:
                queries.append(clean_candidate)

        try:
            generated_queries = self.llm_service_factory().generate_search_queries(
                query,
                rewritten_query,
                url=url,
            )
        except Exception:
            generated_queries = []

        for candidate in generated_queries:
            clean_candidate = candidate.strip()
            if clean_candidate and clean_candidate not in queries:
                queries.append(clean_candidate)

        return queries[:5] or [query]

    def _retrieve_chunks(
        self,
        vector_store: VectorStoreService,
        queries: list[str],
        *,
        metadata_filter: dict | None = None,
        all_chunks: list[dict] | None = None,
        scope: str = "global",
        debug: dict | None = None,
    ) -> list[dict]:
        retrieved_chunks = []
        keyword_source_chunks = all_chunks or []
        for search_query in queries:
            vector_chunks = vector_store.query(
                search_query,
                metadata_filter=metadata_filter,
            )
            keyword_chunks = self._keyword_retrieve_chunks(
                search_query,
                keyword_source_chunks,
            )
            retrieved_chunks.extend(vector_chunks)
            retrieved_chunks.extend(keyword_chunks)
            if debug is not None:
                debug["retrieval_steps"].append(
                    {
                        "scope": scope,
                        "query": search_query,
                        "metadata_filter": metadata_filter or {},
                        "vector_count": len(vector_chunks),
                        "keyword_count": len(keyword_chunks),
                    }
                )

        merged_chunks = self._merge_chunks([], retrieved_chunks)
        if debug is not None:
            debug["retrieved_chunk_count"] = len(merged_chunks)
        return merged_chunks

    def _chat_response(
        self,
        answer: str,
        sources: list[dict],
        source_type: str,
        intent: dict,
        retrieval_scope: str,
        debug: dict | None = None,
    ) -> dict:
        error_code = self._error_code(source_type, answer)
        return {
            "status": "failed" if error_code else "ok",
            "error_code": error_code,
            "message": self._response_message(error_code, answer),
            "answer": answer,
            "sources": sources,
            "source_type": source_type,
            "intent": intent["intent"],
            "intent_reason": intent["reason"],
            "retrieval_scope": retrieval_scope,
            "fallback_policy": intent["fallback_policy"],
            "debug": debug or {},
        }

    def _error_code(self, source_type: str, answer: str) -> str | None:
        if answer == "LLM unavailable":
            return "LLM_UNAVAILABLE"
        return self.ERROR_CODE_BY_SOURCE_TYPE.get(source_type)

    def _response_message(self, error_code: str | None, answer: str) -> str:
        if not error_code:
            return ""
        if error_code == "LLM_UNAVAILABLE":
            return "LLM service is unavailable."
        return answer

    def _answer_with_policy(
        self,
        query: str,
        fallback_policy: str,
        relevant_chunks: list[dict],
        page_chunks: list[dict],
        global_chunks: list[dict],
        debug: dict | None = None,
    ) -> tuple[str, str, list[dict]]:
        if fallback_policy == "no_guess_realtime":
            supported_chunks = self._realtime_supported_chunks(query, relevant_chunks)
            if debug is not None:
                debug["realtime_supported_chunks"] = self._debug_chunk_refs(supported_chunks)
            relevant_chunks = supported_chunks

        source_type = self._source_type(page_chunks, global_chunks, relevant_chunks)

        if relevant_chunks:
            prefix = ""
            if fallback_policy == "no_guess_realtime":
                prefix = "以下回答基于知识库中已保存内容，可能不是实时最新。\n\n"
            return prefix + self._build_context_answer(query, relevant_chunks, debug), source_type, relevant_chunks

        if fallback_policy == "llm_allowed":
            return (
                self._build_general_answer(
                    query,
                    "知识库中未找到足够相关内容，以下是通用回答",
                ),
                "llm_fallback",
                [],
            )

        if fallback_policy == "knowledge_then_llm":
            return (
                self._build_general_answer(
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

    def _realtime_supported_chunks(self, query: str, chunks: list[dict]) -> list[dict]:
        if not chunks:
            return []

        normalized_query = re.sub(r"\s+", "", query.strip().lower())
        if "星期" in normalized_query or "周几" in normalized_query:
            required_terms = ("星期", "周几")
        elif "几点" in normalized_query:
            required_terms = ("几点", "现在时间", "当前时间")
        elif "日期" in normalized_query or "几号" in normalized_query:
            required_terms = ("日期", "几号", "年月日")
        elif "天气" in normalized_query:
            required_terms = ("天气", "气温", "降雨", "温度")
        elif any(term in normalized_query for term in ("汇率", "股价", "价格")):
            required_terms = ("汇率", "股价", "价格", "美元", "人民币", "股票")
        elif any(term in normalized_query for term in ("新闻", "最新")):
            required_terms = ("新闻", "最新", "报道", "消息", "发布")
        else:
            required_terms = tuple(
                term
                for term in self._query_terms(query)
                if term not in {"今天", "现在", "当前", "最新", "实时", "今年"}
            )

        if not required_terms:
            return []

        supported = []
        for chunk in chunks:
            searchable_text = self._chunk_search_text(chunk).lower()
            if any(term.lower() in searchable_text for term in required_terms):
                supported.append(chunk)
        return supported

    def _high_relevance_chunks(self, chunks: list[dict]) -> list[dict]:
        return [chunk for chunk in chunks if chunk.get("score", 0) >= self.RELEVANCE_THRESHOLD]

    def _is_page_wide_query(self, query: str, intent: dict) -> bool:
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

    def _select_page_context(
        self,
        chunks: list[dict],
        relevant_chunks: list[dict] | None = None,
    ) -> list[dict]:
        quality_chunks = [
            chunk
            for chunk in chunks
            if self._chunk_quality_score(chunk) >= 0.25
            or self._contains_article_end_marker(chunk.get("content", ""))
            or self._looks_like_list_content(chunk.get("content", ""))
        ]
        if not quality_chunks:
            quality_chunks = chunks

        target_section = self._primary_section(relevant_chunks or quality_chunks)
        if target_section is not None:
            section_chunks = [
                chunk
                for chunk in quality_chunks
                if chunk.get("metadata", {}).get("section_index") == target_section
            ]
            if section_chunks:
                quality_chunks = section_chunks

        quality_chunks = self._trim_to_article_region(
            quality_chunks,
            relevant_chunks or quality_chunks,
        )

        return sorted(
            quality_chunks,
            key=lambda chunk: chunk.get("metadata", {}).get("chunk_index", 0),
        )[: self.PAGE_WIDE_CONTEXT_CHUNK_LIMIT]

    def _trim_to_article_region(
        self,
        chunks: list[dict],
        anchors: list[dict],
    ) -> list[dict]:
        if len(chunks) <= 1:
            return chunks

        sorted_chunks = sorted(
            chunks,
            key=lambda chunk: chunk.get("metadata", {}).get("chunk_index", 0),
        )
        anchor_indexes = {
            chunk.get("metadata", {}).get("chunk_index")
            for chunk in anchors
            if isinstance(chunk.get("metadata", {}).get("chunk_index"), int)
        }
        if not anchor_indexes:
            return sorted_chunks

        min_anchor = min(anchor_indexes)
        start = 0
        for index, chunk in enumerate(sorted_chunks):
            chunk_index = chunk.get("metadata", {}).get("chunk_index")
            content = chunk.get("content", "")
            if isinstance(chunk_index, int) and chunk_index <= min_anchor:
                if self._looks_like_article_start(content):
                    start = index
                    break
                start = index

        selected = []
        for chunk in sorted_chunks[start:]:
            content = chunk.get("content", "")
            if self._looks_like_related_content(content) and selected:
                break
            selected.append(chunk)
            if self._contains_article_end_marker(content):
                break

        return selected or sorted_chunks

    def _looks_like_article_start(self, content: str) -> bool:
        normalized = re.sub(r"\s+", " ", content.strip())
        start_terms = (
            "作为一名",
            "本文",
            "在本文",
            "首先",
            "一.",
            "一、",
            "1.",
            "方法1",
            "背景",
            "近日",
        )
        return any(term in normalized for term in start_terms)

    def _contains_article_end_marker(self, content: str) -> bool:
        normalized = re.sub(r"\s+", " ", content.strip())
        end_terms = (
            "喜欢点赞收藏",
            "下期再见",
            "原创声明",
            "本文系作者授权",
            "未经许可，不得转载",
            "阅读原文可查看",
        )
        return any(term in normalized for term in end_terms)

    def _looks_like_related_content(self, content: str) -> bool:
        normalized = re.sub(r"\s+", " ", content.strip())
        related_terms = (
            "作者相关精选",
            "关联问题",
            "换一批",
            "相关文章",
            "相关推荐",
            "精选推荐",
        )
        if any(term in normalized for term in related_terms):
            return True

        link_count = (
            normalized.count("http://")
            + normalized.count("https://")
            + normalized.count("](")
            + normalized.count("[](")
        )
        author_link_terms = (
            "developer/user",
            "关注作者",
            "阅读原文",
        )
        return link_count >= 2 and any(term in normalized for term in author_link_terms)

    def _looks_like_list_content(self, content: str) -> bool:
        normalized = re.sub(r"\s+", " ", content.strip())
        list_markers = (
            "一.",
            "二.",
            "三.",
            "四.",
            "一、",
            "二、",
            "三、",
            "四、",
            "1.",
            "2.",
            "3.",
            "方法1",
            "方法2",
            "算法",
            "步骤",
        )
        return sum(1 for marker in list_markers if marker in normalized) >= 2

    def _expand_page_context(
        self,
        relevant_chunks: list[dict],
        all_page_chunks: list[dict],
    ) -> list[dict]:
        section_chunks = self._expand_section_chunks(relevant_chunks, all_page_chunks)
        if section_chunks:
            return section_chunks
        return self._expand_page_chunks(relevant_chunks, all_page_chunks)

    def _expand_section_chunks(
        self,
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
        self,
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

    def _primary_section(self, chunks: list[dict]) -> int | None:
        section_scores = {}
        section_counts = {}
        for chunk in chunks:
            metadata = chunk.get("metadata", {})
            section_index = metadata.get("section_index")
            if section_index is None:
                continue
            section_scores[section_index] = section_scores.get(section_index, 0) + max(
                0.1,
                self._chunk_quality_score(chunk),
            )
            section_counts[section_index] = section_counts.get(section_index, 0) + 1

        if not section_scores:
            return None
        section_index, score = max(section_scores.items(), key=lambda item: item[1])
        if score < 0.7 and section_counts.get(section_index, 0) <= 1:
            return None
        return section_index

    def _chunk_quality_score(self, chunk: dict) -> float:
        content = re.sub(r"\s+", " ", chunk.get("content", "").strip())
        if not content:
            return 0.0

        length = len(content)
        score = min(length / 280, 1.0)

        link_count = (
            content.count("http://")
            + content.count("https://")
            + content.count("](")
            + content.count("[](")
        )
        if link_count:
            score -= min(0.7, link_count * 0.12)

        noise_terms = (
            "adtrace",
            "fromSource",
            "utm",
            "关注作者",
            "作者相关精选",
            "关联问题",
            "换一批",
            "举报",
            "原创声明",
            "本文参与",
            "社区规范",
            "联系我们",
            "友情链接",
            "喜欢点赞收藏",
            "阅读原文",
            "控制台",
            "发布",
            "登录",
            "立即使用",
            "广告",
        )
        noise_hits = sum(1 for term in noise_terms if term in content)
        score -= min(0.8, noise_hits * 0.16)

        chinese_chars = len(re.findall(r"[\u4e00-\u9fff]", content))
        if chinese_chars >= 80:
            score += 0.2

        structure_terms = (
            "一.",
            "二.",
            "三.",
            "四.",
            "1.",
            "2.",
            "3.",
            "算法",
            "方法",
            "步骤",
            "包括",
        )
        structure_hits = sum(1 for term in structure_terms if term in content)
        score += min(0.35, structure_hits * 0.05)

        return max(0.0, min(score, 1.0))

    def _merge_chunks(self, primary_chunks: list[dict], secondary_chunks: list[dict]) -> list[dict]:
        merged = []
        seen = {}

        for chunk in primary_chunks + secondary_chunks:
            key = (
                chunk.get("metadata", {}).get("url", ""),
                chunk.get("metadata", {}).get("chunk_index", ""),
                chunk.get("content", ""),
            )
            existing_index = seen.get(key)
            if existing_index is not None:
                existing = merged[existing_index]
                if chunk.get("score", 0) > existing.get("score", 0):
                    merged[existing_index] = chunk
                continue
            seen[key] = len(merged)
            merged.append(chunk)

        return merged

    def _keyword_retrieve_chunks(
        self,
        query: str,
        chunks: list[dict],
        limit: int = 5,
    ) -> list[dict]:
        terms = self._query_terms(query)
        if not terms or not chunks:
            return []

        scored_chunks = []
        for chunk in chunks:
            searchable_text = self._chunk_search_text(chunk)
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

    def _get_all_chunks(self, vector_store: VectorStoreService) -> list[dict]:
        get_all_chunks = getattr(vector_store, "get_all_chunks", None)
        if not get_all_chunks:
            return []
        return get_all_chunks()

    def _chunk_search_text(self, chunk: dict) -> str:
        metadata = chunk.get("metadata", {})
        return "".join(
            [
                str(metadata.get("title", "")),
                str(metadata.get("section_title", "")),
                str(chunk.get("content", "")),
            ]
        )

    def _rank_chunks(self, query: str, chunks: list[dict]) -> list[dict]:
        terms = self._query_terms(query)

        def rank_score(chunk: dict) -> float:
            content = chunk.get("content", "")
            keyword_hits = sum(1 for term in terms if term and term in content)
            return chunk.get("score", 0) + keyword_hits

        return sorted(chunks, key=rank_score, reverse=True)

    def _llm_rerank_chunks(
        self,
        query: str,
        chunks: list[dict],
        debug: dict | None = None,
    ) -> list[dict]:
        if len(chunks) <= 1:
            return chunks

        candidates = chunks[: self.LLM_RERANK_CANDIDATE_LIMIT]
        try:
            candidate_texts = [
                self._format_context_chunk(index, chunk)
                for index, chunk in enumerate(candidates)
            ]
            indexes = self.llm_service_factory().rerank_chunks(query, candidate_texts)
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
        reranked_chunks = selected + remaining_candidates + chunks[self.LLM_RERANK_CANDIDATE_LIMIT :]
        if debug is not None:
            debug["llm_rerank"] = {
                "candidate_count": len(candidates),
                "selected_candidate_indexes": indexes,
                "before": self._debug_chunk_refs(candidates),
                "after": self._debug_chunk_refs(reranked_chunks),
            }
        return reranked_chunks

    def _query_terms(self, query: str) -> list[str]:
        normalized = re.sub(r"\s+", "", query.strip())
        terms = {normalized}

        for size in (2, 3, 4):
            for index in range(0, max(0, len(normalized) - size + 1)):
                terms.add(normalized[index : index + size])

        return [term for term in terms if len(term) >= 2]

    def _source_type(
        self,
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

    def _build_general_answer(self, query: str, reason: str) -> str:
        try:
            answer = self.llm_service_factory().answer_without_context(
                question=query,
                reason=reason,
            )
            if answer.startswith("LLM unavailable"):
                return "LLM unavailable"
            return answer
        except Exception:
            return "LLM unavailable"

    def _build_context_answer(
        self,
        query: str,
        chunks: list[dict],
        debug: dict | None = None,
    ) -> str:
        try:
            llm_service = self.llm_service_factory()
            context_chunks = [
                self._format_context_chunk(index, chunk)
                for index, chunk in enumerate(chunks)
            ]
            compressed_context = self._compress_context(query, context_chunks, llm_service)
            answer_context = [compressed_context] if compressed_context else context_chunks
            if debug is not None:
                debug["context"] = {
                    "chunk_count": len(context_chunks),
                    "compressed": bool(compressed_context),
                    "compressed_length": len(compressed_context),
                    "raw_length": sum(len(chunk) for chunk in context_chunks),
                }
            answer = llm_service.answer(question=query, context_chunks=answer_context)
            if answer.startswith("LLM unavailable"):
                return "LLM unavailable"
            return answer
        except Exception:
            return "LLM unavailable"

    def _compress_context(
        self,
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

    def _format_context_chunk(self, index: int, chunk: dict) -> str:
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

    def _build_sources(self, chunks: list[dict], query: str | None = None) -> list[dict]:
        sources = self._group_source_chunks(chunks)
        sources = self._select_display_sources(sources)
        if query and sources:
            self._attach_source_notes(query, sources)
        return sources

    def _group_source_chunks(self, chunks: list[dict]) -> list[dict]:
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
                display_title = self._display_title(title, url)
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
            source["content_preview"] = self._readable_preview(content)
            del source["_content_parts"]

        return sources

    def _select_display_sources(self, sources: list[dict]) -> list[dict]:
        if len(sources) <= self.SOURCE_LIMIT:
            return sources

        def source_score(source: dict) -> float:
            preview = source.get("content_preview", "")
            noise_penalty = 0.0
            for term in ("adtrace", "关注作者", "关联问题", "原创声明", "友情链接", "登录"):
                if term in preview:
                    noise_penalty += 0.2
            chunk_count = len(source.get("chunk_indexes", []))
            return (
                float(source.get("score", 0) or 0)
                + min(chunk_count, 4) * 0.08
                + min(len(preview) / 800, 0.3)
                - noise_penalty
            )

        ranked_sources = sorted(sources, key=source_score, reverse=True)
        selected_sources = ranked_sources[: self.SOURCE_LIMIT]
        selected_keys = {
            (
                source.get("url", ""),
                source.get("section_index"),
                source.get("chunk_index"),
            )
            for source in selected_sources
        }
        return [
            source
            for source in sources
            if (
                source.get("url", ""),
                source.get("section_index"),
                source.get("chunk_index"),
            )
            in selected_keys
        ]

    def _readable_preview(self, content: str) -> str:
        normalized = self._clean_source_preview_content(content)
        normalized = re.sub(r"\n{3,}", "\n\n", normalized.strip())
        if len(normalized) <= self.SOURCE_PREVIEW_LENGTH:
            return normalized
        return normalized[: self.SOURCE_PREVIEW_LENGTH].rstrip() + "..."

    def _clean_source_preview_content(self, content: str) -> str:
        cleaned = content.strip()
        if not cleaned:
            return ""

        cleaned = re.sub(r"!\[[^\]]*]\([^)]+\)", "", cleaned)
        cleaned = re.sub(r"\[\]\([^)]+\)", "", cleaned)
        cleaned = re.sub(r"\[([^\]]+)]\([^)]+\)", r"\1", cleaned)
        cleaned = re.sub(r"https?://\S+", "", cleaned)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()

        for marker in (
            "喜欢点赞收藏",
            "下期再见",
            "原创声明",
            "本文系作者授权",
            "未经许可，不得转载",
            "作者相关精选",
            "关联问题",
            "换一批",
            "相关推荐",
        ):
            marker_index = cleaned.find(marker)
            if marker_index > 0:
                cleaned = cleaned[:marker_index].strip()

        start_markers = (
            "作为一名",
            "常用的算法类别",
            "一.",
            "一、",
            "1.",
            "方法1",
            "背景",
            "近日",
        )
        noise_before_start = (
            "关注",
            "举报",
            "关联问题",
            "换一批",
            "作者相关精选",
            "控制台",
            "登录",
            "发布",
            "adtrace",
        )
        for marker in start_markers:
            marker_index = cleaned.find(marker)
            if marker_index > 0:
                prefix = cleaned[:marker_index]
                if any(term in prefix for term in noise_before_start):
                    cleaned = cleaned[marker_index:].strip()
                break

        return cleaned

    def _display_title(self, title: str, url: str) -> str:
        cleaned = re.sub(r"[-_|]腾讯云开发者社区[-_]?.*$", "", title or "").strip()
        cleaned = re.sub(r"\s+", " ", cleaned)
        if cleaned:
            return cleaned
        return url or "Untitled source"

    def _attach_source_notes(self, query: str, sources: list[dict]) -> None:
        source_texts = [
            self._source_note_input(source)
            for source in sources
        ]

        try:
            describe_sources = getattr(self.llm_service_factory(), "describe_sources")
            notes = describe_sources(query, source_texts)
        except Exception:
            notes = []

        for index, source in enumerate(sources):
            note = notes[index].strip() if index < len(notes) and notes[index] else ""
            if note and not note.startswith("LLM unavailable"):
                note = self._clean_source_note(note, source)
                source["source_note"] = note
                source["source_summary"] = note

    def _source_note_input(self, source: dict) -> str:
        return (
            f"title: {source.get('display_title') or source.get('title') or ''}\n"
            f"section_title: {source.get('section_title') or ''}\n"
            f"content:\n{source.get('content_preview', '')}"
        ).strip()

    def _clean_source_note(self, note: str, source: dict | None = None) -> str:
        cleaned = re.sub(r"(来源|片段|source)\s*\[\s*\d+\s*\]", "该依据", note, flags=re.IGNORECASE)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        cleaned = re.sub(r"^(这段内容|该段内容|这部分内容|这部分|该内容)", "该依据", cleaned)
        cleaned = re.sub(r"^该依据(介绍了|说明了|列出了|提到|包含)", r"依据\1", cleaned)

        if source:
            display_title = source.get("display_title", "") or source.get("title", "")
            if display_title and cleaned.startswith(("该依据", "依据")):
                cleaned = f"《{display_title}》{cleaned}"
        return cleaned

    def _debug_chunk_refs(self, chunks: list[dict], limit: int = 10) -> list[dict]:
        refs = []
        for chunk in chunks[:limit]:
            metadata = chunk.get("metadata", {})
            refs.append(
                {
                    "url": metadata.get("url", ""),
                    "title": metadata.get("title", ""),
                    "section_title": metadata.get("section_title", ""),
                    "chunk_index": metadata.get("chunk_index"),
                    "section_index": metadata.get("section_index"),
                    "score": chunk.get("score", 0),
                    "preview": self._debug_preview(chunk.get("content", "")),
                }
            )
        return refs

    def _debug_preview(self, content: str, limit: int = 120) -> str:
        normalized = re.sub(r"\s+", " ", content.strip())
        if len(normalized) <= limit:
            return normalized
        return normalized[:limit].rstrip() + "..."
