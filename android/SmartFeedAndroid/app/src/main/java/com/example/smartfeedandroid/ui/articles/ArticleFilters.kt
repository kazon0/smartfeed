package com.example.smartfeedandroid.ui.articles

import com.example.smartfeedandroid.data.remote.SavedArticle

internal const val ALL_ARTICLE_TOPICS = "全部"

internal enum class ArticleSort {
    Default,
    Title,
    ChunkCount
}

internal fun articleTopics(articles: List<SavedArticle>): List<String> {
    val topicOrder = listOf("科技", "学习", "健康", "职业", "财经", "生活", "新闻", "其他")
    val topics = articles
        .map(::articleTopic)
        .distinct()
        .sortedWith(
            compareBy<String> { topic ->
                topicOrder.indexOf(topic).takeIf { it >= 0 } ?: topicOrder.size
            }.thenBy { it }
        )

    return listOf(ALL_ARTICLE_TOPICS) + topics
}

internal fun visibleArticles(
    articles: List<SavedArticle>,
    selectedTopic: String,
    searchQuery: String,
    sort: ArticleSort
): List<SavedArticle> {
    val cleanQuery = searchQuery.trim().lowercase()
    val filtered = articles.filter { article ->
        val matchesTopic = selectedTopic == ALL_ARTICLE_TOPICS ||
            articleTopic(article) == selectedTopic
        val matchesSearch = cleanQuery.isBlank() || cleanQuery in listOf(
            article.title,
            article.domain,
            article.url,
            articleTopic(article)
        ).joinToString(" ").lowercase()

        matchesTopic && matchesSearch
    }

    return when (sort) {
        ArticleSort.Default -> filtered
        ArticleSort.Title -> filtered.sortedWith(
            compareBy<SavedArticle> { it.title.isBlank() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { articleDisplayTitle(it) }
        )
        ArticleSort.ChunkCount -> filtered.sortedWith(
            compareByDescending<SavedArticle> { it.chunkCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { articleDisplayTitle(it) }
        )
    }
}

internal fun articleTopic(article: SavedArticle): String {
    return article.topic.ifBlank { "其他" }
}

private fun articleDisplayTitle(article: SavedArticle): String {
    return article.title.ifBlank { article.url }
}
