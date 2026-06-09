# SmartFeed Snapshot v0

## 1. 当前系统模块总结

### FastAPI routes

- `GET /articles`
  - 定义位置：`app/routes/articles.py`
  - 当前流程：
    - 调用 `VectorStoreService.list_articles()`。
    - 基于 ChromaDB chunks metadata 聚合已保存文章。
  - 返回字段：
    - `articles`
    - `total`
  - `articles` item 当前字段：
    - `url`
    - `title`
    - `domain`
    - `chunk_count`
    - `topic`

- `DELETE /articles`
  - 定义位置：`app/routes/articles.py`
  - 输入：`{"url": "..."}`
  - 当前流程：
    - 调用 `VectorStoreService.delete_by_url(url)`。
    - 删除该 URL 对应的全部 chunks。
  - 返回字段：
    - `status`
    - `url`
    - `deleted_chunks`

- `GET /`
  - 定义位置：`app/main.py`
  - 返回：`{"status": "ok"}`

- `GET /debug`
  - 定义位置：`app/routes/debug.py`
  - 返回开发调试 HTML 页面。
  - 页面通过浏览器调用现有 `/upload` 和 `/chat`。
  - 页面展示 parser、stored chunks、summary、chunks、answer、sources、source_type 和 chat diagnostics。
  - sources 默认展示文章标题、URL、摘要和 chunk 索引，原始 `content_preview` 放在折叠的 Raw preview 中。
  - diagnostics 展示 rewritten query、multi-query 列表、每个 query 的向量/关键词命中数、selected chunks 和 context compression 状态。

- `POST /upload`
  - 定义位置：`app/routes/upload.py`
  - 输入：`{"url": "..."}`
  - 当前流程：
    - 调用 `WebParserService.prepare(url)` 抓取并解析网页。
    - 解析成功后检查 `data["chunks"]`。
    - 如果 chunks 为空，返回 failed，不写入 ChromaDB，不调用 summary。
    - 如果 chunks 非空，调用 `LLMService.summarize(data["content"])` 生成中文 summary。
    - 调用 `LLMService.classify_topic(title, url, summary, content)` 生成文章 topic。
    - 如果 LLM topic 分类不可用或置信度低于 `0.55`，使用 `VectorStoreService.classify_topic()` 的规则分类 fallback。
    - 将 `topic`、`topic_source`、`topic_confidence`、`topic_reason` 写入网页 metadata。
    - 调用 `VectorStoreService.delete_by_url(url)` 删除同 URL 旧 chunks。
    - 调用 `VectorStoreService.add_chunks(chunks, metadata, chunk_metadata)` 写入 ChromaDB。
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

- `GET /stats`
  - 定义位置：`app/routes/stats.py`
  - 当前流程：
    - 调用 `VectorStoreService.stats()`。
    - 从 ChromaDB 已入库 chunks 统计知识库分布。
    - 使用轻量关键词规则对 chunk 做主题分类。
  - 返回字段：
    - `total_chunks`
    - `total_articles`
    - `topics`
    - `domains`
    - `articles`

