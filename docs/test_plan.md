# SmartFeed MVP Test Plan

## Prerequisites

Install dependencies:

```bash
pip install -r requirements.txt
```

Start the backend:

```bash
uvicorn app.main:app --reload
```

Open the debug page:

```text
http://127.0.0.1:8000/debug
```

## 1. Clear ChromaDB

Stop the backend, then remove the local vector database:

```bash
rm -rf chroma_db
```

Restart:

```bash
uvicorn app.main:app --reload
```

Expected result:

- `/search` should return empty results before any upload.
- `/chat` should return `source_type: "llm_fallback"` when no knowledge exists.

## 2. Upload example.com

Using curl:

```bash
curl -X POST http://127.0.0.1:8000/upload \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Using debug page:

- Open `/debug`.
- Enter `https://example.com`.
- Click Upload.

Expected result:

- Response should not crash.
- `data.metadata.parser` should be visible.
- `stored_chunks` should be `0` or greater.
- If content is readable, chunks should be displayed.

## 3. Upload a Chinese Article Page

Example:

```bash
curl -X POST http://127.0.0.1:8000/upload \
  -H "Content-Type: application/json" \
  -d '{"url":"https://news.99.com.cn/minsheng/20260605/2386221.htm"}'
```

Expected result:

- `status` should be `received` if readable content is extracted.
- `stored_chunks` should be greater than `0`.
- `data.metadata.parser` should be `jina` or `html_fallback`.
- `summary` should be present.
- Chunks should not be dominated by CSS, JSON, footer, hot search, or navigation text.

## 4. Test /chat With URL

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"这篇文章讲了什么","url":"https://news.99.com.cn/minsheng/20260605/2386221.htm"}'
```

Expected result:

- `source_type` should be `page` if page chunks exist.
- `intent` should be `page_reference`.
- `retrieval_scope` should be `page_first`.
- `sources` should include the provided URL.
- `answer` should be generated from retrieved chunks, unless DeepSeek is unavailable.

## 5. Test /chat Without URL

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"吃菌子中毒会怎么样"}'
```

Expected result:

- System searches the global knowledge base.
- `intent` should be `knowledge_or_general_query`.
- `source_type` should be `knowledge_base` if matching chunks exist.
- `sources` should include matching chunks.

## 5.1 Test Article Reference Without URL

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"这篇文章讲了什么"}'
```

Expected result:

- System should not run global retrieval.
- `intent` should be `page_reference`.
- `retrieval_scope` should be `none`.
- `fallback_policy` should be `ask_for_page`.
- `source_type` should be `need_page_context`.
- `answer` should ask the user to provide an article URL or title.

## 5.2 Test Knowledge Or General Query

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"如何学习 Kotlin"}'
```

Expected result:

- System should search the global knowledge base first.
- `intent` should be `knowledge_or_general_query`.
- `retrieval_scope` should be `global`.
- If there are no high-relevance chunks, `source_type` should be `llm_fallback`.

## 5.3 Test Realtime Query

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"今天星期几"}'
```

Expected result:

- System should search the global knowledge base first.
- `intent` should be `realtime_or_current_query`.
- If there are no high-relevance chunks, `source_type` should be `unsupported_realtime`.
- The answer should not guess realtime information.

## 5.4 Test Search History

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"我之前看过关于人工智能的文章吗"}'
```

Expected result:

- System should search only the knowledge base.
- `intent` should be `search_history`.
- If there are no high-relevance chunks, `source_type` should be `no_knowledge_found`.
- The answer should not use general LLM knowledge as a substitute.

## 5.5 Test Unsupported Action

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"删除这篇文章"}'
```

Expected result:

- System should not search the knowledge base.
- `intent` should be `unsupported_action`.
- `source_type` should be `unsupported_action`.
- The answer should say the current version does not support the operation.

## 6. Test Unknown / Not Ingested URL

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"这篇文章讲了什么","url":"https://not-uploaded.example/article"}'
```

Expected result:

- Page retrieval should find no page chunks.
- System may fall back to global retrieval.
- If global knowledge has no related content, response should use `source_type: "llm_fallback"`.

## 7. Test DeepSeek API Key Missing

Temporarily remove or rename `.env`, or unset the key:

```bash
unset DEEPSEEK_API_KEY
```

Restart the backend.

Test `/upload` and `/chat`.

Expected result:

- Backend should not crash.
- `/upload` should still parse and store chunks.
- `summary` may contain `LLM unavailable...`.
- `/chat` may return `answer: "LLM unavailable"`.

Restore `.env` after the test.

## 8. Run Automated Tests

```bash
pytest
```

Expected result:

- All tests pass.
