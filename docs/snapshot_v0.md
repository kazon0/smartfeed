# SmartFeed Snapshot v0

## 1. 当前系统模块总结

### FastAPI routes

- `GET /`
  - 定义位置：`app/main.py`
  - 返回：`{"status": "ok"}`

- `GET /debug`
  - 定义位置：`app/routes/debug.py`
  - 返回开发调试 HTML 页面。
  - 页面通过浏览器调用现有 `/upload` 和 `/chat`。
  - 页面展示 parser、stored chunks、summary、chunks、answer、sources、source_type。
  - sources 默认展示文章标题、URL、摘要和 chunk 索引，原始 `content_preview` 放在折叠的 Raw preview 中。

- `POST /upload`
  - 定义位置：`app/routes/upload.py`
  - 输入：`{"url": "..."}`
  - 当前流程：
    - 调用 `WebParserService.prepare(url)` 抓取并解析网页。
    - 解析成功后检查 `data["chunks"]`。
    - 如果 chunks 为空，返回 failed，不写入 ChromaDB，不调用 summary。
    - 如果 chunks 非空，先调用 `VectorStoreService.delete_by_url(url)` 删除同 URL 旧 chunks。
    - 调用 `VectorStoreService.add_chunks(chunks, metadata, chunk_metadata)` 写入 ChromaDB。
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

- `POST /chat`
  - 定义位置：`app/routes/chat.py`
  - 输入：
    - `query`
    - `url` 可选
    - `mode` 保留，默认 `global`
  - 当前流程：
    - 调用 `QueryIntentService.classify(query, has_url)`。
    - 如果 `retrieval_scope == "none"`，根据 fallback policy 直接返回，不检索。
    - 如果请求包含 `url`，进入当前网页聊天逻辑。
    - 当前网页聊天会先按 `metadata.url` 查询当前网页 chunks，并读取该 URL 已入库的全部 chunks。
    - 对 `总结`、`讲了什么`、`有哪些`、`方法`、`算法`、`步骤` 等页面级问题，直接选择当前 URL 的页面上下文 chunks 供 LLM 回答。
    - 对更具体的问题，先使用向量检索当前网页 chunks，同时在当前 URL 全部 chunks 中做轻量关键词召回。
    - 当前网页向量结果和关键词结果会合并去重，再优先扩展到命中 chunk 所在 section。
    - 如果旧 chunks 没有 section metadata，则 fallback 到相邻 chunks 扩展。
    - 如果当前 URL 没有可用 chunks，不使用全局知识库假装回答该网页，而是返回已保存文章建议。
    - 如果请求不包含 `url`，执行全局知识库检索。
    - 全局检索结果会做轻量关键词重排。
    - 使用 `score >= 0.25` 判断高相关 chunks。
    - 根据 `fallback_policy` 决定是否调用 LLM 兜底、是否拒绝实时猜测、是否只返回知识库未命中。
    - 有高相关 chunks 时调用 `LLMService.answer()` 基于 chunks 生成回答。
    - 传给 LLM 的 context 使用 `[1]`、`[2]`、`[3]` 形式的来源编号。
    - 无高相关 chunks 且允许 LLM 兜底时调用 `LLMService.answer_without_context()`。
    - 返回 sources 时会合并同一 URL、同一 section 下连续 chunks，形成更适合前端展示的引用块。
    - sources 包含 `display_title`，用于前端展示更干净的文章标题。
    - sources 包含 `section_title` 和 `section_index`，用于展示来源章节。
    - sources 可包含 `source_summary` / `source_note`，用于说明该来源与问题的关系。
    - sources 保留 `content_preview`，用于调试或展开查看依据，不作为默认主展示内容。
  - 返回字段：
    - `answer`
    - `sources`
    - `source_type`
    - `intent`
    - `intent_reason`
    - `retrieval_scope`
    - `fallback_policy`

### services 层