- `POST /chat`
  - 定义位置：`app/routes/chat.py`
  - 业务实现位置：`app/services/chat_service.py`
  - 输入：
    - `query`
    - `url` 可选
    - `mode` 保留，默认 `global`
  - 当前流程：
    - 调用 `QueryIntentService.classify(query, has_url)`。
    - 如果 `retrieval_scope == "none"`，根据 fallback policy 直接返回，不检索。
    - 检索前调用 `LLMService.rewrite_query()` 将用户问题改写为更适合检索的 query。
    - query rewrite 不可用时回退使用用户原始 query。
    - 调用 `LLMService.generate_search_queries()` 基于用户原始 query 和 rewritten query 生成 2 到 4 个检索查询变体。
    - multi-query 不可用时至少使用 rewritten query 和用户原始 query。
    - 向量检索、关键词召回和轻量重排使用 multi-query 合并后的查询集合。
    - 最终回答仍使用用户原始 query。
    - 如果请求包含 `url`，进入当前网页聊天逻辑。
    - 当前网页聊天会先按 `metadata.url` 查询当前网页 chunks，并读取该 URL 已入库的全部 chunks。
    - 对 `总结`、`讲了什么`、`有哪些`、`方法`、`算法`、`步骤` 等页面级问题，选择当前 URL 的高质量页面上下文 chunks 供 LLM 回答。
    - 页面级上下文选择会降低链接密度高、作者卡片、广告、推荐文章、页脚和导航类 chunks 的优先级。
    - 对更具体的问题，先使用向量检索当前网页 chunks，同时在当前 URL 全部 chunks 中做轻量关键词召回。
    - 当前网页向量结果和关键词结果会合并去重，再优先扩展到命中 chunk 所在 section。
    - 如果旧 chunks 没有 section metadata，则 fallback 到相邻 chunks 扩展。
    - 如果当前 URL 没有可用 chunks，不使用全局知识库假装回答该网页，而是返回已保存文章建议。
    - 如果请求不包含 `url`，执行全局知识库检索。
    - 全局知识库检索会合并 ChromaDB 向量召回和全库 chunks 关键词召回。
    - 全局合并结果会做轻量关键词重排。
    - 轻量重排后会尝试调用 `LLMService.rerank_chunks()` 对候选 chunks 做语义重排。
    - LLM rerank 不可用时保持原有检索排序，不中断 `/chat`。
    - 使用 `score >= 0.25` 判断高相关 chunks。
    - 根据 `fallback_policy` 决定是否调用 LLM 兜底、是否拒绝实时猜测、是否只返回知识库未命中。
    - 有高相关 chunks 时调用 `LLMService.answer()` 基于 chunks 生成回答。
    - 传给 LLM 的 context 使用 `[1]`、`[2]`、`[3]` 形式的来源编号。
    - 回答前会尝试调用 `LLMService.compress_context()` 压缩上下文，保留与问题相关的来源头信息、步骤、代码、清单和结论。
    - context compression 不可用或返回空时，回退使用原始候选 chunks。
    - 无高相关 chunks 且允许 LLM 兜底时调用 `LLMService.answer_without_context()`。
    - 返回 sources 时会合并同一 URL、同一 section 下连续 chunks，形成更适合前端展示的引用块。
    - sources 包含 `display_title`，用于前端展示更干净的文章标题。
    - sources 包含 `section_title` 和 `section_index`，用于展示来源章节。
    - sources 可包含 `source_summary` / `source_note`，用于说明该来源与问题的关系。
    - sources 保留 `content_preview`，用于调试或展开查看依据，不作为默认主展示内容。
    - sources 当前最多返回 3 个展示来源。
    - 返回 `debug` 诊断字段，包含 rewritten query、search queries、retrieval steps、selected chunks 和 context compression 状态。
    - `debug` 字段用于开发排查，不作为 Android 核心业务字段。
  - 返回字段：
    - `status`
    - `error_code`
    - `message`
    - `answer`
    - `sources`
    - `source_type`
    - `intent`
    - `intent_reason`
    - `retrieval_scope`
    - `fallback_policy`
    - `debug`

### services 层

- `app/services/chat_service.py`
  - 服务类：`ChatService`
  - 当前能力：
    - 承载 `/chat` 的 query intent、query rewrite、multi-query retrieval、page/global retrieval、keyword retrieval、rerank、context compression、fallback 和 sources 构造流程。
    - `app/routes/chat.py` 只负责 FastAPI 请求模型和调用 `ChatService`。
    - 支持注入 `VectorStoreService`、`LLMService`、`QueryIntentService`，便于测试和后续替换 LangChain pipeline。
    - 对外返回结构保持 `/chat` 当前稳定字段不变。
    - 输出 `debug` 诊断信息，用于 `/debug` 页面展示检索链路。

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
    - `delete_by_url(url)` 返回删除的 chunk 数。
    - `list_articles(limit=1000)` 按 URL 聚合返回已保存文章列表。
    - `list_articles()` 优先使用 chunks metadata 中保存的 `topic`，旧数据没有 topic 时使用规则分类 fallback。
    - `get_chunks_by_url(url)` 返回指定 URL 的全部 chunks，并按 `chunk_index` 排序。
    - `get_all_chunks(limit=1000)` 返回本地知识库中的 chunks，用于轻量关键词召回。
    - `list_sources(limit=10)` 返回已保存文章来源列表。
    - `stats(limit=5000)` 返回知识库 chunks、文章、来源域名和主题占比统计。
    - 规则分类包括科技、学习、健康、职业、财经、生活、新闻、其他，用于旧数据和 LLM 不可用时 fallback。
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
    - `classify_topic(title, url, summary, content)` 基于 DeepSeek 对文章做单标签 topic 分类，返回 topic、confidence、reason、source。
    - `rewrite_query(question, url=None)` 基于 DeepSeek 将用户问题改写成更适合检索的查询，返回 query、source、reason。
    - `generate_search_queries(question, rewritten_query, url=None)` 基于 DeepSeek 生成多个检索查询变体。
    - `answer(question, context_chunks)` 基于 context chunks 生成自然语言回答。
    - `answer()` prompt 要求最终回答面向普通用户，单篇文章场景使用文章标题或当前网页自然引用，不要求用户理解来源编号。
    - `answer_without_context(question, reason)` 在无可用知识库上下文或通用兜底场景生成回答。
    - `describe_sources(question, source_texts)` 为 sources 生成一句中文来源说明。
    - `rerank_chunks(question, candidates)` 基于 DeepSeek 对候选 chunks 返回相关性排序。
    - `compress_context(question, context_chunks)` 基于 DeepSeek 将候选上下文压缩为更适合回答的 context。
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

