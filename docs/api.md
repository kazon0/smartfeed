# SmartFeed API

Except for `GET /`, `GET /health`, `GET /debug`, `POST /auth/register`, and `POST /auth/login`,
all endpoints require `Authorization: Bearer $ACCESS_TOKEN`. Article, search,
statistics, insights, upload, and chat data are scoped to the authenticated user.

## GET /

Health check.

### Request JSON

No request body.

### Response JSON

```json
{
  "status": "ok"
}
```

### curl

```bash
curl http://127.0.0.1:8000/
```

### Typical Failures

- Server is not running.
- Port is not `8000`.

## GET /health

Deployment health check.

### Request JSON

No request body.

### Response JSON

```json
{
  "status": "ok"
}
```

### curl

```bash
curl http://127.0.0.1:8000/health
```

## POST /auth/register

Creates a user with an Argon2 password hash and returns a JWT access token.

```json
{
  "email": "user@example.com",
  "password": "strong-password",
  "display_name": "SmartFeed User"
}
```

Successful response uses HTTP `201`:

```json
{
  "access_token": "...",
  "token_type": "bearer",
  "user": {
    "id": "...",
    "email": "user@example.com",
    "display_name": "SmartFeed User",
    "created_at": "2026-06-21T00:00:00Z",
    "updated_at": "2026-06-21T00:00:00Z"
  }
}
```

- Duplicate normalized email returns HTTP `409`.
- Passwords must contain 8 to 128 characters.

## POST /auth/login

Authenticates an email and password and returns the same token response as registration.

```json
{
  "email": "user@example.com",
  "password": "strong-password"
}
```

Invalid credentials return HTTP `401`.

## GET /auth/me

Returns the current user for a bearer access token.

```bash
curl http://127.0.0.1:8000/auth/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

Missing, invalid, expired, or deleted-user tokens return HTTP `401`.

## PATCH /auth/me

Updates the current user's display name and profile bio. Values are trimmed; the display name must contain 1 to 120 characters and the bio may contain up to 200 characters.

```bash
curl -X PATCH http://127.0.0.1:8000/auth/me \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"display_name":"Journal Reader","bio":"Keep collecting useful ideas."}'
```

The response uses the same user object as `GET /auth/me`. Invalid display names return HTTP `422`.

Authentication requires `DATABASE_URL` and a `JWT_SECRET` containing at least 32 characters. Missing, invalid, or expired credentials on protected business endpoints return HTTP `401`.

Chroma chunks created before user isolation do not contain `user_id` and are not
visible to any account. Re-upload those articles after signing in.

## GET /debug

Development-only browser page for manually testing upload and chat flows.

### Request JSON

No request body.

### Response

HTML page.

### curl

```bash
curl http://127.0.0.1:8000/debug
```

### Typical Failures

- Server is not running.
- Browser cannot reach `127.0.0.1:8000`.

## POST /upload

Fetches a web page, extracts readable content, stores user-scoped chunks in ChromaDB, upserts article metadata in PostgreSQL, and returns a DeepSeek summary.

### Request JSON

```json
{
  "url": "https://example.com"
}
```

### Response JSON

Successful ingestion:

```json
{
  "status": "received",
  "data": {
    "url": "https://example.com",
    "title": "Example Domain",
    "content": "Readable article text...",
    "sections": [
      {
        "index": 0,
        "title": "Section title",
        "content": "Section text..."
      }
    ],
    "chunks": ["chunk 1", "chunk 2"],
    "chunk_metadata": [
      {
        "section_index": 0,
        "section_title": "Section title",
        "section_chunk_index": 0
      }
    ],
    "metadata": {
      "source": "web",
      "parser": "jina",
      "length": 1234,
      "topic": "新闻",
      "topic_source": "llm",
      "topic_confidence": 0.86,
      "topic_reason": "文章来自新闻媒体，内容是政策修订报道。"
    }
  },
  "stored_chunks": 2,
  "summary": "中文总结..."
}
```

No readable article content:

```json
{
  "status": "failed",
  "error": "No readable article content extracted",
  "url": "https://example.com",
  "stored_chunks": 0
}
```

Fetch or parse error:

```json
{
  "status": "received",
  "data": {
    "error": "Request failed: ...",
    "url": "https://bad-url.example"
  },
  "stored_chunks": 0,
  "summary": ""
}
```

### curl

```bash
curl -X POST http://127.0.0.1:8000/upload \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