- `app/services/web_parser.py`
  - 服务类：`WebParserService`
  - 当前能力：
    - 优先使用 Jina Reader：`https://r.jina.ai/{url}`。
    - Jina 不可用、返回异常或没有 chunks 时，fallback 到原 HTML 解析。
    - 使用 `requests.Session` 请求网页。
    - 设置 `User-Agent`、`Accept`、`Accept-Language` 请求头。
    - 使用 `response.apparent_encoding` 处理页面编码。
    - Jina 路径会解析 `Title:` 和 `Markdown Content:`。
    - Jina 路径会基于 Markdown heading 提取 `sections`。
    - Jina 路径会清理 Markdown 图片、链接和格式符号。
    - HTML fallback 路径使用 `BeautifulSoup(html, "html.parser")`。
    - HTML fallback 路径删除 `script`、`style`、`noscript`、`nav`、`header`、`footer`、`aside`、`form`、`iframe`。
    - HTML fallback 路径优先提取 `article`、`main`、content/article/detail 相关容器。
    - HTML fallback 路径会基于 `h1` 到 `h4`、段落、列表和代码节点提取 `sections`。
    - 清洗正文文本，去除空行、重复行、明显 CSS/JS/JSON 行、备案版权导航类内容。
    - 按 `500` 字符 chunk size 和 `50` 字符 overlap 切分正文。
    - 每个 chunk 会生成对应 `chunk_metadata`，包含 `section_index`、`section_title`、`section_chunk_index`。
    - 过滤明显噪声 chunk。
  - 成功输出：
    - `url`
    - `title`
    - `content`
    - `sections`
    - `chunks`
    - `chunk_metadata`
    - `metadata.source`
    - `metadata.parser`，值为 `jina` 或 `html_fallback`
    - `metadata.length`
  - 失败输出：
    - `error`
    - `url`

- `app/services/vector_store.py`
  - 服务类：`VectorStoreService`
  - 当前能力：
    - 使用 `chromadb.PersistentClient(path="chroma_db")`。
    - 使用 collection：`smartfeed`。
    - collection metadata 设置为 cosine：`{"hnsw:space": "cosine"}`。
    - `add_chunks(chunks, metadata, chunk_metadata=None)` 写入 chunks、embeddings、metadata。
    - 写入时会将通用 metadata 和每个 chunk 的 `section_index`、`section_title`、`section_chunk_index` 合并。
    - `delete_by_url(url)` 删除 `metadata.url == url` 的旧 chunks。
    - `get_chunks_by_url(url)` 返回指定 URL 的全部 chunks，并按 `chunk_index` 排序。
    - `list_sources(limit=10)` 返回已保存文章来源列表。
    - `query(text, top_k=5, metadata_filter=None)` 执行语义检索。
    - query 支持可选 Chroma metadata filter。
    - 优先使用 `sentence-transformers` 的 `all-MiniLM-L6-v2`。
    - embedding 模型不可用时使用确定性的 mock embedding。
    - 检索结果返回 `content`、`metadata`、`score`。

- `app/services/llm_service.py`
  - 服务类：`LLMService`
  - 当前能力：
    - 使用 DeepSeek Chat Completions API。
    - 使用 `python-dotenv` 加载 `.env`。
    - 从环境变量 `DEEPSEEK_API_KEY` 读取 API Key。
    - `summarize(text)` 生成 100 到 200 字中文总结。
    - `answer(question, context_chunks)` 基于 context chunks 生成自然语言回答。
    - `answer()` prompt 要求最终回答面向普通用户，单篇文章场景使用文章标题或当前网页自然引用，不要求用户理解来源编号。
    - `answer_without_context(question, reason)` 在无可用知识库上下文或通用兜底场景生成回答。
    - `describe_sources(question, source_texts)` 为 sources 生成一句中文来源说明。
    - DeepSeek 调用不可用时返回 `LLM unavailable: ...` 字符串。