### Android MVP

- 项目路径：`android/SmartFeedAndroid`
- 当前包名：`com.example.smartfeedandroid`
- 当前能力：
  - 已配置 Android 网络权限。
  - 已允许访问本地开发后端的 cleartext HTTP。
  - 使用 Retrofit 调用 FastAPI 后端。
  - Retrofit base URL 当前为 `http://10.0.2.2:8000/`。
  - 已封装 `SmartFeedApi`、`SmartFeedNetwork`、`UploadRepository`、`ChatRepository`。
  - 已实现 `/upload` 请求模型和响应模型。
  - 已实现 `/chat` 请求模型和响应模型。
  - `SmartFeedScreen` 使用 Jetpack Compose 组装 Home / Analysis / Profile 三个底部 tab。
  - Android UI 已按 feature 拆分到 `ui/home`、`ui/chat`、`ui/analysis`、`ui/articles`、`ui/navigation`、`ui/common`、`ui/profile`。
  - Home tab 展示 URL 输入、上传入口和内存级历史对话列表。
  - Home 页面实现位置：`ui/home/HomeScreen.kt`。
  - 点击 Home 中的 conversation 会进入聊天详情页。
  - 聊天详情页展示 summary、用户消息、AI 回答和 sources。
  - Chat 页面实现位置：`ui/chat/ChatDetailScreen.kt`。
  - Analysis tab 当前调用后端 `/stats` 展示知识库文章数、chunks 数和来源域名占比。
  - Analysis tab 的 Topic share 当前基于 `/articles` 返回的文章 `topic` 按文章数量统计。
  - Analysis tab 使用 Compose Canvas 绘制主题占比饼图。
  - Analysis 页面实现位置：`ui/analysis/AnalysisScreen.kt`。
  - Analysis tab 右上角提供文章管理入口。
  - 文章管理页调用后端 `/articles` 展示已保存文章列表。
  - 文章管理页用顶部 topic tabs 切换文章分类。
  - 文章管理页点击文章可以跳转原网页。
  - 文章管理页左滑文章会显示 Delete 按钮，点击按钮后调用后端 `DELETE /articles` 按 URL 删除知识库文章。
  - 文章管理页面实现位置：`ui/articles/ArticleManagerScreen.kt`。
  - 删除文章后会刷新文章列表和知识库统计。
  - Profile tab 当前为占位页。
  - 主要页面已添加 Compose Preview，便于在 Android Studio 中查看和调整 UI。
  - `HomeViewModel` 负责上传、提问、本地 UI 状态、本地 conversations 列表和当前 messages。
  - 已定义内存级 `Conversation` 和 `ChatMessage`。
  - 已实现 `ConversationStore`，使用 Android Room 保存 conversations。
  - Room 当前表：`conversations`。
  - conversations 的 messages 当前仍通过 kotlinx serialization 序列化为 `messagesJson` 字段。
  - 旧 SharedPreferences conversations 会在首次 Room 加载为空时自动迁移到 Room。
  - App 启动时会从 Room 加载本地保存的 conversations。
  - 已注册 Android 系统分享入口，支持接收 `text/plain` 类型的分享文本。
  - App 会从分享文本中提取第一个 `http` / `https` URL。
  - 从浏览器分享 URL 到 SmartFeed 后，会自动调用上传流程。
  - 上传新 URL 成功后会创建一个新的 conversation。
  - 上传或分享已存在 URL 时，会打开已有 conversation 并更新 title、summary、status 和 stored chunks，不创建重复历史项。
  - URL 去重比较会忽略首尾空格、fragment、`www.` 和末尾 `/` 差异。
  - 上传返回的 summary 会作为当前 conversation 的 summary 消息展示。
  - 可以创建 global knowledge chat。
  - 可以在当前进程内切换已有 conversation。
  - 当前 conversations 和 messages 会保存到本地 Room。