### Typical Failures

- URL cannot be resolved or fetched.
- Jina Reader and HTML fallback both fail.
- Page has no readable article content.
- DeepSeek API key is missing: `summary` may contain `LLM unavailable...`.
- DeepSeek topic classification unavailable or low confidence: topic falls back to rule-based classification.
- ChromaDB or embedding dependencies are not installed.

## POST /search

Runs semantic search against the ChromaDB knowledge base.

### Request JSON

```json
{
  "query": "人工智能是什么"
}
```

### Response JSON

```json
{
  "query": "人工智能是什么",
  "results": [
    {
      "content": "matched chunk text...",
      "metadata": {
        "source": "web",
        "parser": "jina",
        "length": 1234,
        "url": "https://example.com",
        "title": "Example Domain",
        "section_index": 0,
        "section_title": "Section title",
        "section_chunk_index": 0,
        "chunk_index": 0
      },
      "score": 0.82
    }
  ]
}
```

### curl

```bash
curl -X POST http://127.0.0.1:8000/search \
  -H "Content-Type: application/json" \
  -d '{"query":"人工智能是什么"}'
```

### Typical Failures

- Empty query returns an empty results list.
- No uploaded content returns an empty results list.
- ChromaDB or embedding dependencies are not installed.

## GET /stats

Returns knowledge base distribution statistics based on chunks currently stored in ChromaDB.

### Request JSON

No request body.

### Response JSON

```json
{
  "total_chunks": 12,
  "total_articles": 3,
  "topics": [
    {
      "topic": "科技",
      "chunk_count": 5,
      "percentage": 41.67
    }
  ],
  "domains": [
    {
      "domain": "example.com",
      "chunk_count": 4,
      "percentage": 33.33
    }
  ],
  "articles": [
    {
      "url": "https://example.com/article",
      "title": "Example Article",
      "domain": "example.com",
      "chunk_count": 4,
      "percentage": 33.33
    }
  ]
}
```

### curl

```bash
curl http://127.0.0.1:8000/stats
```

### Typical Failures

- No uploaded content returns zero counts and empty distribution lists.
- Topic classification is rule-based and may be inaccurate for ambiguous content.
- ChromaDB dependencies or local database files are unavailable.

## GET /articles

Lists the authenticated user's saved article metadata from PostgreSQL.

### Request JSON

No request body.

### Response JSON

```json
{
  "articles": [
    {
      "url": "https://example.com/article",
      "title": "Example Article",
      "domain": "example.com",
      "chunk_count": 4,
      "topic": "科技"
    }
  ],
  "total": 1
}
```

### curl

```bash
curl http://127.0.0.1:8000/articles
```

### Typical Failures

- No uploaded content returns an empty list.
- PostgreSQL is unavailable or migrations have not been applied.

## GET /articles/status

Returns whether a single URL exists in the authenticated user's PostgreSQL article records.

This is intended for Android share/open flows that need to decide whether a URL
has already been ingested before uploading again.

### Request JSON

No request body. Pass `url` as a query parameter.

### Response JSON

When the URL exists:

```json
{
  "exists": true,
  "url": "https://example.com/article",
  "title": "Example Article",
  "domain": "example.com",
  "topic": "科技",
  "chunk_count": 4
}
```

When the URL is not found:

```json
{
  "exists": false,
  "url": "https://example.com/missing",
  "title": "",
  "domain": "example.com",
  "topic": "",
  "chunk_count": 0
}
```

