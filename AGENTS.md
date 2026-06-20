# SmartFeed

## Project Overview

SmartFeed 是一个基于 RAG 的个人 AI 知识库管理平台。

当前 MVP 目标：

1. 收藏网页文章
2. 提取网页正文
3. 将正文结构化为 sections 和 chunks
4. 将 chunks 向量化并写入 ChromaDB
5. 使用自然语言提问
6. 基于当前网页或全局知识库生成 AI 回答
7. 为 Android 客户端提供稳定 API

## Current Implementation

Backend 已实现：

* FastAPI 应用
* `GET /`
* `GET /debug`
* `POST /upload`
* `POST /search`
* `POST /chat`
* `ChatService`
* `WebParserService`
* `VectorStoreService`
* `LLMService`
* `QueryIntentService`
* `RAGPipeline`
* `LangChainRAGPipeline`
* ChromaDB 持久化存储
* DeepSeek summary / answer
* DeepSeek article topic classification
* DeepSeek query rewrite for `/chat`
* DeepSeek multi-query retrieval for `/chat`
* DeepSeek rerank for `/chat`
* DeepSeek context compression for `/chat`
* Optional LangChain Core pipeline wrapper for `/chat`
* Rule-based topic fallback
* Jina Reader 优先网页解析
* HTML fallback 解析
* sections 结构化解析
* chunk metadata，包括 `section_index`、`section_title`
* 当前网页 page chat
* 全局 knowledge chat
* 当前网页 hybrid retrieval
* 全局 hybrid retrieval
* `/chat` 稳定字段：`status`、`error_code`、`message`
* `/chat` 返回 `debug` 诊断字段，仅用于开发排查，不作为 Android 核心业务协议
* pytest 最小测试
* RAG 回归评测测试
* API 文档和测试计划
* Android Compose MVP
* Android Retrofit 网络层
* Android `/upload` 和 `/chat` 接入
* `/chat` 可选 history 上下文
* Android 内存级 conversation/message 结构
* Android Home / Analysis / Profile 底部 tab
* Android 历史对话列表和聊天详情页分层
* Android 系统分享入口
* Android 分享 URL 自动上传并创建新对话
* Android 已入库 URL 跳过重复上传并新建文章聊天
* Android Room 本地历史对话保存
* Android Room messages 独立表保存
* Android `/chat` 请求发送当前对话最近 messages 作为 history
* Android SharedPreferences 旧历史自动迁移到 Room
* `GET /stats`
* `GET /articles`
* `GET /articles/status`
* `GET /insights`
* `DELETE /articles`
* Android Analysis 主题占比饼图
* Android Analysis 智能总结展示
* Android Analysis 知识库画像和内容厚度展示
* Android Analysis 右上角文章管理入口
* Android 已保存文章按 topic 分组展示
* Android 文章列表点击新建文章聊天
* Android 主要用户可见文案开始抽离到 `strings.xml`
* Android 文章列表左滑显示删除按钮
* Android Home 最近对话支持分类过滤
* Android Home 最近对话支持本地搜索
* Android Chat sources 以“回答依据”短卡片展示并支持展开
* Android UI feature folders:
  * `ui/home`
  * `ui/chat`
  * `ui/analysis`
  * `ui/articles`
  * `ui/navigation`
  * `ui/common`
  * `ui/profile`
* Android main screens have Compose previews
* Android local conversation rules are split into `ui/home/ConversationManager.kt`
* Android Room conversation mappers are split into `ui/home/ConversationMappers.kt`

当前未实现：

* 用户系统
* session / 多会话管理
* WebSocket
* Android Profile 真实个人中心
* 实时搜索、天气、股价、汇率等外部实时工具

后续计划加入：

* LangChain 深度化：用于更复杂的 prompt orchestration、tool/chain 管理和后续 agent 化。
* WebSocket：用于流式回答、长任务状态推送、客户端实时交互。

## Tech Stack

Backend:

* Python
* FastAPI
* Uvicorn
* requests
* BeautifulSoup
* Jina Reader
* ChromaDB
* sentence-transformers
* python-dotenv
* langchain-core
* pytest

LLM:

* DeepSeek API

Mobile, planned:

* Android
* Kotlin
* Jetpack Compose
* Coroutines
* Flow
* Retrofit or Ktor

## Architecture

Current backend flow:

```text
URL
-> Jina Reader or HTML fallback
-> clean text
-> sections
-> chunks + chunk metadata
-> embedding
-> ChromaDB
-> /search or /chat
-> query rewrite / multi-query retrieval / rerank / context compression
-> DeepSeek answer
```

Planned client flow:

```text
Android App
-> FastAPI
-> ChromaDB
-> DeepSeek
```

## Important Files

Backend:

* `app/main.py`
* `app/routes/upload.py`
* `app/routes/search.py`
* `app/routes/chat.py`
* `app/routes/debug.py`
* `app/services/chat_service.py`
* `app/services/web_parser.py`
* `app/services/vector_store.py`
* `app/services/llm_service.py`
* `app/services/query_intent.py`
* `app/services/rag_pipeline.py`
* `app/services/langchain_rag_pipeline.py`
* `app/services/rag_pipeline_factory.py`