## 2. 当前数据流

URL → Jina Reader 或 HTML fallback → text → sections → chunks + chunk metadata → embedding → ChromaDB → search/chat results

## 3. 当前 RAG + LLM 流程

### `/upload`

URL → parse → clean text → sections → chunks + section metadata → DeepSeek summary → LLM topic classification or rule fallback → delete old chunks by URL → embedding → ChromaDB → response

### `/search`

query → embedding → ChromaDB topK chunks → search results

### `/stats`

ChromaDB chunks → topic keyword classification → topics/domains/articles distribution → Android Analysis source/domain stats

ChromaDB articles → article topic grouping → Android Analysis topic pie chart + article manager tabs

### `/chat`

query + optional url → ChatService → query intent classification → query rewrite → multi-query generation → retrieval scope decision → page/global hybrid retrieval → keyword rerank / LLM rerank / page context selection → context compression → relevance or policy decision → LLM answer → status + error_code + merged sources + source_type

### `/debug`

browser page → calls `/upload` and `/chat` → displays parser, chunks, summary, answer, sources and chat diagnostics

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
- 已保存文章列表接口已实现。
- 按 URL 删除文章 chunks 接口已实现。
- 上传时 DeepSeek topic 分类已实现。
- topic 分类低置信度或不可用时规则 fallback 已实现。
- ChromaDB 持久化向量存储已实现。
- `smartfeed` collection 已实现。
- chunks 写入向量库已实现。
- `sentence-transformers` embedding 优先加载已实现。
- 确定性 mock embedding fallback 已实现。
- `POST /search` 语义检索接口已实现。
- search 返回 `query` 和 `results` 已实现。
- search results 返回 `content`、`metadata`、`score` 已实现。
- `GET /stats` 知识库统计接口已实现。
- stats 返回主题占比、来源域名占比和文章占比已实现。
- stats chunks 主题分类当前使用轻量关键词规则已实现。
- articles topic 优先使用上传时保存的 LLM 分类结果已实现。
- `POST /chat` 问答接口已实现。
- chat query intent classification 已实现。
- chat 支持 `page_reference`、`knowledge_or_general_query`、`realtime_or_current_query`、`search_history`、`unsupported_action`。
- chat 支持可选 url 优先检索当前网页已实现。
- chat 对当前网页页面级问题使用该 URL 已入库页面上下文已实现。
- chat 对当前网页页面级上下文做 chunk quality filtering 已实现。
- chat 对当前网页具体问题使用向量检索 + 关键词召回合并已实现。
- chat 对当前网页具体问题优先扩展到命中 section 已实现。
- chat 对没有 section metadata 的旧 chunks 使用相邻 chunks fallback 已实现。
- chat 当前 URL 没有可用 chunks 时返回已保存文章建议已实现。
- chat 全局知识库检索已实现。
- chat 全局知识库向量召回 + 关键词召回合并已实现。
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
- chat sources 最多返回 3 个展示来源已实现。
- chat source_type 返回已实现。
- chat `status`、`error_code`、`message` 稳定字段返回已实现。
- chat intent metadata 返回已实现。
- chat debug diagnostics 返回已实现。
- `/debug` 页面展示 chat diagnostics 已实现。
- DeepSeek API 调用封装已实现。
- `.env` API Key 加载已实现。
- `LLMService.summarize(text)` 已实现。
- `LLMService.classify_topic(title, url, summary, content)` 已实现。
- `LLMService.rewrite_query(question, url=None)` 已实现。
- `LLMService.answer(question, context_chunks)` 已实现。
- `LLMService.answer_without_context(question, reason)` 已实现。
- `/upload` 返回 `summary` 字段已实现。
- `docs/api.md` 接口文档已创建。
- `docs/test_plan.md` 手动测试计划已创建。
- `tests/test_mvp.py` 最小自动化测试已创建。
- `requirements.txt` 已包含 `pytest`。
- Android Compose 项目已接入 Retrofit。
- Android 端已实现上传 URL 并展示 summary、status、stored chunks。
- Android 端已实现本地聊天消息列表。
- Android 端已实现调用当前 `/chat` 并展示 answer 和 sources。
- Android 端已将上传和聊天状态迁入 `HomeViewModel`。
- Android 端已实现内存级 conversations 列表。
- Android 端已实现上传 URL 后创建新 conversation。
- Android 端已实现 summary 作为聊天消息展示。
- Android 端已实现当前进程内 conversation 切换。
- Android 端已实现 Home / Analysis / Profile 底部 tab 结构。
- Android 端已将历史对话列表和聊天详情页拆开。
- Android 端已实现系统分享入口。
- Android 端已实现从分享文本提取 URL 并自动上传。
- Android 端已实现重复分享同 URL 时打开已有对话。
- Android 端重复上传同 URL 时会更新已有对话并保留原聊天消息。
- Android 端已实现 Room 级本地历史对话保存。
- Android 端已实现 SharedPreferences 旧历史到 Room 的自动迁移。
- Android 端已实现 Analysis 页调用后端 `/stats`。
- Android 端已实现知识库主题占比饼图。
- Android 端已展示主题、文章和来源域名分布。
- Android 端已通过 Analysis 右上角入口进入文章管理页。
- Android Analysis Topic share 已改为按文章数量统计 topic。
- Android 文章管理页已使用 topic tabs 展示已保存文章。
- Android 文章管理页已支持点击打开原网页。
- Android 文章管理页已支持左滑显示删除按钮。

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
- 列出已保存文章。
- 删除某个 URL 对应的全部 chunks。
- 将 chunks、metadata、embedding 存入本地 ChromaDB。
- 调用 DeepSeek 为网页正文生成中文 summary。
- 接收自然语言 query。
- 对 chat query 进行规则型意图分类。
- 对 chat query 进行 LLM query rewrite，LLM 不可用时回退原始 query。
- 对 chat query 进行 LLM multi-query generation，LLM 不可用时回退 rewritten query + 原始 query。
- 根据 intent 决定检索范围和 fallback 策略。
- 将 query 转为 embedding。
- 从 ChromaDB 中检索 topK 相关 chunks。
- 当前网页聊天会在指定 URL 的 chunks 中做轻量关键词召回。
- 全局聊天会在已保存 chunks 中做轻量关键词召回。
- chat 检索使用 rewritten query，最终回答使用用户原始 query。
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
- 通过 `/stats` 查看知识库主题、来源域名和文章占比。