- `app/services/query_intent.py`
  - 服务类：`QueryIntentService`
  - 当前能力：
    - `classify(query, has_url=False)` 返回 query intent 信息。
    - 返回字段包括 `intent`、`retrieval_scope`、`requires_page_context`、`fallback_policy`、`reason`。
    - 识别优先级：
      - `unsupported_action`
      - `page_reference`
      - `search_history`
      - `realtime_or_current_query`
      - `knowledge_or_general_query`
    - `page_reference` 有 URL 时返回 `page_first`，无 URL 时返回 `none` + `ask_for_page`。
    - `knowledge_or_general_query` 返回 `global` + `llm_allowed`。
    - `realtime_or_current_query` 返回 `global` + `no_guess_realtime`。
    - `search_history` 返回 `global` + `no_llm_general_answer`。
    - `unsupported_action` 返回 `none` + `unsupported_action`。

## 2. 当前数据流

URL → Jina Reader 或 HTML fallback → text → sections → chunks + chunk metadata → embedding → ChromaDB → search/chat results

## 3. 当前 RAG + LLM 流程

### `/upload`

URL → parse → clean text → sections → chunks + section metadata → delete old chunks by URL → embedding → ChromaDB → DeepSeek summary → response

### `/search`

query → embedding → ChromaDB topK chunks → search results

### `/chat`

query + optional url → query intent classification → retrieval scope decision → page hybrid retrieval or global retrieval → keyword rerank / page context selection → relevance or policy decision → LLM answer → merged sources + source_type

### `/debug`

browser page → calls `/upload` and `/chat` → displays parser, chunks, summary, answer and sources

## 4. 已完成能力清单

- FastAPI 应用入口已创建。
- `GET /` 健康检查已实现。
- `GET /debug` 开发调试页已实现。
- `POST /upload` 网页上传入口已实现。
- Jina Reader 优先解析已实现。
- HTML fallback 解析已实现。
- 网页标题提取已实现。
- 网页正文提取已实现。
- 网页 sections 结构化提取已实现。
- parser 类型返回已实现。
- 文本清洗已实现。
- 正文 chunking 已实现。
- chunk 与 section metadata 关联已实现。
- 噪声 chunk 过滤已实现。
- 同 URL 覆盖更新已实现。
- ChromaDB 持久化向量存储已实现。
- `smartfeed` collection 已实现。
- chunks 写入向量库已实现。
- `sentence-transformers` embedding 优先加载已实现。
- 确定性 mock embedding fallback 已实现。
- `POST /search` 语义检索接口已实现。
- search 返回 `query` 和 `results` 已实现。
- search results 返回 `content`、`metadata`、`score` 已实现。
- `POST /chat` 问答接口已实现。
- chat query intent classification 已实现。
- chat 支持 `page_reference`、`knowledge_or_general_query`、`realtime_or_current_query`、`search_history`、`unsupported_action`。
- chat 支持可选 url 优先检索当前网页已实现。
- chat 对当前网页页面级问题使用该 URL 已入库页面上下文已实现。
- chat 对当前网页具体问题使用向量检索 + 关键词召回合并已实现。
- chat 对当前网页具体问题优先扩展到命中 section 已实现。
- chat 对没有 section metadata 的旧 chunks 使用相邻 chunks fallback 已实现。
- chat 当前 URL 没有可用 chunks 时返回已保存文章建议已实现。
- chat 全局知识库检索已实现。
- chat 高相关 score 阈值判断已实现，当前阈值为 `0.25`。
- chat keyword rerank 已实现。
- chat policy-based fallback 已实现。
- chat sources 返回已实现。
- chat sources 连续 chunk 合并展示已实现。
- chat sources 返回 `display_title` 已实现。
- chat sources 返回 `section_title` 和 `section_index` 已实现。
- chat sources 返回 `source_summary` 已实现。
- chat sources 返回 `content_preview` 已实现，当前长度最多为前 1200 字。
- chat sources 返回 `chunk_indexes` 已实现。
- chat sources 返回可选 `source_note` 已实现。
- chat source_type 返回已实现。
- chat intent metadata 返回已实现。
- DeepSeek API 调用封装已实现。
- `.env` API Key 加载已实现。
- `LLMService.summarize(text)` 已实现。
- `LLMService.answer(question, context_chunks)` 已实现。
- `LLMService.answer_without_context(question, reason)` 已实现。
- `/upload` 返回 `summary` 字段已实现。
- `docs/api.md` 接口文档已创建。
- `docs/test_plan.md` 手动测试计划已创建。
- `tests/test_mvp.py` 最小自动化测试已创建。
- `requirements.txt` 已包含 `pytest`。

