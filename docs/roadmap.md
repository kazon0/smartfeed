# SmartFeed Roadmap

本文档记录从当前单机 MVP 到可部署、多用户、可演示版本的后续顺序。当前真实实现以 `docs/snapshot_v0.md` 为准。

## 目标版本

交付版本需要同时具备：

- Android 分享网页、文章管理、本地历史和知识分析。
- 稳定的 FastAPI `/upload`、`/chat`、文章与统计 API。
- 可切换、可回退的 LangChain RAG 编排。
- PostgreSQL 用户与业务 metadata。
- 注册、登录和 JWT 鉴权。
- ChromaDB 按 `user_id` 强制隔离。
- Android 登录态、账号页和基础云端同步。
- 可访问的 HTTPS 后端部署。
- WebSocket 流式回答和任务状态事件，同时保留 `POST /chat` fallback。
- README、架构图、APK、截图和演示流程。

## 1. LangChain RAG 编排

目标：把当前可选包装层升级为真实、可观测、可回退的 RAG pipeline。

实施顺序：

1. 为 classic 和 LangChain pipeline 提供统一 `run()` 边界。
2. 使用 LangChain Runnable 串联 rewrite、multi-query、retrieval、rank 和 rerank。
3. 将 context compression 和 answer 纳入 LangChain 编排。
4. 保留 classic pipeline 和原始 context fallback。
5. 在 `/chat.debug` 中记录 pipeline、阶段和 fallback 原因。

完成标准：

- `SMARTFEED_RAG_PIPELINE=langchain` 执行真实 Runnable chain。
- classic 和 LangChain 对 Android 保持同一 `/chat` 稳定协议。
- LangChain 初始化或阶段执行失败时请求仍可完成。

## 2. 云端数据基础

目标：为多用户数据归属、同步和部署建立持久化模型。

范围：

- 引入 PostgreSQL 和数据库 migration。
- 建立 `users`、`articles`、`conversations`、`messages` 表。
- 文章表保存 URL、title、topic、summary、created_at、updated_at 和 owner_id。
- conversation/message 云端模型与 Android 当前本地模型对齐。
- Room 继续作为 Android 离线缓存，不直接替代云端数据源。

完成标准：

- 新环境可以通过 migration 创建数据库。
- 业务记录具有明确 owner 和时间字段。
- 不依赖 Chroma metadata 聚合承担全部业务数据库职责。

## 3. 注册登录与用户隔离

状态：后端 JWT 认证、PostgreSQL article metadata、conversation/message 云端同步 API 和 Chroma `user_id` 强制隔离已完成。

目标：实现可实际使用的多用户知识库。

范围：

- `POST /auth/register`、`POST /auth/login` 和当前用户接口。
- 密码哈希、JWT access token、FastAPI auth dependency。
- `/upload`、`/search`、`/chat`、`/articles`、`/stats`、`/insights` 绑定当前用户。
- Chroma chunks 写入 `user_id`，所有查询和删除强制附加用户过滤条件。
- 补充跨用户读取、删除和检索隔离验证。

完成标准：

- 两个账号无法看到或检索对方文章。
- 未认证请求返回稳定错误结构。
- 认证和数据隔离不依赖 Android 自觉传入 `user_id`。

## 4. Android 账号与云同步

状态：后端 conversation/message 全量同步 API，以及 Android 注册登录、Keystore token、Bearer 注入、Room owner 分区和 Room/云端自动同步已完成。

目标：Android 能使用云端账号并恢复用户数据。

范围：

- 注册、登录、退出登录页面。
- 安全保存 token，Retrofit 自动添加 `Authorization` header。
- token 失效统一处理。
- Profile 展示账号、同步状态和退出登录。
- 云端 conversation/message API 接入。
- 第一版同步采用 `updatedAtMillis` 时间戳合并策略，不做复杂冲突解决。

完成标准：

- 登录后可上传、聊天和读取自己的文章。
- 重装或另一设备登录后可恢复云端会话。
- Room 缓存与云端同步失败不会导致本地历史静默丢失。

## 5. 服务器部署