### 当前不能保证什么

- 不能保证所有反爬网页都能抓取成功。
- 不能保证 Jina Reader 对所有网站都可用。
- 不能保证需要 JavaScript 渲染正文的网站能被 HTML fallback 完整解析。
- 不能保证 DeepSeek API 在未配置 `DEEPSEEK_API_KEY` 时可用。
- 不能保证 mock embedding 下的语义检索质量与真实 embedding 一致。
- 不能保证规则型 query intent classification 覆盖所有自然语言表达。
- 不能保证固定 chunking 能完整保留原网页的标题层级、代码块和章节结构。
- 当前页面级回答使用轻量 chunk quality filtering，但不能保证完全去除所有网页噪声。
- 全局关键词召回当前是轻量字符串匹配，不是完整 BM25/reranker。
- 不能提供实时搜索、天气、股价、汇率等外部实时工具结果。
- stats chunks 主题分类当前仍是关键词规则，不是 LLM 分类、embedding 聚类或人工标签。
- 已保存文章 topic 当前优先使用 DeepSeek 单标签分类，不是多标签、embedding 聚类或人工标签。
- Android 当前本地持久化使用 Room。
- Android 当前没有登录、WebSocket 或 session。
- Android 分享入口当前只处理 `text/plain` 中的第一个 `http` / `https` URL。
- Android Profile 当前只是占位页，没有真实用户功能。

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
- Android：Kotlin
- Android UI：Jetpack Compose / Material 3
- Android network：Retrofit + OkHttp
- Android JSON：kotlinx.serialization
- Android state：ViewModel + Compose state