## 5. 当前系统边界

### 当前能做什么

- 接收网页 URL。
- 优先通过 Jina Reader 获取网页正文。
- 在 Jina 不可用时使用 HTML fallback。
- 解析网页标题和正文。
- 标记本次解析使用的 parser。
- 将正文切分为 chunks。
- 将正文按标题结构组织为 sections。
- 将 chunks 关联到对应 section metadata。
- 将 chunks 转为 embedding。
- 按 URL 删除旧 chunks 并写入新 chunks。
- 将 chunks、metadata、embedding 存入本地 ChromaDB。
- 调用 DeepSeek 为网页正文生成中文 summary。
- 接收自然语言 query。
- 对 chat query 进行规则型意图分类。
- 根据 intent 决定检索范围和 fallback 策略。
- 将 query 转为 embedding。
- 从 ChromaDB 中检索 topK 相关 chunks。
- 当前网页聊天会在指定 URL 的 chunks 中做轻量关键词召回。
- 按 score 阈值判断是否存在高相关知识库内容。
- 基于高相关 chunks 调用 DeepSeek 生成回答。
- 在允许时使用 DeepSeek 生成通用兜底回答。
- 对实时类问题在无高相关知识库内容时拒绝猜测。
- 对用户历史/收藏查询在无知识库结果时不使用通用知识替代。
- 对当前不支持的管理操作直接返回 unsupported。
- 当前网页聊天中，对总结、列表、方法、算法、步骤类问题使用当前 URL 页面上下文回答。
- 当前网页聊天中，如果当前 URL 没有可用 chunks，不用无关全局 chunks 回答该网页。
- 返回合并后的回答来源 sources、source_type、intent、retrieval_scope 和 fallback_policy。
- 通过 `/debug` 页面进行开发验证。

### 当前不能保证什么

- 不能保证所有反爬网页都能抓取成功。
- 不能保证 Jina Reader 对所有网站都可用。
- 不能保证需要 JavaScript 渲染正文的网站能被 HTML fallback 完整解析。
- 不能保证 DeepSeek API 在未配置 `DEEPSEEK_API_KEY` 时可用。
- 不能保证 mock embedding 下的语义检索质量与真实 embedding 一致。
- 不能保证规则型 query intent classification 覆盖所有自然语言表达。
- 不能保证固定 chunking 能完整保留原网页的标题层级、代码块和章节结构。
- 当前页面级回答最多选择当前 URL 的前 `30` 个 chunks 作为上下文。
- 全局知识库检索当前仍以向量检索和轻量重排为主，尚未实现全局关键词召回。
- 不能提供实时搜索、天气、股价、汇率等外部实时工具结果。

## 6. 技术栈总结

- Backend framework：FastAPI
- ASGI server：Uvicorn
- HTTP client：requests
- Primary web reader：Jina Reader `https://r.jina.ai/`
- HTML parser：BeautifulSoup / beautifulsoup4
- Vector database：ChromaDB
- Embedding：sentence-transformers
- Embedding model：all-MiniLM-L6-v2
- Fallback embedding：deterministic mock embedding
- LLM provider：DeepSeek
- LLM API：Chat Completions
- LLM model：deepseek-chat
- Env loader：python-dotenv
- LLM API key env：`DEEPSEEK_API_KEY`
- Test framework：pytest
- Persistence path：`chroma_db`
- Python package list：`requirements.txt`
