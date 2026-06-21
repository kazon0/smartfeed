import re
from collections.abc import Callable

from app.services.llm_service import LLMService
from app.services.vector_store import VectorStoreService


class RAGPipeline:
    LLM_RERANK_CANDIDATE_LIMIT = 10

    def __init__(
        self,
        llm_service_factory: Callable[[], LLMService] = LLMService,
    ):
        self.llm_service_factory = llm_service_factory

    def run(
        self,
        query: str,
        vector_store: VectorStoreService,
        *,
        rerank_query: str | None = None,
        url: str | None = None,
        metadata_filter: dict | None = None,
        all_chunks: list[dict] | None = None,
        scope: str = "global",
        relevance_threshold: float = 0.25,
        debug: dict | None = None,
        use_llm_preprocessing: bool = True,
    ) -> dict:
        if use_llm_preprocessing:
            rewritten_query = self.rewrite_query(query, url)
            search_queries = self.search_queries(query, rewritten_query, url)
        else:
            rewritten_query = query
            search_queries = [query]
            if debug is not None:
                debug["fast_path"] = True
        ranking_query = " ".join(search_queries)
        retrieved_chunks = self.retrieve_chunks(
            vector_store,
            search_queries,
            metadata_filter=metadata_filter,
            all_chunks=all_chunks,
            scope=scope,
            debug=debug,
        )
        ranked_chunks = self.rank_chunks(ranking_query, retrieved_chunks)
        if debug is not None:
            debug["rewritten_query"] = rewritten_query
            debug["search_queries"] = search_queries
            debug["ranking_query"] = ranking_query
            debug["ranked_chunks"] = self.debug_chunk_refs(ranked_chunks)

        if use_llm_preprocessing:
            reranked_chunks = self.llm_rerank_chunks(
                rerank_query or query,
                ranked_chunks,
                debug,
            )
        else:
            reranked_chunks = ranked_chunks
        relevant_chunks = self.high_relevance_chunks(
            reranked_chunks,
            relevance_threshold,
        )
        if debug is not None:
            debug["relevant_chunks"] = self.debug_chunk_refs(relevant_chunks)

        return {
            "rewritten_query": rewritten_query,
            "search_queries": search_queries,
            "ranking_query": ranking_query,
            "retrieved_chunks": retrieved_chunks,
            "ranked_chunks": reranked_chunks,
            "relevant_chunks": relevant_chunks,
        }

    def answer_with_context(
        self,
        query: str,
        context_chunks: list[str],
        llm_service: LLMService,
        debug: dict | None = None,
        compress_context: bool = True,
    ) -> str:
        answer_context = self.prepare_answer_context(
            query,
            context_chunks,
            llm_service,
            debug,
            compress_context=compress_context,
        )
        return llm_service.answer(question=query, context_chunks=answer_context)

    def answer_with_context_stream(
        self,
        query: str,
        context_chunks: list[str],
        llm_service: LLMService,
        debug: dict | None = None,
        compress_context: bool = True,
    ):
        answer_context = self.prepare_answer_context(
            query,
            context_chunks,
            llm_service,
            debug,
            compress_context=compress_context,
        )
        yield from llm_service.answer_stream(
            question=query,
            context_chunks=answer_context,
        )

    def prepare_answer_context(
        self,
        query: str,
        context_chunks: list[str],
        llm_service: LLMService,
        debug: dict | None = None,
        compress_context: bool = True,
    ) -> list[str]:
        if not compress_context:
            if debug is not None:
                debug["context"] = {
                    "chunk_count": len(context_chunks),
                    "compressed": False,
                    "compression_skipped": True,
                    "compressed_length": 0,
                    "raw_length": sum(len(chunk) for chunk in context_chunks),
                }
            return context_chunks

        compressed_context = ""
        try:
            candidate = llm_service.compress_context(query, context_chunks)
            if (
                candidate
                and not candidate.startswith("LLM unavailable")
                and self.compression_preserves_context_coverage(candidate, context_chunks)
            ):
                compressed_context = candidate
        except Exception:
            compressed_context = ""

        if debug is not None:
            debug["context"] = {
                "chunk_count": len(context_chunks),
                "compressed": bool(compressed_context),
                "compressed_length": len(compressed_context),
                "raw_length": sum(len(chunk) for chunk in context_chunks),
            }
        return [compressed_context] if compressed_context else context_chunks

    def compression_preserves_context_coverage(
        self,
        compressed_context: str,
        context_chunks: list[str],
    ) -> bool:
        urls = {
            match.group(1)
            for chunk in context_chunks
            if (match := re.search(r"\burl:\s*(\S+)", chunk))
        }
        section_titles = {
            match.group(1).strip()
            for chunk in context_chunks
            if (
                match := re.search(
                    r"\bsection_title:\s*(.*?)(?:\s+section_index:|\n|$)",
                    chunk,
                )
            )
        }

        if len(urls) > 1 and any(url not in compressed_context for url in urls):
            return False
        if len(section_titles) > 1 and any(
            title not in compressed_context for title in section_titles
        ):
            return False
        return True

    def rewrite_query(self, query: str, url: str | None = None) -> str:
        try:
            result = self.llm_service_factory().rewrite_query(query, url=url)
        except Exception:
            return query

        rewritten_query = result.get("query", "")
        if not isinstance(rewritten_query, str) or not rewritten_query.strip():
            return query
        return rewritten_query.strip()

    def search_queries(
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

    def retrieve_chunks(
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
            keyword_chunks = self.keyword_retrieve_chunks(
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

        merged_chunks = self.merge_chunks([], retrieved_chunks)
        if debug is not None:
            debug["retrieved_chunk_count"] = len(merged_chunks)
        return merged_chunks

    def high_relevance_chunks(
        self,
        chunks: list[dict],
        threshold: float,
    ) -> list[dict]:
        return [chunk for chunk in chunks if chunk.get("score", 0) >= threshold]

    def rank_chunks(self, query: str, chunks: list[dict]) -> list[dict]:
        terms = self.query_terms(query)

        def rank_score(chunk: dict) -> float:
            metadata = chunk.get("metadata", {})
            content_hits = self.term_hits(terms, chunk.get("content", ""))
            section_hits = self.term_hits(terms, metadata.get("section_title", ""))
            title_hits = self.term_hits(terms, metadata.get("title", ""))
            return (
                chunk.get("score", 0)
                + content_hits
                + section_hits * 1.5
                + title_hits * 0.25
            )

        return sorted(chunks, key=rank_score, reverse=True)

    def term_hits(self, terms: list[str], text: str) -> int:
        return sum(1 for term in terms if term and term in text)

    def llm_rerank_chunks(
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
                self.format_context_chunk(index, chunk)
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
                "before": self.debug_chunk_refs(candidates),
                "after": self.debug_chunk_refs(reranked_chunks),
            }
        return reranked_chunks

    def get_all_chunks(self, vector_store: VectorStoreService) -> list[dict]:
        get_all_chunks = getattr(vector_store, "get_all_chunks", None)
        if not get_all_chunks:
            return []
        return get_all_chunks()

    def keyword_retrieve_chunks(
        self,
        query: str,
        chunks: list[dict],
        limit: int = 5,
    ) -> list[dict]:
        terms = self.query_terms(query)
        if not terms or not chunks:
            return []

        scored_chunks = []
        for chunk in chunks:
            searchable_text = self.chunk_search_text(chunk)
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

    def merge_chunks(self, primary_chunks: list[dict], secondary_chunks: list[dict]) -> list[dict]:
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

    def query_terms(self, query: str) -> list[str]:
        normalized = re.sub(r"\s+", "", query.strip())
        terms = {normalized}

        for size in (2, 3, 4):
            for index in range(0, max(0, len(normalized) - size + 1)):
                terms.add(normalized[index : index + size])

        return [term for term in terms if len(term) >= 2]

    def chunk_search_text(self, chunk: dict) -> str:
        metadata = chunk.get("metadata", {})
        return "".join(
            [
                str(metadata.get("title", "")),
                str(metadata.get("section_title", "")),
                str(chunk.get("content", "")),
            ]
        )

    def format_context_chunk(self, index: int, chunk: dict) -> str:
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

    def debug_chunk_refs(self, chunks: list[dict], limit: int = 10) -> list[dict]:
        refs = []
        for chunk in chunks[:limit]:
            metadata = chunk.get("metadata", {})
            refs.append(
                {
                    "url": metadata.get("url", ""),
                    "title": metadata.get("title", ""),
                    "section_index": metadata.get("section_index"),
                    "section_title": metadata.get("section_title", ""),
                    "chunk_index": metadata.get("chunk_index"),
                    "score": chunk.get("score", 0),
                    "preview": self.debug_preview(chunk.get("content", "")),
                }
            )
        return refs

    def debug_preview(self, content: str, limit: int = 120) -> str:
        return re.sub(r"\s+", " ", content.strip())[:limit]