### curl

```bash
curl "http://127.0.0.1:8000/articles/status?url=https%3A%2F%2Fexample.com%2Farticle"
```

### Typical Failures

- Missing `url` query parameter returns FastAPI validation error.
- URL has never been uploaded or has been deleted: `exists` is `false`.
- PostgreSQL is unavailable or migrations have not been applied.

## GET /insights

Returns an AI-assisted knowledge base summary for the Android Analysis page.

### Request JSON

No request body.

### Response JSON

```json
{
  "status": "ok",
  "total_articles": 3,
  "summary": "你的知识库主要关注科技和学习内容，近期保存的文章适合围绕算法、开发和学习方法继续追问。",
  "highlights": [
    "科技类内容占比较高",
    "主要来源集中在少数网站"
  ],
  "suggestions": [
    "可以从文章管理页打开重点文章继续提问",
    "可以补充不同来源的文章做对比"
  ],
  "source": "llm"
}
```

### curl

```bash
curl http://127.0.0.1:8000/insights
```

### Typical Failures

- No uploaded content returns a fallback summary and empty highlights.
- If `DEEPSEEK_API_KEY` is not configured or the LLM request fails, the API returns a rule-based fallback summary with `source: "fallback"`.
- PostgreSQL is unavailable or migrations have not been applied.

## DELETE /articles

Deletes the authenticated user's PostgreSQL article record and matching ChromaDB chunks.

### Request JSON

```json
{
  "url": "https://example.com/article"
}
```

### Response JSON

```json
{
  "status": "deleted",
  "url": "https://example.com/article",
  "deleted_chunks": 4
}
```

If the URL is not found:

```json
{
  "status": "not_found",
  "url": "https://example.com/missing",
  "deleted_chunks": 0
}
```

### curl

```bash
curl -X DELETE http://127.0.0.1:8000/articles \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/article"}'
```

### Typical Failures

- URL does not exist in the local ChromaDB collection.
- ChromaDB dependencies or local database files are unavailable.

## GET /conversations

Returns all conversations and messages owned by the authenticated user, newest
first. The response fields map directly to the Android Room conversation model:
`source_url`, `topic`, `title`, `summary`, `status`, `stored_chunks`,
`created_at_millis`, `updated_at_millis`, and `messages`.

