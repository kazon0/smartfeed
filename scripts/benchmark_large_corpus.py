#!/usr/bin/env python3
import argparse
import json
import os
import shutil
import statistics
import sys
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def metric_summary(values: list[float]) -> dict:
    if not values:
        return {"count": 0, "median": None, "min": None, "max": None}
    ordered = sorted(values)
    return {
        "count": len(ordered),
        "median": round(statistics.median(ordered), 2),
        "min": round(ordered[0], 2),
        "max": round(ordered[-1], 2),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Seed a large SmartFeed corpus and benchmark vector/RAG retrieval."
    )
    parser.add_argument("--target-chars", type=int, default=1_000_000)
    parser.add_argument("--article-count", type=int, default=40)
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--user-id", default="large-corpus-benchmark")
    parser.add_argument(
        "--chroma-dir",
        default="/tmp/smartfeed-large-corpus-chroma",
    )
    parser.add_argument(
        "--query",
        default="根据知识库解释 Kotlin Flow、LangChain RAG 和 ChromaDB 检索如何配合",
    )
    parser.add_argument("--reset", action="store_true", default=True)
    parser.add_argument(
        "--no-reset",
        action="store_false",
        dest="reset",
        help="Reuse the existing Chroma directory instead of deleting it first.",
    )
    parser.add_argument(
        "--mock-embeddings",
        action="store_true",
        help="Use deterministic mock embeddings for quick local script validation.",
    )
    parser.add_argument("--output", default="")
    return parser.parse_args()


def build_article_content(article_index: int, target_chars: int) -> str:
    topics = [
        (
            "Kotlin Flow 响应式管道",
            "Kotlin Flow 适合在 Android 端表达异步数据流。它可以把 WebSocket 回调转换成可收集的事件序列，"
            "再通过 ViewModel 暴露给 Compose UI。配合 coroutine scope、callbackFlow、retry 和 fallback，"
            "客户端能够在不阻塞主线程的情况下消费模型返回的文本流。"
        ),
        (
            "LangChain RAG 编排",
            "LangChain 可以把 query rewrite、multi-query retrieval、rerank、compression 和 answer 组织成清晰的 Runnable 链。"
            "完整链路更适合质量优先的问答，低延迟演示则可以跳过部分前置 LLM 步骤，优先让用户看到首段回答。"
        ),
        (
            "ChromaDB 向量检索",
            "ChromaDB 负责保存文章 chunk 的向量、正文和 metadata。每个 chunk 记录 url、title、section_index、"
            "section_title、chunk_index 和 user_id，查询时先按用户隔离，再取 topK 结果交给 RAG pipeline。"
        ),
        (
            "网页剪藏与正文切分",
            "网页导入后会优先使用 Jina Reader 提取正文，失败时回退 HTML 解析。正文按照章节结构组织，再按固定长度切成 chunk，"
            "保留章节标题和索引，方便回答时扩展上下文，而不是只拼接孤立片段。"
        ),
        (
            "移动端知识库体验",
            "移动端回答应短、清晰、可扫读。回答依据按文章聚合，默认展示标题、章节和一句来源说明，"
            "长 preview 只作为展开依据，避免聊天消息被很多碎片来源卡打断。"
        ),
        (
            "健康文章示例",
            "类风湿湿是一类常见的风湿免疫相关问题。知识库问答不应替代医生诊断，但可以根据已保存文章解释症状、"
            "检查指标、治疗思路和日常管理建议，并提醒用户结合专业医疗意见。"
        ),
    ]
    title, seed = topics[article_index % len(topics)]
    paragraphs = []
    counter = 0
    while sum(len(item) for item in paragraphs) < target_chars:
        paragraphs.append(
            f"第{counter + 1}段，主题：{title}。{seed}"
            f" 本段属于压测语料 article-{article_index:03d}，用于验证百万字级私有知识库的入库、检索、重排和回答链路。"
            f" 关键词包括 SmartFeed、FastAPI、Android、WebSocket、Flow、RAG、ChromaDB、LangChain。"
        )
        counter += 1
    return "\n".join(paragraphs)


