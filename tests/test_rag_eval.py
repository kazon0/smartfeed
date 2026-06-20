from app.services.chat_service import ChatService
from app.services.query_intent import QueryIntentService


ALG_URL = "https://cloud.tencent.com/developer/article/2352039"
NEWS_URL = "https://news.99.com.cn/minsheng/20260605/2386221.htm"
KOTLIN_URL = "https://example.com/kotlin"
CHEATING_URL = "https://c.m.163.com/news/a/KJVOP9P80536N8SL.html"
UNKNOWN_URL = "https://example.com/not-uploaded"


def chunk(url, title, content, index, section_title="", section_index=0, score=0.92):
    return {
        "content": content,
        "metadata": {
            "url": url,
            "title": title,
            "section_index": section_index,
            "section_title": section_title,
            "chunk_index": index,
        },
        "score": score,
    }


CORPUS = [
    chunk(
        ALG_URL,
        "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
        "原创 小齐来了 关注作者 关联问题 换一批 程序员常用的基础算法有哪些？",
        1,
        "程序员应该知道的十个基础算法",
        2,
        0.3,
    ),
    chunk(
        ALG_URL,
        "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
        "作为一名程序员，掌握各种算法可以帮助解决复杂问题。常用的算法类别及其应用如下：一. 排序算法 1.冒泡排序 2.快速排序 3.归并排序。",
        2,
        "程序员应该知道的十个基础算法",
        2,
    ),
    chunk(
        ALG_URL,
        "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
        "二. 搜索算法 1.二分查找：适用于有序数组。2.广度优先搜索：按层次遍历图或树。3.深度优先搜索：沿路径搜索再回溯。三. 图算法 1.最短路径算法 2.最小生成树算法。",
        3,
        "程序员应该知道的十个基础算法",
        2,
    ),
    chunk(
        ALG_URL,
        "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
        "四. 动态规划 1.背包问题：在容量限制下最大化价值。2.最长公共子序列：找出两个序列中最长的共同子序列。喜欢点赞收藏，下期再见。原创声明：本文系作者授权腾讯云开发者社区发表。",
        4,
        "程序员应该知道的十个基础算法",
        2,
    ),
    chunk(
        ALG_URL,
        "程序员应该知道的十个基础算法-腾讯云开发者社区-腾讯云",
        "作者相关精选 [](https://cloud.tencent.com/developer/user/1) 推荐文章 JavaScript 算法与数据结构。",
        5,
        "程序员应该知道的十个基础算法",
        2,
        0.35,
    ),
    chunk(
        NEWS_URL,
        "云南一医生全家已连续3年吃菌子中毒，出现幻觉并昏迷两天_百姓民生_新闻_99健康网",
        "野生菌中毒可能出现恶心、呕吐、腹痛、腹泻等胃肠道症状，也可能出现幻觉、意识障碍、肝肾损伤，严重时可危及生命。",
        0,
        "野生菌中毒症状",
        0,
    ),
    chunk(
        KOTLIN_URL,
        "Kotlin 协程学习笔记",
        "Kotlin 协程可以从 suspend 函数、CoroutineScope、Dispatcher 和 Flow 开始学习，先理解异步任务和结构化并发。",
        0,
        "协程学习路径",
        0,
    ),
    chunk(
        CHEATING_URL,
        "2026高考时间官宣！考生家长必看！",
        "教育部提醒，组织考试作弊、向考生提供试题答案、替考等行为都属于违法行为。案例2中，陈某、谢某共谋组织高考考试作弊，联系考生及家长、收取费用、提供试题答案，最终被公安机关查获。",
        0,
        "高考作弊案例",
        0,
    ),
]


