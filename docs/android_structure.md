# Android Structure

SmartFeed Android 当前采用轻量 MVVM 分层，避免页面和业务逻辑继续堆在单个文件中。

## 目录职责

```text
android/SmartFeedAndroid/app/src/main/java/com/example/smartfeedandroid/
├── MainActivity.kt
├── data/
│   ├── local/              # Room、本地会话持久化
│   ├── remote/             # Retrofit API、请求/响应 DTO
│   └── repository/         # 后端接口封装
└── ui/
    ├── analysis/           # Analysis 页面、AnalysisViewModel、饼图、主题分布
    ├── articles/           # 已保存文章管理页、ArticleManagerViewModel
    ├── chat/               # 聊天详情页、ChatViewModel、聊天请求 coordinator、气泡、输入栏、sources
    ├── common/             # 通用颜色、ResultCard、ResultRow
    ├── home/               # Home 入口、HomeViewModel、UI state、会话模型和会话规则
    ├── model/              # 跨页面 UI model，例如 Conversation、ChatMessage
    ├── navigation/         # Bottom navigation
    ├── profile/            # Profile 占位页
    └── theme/              # Compose theme
```

## 当前 MVVM 对应关系

- View：`ui/home/SmartFeedScreen.kt` 负责页面组装和导航。
- Feature UI：
  - `ui/home/HomeScreen.kt`
  - `ui/chat/ChatDetailScreen.kt`
  - `ui/analysis/AnalysisScreen.kt`
  - `ui/articles/ArticleManagerScreen.kt`
  - `ui/profile/PlaceholderScreen.kt`
- ViewModel：
  - `ui/home/HomeViewModel.kt`：Home、分享入口、会话打开、上传入口和聊天入口。
  - `ui/chat/ChatViewModel.kt`：聊天输入框状态、发送状态和聊天请求触发。
  - `ui/analysis/AnalysisViewModel.kt`：Analysis 页 `/stats`、`/insights` 和文章列表数据。
  - `ui/articles/ArticleManagerViewModel.kt`：文章管理页文章列表和删除文章。
- State：
  - `ui/home/HomeUiState.kt`
  - `ui/chat/ChatViewModel.kt` 中的 `ChatUiState`
  - `ui/analysis/AnalysisViewModel.kt` 中的 `AnalysisUiState`
  - `ui/articles/ArticleManagerViewModel.kt` 中的 `ArticleManagerUiState`
- Coordinators：
  - `ui/home/ConversationCoordinator.kt`：选择、删除、创建、打开 conversation，并处理消息追加后的本地状态。
  - `ui/home/ArticleUploadCoordinator.kt`：文章状态查询、已入库跳过上传、新文章上传。
  - `ui/chat/ChatCoordinator.kt`：构造最近聊天 history 并调用 `/chat`。
- Model：`ui/model/Conversation.kt`、`ui/model/ChatMessage.kt`
- Local conversation rules：`ui/home/ConversationManager.kt`
- Local persistence mapper：`ui/home/ConversationMappers.kt`
- Repository：`data/repository/*`
- Network DTO：`data/remote/SmartFeedApi.kt`
- Local persistence：`data/local/ConversationStore.kt`

## Share And Article Status Flow

- `MainActivity` 接收系统分享文本并提取第一个 `http` / `https` URL。
- `HomeViewModel.handleSharedUrl(url)` 进入 Home，并调用上传流程。
- 上传流程会先通过 `ArticleRepository.getArticleStatus(url)` 调用后端 `GET /articles/status`。
- 如果后端已有该 URL 且 `chunk_count > 0`，Android 会跳过重复 `/upload`，直接新建一条文章聊天。
- 如果后端没有该 URL，Android 才调用 `/upload` 解析并入库。
- 文章管理页点击已保存文章也会新建一条文章聊天，不复用旧会话。

## Preview

当前主要页面已添加 Compose Preview：

- `HomeScreenPreview`
- `ChatDetailScreenPreview`
- `AnalysisScreenPreview`
- `ArticleManagerScreenPreview`
- `PlaceholderScreenPreview`

在 Android Studio 中打开对应文件，切换到 `Split` 或 `Design` 即可查看预览。

## 后续建议

当前为了降低风险，会话模型仍保留在 `ui/home`。本地对话规则已从 `HomeViewModel` 拆到 `ConversationManager` 和 `ConversationCoordinator`，Room 映射已拆到 `ConversationMappers`，上传和聊天请求流程已拆到 coordinator，聊天输入和发送状态已拆到 `ChatViewModel`。后续如果继续规范化，可以再拆：

- `ui/state/HomeUiState.kt`
- `ui/home/HomeViewModel.kt` 后续可进一步改名为根导航 ViewModel。
