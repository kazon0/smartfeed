# Android Structure

SmartFeed Android 当前采用轻量 MVVM 分层，避免页面和业务逻辑继续堆在单个文件中。

## 目录职责

```text
android/SmartFeedAndroid/app/src/main/java/com/example/smartfeedandroid/
├── MainActivity.kt
├── data/
│   ├── local/              # Room、本地会话持久化
│   ├── auth/               # Keystore token 和当前认证 session
│   ├── remote/             # Retrofit API、请求/响应 DTO
│   └── repository/         # 后端接口封装
└── ui/
    ├── analysis/           # Analysis 页面、AnalysisViewModel、饼图、主题分布
    ├── articles/           # 已保存文章管理页、ArticleManagerViewModel
    ├── chat/               # 聊天详情页、ChatViewModel、聊天请求 coordinator、气泡、输入栏、sources
    ├── common/             # 通用颜色、ResultCard、ResultRow
    ├── home/               # Home 入口、HomeViewModel、UI state、会话模型和会话规则
    ├── model/              # 跨页面 UI model，例如 Conversation、ChatMessage
    ├── navigation/         # Bottom navigation，包含中间新聊天动作按钮
    ├── profile/            # 注册登录、AuthViewModel 和真实 Profile
    ├── state/              # 跨根页面状态，例如 HomeUiState
    └── theme/              # Compose theme
```

## 当前 MVVM 对应关系

- View：`ui/home/SmartFeedScreen.kt` 负责页面组装和导航。
- Bottom navigation：当前包含 `首页`、`文章`、中间 `新聊天` 加号动作、`分析`、`我的`。
- Feature UI：
  - `ui/home/HomeScreen.kt`
  - `ui/home/HomeConversationList.kt`
  - `ui/chat/ChatDetailScreen.kt`
  - `ui/chat/ChatBubbles.kt`
  - `ui/chat/ChatSourceCard.kt`
  - `ui/analysis/AnalysisScreen.kt`
  - `ui/articles/ArticleManagerScreen.kt`
  - `ui/profile/AuthScreen.kt`
  - `ui/profile/ProfileScreen.kt`
- ViewModel：
  - `ui/home/HomeViewModel.kt`：Home、分享入口、会话打开、上传入口和底部新聊天入口。
  - `ui/chat/ChatViewModel.kt`：聊天输入框状态、发送状态和聊天请求触发。
  - `ui/analysis/AnalysisViewModel.kt`：Analysis 页 `/stats`、`/insights` 和文章列表数据。
  - `ui/articles/ArticleManagerViewModel.kt`：文章管理页文章列表和删除文章。
  - `ui/profile/AuthViewModel.kt`：session 恢复、注册、登录和退出登录。
- State：
  - `ui/state/HomeUiState.kt`
  - `ui/chat/ChatUiState.kt`
  - `ui/analysis/AnalysisViewModel.kt` 中的 `AnalysisUiState`
  - `ui/articles/ArticleManagerViewModel.kt` 中的 `ArticleManagerUiState`
- Coordinators：
  - `ui/home/ConversationCoordinator.kt`：选择、删除、创建、打开 conversation，并处理消息追加后的本地状态。
  - `ui/home/ArticleUploadCoordinator.kt`：文章状态查询、已入库跳过上传、新文章上传。
  - `ui/chat/ChatCoordinator.kt`：构造最近聊天 history 并调用 `/chat`。
- Chat local models：`ui/chat/ChatModels.kt` 保存 `ChatSendContext` 和 `ChatResult`，避免继续堆在 ViewModel 或 coordinator 文件中。
- Chat source UI：`ui/chat/ChatSourceCard.kt` 保存回答依据卡片展开和打开原文逻辑。
- Chat bubble UI：`ui/chat/ChatBubbles.kt` 保存用户消息、助手消息、thinking 和气泡绘制组件。
- Model：`ui/model/Conversation.kt`、`ui/model/ChatMessage.kt`
  - `Conversation` 是当前跨页面 UI 会话模型，保存 `sourceUrl`、`topic`、`title`、`createdAtMillis`、`updatedAtMillis` 和 messages。
- Local conversation rules：`ui/home/ConversationManager.kt`
- Local conversation filters：`ui/home/ConversationFilters.kt`，保存 Home 最近对话筛选、搜索和 topic fallback 规则。
- Home conversation list UI：`ui/home/HomeConversationList.kt` 保存最近对话列表、筛选菜单、左滑删除和 topic 角标。
- Local persistence mapper：`ui/home/ConversationMappers.kt`
- Local persistence coordinator：`ui/home/ConversationPersistence.kt`，封装 UI conversation 与 `ConversationStore`、云端 conversation sync 之间的加载、保存、删除和 mapper 调用。
- Repository：`data/repository/*`，其中 `ConversationSyncRepository.kt` 对接 `GET/PUT/DELETE /conversations`，并按 `updatedAtMillis` 合并本地与云端会话。
- Network DTO：`data/remote/SmartFeedApi.kt`
- Authentication：`data/auth/AuthSession.kt` 保存认证状态，`SecureTokenStore.kt` 使用 Android Keystore AES-GCM 加密 access token，OkHttp 自动附加 Bearer header。
- WebSocket：`ChatRepository` 和 `UploadRepository` 使用 OkHttp WebSocket 接入 `/ws/chat` 与 `/ws/upload`，实时展示回答 delta、上传阶段和文章总结 delta；失败时回退 Retrofit 的 `POST /chat` / `POST /upload`。
- Local persistence：
  - `data/local/ConversationStore.kt`：本地 conversation 加载、保存和 legacy SharedPreferences 迁移。
  - `data/local/StoredConversation.kt`：本地持久化 DTO。
  - `data/local/ConversationEntity.kt`：Room entity。
  - `data/local/ConversationDao.kt`、`data/local/MessageDao.kt`：Room DAO。
  - `data/local/SmartFeedDatabase.kt`：Room database 和 migrations；schema v4 使用 `ownerId` 隔离账号本地会话。