class EvalVectorStore:
    def __init__(self):
        self.chunks = CORPUS

    def query(self, text, top_k=5, metadata_filter=None):
        source_chunks = self._filter(metadata_filter)
        scored = []
        for item in source_chunks:
            score = self._score(text, item)
            if score > 0:
                scored.append({**item, "score": min(1.0, score)})
        return sorted(scored, key=lambda item: item["score"], reverse=True)[:top_k]

    def get_chunks_by_url(self, url):
        return sorted(
            [item for item in self.chunks if item["metadata"].get("url") == url],
            key=lambda item: item["metadata"].get("chunk_index", 0),
        )

    def get_all_chunks(self, limit=1000):
        return self.chunks[:limit]

    def list_sources(self, limit=10):
        sources = []
        seen = set()
        for item in self.chunks:
            metadata = item["metadata"]
            url = metadata["url"]
            if url in seen:
                continue
            seen.add(url)
            sources.append(
                {
                    "url": url,
                    "title": metadata["title"],
                    "chunk_index": None,
                    "score": None,
                    "content_preview": "",
                }
            )
            if len(sources) >= limit:
                break
        return sources

    def _filter(self, metadata_filter):
        if not metadata_filter:
            return self.chunks
        url = metadata_filter.get("url")
        return [item for item in self.chunks if item["metadata"].get("url") == url]

    def _score(self, text, item):
        searchable = (
            item["metadata"].get("title", "")
            + item["metadata"].get("section_title", "")
            + item["content"]
        )
        terms = [
            "算法",
            "排序",
            "搜索",
            "二分查找",
            "动态规划",
            "最长公共子序列",
            "菌子",
            "中毒",
            "症状",
            "kotlin",
            "协程",
            "作弊",
            "案例",
            "高考",
            "考试",
        ]
        hits = sum(1 for term in terms if term.lower() in text.lower() and term.lower() in searchable.lower())
        if hits <= 0:
            return 0.0
        return 0.35 + hits * 0.18


class EvalLLMService:
    def rewrite_query(self, question, *, url=None):
        if "十大算法" in question or "十种算法" in question:
            return {"query": "十大算法 列表 排序 搜索 图算法 动态规划", "source": "fake", "reason": ""}
        if "二分查找" in question:
            return {"query": "二分查找 搜索算法", "source": "fake", "reason": ""}
        if "菌子" in question or "中毒" in question:
            return {"query": "野生菌中毒 症状", "source": "fake", "reason": ""}
        if "Kotlin" in question or "kotlin" in question:
            return {"query": "Kotlin 协程 学习", "source": "fake", "reason": ""}
        if "作弊" in question or "案例" in question:
            return {"query": "高考 作弊 案例 违法", "source": "fake", "reason": ""}
        return {"query": question, "source": "fake", "reason": ""}

    def generate_search_queries(self, question, rewritten_query, *, url=None):
        return [rewritten_query, question]

    def rerank_chunks(self, question, candidates):
        return list(range(min(3, len(candidates))))

    def compress_context(self, question, context_chunks):
        return ""

    def answer(self, question, context_chunks):
        context = "\n".join(context_chunks)
        if "十大算法" in question or "十种算法" in question:
            assert "推荐文章" not in context
            return "根据《程序员应该知道的十个基础算法》，十大算法包括冒泡排序、快速排序、归并排序、二分查找、广度优先搜索、深度优先搜索、最短路径算法、最小生成树算法、背包问题和最长公共子序列。"
        if "二分查找" in question:
            assert "冒泡排序" in context
            assert "动态规划" in context
            assert "推荐文章" not in context
            return "根据当前网页，二分查找适用于有序数组，通过不断缩小搜索范围定位目标。"
        if "菌子" in question or "中毒" in question:
            return "根据野生菌中毒文章，可能出现胃肠道症状、幻觉、意识障碍和肝肾损伤。"
        if "Kotlin" in question or "kotlin" in question:
            return "根据 Kotlin 学习笔记，可以从 suspend、CoroutineScope、Dispatcher 和 Flow 开始。"
        if "作弊" in question or "案例" in question:
            return "根据高考提醒文章，组织考试作弊、提供试题答案和替考都属于违法行为，陈某、谢某组织高考作弊的案例最终被公安机关查获。"
        return "根据知识库内容回答。"

    def answer_without_context(self, question, reason):
        return f"{reason}。\n\n这是通用回答。"

    def describe_sources(self, question, source_texts):
        return ["这段来源包含回答问题所需的正文依据。" for _ in source_texts]


def make_service():
    return ChatService(
        vector_store_factory=EvalVectorStore,
        llm_service_factory=EvalLLMService,
        intent_service_factory=QueryIntentService,
    )


def assert_source_url(response, expected_url):
    assert response["sources"]
    assert all(source.get("url") == expected_url for source in response["sources"])


