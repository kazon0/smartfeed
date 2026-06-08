# SmartFeed Roadmap

本文档只记录项目后续阶段安排，不记录当前实现细节。当前真实实现请看 `docs/snapshot_v0.md`。

## 1. 当前阶段：Android MVP 闭环

目标：让用户可以在 Android App 内完成最核心体验。

范围：

- 输入 URL。
- 调用后端 `/upload`。
- 展示网页 summary、status、stored chunks。
- 调用后端 `/chat`。
- 用聊天气泡展示用户问题和 AI 回答。
- 展示基础 sources。
- 使用本地 Room 保存 conversations 和 messages。
- Home / Analysis / Profile 底部 tab。
- Home 展示历史对话列表。
- 点击历史对话进入聊天详情页。
- 支持从浏览器系统分享菜单接收网页 URL。
- 分享 URL 后自动创建新对话并展示 summary。
- 重复分享或上传同 URL 时打开已有对话，不创建重复历史项。
- Analysis 展示基于已保存文章 topic 的主题占比。
- 后端上传时使用 DeepSeek 对文章做 topic 分类，低置信度或不可用时回退到规则分类。
- Analysis 使用饼图展示主题分布。
- 后端提供已保存文章列表和按 URL 删除文章 chunks 的接口。
- Android Analysis 右上角提供文章管理入口。
- Android 文章管理页按 topic 分组展示历史文章。
- Android 文章管理页点击文章可跳转原网页。
- Android 文章管理页支持左滑显示删除按钮。

暂不做：

- 复杂 Room schema，例如单独 message 表和 migration schema 管理。
- 更完整的知识库管理页面，例如搜索、筛选、批量操作。
- Profile 真实用户能力。
- 用户登录。
- 云端同步。
- WebSocket。
- LangChain。

进入下一阶段的条件：

- Android App 能稳定上传网页。
- Android App 能稳定围绕当前网页问答。
- 后端 `/upload` 和 `/chat` 对 Android 返回字段稳定。

## 2. 下一阶段：对话结构与分享入口

目标：形成“分享网页即创建新对话”的产品形态。

当前实现：

- 设计 Android 本地 `Conversation` 和 `Message` 模型。
- 首页从单页面改为历史对话入口的雏形。
- 单个对话页展示聊天气泡。
- 支持从浏览器分享 URL 到 App。
- App 接收 URL 后创建新对话。
- App 接收已存在 URL 时打开已有对话。
- 自动调用 `/upload`。
- 将网页 summary 作为第一条 AI 消息展示。

暂不做：

- 多设备同步。
- 用户账号。
- 后端 session。
- WebSocket 流式输出。

进入下一阶段的条件：

- 用户从浏览器分享网页后，App 能自动进入一个新对话。
- 新对话能展示网页 summary。
- 用户能继续围绕该网页提问。

## 3. 本地记忆阶段

目标：App 重启后仍保留历史对话和消息。

当前实现：

- 已使用 Room 保存本地 conversations。
- 已使用 kotlinx serialization 将 messages 序列化为 Room 字段 `messagesJson`。
- 已支持从旧 SharedPreferences conversations 自动迁移到 Room。
- App 启动后可以恢复本地历史对话。

范围：

- 后续可将 messages 从 JSON 字段拆为独立 Room 表。
- 持久化文章 URL、title、summary、created_at。
- 首页展示历史对话列表。
- 对话页按 conversation 读取本地 messages。

暂不做：

- 云端同步。
- 用户账号。
- 多端冲突处理。
- 复杂推荐系统。

进入下一阶段的条件：

- App 重启后历史对话仍存在。
- 每个对话能正确绑定文章 URL。
- 本地消息不会影响后端 RAG 数据结构。

## 4. 知识库统计阶段

目标：让用户知道自己保存了哪些内容，知识库大概分布如何。

当前实现：

- 后端已提供 `/stats`。
- `/stats` 基于 ChromaDB chunks 统计主题、来源域名、文章占比。
- 文章 topic 当前优先使用上传时保存的 DeepSeek 分类结果。
- 规则分类当前作为 DeepSeek 不可用、低置信度或旧数据 fallback。
- Android Analysis 页已展示主题饼图和分布列表。
- 后端已提供 `/articles` 列出 ChromaDB 中已保存文章。
- 后端已提供 `DELETE /articles` 按 URL 删除该文章对应 chunks。
- Android Analysis 页已提供文章管理入口。
- Android 文章管理页已按 topic 展示已保存文章。
- Android 文章管理页已支持点击打开原网页。
- Android 文章管理页已支持左滑显示删除按钮。

