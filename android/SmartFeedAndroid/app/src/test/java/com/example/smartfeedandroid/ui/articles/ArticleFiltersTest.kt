package com.example.smartfeedandroid.ui.articles

import com.example.smartfeedandroid.data.remote.SavedArticle
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleFiltersTest {
    private val articles = listOf(
        article("b", "Kotlin 指南", "developer.android.com", 6, "科技"),
        article("a", "AI 入门", "example.com", 12, "科技"),
        article("c", "睡眠建议", "health.example.com", 4, "健康"),
        article("d", "", "custom.example.com", 12, "自定义")
    )

    @Test
    fun topicsFollowKnownOrderThenUnknownTopics() {
        assertEquals(listOf("全部", "科技", "健康", "自定义"), articleTopics(articles))
    }

    @Test
    fun searchMatchesTitleDomainUrlAndTopic() {
        assertEquals(listOf("b"), visibleArticles(articles, "全部", "KOTLIN", ArticleSort.Default).ids())
        assertEquals(listOf("c"), visibleArticles(articles, "全部", "health.example", ArticleSort.Default).ids())
        assertEquals(listOf("d"), visibleArticles(articles, "全部", "custom.example.com/d", ArticleSort.Default).ids())
        assertEquals(listOf("b", "a"), visibleArticles(articles, "全部", "科技", ArticleSort.Default).ids())
    }

    @Test
    fun topicAndSearchFiltersApplyTogether() {
        assertEquals(
            listOf("a"),
            visibleArticles(articles, "科技", "example.com", ArticleSort.Default).ids()
        )
    }

    @Test
    fun sortSupportsTitleAndChunkCountWithoutChangingDefaultOrder() {
        assertEquals(listOf("b", "a", "c", "d"), visibleArticles(articles, "全部", "", ArticleSort.Default).ids())
        assertEquals(listOf("a", "b", "c", "d"), visibleArticles(articles, "全部", "", ArticleSort.Title).ids())
        assertEquals(listOf("a", "d", "b", "c"), visibleArticles(articles, "全部", "", ArticleSort.ChunkCount).ids())
    }

    private fun article(
        id: String,
        title: String,
        domain: String,
        chunkCount: Int,
        topic: String
    ): SavedArticle {
        return SavedArticle(
            url = "https://$domain/$id",
            title = title,
            domain = domain,
            chunkCount = chunkCount,
            topic = topic
        )
    }

    private fun List<SavedArticle>.ids(): List<String> {
        return map { it.url.substringAfterLast('/') }
    }
}