Docs:

* `docs/api.md`
* `docs/test_plan.md`
* `docs/rag_eval.md`
* `docs/snapshot_v0.md`
* `docs/roadmap.md`
* `docs/android_structure.md`

Tests:

* `tests/test_mvp.py`
* `tests/test_rag_eval.py`

Runtime/generated files that should not be committed:

* `.env`
* `venv/`
* `__pycache__/`
* `*.pyc`
* `chroma_db/`

## Development Rules

* 优先保证项目可运行。
* 优先保持 API 稳定。
* 不要一次性做大重构。
* 每次只做一个小闭环：实现、测试、文档、说明如何验证。
* LangChain 当前只作为可选 RAG pipeline 包装层，启用方式是设置 `SMARTFEED_RAG_PIPELINE=langchain`。
* 未设置 `SMARTFEED_RAG_PIPELINE` 时继续使用 classic pipeline。
* WebSocket 已进入明确路线图，但应在认证、用户隔离和部署基础完成后接入，并保留 `POST /chat` fallback。
* 用户系统已明确要求加入；使用 PostgreSQL、密码哈希、JWT 和服务端强制数据隔离，小步实现 migration、认证和业务接入。
* 不修改 DeepSeek API key 管理方式，继续使用 `.env` + `python-dotenv`。
* 不提交运行产物，例如 `chroma_db`、`__pycache__`、`.pyc`。
* 修改 `/chat` 回答策略、fallback、sources 展示时优先修改 `app/services/chat_service.py`，保持 `app/routes/chat.py` 只做请求入口。
* 修改 `/chat` 检索流程、query rewrite、multi-query、关键词召回或 rerank 时优先修改 `app/services/rag_pipeline.py`。
* 修改 `/chat` 时必须注意 Android 客户端稳定字段：
  * `status`
  * `error_code`
  * `message`
  * `answer`
  * `sources`
  * `source_type`
  * `intent`
  * `retrieval_scope`
  * `fallback_policy`
  * `debug` 可以扩展，但 Android 不应依赖其内部结构

## Preferred Codex Working Mode

适合本项目的 Codex 工作方式：

1. 先阅读相关代码和 `docs/snapshot_v0.md`。
2. 只选择一个明确小目标。
3. 修改代码。
4. 补充或更新测试。
5. 更新 `docs/api.md`、`docs/test_plan.md` 或 `docs/snapshot_v0.md`。
6. 运行：

```bash
venv/bin/python -m compileall app tests
venv/bin/python -m pytest tests/test_mvp.py -q
venv/bin/python -m pytest tests/test_rag_eval.py -q
```

7. 最后说明：
   * 改了哪些文件
   * 如何运行
   * 如何验证
   * 是否需要重新上传文章
   * 建议的 git commit message

不推荐的工作方式：

* 一次性要求生成完整 Android App。
* 一次性重构整个 RAG 架构。
* 没有测试就改核心 `/chat` 逻辑。
* 把调试数据库或缓存文件提交进 Git。

## Android Development Plan

Android 不建议直接让 Codex 从零生成完整项目。

推荐流程：

1. 先用 Android Studio 创建空白 Compose 项目。
2. 项目位置建议：

```text
smartfeed/
  app/
  docs/
  tests/
  android/
    SmartFeedAndroid/
```

3. 确认 Android Studio 能运行空白 App。
4. 再让 Codex 进入 `android/SmartFeedAndroid` 小步实现。

Android MVP 阶段：

1. 网络层
   * 配置后端 base URL
   * 实现 `/upload`
   * 实现 `/chat`

2. Upload 页面
   * 输入 URL
   * 调用 `/upload`
   * 展示 summary

3. Chat 页面
   * 输入 query
   * 可选当前 URL
   * 调用 `/chat`
   * 展示 answer

4. Sources 展示
   * 默认展示 `display_title`
   * 展示 `section_title`
   * 展示 `source_summary`
   * 点击打开 `url`
   * `content_preview` 只做折叠查看依据，不默认展示

5. Share Intent
   * 接收系统分享网页链接
   * 自动填入 URL
   * 先调用 `/articles/status`
   * 如果已入库，直接新建文章聊天
   * 如果未入库，再调用 `/upload`

## Current Next Step

后端已经可以支持 Android MVP 接入，Android 已完成基础上传和聊天接入。

建议下一步：

1. 统一 classic/LangChain pipeline `run()` 边界并完成真实 Runnable chain。
2. 引入 PostgreSQL schema、migration、JWT 注册登录和 `user_id` 数据隔离。
3. 接入 Android 账号、云端 conversation/message 同步和正式 HTTPS 服务。
4. 在认证与部署边界稳定后实现 WebSocket 流式回答，并保留 `POST /chat` fallback。
5. 最后完成真机验收、README、架构图、APK、截图和演示视频。