范围：

- 后续可将文章管理页升级为更完整的知识库管理入口。
- 后续可升级为 LLM 分类、embedding 聚类或用户自定义标签。
- 后续可统计保存时间、阅读/提问频率和主题趋势。
- 后续可从后端返回更稳定的分类标签体系。

暂不做：

- 复杂知识图谱。
- 自动标签体系。
- 推荐流。
- 用户画像。

进入下一阶段的条件：

- 用户能看到知识库总量。
- 用户能看到主要来源网站。
- 用户能打开或继续某篇已保存文章的对话。

## 5. RAG 质量升级阶段

目标：提高检索命中率、上下文完整性和回答质量。

当前实现：

- `/chat` 已接入 query rewrite。
- query rewrite 使用 DeepSeek 将用户问题改写为更适合检索的 query。
- query rewrite 不可用时回退原始 query。
- 检索使用 rewritten query，最终回答仍使用用户原始问题。
- `/chat` 已接入 LLM rerank，对向量召回和关键词召回后的候选 chunks 做语义重排。
- LLM rerank 不可用时回退到原有排序。
- `/chat` 已接入 context compression，在回答前压缩候选上下文，保留来源、步骤、代码、清单和结论。
- context compression 不可用时回退到原始 chunks。

范围：

- 优化 chunk 策略。
- 优化 section-aware retrieval。
- 增加 multi-query 检索。
- 改善代码类文章、列表类文章、长文总结的回答质量。
- 将 `/chat` 中逐渐变复杂的 retrieval / rerank / compression / fallback 流程抽到 service 层。
- 保持 Android API 尽量稳定。

暂不做：

- Agent system。
- 多工具自动调用。
- 复杂工作流编排。

进入下一阶段的条件：

- 当前规则型 RAG 逻辑已经明显不够用。
- 检索链路需要统一编排。
- prompt、retrieval、compression、fallback 的组合开始变复杂。

## 6. LangChain 阶段

目标：在不破坏现有 API 的前提下，引入更标准的 RAG pipeline 编排。

范围：

- 在 service 层内部引入 LangChain。
- 封装 retriever。
- 封装 prompt chain。
- 可选引入 context compression。
- 可选引入 multi-query retriever。
- 可选引入 memory chain。

暂不做：

- 因为使用 LangChain 而重写所有后端。
- 让 Android 直接感知 LangChain。
- 过早引入 Agent。

进入下一阶段的条件：

- LangChain 版本的 RAG pipeline 比当前手写逻辑更容易维护。
- 测试能证明回答质量或代码维护性有提升。

## 7. 云端存储与用户管理阶段

目标：从本地个人工具升级为多用户云端产品。

范围：

- 用户注册和登录。
- JWT 或同类认证机制。
- PostgreSQL 保存用户、文章、会话、消息 metadata。
- ChromaDB 按 user_id 做数据隔离。
- Android 保存登录态。
- 支持跨设备同步。
- 后端部署。

暂不做：

- 团队协作。
- 复杂权限系统。
- 付费系统。

进入下一阶段的条件：

- 单机版核心体验已经稳定。
- 本地历史、分享入口、知识库统计已经可用。
- 明确需要跨设备或多用户能力。

## 8. 高级记忆与个性化阶段

目标：让系统逐渐理解用户长期知识库和偏好。

范围：

- 对话摘要记忆。
- 用户长期偏好。
- 用户知识画像。
- 基于历史保存内容辅助回答。
- 基于知识库分布做主动整理建议。

暂不做：

- 在用户系统前实现复杂长期记忆。
- 在检索质量不稳定前做主动推荐。

## 当前推荐顺序

1. 验证当前 Android MVP 闭环：上传、分享、聊天、本地历史、Analysis、文章管理页。
2. 升级 RAG 检索质量。
3. 在确实需要编排能力时引入 LangChain。
4. 做云端存储和用户管理。
5. 做高级记忆和个性化。