## Share And Article Status Flow

- `MainActivity` 接收系统分享文本并提取第一个 `http` / `https` URL。
- `HomeViewModel.handleSharedUrl(url)` 进入 Home，并调用上传流程。
- 上传流程会先通过 `ArticleRepository.getArticleStatus(url)` 调用后端 `GET /articles/status`。
- 如果后端已有该 URL 且 `chunk_count > 0`，Android 会跳过重复 `/upload`，直接新建一条文章聊天。
- 如果后端没有该 URL，Android 才调用 `/upload` 解析并入库。
- 文章管理页点击已保存文章也会新建一条文章聊天，不复用旧会话。
- 文章页支持按标题、主题、来源和 URL 本地搜索，支持 topic 筛选以及默认、标题、片段数排序。
- 文章聊天会把后端返回的 topic 保存到本地 conversation，Home 过滤和角标优先使用该字段，旧数据再回退到本地关键词推断。
- 已保存文章列表现在是底部 `文章` tab 的一级页面，不再作为 Analysis 页的二级入口。

## Current MVVM Boundary

当前结构是轻量 MVVM：页面状态和业务编排已经从 Compose UI 中拆出，但还不是完整的 domain/data/ui 分层。

- `HomeViewModel` 目前仍是根页面状态协调者，负责 Home、分享入口、会话打开和持久化触发。
- `ConversationManager` 承载本地会话规则，`ConversationCoordinator` 承载 UI state 切换，两者降低了 `HomeViewModel` 的直接复杂度。
- `ConversationPersistence` 封装 `ConversationStore` 和 UI/storage mapper，避免 `HomeViewModel` 直接依赖 Room 持久化细节。
- `ConversationSyncRepository` 封装云端会话同步，避免 `HomeViewModel` 直接依赖 Retrofit DTO 或同步策略。
- Room database、DAO、entity、migration 已从 `ConversationStore` 拆出，`ConversationStore` 只保留本地会话读写和 legacy SharedPreferences 迁移编排。
- `ui/model` 文件少是正常的；只有跨页面共享且稳定的 UI model 才放这里，不需要为了“看起来分层”拆空模型。

## Preview

当前主要页面已添加 Compose Preview：

- `HomeScreenPreview`
- `ChatDetailScreenPreview`
- `AnalysisScreenPreview`
- `ArticleManagerScreenPreview`

在 Android Studio 中打开对应文件，切换到 `Split` 或 `Design` 即可查看预览。

## Unit Tests

- `ConversationManagerTest` 覆盖文章 conversation 元数据、URL 归一化去重、重复上传时摘要替换、新 conversation 排序和消息追加规则。
- `ConversationMappersTest` 覆盖 conversation 完整 round-trip、旧 `sourceUrl` fallback、未知旧消息类型兼容和消息顺序。
- `SmartFeedDatabaseMigrationTest` 使用 SQLite JDBC 内存数据库执行 Room 共用的 migration SQL，验证 `1 -> 2 -> 3 -> 4`、messages 表创建、owner 分区字段以及旧 conversation metadata 回填。
- `ConversationFiltersTest` 覆盖 topic 优先级和 fallback、筛选项排序、筛选匹配以及最近消息搜索范围。
- `ChatCoordinatorTest` 覆盖 `/chat` history 的消息类型映射、错误过滤、助手消息 fallback 和最近 8 条限制。
- `ArticleFiltersTest` 覆盖文章主题顺序、搜索字段组合、主题叠加筛选和排序规则。
- 这些规则使用本地 JVM 单元测试运行，不依赖 Android 设备或后端服务。

## 后续建议

当前为了降低风险，会话模型仍保留在 `ui/home`。本地对话规则已从 `HomeViewModel` 拆到 `ConversationManager` 和 `ConversationCoordinator`，Room 映射已拆到 `ConversationMappers`，上传和聊天请求流程已拆到 coordinator，聊天输入和发送状态已拆到 `ChatViewModel`。后续如果继续规范化，可以再拆：

- `ui/state/HomeUiState.kt`
- `ui/home/HomeViewModel.kt` 后续可进一步改名为根导航 ViewModel。
