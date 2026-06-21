# Performance Benchmark

SmartFeed includes a repeatable chat benchmark for measuring the deployed
service instead of estimating resume metrics.

## Run

Set a dedicated benchmark account through environment variables so credentials
do not enter shell history or Git:

```bash
export SMARTFEED_BENCH_EMAIL='benchmark@example.com'
export SMARTFEED_BENCH_PASSWORD='replace-with-password'
venv/bin/python scripts/benchmark_chat.py --runs 3 \
  --output benchmark-result.json
```

The default target is the deployed Sealos API. Use `--base-url` for a local or
different environment. Use `--url` to benchmark current-page chat instead of a
global knowledge query.

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

Do not claim `TTFT < 500ms`, million-character scale, or second-level retrieval
until a saved benchmark result from the deployed configuration demonstrates it.
If `SMARTFEED_WS_FAST_PATH=0`, WebSocket TTFT includes the full LangChain
rewrite, multi-query, rerank, and compression stages before answer streaming.
Use `SMARTFEED_WS_FAST_PATH=1` for first-token latency demos, and report that
configuration with the benchmark result.

The benchmark account must contain representative saved articles. A run with
zero articles/chunks measures general LLM fallback latency, not RAG retrieval
performance.
