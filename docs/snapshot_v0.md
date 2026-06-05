# SmartFeed Snapshot v0

## 1. 当前系统模块总结

### FastAPI routes

- `GET /`
  - 定义位置：`app/main.py`
  - 返回：`{"status": "ok"}`

- `POST /upload`
  - 定义位置：`app/routes/upload.py`
  - 输入：`{"url": "..."}`
  - 当前流程：
    - 调用 `WebParserService.prepare(url)` 抓取并解析网页。
    - 解析成功后读取 `data["chunks"]`。
    - 调用 `VectorStoreService.add_chunks(chunks, metadata)` 写入 ChromaDB。
    - 调用 `LLMService.summarize(data["content"])` 生成中文 summary。
  - 返回字段：
    - `status`
    - `data`
    - `stored_chunks`
    - `summary`

- `POST /search`
  - 定义位置：`app/routes/search.py`
  - 输入：`{"query": "..."}`
  - 当前流程：
    - 调用 `VectorStoreService.query(query)`。
  - 返回字段：
    - `query`
    - `results`

### services 层

- `app/services/web_parser.py`
  - 服务类：`WebParserService`
  - 当前能力：
    - 使用 `requests.Session` 请求网页。
    - 设置 `User-Agent`、`Accept`、`Accept-Language` 请求头。
    - 使用 `response.apparent_encoding` 处理页面编码。
    - 使用 `BeautifulSoup(html, "html.parser")` 解析 HTML。
    - 删除 `script`、`style`、`noscript`。
    - 优先提取 `<article>`，否则使用 `<body>`。
    - 清洗正文文本，去除空行。
    - 按 `500` 字符 chunk size 和 `50` 字符 overlap 切分正文。
  - 成功输出：
    - `url`
    - `title`
    - `content`
    - `chunks`
    - `metadata`
  - 失败输出：
    - `error`
    - `url`

- `app/services/vector_store.py`
  - 服务类：`VectorStoreService`
  - 当前能力：
    - 使用 `chromadb.PersistentClient(path="chroma_db")`。
    - 使用 collection：`smartfeed`。
    - collection metadata 设置为 cosine：`{"hnsw:space": "cosine"}`。
    - `add_chunks(chunks, metadata)` 写入 chunks、embeddings、metadata。
    - `query(text, top_k=5)` 执行语义检索。
    - 优先使用 `sentence-transformers` 的 `all-MiniLM-L6-v2`。
    - embedding 模型不可用时使用确定性的 mock embedding。
    - 检索结果返回 `content`、`metadata`、`score`。

- `app/services/llm_service.py`
  - 服务类：`LLMService`
  - 当前能力：
    - 使用 DeepSeek Chat Completions API。
    - 从环境变量 `DEEPSEEK_API_KEY` 读取 API Key。
    - `summarize(text)` 生成 100 到 200 字中文总结。
    - `answer(question, context_chunks)` 基于 context chunks 生成自然语言回答。
    - DeepSeek 调用不可用时返回 `LLM unavailable: ...` 字符串。

## 2. 当前数据流

URL → HTML → text → chunks → embedding → ChromaDB → search results

## 3. 当前 RAG + LLM 流程

### `/upload`

URL → HTML → text → chunks → embedding → ChromaDB → DeepSeek summary → response

### `/search`

query → embedding → ChromaDB topK chunks → search results

当前系统已经从纯 RAG 检索系统变为 RAG + LLM 理解系统：`/upload` 同时完成网页解析、chunk 入库和 DeepSeek summary 生成；`/search` 完成基于 ChromaDB 的语义检索。

## 4. 已完成能力清单

- FastAPI 应用入口已创建。
- `GET /` 健康检查已实现。
- `POST /upload` 网页上传入口已实现。
- 网页 HTML 获取已实现。
- 网页标题提取已实现。
- 网页正文提取已实现。
- 文本清洗已实现。
- 正文 chunking 已实现。
- ChromaDB 持久化向量存储已实现。
- `smartfeed` collection 已实现。
- chunks 写入向量库已实现。
- `sentence-transformers` embedding 优先加载已实现。
- 确定性 mock embedding fallback 已实现。
- `POST /search` 语义检索接口已实现。
- search 返回 `query` 和 `results` 已实现。
- search results 返回 `content`、`metadata`、`score` 已实现。
- DeepSeek API 调用封装已实现。
- `LLMService.summarize(text)` 已实现。
- `LLMService.answer(question, context_chunks)` 已实现。
- `/upload` 返回 `summary` 字段已实现。

## 5. 当前系统边界

### 当前能做什么

- 接收网页 URL。
- 抓取网页 HTML。
- 解析网页标题和正文。
- 将正文切分为 chunks。
- 将 chunks 转为 embedding。
- 将 chunks、metadata、embedding 存入本地 ChromaDB。
- 调用 DeepSeek 为网页正文生成中文 summary。
- 接收自然语言 query。
- 将 query 转为 embedding。
- 从 ChromaDB 中检索 topK 相关 chunks。
- 返回检索结果的正文片段、metadata 和 score。

### 当前不能保证什么

- 不能保证所有反爬网页都能抓取成功。
- 不能保证需要 JavaScript 渲染正文的网站能被解析完整。
- 不能保证 DeepSeek API 在未配置 `DEEPSEEK_API_KEY` 时可用。
- 不能保证 mock embedding 下的语义检索质量与真实 embedding 一致。

## 6. 技术栈总结

- Backend framework：FastAPI
- ASGI server：Uvicorn
- HTTP client：requests
- HTML parser：BeautifulSoup / beautifulsoup4
- Vector database：ChromaDB
- Embedding：sentence-transformers
- Embedding model：all-MiniLM-L6-v2
- Fallback embedding：deterministic mock embedding
- LLM provider：DeepSeek
- LLM API：Chat Completions
- LLM model：deepseek-chat
- LLM API key env：`DEEPSEEK_API_KEY`
- Persistence path：`chroma_db`
- Python package list：`requirements.txt`
