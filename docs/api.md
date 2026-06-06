# SmartFeed API

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

Fetches a web page, extracts readable content, chunks it, stores chunks in ChromaDB, and returns a DeepSeek summary.

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
      "length": 1234
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
  "answer": "自然语言回答...",
  "sources": [
    {
      "url": "https://example.com",
      "title": "Example Domain",
      "display_title": "Example Domain",
      "section_title": "Section title",
      "section_index": 0,
      "chunk_index": 0,
      "chunk_indexes": [0, 1, 2],
      "score": 0.82,
      "source_summary": "这段来源解释了与问题相关的核心内容。",
      "content_preview": "merged source text preview...",
      "source_note": "这段来源解释了与问题相关的核心内容。"
    }
  ],
  "source_type": "knowledge_base",
  "intent": "knowledge_or_general_query",
  "intent_reason": "默认作为普通知识、学习、技术、解释、建议或代码类问题处理。",
  "retrieval_scope": "global",
  "fallback_policy": "llm_allowed"
}
```

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

- Page-wide questions such as `这篇文章讲了什么`、`总结一下`、`十种算法有哪些`、`有哪些方法` use the ingested chunks for that URL as page context.
- More specific questions still retrieve matching chunks first, then expand neighboring chunks.
- If the URL has no ingested chunks, SmartFeed does not use unrelated global chunks to answer that page. It returns `source_type: "page_not_found_with_suggestions"` and lists saved article suggestions.
- `sources` are display-oriented citation blocks. Consecutive chunks from the same article section are merged, `section_title` identifies the article section, `chunk_indexes` records the included chunk indexes, `display_title` is for UI display, and `source_summary` / `source_note` is added when LLM source description is available.
- `content_preview` is kept for debugging or "view evidence" expansion. It should not be the default main UI text.

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