状态：Sealos 第一版公网部署已完成，已使用 GHCR 镜像、Sealos PostgreSQL、`/data` 持久化 ChromaDB、`/health` 健康检查和 Android Gradle 生产 base URL 验证注册登录链路；密钥和数据库密码轮换仍需手动完成。

目标：提供可供 Android 和简历演示使用的公网 HTTPS 服务。

范围：

- 部署 FastAPI 和 PostgreSQL。
- 为 ChromaDB 配置持久卷，或迁移到支持持久化和用户过滤的向量服务。
- 配置 DeepSeek key、JWT secret、数据库地址和生产环境变量。
- 增加 health check、结构化日志、CORS 和基础错误监控。
- Android 使用正式 HTTPS base URL，并保留开发环境切换方式。
- 验证服务重启后文章、用户和向量数据仍存在。

完成标准：

- 新账号可以通过公网完成注册、上传和聊天。
- 服务重启不会清空 PostgreSQL 或向量数据。
- 敏感配置不进入 Git。

## 6. WebSocket 流式交互

状态：后端 `/ws/chat` 已支持 JWT 鉴权、用户隔离、阶段事件、答案 delta 和最终完整回答；后端 `/ws/upload` 已支持上传阶段事件和文章总结 delta；Android 已接入聊天和上传 WebSocket，并保留 HTTP fallback。
WebSocket 聊天当前使用低延迟检索路径，优先减少首字等待；普通 `POST /chat` 继续使用完整 RAG 质量路径。
低延迟路径可通过 `SMARTFEED_WS_FAST_PATH=0` 关闭；Android 端已增加 delta 缓冲和打字机节奏，避免模型分片过快导致“瞬间整段出现”。

目标：提供简历可展示的实时回答和长任务状态推送。

范围：

- FastAPI WebSocket chat endpoint。
- FastAPI WebSocket upload endpoint。
- WebSocket 握手鉴权和用户隔离。
- 定义稳定事件协议，例如 `status`、`delta`、`completed` 和 `error`。
- Android 使用 OkHttp WebSocket 展示流式回答、上传总结和阶段状态。
- 断线、超时和协议错误时回退现有 `POST /chat` / `POST /upload`。
- 不在这一阶段引入通用 Agent 或任意工具调用。

完成标准：

- Android 能逐步展示回答内容。
- Android 导入文章时能逐步展示总结内容。
- WebSocket 断开不会丢失最终消息或破坏本地 conversation。
- WebSocket 与 `POST /chat` / `POST /upload` 使用同一权限边界。

## 7. 产品收尾

范围：

- 文章保存时间和“最新保存”排序。
- Profile、错误状态、空状态、重试和主要文案收尾。
- 真机验证分享、重复上传、聊天、历史恢复、删除、同步和流式回答。
- 只修复崩溃、数据丢失、越权和明显回答错误等高优先级问题。

## 8. 简历交付

范围：

- README 项目介绍、运行方式、部署地址和技术亮点。
- 系统架构图、RAG pipeline 图、认证/数据隔离和 WebSocket 时序图。
- APK、核心截图、1 到 2 分钟演示视频。
- 固定演示文章、账号和提问脚本。
- 清理 `.env`、数据库、缓存、APK 和其他运行产物。

## 测试策略

- 测试伴随功能，不再单独连续扩充测试阶段。
- 每个新能力只补关键成功路径、失败回退和权限边界。
- 每个阶段结束运行现有后端与 Android 测试集。
- 部署、WebSocket 和多用户隔离必须增加集成验证，普通 UI 调整不追求高覆盖率。

## 当前执行顺序

1. 统一 classic/LangChain pipeline `run()` 边界。
2. 完成 LangChain Runnable RAG chain。
3. 建立 PostgreSQL schema 和 migration。
4. 实现 JWT 注册登录和全 API 用户隔离。
5. 接入 Android 账号和云端会话同步。
6. 部署 HTTPS 后端、PostgreSQL 和持久化向量存储。
7. 实现 WebSocket 流式回答与 Android 接入。
8. 完成产品收尾、真机验收和简历材料。