def test_rag_eval_cross_section_context_keeps_each_matched_section():
    service = make_service()
    first_section = [
        chunk(
            ALG_URL,
            "长文章",
            f"第一章背景材料 {index}。" + "这一段提供完整背景信息。" * 8,
            index,
            "第一章",
            0,
        )
        for index in range(14)
    ]
    second_section = [
        chunk(
            ALG_URL,
            "长文章",
            f"第二章结论 {index}。" + "这一段解释最终结论和影响。" * 8,
            14 + index,
            "第二章",
            1,
        )
        for index in range(2)
    ]

    selected = service._expand_section_chunks(
        [first_section[11], second_section[1]],
        first_section + second_section,
    )

    selected_sections = {
        item["metadata"]["section_index"]
        for item in selected
    }
    assert len(selected) == 12
    assert selected_sections == {0, 1}
    assert first_section[11] in selected
    assert second_section[1] in selected


def test_rag_eval_page_wide_algorithm_list_uses_clean_current_page_sources():
    response = make_service().chat("十大算法是什么", url=ALG_URL)
    preview = response["sources"][0]["content_preview"]

    assert response["status"] == "ok"
    assert response["source_type"] == "page"
    assert_source_url(response, ALG_URL)
    assert "冒泡排序" in response["answer"]
    assert "最长公共子序列" in response["answer"]
    assert "冒泡排序" in preview
    assert "最长公共子序列" in preview
    assert "推荐文章" not in preview
    assert "原创声明" not in preview


def test_rag_eval_specific_page_question_stays_on_current_page_section():
    response = make_service().chat("二分查找怎么理解", url=ALG_URL)

    assert response["status"] == "ok"
    assert response["source_type"] == "page"
    assert_source_url(response, ALG_URL)
    assert "二分查找" in response["answer"]
    assert any("程序员应该知道的十个基础算法" in source["display_title"] for source in response["sources"])


def test_rag_eval_global_query_finds_saved_health_article():
    response = make_service().chat("吃菌子中毒会怎么样")

    assert response["status"] == "ok"
    assert response["source_type"] == "knowledge_base"
    assert_source_url(response, NEWS_URL)
    assert "幻觉" in response["answer"]
    assert "肝肾损伤" in response["answer"]


def test_rag_eval_global_learning_query_can_use_saved_kotlin_article():
    response = make_service().chat("如何学习 Kotlin")

    assert response["status"] == "ok"
    assert response["source_type"] == "knowledge_base"
    assert_source_url(response, KOTLIN_URL)
    assert "CoroutineScope" in response["answer"]


def test_rag_eval_global_case_query_finds_saved_cheating_article():
    response = make_service().chat("有没有什么作弊案例")

    assert response["status"] == "ok"
    assert response["source_type"] == "knowledge_base"
    assert_source_url(response, CHEATING_URL)
    assert "组织考试作弊" in response["answer"]
    assert "陈某" in response["answer"]


def test_rag_eval_article_reference_without_url_does_not_randomly_search():
    response = make_service().chat("这篇文章讲了什么")

    assert response["status"] == "failed"
    assert response["source_type"] == "need_page_context"
    assert response["sources"] == []
    assert "无法确定" in response["answer"]


def test_rag_eval_unknown_current_page_returns_saved_article_suggestions():
    response = make_service().chat("这篇文章讲了什么", url=UNKNOWN_URL)

    assert response["status"] == "failed"
    assert response["source_type"] == "page_not_found_with_suggestions"
    assert response["sources"]
    assert UNKNOWN_URL not in [source.get("url") for source in response["sources"]]
    assert "当前网页没有找到" in response["answer"]


def test_rag_eval_unknown_current_page_suggestions_are_article_links_not_answer_sources():
    response = make_service().chat("这篇文章讲了什么", url=UNKNOWN_URL)

    assert response["status"] == "failed"
    assert response["source_type"] == "page_not_found_with_suggestions"
    assert response["sources"]
    assert all(source.get("chunk_index") is None for source in response["sources"])
    assert all(not source.get("content_preview") for source in response["sources"])


def test_rag_eval_realtime_query_without_knowledge_does_not_guess():
    response = make_service().chat("今天星期几")

    assert response["status"] == "failed"
    assert response["source_type"] == "unsupported_realtime"
    assert response["sources"] == []
    assert "实时信息" in response["answer"]