def build_sections(article_index: int, content: str) -> list[dict]:
    lines = [line for line in content.splitlines() if line.strip()]
    section_size = max(1, len(lines) // 6)
    sections = []
    for section_index in range(0, len(lines), section_size):
        section_lines = lines[section_index : section_index + section_size]
        sections.append(
            {
                "index": len(sections),
                "title": f"压测章节 {len(sections) + 1}",
                "content": "\n".join(section_lines),
            }
        )
    return sections


class BenchmarkLLMService:
    def rewrite_query(self, question: str, url: str | None = None) -> dict:
        return {"query": question, "source": "benchmark", "reason": "skip external LLM"}

    def generate_search_queries(
        self,
        question: str,
        rewritten_query: str,
        *,
        url: str | None = None,
    ) -> list[str]:
        return [rewritten_query, "Kotlin Flow WebSocket RAG", "LangChain ChromaDB 检索"]

    def rerank_chunks(self, question: str, candidates: list[str]) -> list[int]:
        return list(range(len(candidates)))

    def compress_context(self, question: str, context_chunks: list[str]) -> str:
        return "\n\n".join(context_chunks[:3])

    def answer(self, question: str, context_chunks: list[str]) -> str:
        return (
            "压测回答：已基于检索到的知识库片段生成回答。"
            f" query={question[:80]}，context_chunks={len(context_chunks)}。"
        )

    def describe_sources(self, question: str, source_texts: list[str]) -> list[str]:
        return ["压测来源说明：该文章包含与问题相关的 RAG/Flow/ChromaDB 内容。"]


def run_benchmark(args: argparse.Namespace) -> dict:
    os.environ["CHROMA_PERSIST_DIR"] = args.chroma_dir

    from app.services.chat_service import ChatService
    from app.services.query_intent import QueryIntentService
    from app.services.rag_pipeline import RAGPipeline
    from app.services.vector_store import VectorStoreService
    from app.services.web_parser import WebParserService

    if args.mock_embeddings:
        VectorStoreService._embedding_model_loaded = True
        VectorStoreService._embedding_model = None

    chroma_path = Path(args.chroma_dir)
    if args.reset and chroma_path.exists():
        shutil.rmtree(chroma_path)

    parser = WebParserService()
    vector_store = VectorStoreService(user_id=args.user_id)

    target_chars = max(args.target_chars, 1)
    article_count = max(args.article_count, 1)
    chars_per_article = max(1, target_chars // article_count)
    seeded_articles = []
    seeded_chars = 0
    seeded_chunks = 0
    seed_started = time.perf_counter()

    for article_index in range(article_count):
        content = build_article_content(article_index, chars_per_article)
        sections = build_sections(article_index, content)
        chunks, chunk_metadata = parser._chunk_sections(sections)
        url = f"https://benchmark.smartfeed.local/articles/{article_index:03d}"
        title = f"SmartFeed 百万字压测文章 {article_index + 1:03d}"
        metadata = {
            "source": "benchmark",
            "parser": "synthetic",
            "length": len(content),
            "url": url,
            "title": title,
            "topic": "科技",
        }
        vector_store.delete_by_url(url)
        article_started = time.perf_counter()
        stored_chunks = vector_store.add_chunks(chunks, metadata, chunk_metadata)
        article_ms = (time.perf_counter() - article_started) * 1000
        seeded_chars += len(content)
        seeded_chunks += stored_chunks
        seeded_articles.append(
            {
                "url": url,
                "title": title,
                "chars": len(content),
                "chunks": stored_chunks,
                "seed_ms": round(article_ms, 2),
            }
        )

    seed_ms = (time.perf_counter() - seed_started) * 1000

    query_runs = []
    for _ in range(max(args.runs, 1)):
        started = time.perf_counter()
        results = vector_store.query(args.query, top_k=args.top_k)
        query_runs.append(
            {
                "total_ms": round((time.perf_counter() - started) * 1000, 2),
                "result_count": len(results),
                "top_score": round(results[0]["score"], 4) if results else None,
                "top_title": results[0]["metadata"].get("title", "") if results else "",
            }
        )

    chat_service = ChatService(
        vector_store_factory=lambda: vector_store,
        llm_service_factory=BenchmarkLLMService,
        intent_service_factory=QueryIntentService,
        rag_pipeline_factory=RAGPipeline,
    )
    chat_started = time.perf_counter()
    chat_response = chat_service.chat(args.query)
    chat_ms = (time.perf_counter() - chat_started) * 1000
    debug = chat_response.get("debug", {})

    return {
        "config": {
            "target_chars": args.target_chars,
            "article_count": article_count,
            "runs": args.runs,
            "top_k": args.top_k,
            "user_id": args.user_id,
            "chroma_dir": args.chroma_dir,
            "mock_embeddings": args.mock_embeddings,
        },
        "corpus": {
            "total_chars": seeded_chars,
            "total_articles": len(seeded_articles),
            "total_chunks": seeded_chunks,
            "seed_total_ms": round(seed_ms, 2),
            "seed_chunks_per_second": round(seeded_chunks / max(seed_ms / 1000, 0.001), 2),
        },
        "retrieval": {
            "query": args.query,
            "summary_ms": metric_summary([run["total_ms"] for run in query_runs]),
            "runs": query_runs,
        },
        "local_rag": {
            "total_ms": round(chat_ms, 2),
            "status": chat_response.get("status"),
            "source_type": chat_response.get("source_type"),
            "source_count": len(chat_response.get("sources", [])),
            "retrieved_chunk_count": debug.get("retrieved_chunk_count"),
            "relevant_chunk_count": len(debug.get("relevant_chunks", [])),
            "selected_chunk_count": len(debug.get("selected_chunks", [])),
            "server_timings_ms": debug.get("timings_ms", {}),
        },
        "sample_articles": seeded_articles[:3],
    }


def main() -> int:
    args = parse_args()
    report = run_benchmark(args)
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as output_file:
            output_file.write(rendered + "\n")
    print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
