# SmartFeed 智享资讯

SmartFeed 是一个面向个人知识管理的 AI 资讯收藏助手。它支持将网页文章保存为私有知识库，通过 RAG 检索和大模型生成摘要、问答与知识库洞察，并提供 Android 客户端完成文章剪藏、流式聊天、历史同步和内容分析。

项目由 FastAPI 后端和 Android Jetpack Compose 客户端组成。后端负责网页解析、正文切分、向量入库、用户隔离、LangChain RAG 编排、WebSocket 流式响应和云端数据同步；Android 端负责登录、分享入口、文章管理、聊天会话、Room 本地缓存、Markdown 展示和 WebSocket Flow 消费。

## 核心功能

- 网页文章收藏：输入或分享 URL 后自动解析网页正文。
- 结构化入库：按章节提取 sections，再切分为 chunks 并写入 ChromaDB。
- 私有知识库问答：支持当前文章问答和全局知识库问答。
- LangChain RAG：支持 query rewrite、multi-query retrieval、rerank、context compression 和 answer 编排。
- WebSocket 流式输出：聊天回答和文章摘要支持状态事件、delta 事件与完成事件。
- Android Flow 管道：OkHttp WebSocket 事件通过 Kotlin Coroutines Flow 进入 ViewModel 和 Compose UI。
- 用户系统：注册、登录、JWT、PostgreSQL 用户与 metadata 存储。
- 数据隔离：ChromaDB chunks 和 PostgreSQL conversations/messages 按认证用户隔离。
- 云端同步：Android 本地 Room 会话和云端 conversation/message 同步。
- 文章管理：文章列表、主题分类、删除、按 topic 分组。
- 数据分析：知识库主题分布、来源域名、内容厚度和智能总结。
- 移动端体验：Markdown 富文本渲染、来源卡片聚合、HTTP fallback、心跳与有限重连。
- 压测闭环：提供公网聊天 benchmark 和本地百万字级知识库压测脚本。

## 技术栈

**后端**

- Python
- FastAPI
- PostgreSQL / SQLAlchemy / Alembic
- ChromaDB
- sentence-transformers
- LangChain Core
- DeepSeek API
- BeautifulSoup / Jina Reader
- WebSocket
- pytest

**Android**

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit / OkHttp / WebSocket
- Coroutines / Flow
- Room
- kotlinx.serialization
- Android Keystore
- multiplatform-markdown-renderer

## 系统架构

```text
网页 URL
  -> Jina Reader / HTML fallback
  -> 正文清洗与章节提取
  -> chunks + metadata
  -> embeddings
  -> ChromaDB 向量库
  -> LangChain / RAGPipeline
  -> DeepSeek answer
  -> HTTP / WebSocket
  -> Android Compose UI
```

Android 端核心数据流：

```text
OkHttp WebSocket
  -> callbackFlow
  -> Repository
  -> ViewModel
  -> Compose state
  -> 流式消息渲染
```

## 后端运行

创建 Python 环境并安装依赖：

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

创建 `.env`：

```env
DEEPSEEK_API_KEY=your_api_key_here
DATABASE_URL=postgresql+psycopg://smartfeed:smartfeed@localhost:5432/smartfeed
JWT_SECRET=replace_with_a_long_random_secret
JWT_ACCESS_TOKEN_MINUTES=60
SMARTFEED_RAG_PIPELINE=langchain
SMARTFEED_WS_FAST_PATH=1
CHROMA_PERSIST_DIR=chroma_db
CORS_ALLOW_ORIGINS=
```

初始化或升级数据库：

```bash
alembic upgrade head
```

启动服务：

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

健康检查：

```bash
curl http://127.0.0.1:8000/health
```

## Docker 部署

项目内置 `Dockerfile` 和启动脚本：

```bash
docker build -t smartfeed-api .
docker run --env-file .env -p 8000:8000 smartfeed-api
```

容器入口会执行 Alembic migration 并启动 Uvicorn。部署到云平台时建议为 ChromaDB 配置持久化目录：

```env
CHROMA_PERSIST_DIR=/data/chroma_db
RUN_MIGRATIONS=1
```

GitHub Actions 可构建并发布 GHCR 镜像：

```text
ghcr.io/kazon0/smartfeed-api:latest
```

详细部署说明见 [docs/deployment.md](docs/deployment.md)。

## API 概览

主要接口：

- `GET /health`：服务健康检查
- `POST /auth/register`：注册并返回 JWT
- `POST /auth/login`：登录并返回 JWT
- `GET /auth/me`：获取当前用户
- `POST /upload`：解析并保存网页文章
- `POST /search`：语义检索知识库 chunks
- `POST /chat`：基于当前文章或全局知识库问答
- `GET /stats`：知识库统计
- `GET /insights`：知识库智能洞察
- `GET /articles`：文章列表
- `DELETE /articles`：删除文章及对应 chunks
- `GET /conversations`：云端会话列表
- `PUT /conversations/{id}`：同步会话和消息
- `DELETE /conversations/{id}`：删除会话
- `WebSocket /ws/chat`：流式聊天
- `WebSocket /ws/upload`：流式文章导入

业务接口需要携带：

```text
Authorization: Bearer $ACCESS_TOKEN
```

示例：

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"总结一下这篇文章","url":"https://example.com"}'
```

完整字段说明见 [docs/api.md](docs/api.md)。

## Android 运行

Android 项目位于：

```text
android/SmartFeedAndroid
```

直接安装到已连接设备：

```bash
./scripts/install_android.sh
```

指定模拟器或设备：

```bash
./scripts/install_android.sh emulator-5554
```

使用本地后端调试模拟器：

```bash
SMARTFEED_BASE_URL=http://10.0.2.2:8000/ ./scripts/install_android.sh emulator-5554
```

Android 编译检查：

```bash
cd android/SmartFeedAndroid
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug
```

Android 结构说明见 [docs/android_structure.md](docs/android_structure.md)。

## 测试与压测

后端测试：

```bash
venv/bin/python -m compileall app tests scripts
venv/bin/python -m pytest tests/test_mvp.py -q
venv/bin/python -m pytest tests/test_rag_eval.py -q
```

公网 HTTP / WebSocket benchmark：

```bash
SMARTFEED_BENCH_EMAIL='benchmark@example.com' \
SMARTFEED_BENCH_PASSWORD='replace-with-password' \
venv/bin/python scripts/benchmark_chat.py --runs 3
```

本地百万字级知识库压测：

```bash
venv/bin/python scripts/benchmark_large_corpus.py \
  --target-chars 1000000 \
  --article-count 40 \
  --runs 5 \
  --chroma-dir /tmp/smartfeed-large-corpus-1m \
  --output /tmp/smartfeed-large-corpus-1m.json
```

快速验证 ChromaDB / RAG 编排闭环：

```bash
venv/bin/python scripts/benchmark_large_corpus.py \
  --target-chars 1000000 \
  --article-count 40 \
  --runs 5 \
  --mock-embeddings \
  --chroma-dir /tmp/smartfeed-large-corpus-1m-mock
```

性能指标说明见 [docs/performance.md](docs/performance.md)。

## 文档

- [API 文档](docs/api.md)
- [Android 结构说明](docs/android_structure.md)
- [部署说明](docs/deployment.md)
- [性能与压测](docs/performance.md)
- [RAG 评测说明](docs/rag_eval.md)
- [系统设计说明](docs/system_spec.md)

## 运行数据与密钥

以下内容不应提交到 Git：

- `.env`
- `venv/`
- `chroma_db/`
- `docs_internal/`
- `__pycache__/`
- `*.pyc`
