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
    ├── analysis/           # Analysis 页面、饼图、主题分布
    ├── articles/           # 已保存文章管理页
    ├── chat/               # 聊天详情页、气泡、输入栏、sources
    ├── common/             # 通用颜色、ResultCard、ResultRow
    ├── home/               # Home 入口、HomeViewModel、UI state、会话模型
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
- ViewModel：`ui/home/HomeViewModel.kt`
- State：`ui/home/HomeUiState.kt`
- Model：`ui/home/Conversation.kt`、`ui/home/ChatMessage.kt`
- Repository：`data/repository/*`
- Network DTO：`data/remote/SmartFeedApi.kt`
- Local persistence：`data/local/ConversationStore.kt`

## Preview

当前主要页面已添加 Compose Preview：

- `HomeScreenPreview`
- `ChatDetailScreenPreview`
- `AnalysisScreenPreview`
- `ArticleManagerScreenPreview`
- `PlaceholderScreenPreview`

在 Android Studio 中打开对应文件，切换到 `Split` 或 `Design` 即可查看预览。

## 后续建议

当前为了降低风险，`HomeViewModel` 和会话模型仍保留在 `ui/home`。后续如果继续规范化，可以再拆：

- `ui/model/Conversation.kt`
- `ui/model/ChatMessage.kt`
- `ui/state/HomeUiState.kt`
- `ui/home/HomeViewModel.kt` 继续拆成更小的 feature ViewModel 或 use case。
