# Performance Benchmark

SmartFeed includes a repeatable chat benchmark for measuring the deployed
service and a local large-corpus benchmark for validating million-character
knowledge-base behavior instead of estimating resume metrics.

## Deployed Chat Benchmark

Set a dedicated benchmark account through environment variables so credentials
do not enter shell history or Git:

```bash
export SMARTFEED_BENCH_EMAIL='benchmark@example.com'
export SMARTFEED_BENCH_PASSWORD='replace-with-password'
venv/bin/python scripts/benchmark_chat.py --runs 3 \
  --output benchmark-result.json
```

The default target can be overridden with `--base-url`. Use `--url` to benchmark
current-page chat instead of a global knowledge query.

## Metrics

- `knowledge_base.total_articles`: authenticated user's saved article count.
- `knowledge_base.total_chunks`: authenticated user's vector chunk count.
- `http_total_ms`: client-observed `POST /chat` duration.
- `websocket_connect_ms`: WebSocket connection and authentication duration.
- `websocket_first_event_ms`: time from sending the chat request to the first
  WebSocket event for that request, usually a status event.
- `websocket_ttft_ms`: time from sending the chat request to the first answer
  delta. Status events are not counted as a first token.
- `websocket_total_ms`: time from sending the request to `completed`.
- `server_timings_ms`: server-observed intent and total chat duration.
- `langchain_timings_ms`: rewrite, multi-query, retrieval, ranking, reranking,
  compression, and answer stage durations when LangChain mode is active.

Each aggregate reports count, median, p95, minimum, and maximum. Run at least
three measured requests and state the knowledge-base size, deployment region,
pipeline configuration, and query when publishing results.

When reporting benchmark numbers, include the knowledge-base size, deployment
region, pipeline configuration, and query so results are reproducible.
If `SMARTFEED_WS_FAST_PATH=0`, WebSocket TTFT includes the full LangChain
rewrite, multi-query, rerank, and compression stages before answer streaming.
Use `SMARTFEED_WS_FAST_PATH=1` for first-token latency demos, and report that
configuration with the benchmark result.

The benchmark account must contain representative saved articles. A run with
zero articles/chunks measures general LLM fallback latency, not RAG retrieval
performance.

## Large-Corpus Benchmark

Use `scripts/benchmark_large_corpus.py` to create a synthetic private corpus,
write it through the same `WebParserService` chunking rules and
`VectorStoreService.add_chunks()` path, then measure ChromaDB topK retrieval and
local RAG orchestration.

Fast local smoke test:

```bash
venv/bin/python scripts/benchmark_large_corpus.py \
  --target-chars 20000 \
  --article-count 4 \
  --runs 2 \
  --mock-embeddings \
  --chroma-dir /tmp/smartfeed-large-corpus-smoke \
  --output /tmp/smartfeed-large-corpus-smoke.json
```

Million-character validation:

```bash
venv/bin/python scripts/benchmark_large_corpus.py \
  --target-chars 1000000 \
  --article-count 40 \
  --runs 5 \
  --chroma-dir /tmp/smartfeed-large-corpus-1m \
  --output /tmp/smartfeed-large-corpus-1m.json
```

Add `--mock-embeddings` when you want to validate the ChromaDB/RAG plumbing
quickly without loading the sentence-transformers model. Omit it when measuring
real embedding throughput.

Reported fields:

- `corpus.total_chars`: generated corpus size in Chinese characters.
- `corpus.total_articles`: synthetic article count.
- `corpus.total_chunks`: chunks persisted into ChromaDB.
- `corpus.seed_total_ms`: end-to-end local seeding duration.
- `retrieval.summary_ms`: repeated ChromaDB query latency summary.
- `local_rag.total_ms`: local `ChatService` RAG orchestration duration with a
  deterministic benchmark LLM stub, used to measure retrieval and source
  assembly without external LLM network latency.

Sample local mock-embedding run on 2026-06-22:

```text
target_chars: 1,000,000
total_chars: 1,008,925
total_articles: 40
total_chunks: 2,480
seed_total_ms: 5,010.89
seed_chunks_per_second: 494.92
Chroma topK retrieval median: 7.97 ms
local RAG orchestration total: 10,656.98 ms
```

This sample proves the million-character corpus pipeline is reproducible, but
because it used `--mock-embeddings`, it should be described as a plumbing and
retrieval benchmark. Run the same command without `--mock-embeddings` before
claiming real embedding ingestion throughput.