```bash
curl http://127.0.0.1:8000/conversations \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## PUT /conversations/{id}

Creates or replaces one owned conversation and its complete ordered message
list. This endpoint is idempotent and uses `updated_at_millis` for latest-write
wins behavior. A stale device write returns the newer server version without
overwriting it.

```json
{
  "title": "RAG article",
  "source_url": "https://example.com/rag",
  "summary": "Article summary",
  "status": "ready",
  "topic": "科技",
  "stored_chunks": 4,
  "created_at_millis": 1700000000000,
  "updated_at_millis": 1700000001000,
  "messages": [
    {"type": "user", "text": "Explain RAG"},
    {
      "type": "assistant",
      "response": {"status": "success", "answer": "...", "sources": []}
    }
  ]
}
```

Message `type` is one of `user`, `summary`, `assistant`, or `error`. Assistant
`response` preserves the complete Android `ChatResponse`, including sources.
Using an id already owned by another user returns HTTP `409`.

## DELETE /conversations/{id}

Deletes one conversation and its messages only when it belongs to the current
user. Returns `status: "not_found"` for missing or foreign conversations.

## WebSocket /ws/chat

Runs authenticated chat over WebSocket while keeping `POST /chat` as the stable
fallback. It emits stage events, answer delta events, and the final full
`/chat` response. To reduce first-token latency, WebSocket chat uses a
low-latency retrieval path that skips LLM query expansion, LLM rerank, and
context compression before the final streaming answer. `POST /chat` keeps the
full quality-oriented RAG path.
Set `SMARTFEED_WS_FAST_PATH=0` to make WebSocket chat use the same full
preprocessing path as `POST /chat`. Android buffers incoming answer deltas and
renders them at a steady typing cadence, so fast model chunks do not appear as
one sudden block of text.

### Connect

Pass the JWT access token as a query parameter:

```text
wss://your-api.example.com/ws/chat?token=$ACCESS_TOKEN
```

The server also accepts an `Authorization: Bearer $ACCESS_TOKEN` header when the
client can provide one.

### Client Message

Send the same payload shape as `POST /chat`:

```json
{
  "query": "这篇文章讲了什么？",
  "url": "https://example.com/article",
  "mode": "page",
  "history": [
    {"role": "user", "content": "先总结一下"}
  ]
}
```

### Server Events

Connection and auth status:

```json
{"type":"status","stage":"connected"}
{"type":"status","stage":"authenticated"}
```

Per-message status:

```json
{"type":"status","stage":"retrieving"}
{"type":"status","stage":"answering"}
```

Answer streaming:

```json
{"type":"delta","text":"根据当前网页，"}
{"type":"delta","text":"这篇文章主要讨论..."}
```

Final response:

```json
{
  "type": "completed",
  "stage": "completed",
  "response": {
    "status": "ok",
    "answer": "...",
    "sources": []
  }
}
```

Typical error event:

```json
{
  "type": "error",
  "error_code": "UNAUTHORIZED",
  "message": "Invalid or missing access token."
}
```

Current `error_code` values:

- `UNAUTHORIZED`
- `INVALID_REQUEST`
- `CHAT_FAILED`

## WebSocket /ws/upload

Runs authenticated article upload over WebSocket while keeping `POST /upload`
as the stable fallback. It streams long-running upload stages and summary text
while preserving the final `UploadResponse` shape.

### Connect

```text
wss://your-api.example.com/ws/upload?token=$ACCESS_TOKEN
```

### Client Message

```json
{"url":"https://example.com/article"}
```

### Server Events

Connection and auth status:

```json
{"type":"status","stage":"connected"}
{"type":"status","stage":"authenticated"}
```

Upload status:

```json
{"type":"status","stage":"parsing"}
{"type":"status","stage":"summarizing"}
{"type":"status","stage":"classifying"}
{"type":"status","stage":"storing"}
```

Summary streaming:

```json
{"type":"delta","target":"summary","text":"这篇文章主要介绍..."}
```

Final response:

```json
{
  "type": "completed",
  "stage": "completed",
  "response": {
    "status": "received",
    "stored_chunks": 12,
    "summary": "...",
    "data": {
      "url": "https://example.com/article",
      "title": "Article title"
    }
  }
}
```

Current `error_code` values:

- `UNAUTHORIZED`
- `INVALID_REQUEST`
- `UPLOAD_FAILED`

## POST /chat

Runs retrieval and sends retrieved context to DeepSeek to generate an answer.

### Request JSON

Global knowledge base chat:

```json
{
  "query": "吃菌子中毒会怎么样"
}
```

Page-preferred chat:

```json
{
  "query": "这篇文章讲了什么",
  "url": "https://example.com"
}
```

Follow-up chat with optional recent local history:

```json
{
  "query": "继续说",
  "url": "https://example.com",
  "history": [
    {
      "role": "user",
      "content": "这篇文章讲了什么"
    },
    {
      "role": "assistant",
      "content": "这篇文章主要介绍了野生菌中毒风险。"
    }
  ]
}
```

`mode` is still accepted for backward compatibility:

```json
{
  "query": "这篇文章讲了什么",
  "mode": "global",
  "url": "https://example.com"
}
```

### Response JSON

```json
{
  "status": "ok",
  "error_code": null,
  "message": "",
  "answer": "自然语言回答...",
  "sources": [
    {
      "url": "https://example.com",
      "title": "Example Domain",
      "display_title": "Example Domain",
      "section_title": "Section title、Another section",
      "section_titles": ["Section title", "Another section"],
      "section_index": 0,
      "section_indexes": [0, 1],
      "chunk_index": 0,
      "chunk_indexes": [0, 1, 2],
      "score": 0.82,
      "source_summary": "《Example Domain》解释了与问题相关的核心内容。",
      "content_preview": "merged source text preview...",
      "source_note": "《Example Domain》解释了与问题相关的核心内容。"
    }
  ],
  "source_type": "knowledge_base",
  "intent": "knowledge_or_general_query",
  "intent_reason": "默认作为普通知识、学习、技术、解释、建议或代码类问题处理。",
  "retrieval_scope": "global",
  "fallback_policy": "llm_allowed",
  "debug": {
    "rag_pipeline": "RAGPipeline",
    "rewritten_query": "吃菌子中毒 症状 风险 处理",
    "search_queries": ["吃菌子中毒 症状 风险 处理", "吃菌子中毒会怎么样"],
    "retrieval_steps": [
      {
        "scope": "global",
        "query": "吃菌子中毒 症状 风险 处理",
        "metadata_filter": {},
        "vector_count": 5,
        "keyword_count": 2
      }
    ],
    "selected_chunks": [
      {
        "url": "https://example.com/article",
        "title": "Example",
        "chunk_index": 0,
        "score": 0.82,
        "preview": "chunk preview..."
      }
    ],
    "context": {
      "chunk_count": 1,
      "compressed": true,
      "raw_length": 1200,
      "compressed_length": 480
    }
  }
}
```

Stable fields for Android MVP:

- `status`: `ok` or `failed`
- `error_code`: `null` when answerable, otherwise one of the stable error codes below
- `message`: user-readable explanation for failed states, empty when `status` is `ok`
- `answer`: answer text or fallback explanation
- `sources`: article-level source cards for UI display
- `source_type`: where the answer came from
- `intent`: classified query intent
- `retrieval_scope`: retrieval scope selected by backend
- `debug`: diagnostics for development and troubleshooting. Android should not use it for core business logic.

Stable `error_code` values:

- `NEED_PAGE_CONTEXT`
- `PAGE_CONTENT_NOT_FOUND`
- `REALTIME_UNSUPPORTED`
- `NO_KNOWLEDGE_FOUND`
- `UNSUPPORTED_ACTION`
- `LLM_UNAVAILABLE`

Possible `source_type` values:

- `page`
- `knowledge_base`
- `mixed`
- `llm_fallback`
- `need_page_context`
- `page_not_found_with_suggestions`
- `unsupported_realtime`
- `no_knowledge_found`
- `unsupported_action`

Possible `intent` values:

- `page_reference`
- `knowledge_or_general_query`
- `realtime_or_current_query`
- `search_history`
- `unsupported_action`

Possible `retrieval_scope` values:

- `page_first`
- `global`
- `none`

Possible `fallback_policy` values:

- `knowledge_then_llm`
- `ask_for_page`
- `llm_allowed`
- `no_guess_realtime`
- `no_llm_general_answer`
- `unsupported_action`

If the user asks an article-reference question without `url`, SmartFeed does not run global retrieval:

```json
{
  "status": "failed",
  "error_code": "NEED_PAGE_CONTEXT",
  "message": "我无法确定你指的是哪篇文章，请先分享网页，或提供文章链接/标题。",
  "answer": "我无法确定你指的是哪篇文章，请先分享网页，或提供文章链接/标题。",
  "sources": [],
  "source_type": "need_page_context",
  "intent": "page_reference",
  "intent_reason": "用户问题依赖文章指代，但请求中没有 url。",
  "retrieval_scope": "none",
  "fallback_policy": "ask_for_page"
}
```

When `url` is provided, SmartFeed treats the request as current-page chat:

- Page-wide questions such as `这篇文章讲了什么`、`总结一下`、`十种算法有哪些`、`有哪些方法` use quality-filtered page context from the ingested chunks for that URL. Link-heavy, author-card, ad, related-article, footer, and navigation-like chunks are deprioritized.
- More specific current-page questions use lightweight hybrid retrieval: vector results are merged with keyword matches from the uploaded page chunks, then expanded to the matched article section when section metadata exists. If old chunks do not have section metadata, SmartFeed falls back to neighboring chunks.
- If the URL has no ingested chunks, SmartFeed does not use unrelated global chunks to answer that page. It returns `source_type: "page_not_found_with_suggestions"` and lists saved article suggestions.
- `sources` are display-oriented citation blocks. Chunks from the same article URL are merged into one article-level source card, `section_title` summarizes up to the first three matching sections, `section_titles` / `section_indexes` preserve the grouped section metadata, `chunk_indexes` records the included chunk indexes, `display_title` is for UI display, and `source_summary` / `source_note` is added when LLM source description is available.
- `/chat` limits returned sources to the most useful article-level sources. The current limit is 3 distinct articles.
- `content_preview` is display-cleaned: Markdown links are removed, and preview text is truncated before visible noise such as author picks, related recommendations, and copyright/original-declaration blocks. It is still best used as expandable evidence text, not the default main UI text.

When `url` is not provided, SmartFeed searches the global knowledge base with lightweight hybrid retrieval:

- Vector results from ChromaDB are merged with keyword matches from stored chunks.
- Merged chunks are reranked and filtered by score.
- If no high-relevance knowledge exists and policy allows general fallback, the backend returns `source_type: "llm_fallback"`.

Diagnostics:

- `/chat` returns a `debug` object with the active RAG pipeline name, rewritten query, multi-query search variants, retrieval hit counts, selected chunk previews, and context compression status.
- `debug.timings_ms` records server-side intent and total request duration. In LangChain mode, `debug.langchain_timings_ms` records rewrite, multi-query, retrieval, rank, rerank, compression, and answer stage durations. These diagnostics are not stable Android business fields.
- Default `debug.rag_pipeline` is `RAGPipeline`.
- To run the LangChain Core Runnable retrieval chain, start the backend with:

```bash
SMARTFEED_RAG_PIPELINE=langchain uvicorn app.main:app --reload
```

- In that mode, `debug.rag_pipeline` should be `LangChainRAGPipeline`, and `debug.langchain_stages` records rewrite, multi-query, retrieval, rank, rerank, compression, and answer stages. Initialization or chain execution failures fall back to classic `RAGPipeline` behavior.
- For WebSocket chat to use the same full LangChain path, also set `SMARTFEED_WS_FAST_PATH=0`. If this variable is omitted or set to `1`, `/ws/chat` keeps the lower-latency streaming path while regular `POST /chat` can still use LangChain.
- `GET /debug` renders this diagnostics data in the browser so retrieval misses can be inspected without reading large terminal JSON.
- The `debug` object is for development. UI clients should continue relying on stable fields such as `status`, `error_code`, `answer`, `sources`, `source_type`, `intent`, `retrieval_scope`, and `fallback_policy`.

### curl

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"吃菌子中毒会怎么样"}'
```

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"这篇文章讲了什么","url":"https://example.com"}'
```

### Typical Failures

- No matching chunks: response uses `source_type: "llm_fallback"`.
- Article-reference query without `url`: response uses `source_type: "need_page_context"`.
- URL is provided but the page was not uploaded or has no chunks: response uses `source_type: "page_not_found_with_suggestions"`.
- Realtime query without relevant knowledge: response uses `source_type: "unsupported_realtime"`.
- Search-history query without matching knowledge: response uses `source_type: "no_knowledge_found"`.
- Unsupported management action: response uses `source_type: "unsupported_action"`.
- DeepSeek API key is missing or API call fails: `answer` is `LLM unavailable`.
- ChromaDB or embedding dependencies are not installed.
