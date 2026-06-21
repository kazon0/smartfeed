import json
import os

from dotenv import load_dotenv
import requests

load_dotenv()


class LLMService:
    API_URL = "https://api.deepseek.com/chat/completions"
    MODEL = "deepseek-chat"
    TOPICS = ["科技", "学习", "健康", "职业", "财经", "生活", "新闻", "其他"]

    def __init__(self) -> None:
        self.api_key = os.getenv("DEEPSEEK_API_KEY", "")

    def summarize(self, text: str) -> str:
        content = text.strip()
        if not content:
            return ""

        return self._chat(self._summary_prompt(content))

    def summarize_stream(self, text: str):
        content = text.strip()
        if not content:
            return
        yield from self._chat_stream(self._summary_prompt(content))

    def _summary_prompt(self, content: str) -> str:
        return (
            "请用中文总结以下网页内容，控制在100到200字之间。"
            "总结应准确、简洁，覆盖核心信息。\n\n"
            f"{content[:8000]}"
        )

    def classify_topic(
        self,
        *,
        title: str,
        url: str,
        summary: str,
        content: str,
    ) -> dict:
        prompt = (
            "请判断网页文章最合适的一个主题分类。"
            "只能从以下分类中选择一个：科技、学习、健康、职业、财经、生活、新闻、其他。"
            "分类标准："
            "科技包括AI、编程、软件、算法、开发、数据、模型等；"
            "学习包括考试、教育、课程、教程、学习方法、笔记等；"
            "健康包括疾病、医生、治疗、饮食、心理、睡眠等；"
            "职业包括实习、面试、招聘、简历、职场、薪资等；"
            "财经包括股票、基金、投资、汇率、经济、公司、市场等；"
            "生活包括旅行、美食、家庭、情感、消费、娱乐等；"
            "新闻包括新闻报道、政策发布、社会事件、官方通报、媒体报道、时事内容等。"
            "请综合 url、title、summary 和正文片段判断，不要只看关键词。"
            "必须只返回 JSON，不要返回 Markdown，不要添加解释。"
            "JSON 格式："
            '{"topic":"新闻","confidence":0.86,"reason":"一句话原因"}'
            "\n\n"
            f"url:\n{url.strip()}\n\n"
            f"title:\n{title.strip()}\n\n"
            f"summary:\n{summary.strip()[:1200]}\n\n"
            f"content:\n{content.strip()[:4000]}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            return {
                "topic": "其他",
                "confidence": 0.0,
                "reason": response,
                "source": "fallback",
            }

        data = self._parse_json_object(response)
        topic = data.get("topic") if isinstance(data.get("topic"), str) else "其他"
        if topic not in self.TOPICS:
            topic = "其他"

        confidence = data.get("confidence", 0.0)
        if not isinstance(confidence, (int, float)):
            confidence = 0.0

        reason = data.get("reason", "")
        if not isinstance(reason, str):
            reason = ""

        return {
            "topic": topic,
            "confidence": float(confidence),
            "reason": reason,
            "source": "llm",
        }

    def answer(self, question: str, context_chunks: list[str]) -> str:
        return self._chat(self._answer_prompt(question, context_chunks))

    def answer_stream(self, question: str, context_chunks: list[str]):
        yield from self._chat_stream(self._answer_prompt(question, context_chunks))

    def _answer_prompt(self, question: str, context_chunks: list[str]) -> str:
        context = "\n\n".join(chunk.strip() for chunk in context_chunks if chunk.strip())
        return (
            "你是 SmartFeed 的知识库问答助手。"
            "下面的 context 是向量检索返回的知识库片段，可能包含噪声。"
            "每个片段都有内部来源编号、title、url 和 chunk_index。"
            "请先在 context 中寻找与 question 相关的片段。"
            "回答前先综合相关片段的上下文关系，理解文章或章节想说明什么，"
            "再用自然语言解释给用户，不要按 chunk 顺序机械拼接摘要。"
            "只要 context 中存在相关信息，就必须基于这些相关片段回答，不要说没有找到。"
            "最终回答要面向普通用户，不要使用“来源[1]”“来源[2]”这类编号。"
            "如果相关内容主要来自同一篇文章，请用“根据《文章标题》...”或“根据当前网页...”自然引用。"
            "如果来自多篇文章，请用文章标题自然说明来源差异。"
            "回答要适合手机阅读：默认控制在 3 到 6 个短段落或短项目符号内，"
            "优先使用 Markdown 的短列表和少量 **加粗关键词**，不要输出大段论文式文字。"
            "当用户要求总结已保存、收藏或知识库文章时，先用 3 到 5 条要点概括，"
            "最后给 1 句可以继续追问的方向。"
            "如果问题涉及代码、算法、步骤或多个方法，请尽量完整展开说明；"
            "context 中出现代码片段时，应保留关键代码或伪代码，不要只说“方法1、方法2、方法3”。"
            "如果用户要求列出网页中的方法、算法、步骤、要点或清单，"
            "请从 context 中尽可能完整整理，按条目输出；"
            "不要因为 context 没有精确出现用户说的数量词就拒绝回答。"
            "不要使用 context 之外的信息补充细节。"
            "只有当所有 context 片段都与问题无关时，才说明无法从当前知识库内容中确定。\n\n"
            f"context:\n{context}\n\n"
            f"question:\n{question.strip()}"
        )

    def answer_without_context(self, question: str, reason: str) -> str:
        return self._chat(self._answer_without_context_prompt(question, reason))

    def answer_without_context_stream(self, question: str, reason: str):
        yield from self._chat_stream(self._answer_without_context_prompt(question, reason))

    def _answer_without_context_prompt(self, question: str, reason: str) -> str:
        return (
            f"请先明确说明：{reason}。"
            "然后可以使用通用知识回答用户问题，回答应简洁、准确。\n\n"
            f"question:\n{question.strip()}"
        )

    def rewrite_query(self, question: str, *, url: str | None = None) -> dict:
        query = question.strip()
        if not query:
            return {"query": "", "source": "empty", "reason": ""}

        prompt = (
            "请把用户问题改写成更适合知识库检索的中文查询语句。"
            "目标是提高向量检索和关键词检索命中率。"
            "要求："
            "1. 保留用户原意，不要增加用户没有问的新目标；"
            "2. 消解“这篇文章、上面、它、这个”等指代，改成可检索的主题词；"
            "3. 如果问题是代码、算法、步骤、方法、清单类，保留关键技术词和数量词；"
            "4. 不要回答问题，只输出检索查询；"
            "5. 必须只返回 JSON，不要 Markdown。"
            'JSON 格式：{"query":"改写后的检索查询","reason":"一句话原因"}'
            "\n\n"
            f"question:\n{query}\n\n"
            f"current_url:\n{url or ''}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            return {"query": query, "source": "fallback", "reason": response}

        data = self._parse_json_object(response)
        rewritten_query = data.get("query") if isinstance(data.get("query"), str) else ""
        rewritten_query = rewritten_query.strip()
        if not rewritten_query:
            rewritten_query = query

        reason = data.get("reason", "")
        if not isinstance(reason, str):
            reason = ""

        return {
            "query": rewritten_query,
            "source": "llm",
            "reason": reason,
        }

    def generate_search_queries(
        self,
        question: str,
        rewritten_query: str,
        *,
        url: str | None = None,
    ) -> list[str]:
        query = question.strip()
        rewritten = rewritten_query.strip()
        if not query and not rewritten:
            return []

        prompt = (
            "请为知识库检索生成 2 到 4 个查询变体，用来提高召回率。"
            "要求："
            "1. 每个查询都必须服务于用户原始问题；"
            "2. 覆盖同义词、标题词、技术词、清单词、步骤词等可能写法；"
            "3. 如果问题询问文章中的列表、方法、算法、步骤，查询要包含这些清单相关词；"
            "4. 不要生成与用户问题无关的新问题；"
            "5. 必须只返回 JSON 字符串数组，不要 Markdown，不要解释。"
            '示例：["十个基础算法 清单","排序算法 搜索算法 图算法 动态规划","程序员 应该知道 算法"]'
            "\n\n"
            f"question:\n{query}\n\n"
            f"rewritten_query:\n{rewritten}\n\n"
            f"current_url:\n{url or ''}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            return []

        data = self._parse_json_array(response)
        queries = []
        for item in data:
            if not isinstance(item, str):
                continue
            clean_item = item.strip()
            if clean_item and clean_item not in queries:
                queries.append(clean_item)
        return queries[:4]

    def rerank_chunks(self, question: str, candidates: list[str]) -> list[int]:
        clean_candidates = [
            candidate.strip()
            for candidate in candidates
            if candidate.strip()
        ]
        if not clean_candidates:
            return []

        formatted_candidates = "\n\n".join(
            f"[{index}] {candidate[:1200]}"
            for index, candidate in enumerate(clean_candidates)
        )
        prompt = (
            "请根据用户问题，对候选知识库片段按相关性从高到低排序。"
            "只返回最相关片段的 0-based index JSON 数组，例如：[2,0,3]。"
            "不要返回 Markdown，不要添加解释。"
            "如果片段与问题明显无关，可以不放入数组。"
            "排序时优先选择能直接回答问题、包含关键步骤/代码/清单/定义的片段。\n\n"
            f"question:\n{question.strip()}\n\n"
            f"candidates:\n{formatted_candidates}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            return []

        data = self._parse_json_array(response)
        indexes = []
        for item in data:
            if isinstance(item, int) and 0 <= item < len(clean_candidates) and item not in indexes:
                indexes.append(item)
        return indexes

    def compress_context(self, question: str, context_chunks: list[str]) -> str:
        context = self._balanced_context_input(context_chunks)
        if not context:
            return ""

        prompt = (
            "请将下面的知识库 context 压缩成更适合回答用户问题的上下文。"
            "要求："
            "1. 只保留与 question 相关的信息；"
            "2. 保留来源编号、title、url、section_title、chunk_index 等来源头信息；"
            "3. 如果 context 包含多个相关 section 或多个 URL，每个都至少保留一条关键依据；"
            "4. 如果包含代码、算法步骤、方法列表、数字、结论，必须尽量保留；"
            "5. 删除广告、导航、无关推荐、重复句子和噪声；"
            "6. 不要回答 question，只输出压缩后的 context；"
            "7. 如果 context 中没有相关信息，返回空字符串。\n\n"
            f"question:\n{question.strip()}\n\n"
            f"context:\n{context}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            return ""
        return response.strip()

    def _balanced_context_input(
        self,
        context_chunks: list[str],
        limit: int = 12000,
    ) -> str:
        chunks = [chunk.strip() for chunk in context_chunks if chunk.strip()]
        if not chunks:
            return ""

        separator_length = 2 * (len(chunks) - 1)
        chunk_limit = max(1, (limit - separator_length) // len(chunks))
        return "\n\n".join(chunk[:chunk_limit] for chunk in chunks)[:limit]

    def describe_sources(self, question: str, source_texts: list[str]) -> list[str]:
        if not source_texts:
            return []

        formatted_sources = "\n\n".join(
            f"[{index + 1}] {text[:1200]}"
            for index, text in enumerate(source_texts)
        )
        prompt = (
            "请为下面每个来源生成一句中文说明，解释它与用户问题的关系。"
            "只能基于来源内容，不要补充来源里没有的信息。"
            "每句控制在40字以内。"
            "不要出现“来源[1]”“来源[2]”“片段[1]”等内部编号。"
            "不要使用“这段内容”“该段内容”“这部分”等缺少指代对象的说法。"
            "优先结合来源中的 title 或 section_title，用可独立理解的话描述，"
            "例如“《十个基础算法》列出了算法分类和具体名称”。"
            "必须只返回 JSON 字符串数组，不要返回 Markdown，不要添加解释。\n\n"
            f"question:\n{question.strip()}\n\n"
            f"sources:\n{formatted_sources}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            return []

        try:
            data = json.loads(response)
        except json.JSONDecodeError:
            return []

        if not isinstance(data, list):
            return []

        return [item for item in data if isinstance(item, str)]

    def summarize_knowledge_base(self, articles: list[dict]) -> dict:
        clean_articles = [
            {
                "title": str(article.get("title", "")).strip(),
                "url": str(article.get("url", "")).strip(),
                "domain": str(article.get("domain", "")).strip(),
                "topic": str(article.get("topic", "")).strip() or "其他",
                "chunk_count": int(article.get("chunk_count", 0) or 0),
            }
            for article in articles
            if str(article.get("url", "")).strip()
        ]
        if not clean_articles:
            return {
                "summary": "当前知识库还没有已保存文章。可以先分享几篇文章，系统会在这里总结你的关注方向。",
                "highlights": [],
                "suggestions": ["先保存几篇同一主题的文章，方便形成更稳定的知识画像。"],
                "source": "fallback",
            }

        article_lines = "\n".join(
            (
                f"- title: {article['title'][:120]}\n"
                f"  topic: {article['topic']}\n"
                f"  domain: {article['domain']}\n"
                f"  chunk_count: {article['chunk_count']}"
            )
            for article in clean_articles[:80]
        )
        prompt = (
            "请基于用户个人知识库的文章清单，生成一份中文智能分析。"
            "你只能基于给定清单分析，不要编造未出现的文章内容。"
            "输出必须是 JSON，不要 Markdown。"
            "字段："
            "{"
            '"summary":"80到140字，总结用户当前知识库主要关注什么",'
            '"highlights":["3条以内，指出明显的内容结构、主题倾向或来源特征"],'
            '"suggestions":["3条以内，给出下一步保存或提问建议"]'
            "}"
            "\n\n"
            f"articles:\n{article_lines}"
        )
        response = self._chat(prompt)
        if response.startswith("LLM unavailable"):
            fallback = self._fallback_knowledge_summary(clean_articles)
            fallback["source"] = "fallback"
            return fallback

        data = self._parse_json_object(response)
        summary = data.get("summary", "")
        highlights = data.get("highlights", [])
        suggestions = data.get("suggestions", [])

        if not isinstance(summary, str) or not summary.strip():
            fallback = self._fallback_knowledge_summary(clean_articles)
            fallback["source"] = "fallback"
            return fallback

        return {
            "summary": summary.strip(),
            "highlights": [
                item.strip()
                for item in highlights
                if isinstance(item, str) and item.strip()
            ][:3],
            "suggestions": [
                item.strip()
                for item in suggestions
                if isinstance(item, str) and item.strip()
            ][:3],
            "source": "llm",
        }

    def _fallback_knowledge_summary(self, articles: list[dict]) -> dict:
        topic_counts: dict[str, int] = {}
        domain_counts: dict[str, int] = {}
        for article in articles:
            topic = article.get("topic", "其他") or "其他"
            domain = article.get("domain", "unknown") or "unknown"
            topic_counts[topic] = topic_counts.get(topic, 0) + 1
            domain_counts[domain] = domain_counts.get(domain, 0) + 1

        top_topic = max(topic_counts.items(), key=lambda item: item[1])[0]
        top_domain = max(domain_counts.items(), key=lambda item: item[1])[0]
        summary = (
            f"当前知识库共保存 {len(articles)} 篇文章，主要集中在“{top_topic}”方向，"
            f"常见来源包括 {top_domain}。可以继续围绕高频主题追问，也可以补充不同来源的文章。"
        )
        return {
            "summary": summary,
            "highlights": [
                f"最主要主题是“{top_topic}”。",
                f"主要内容来源是 {top_domain}。",
            ],
            "suggestions": [
                "可以从文章管理页打开一篇文章继续追问。",
                "如果主题过于集中，可以补充不同领域的文章做对比。",
            ],
        }

    def _parse_json_object(self, text: str) -> dict:
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            start = text.find("{")
            end = text.rfind("}")
            if start < 0 or end <= start:
                return {}
            try:
                data = json.loads(text[start : end + 1])
            except json.JSONDecodeError:
                return {}

        if not isinstance(data, dict):
            return {}
        return data

    def _parse_json_array(self, text: str) -> list:
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            start = text.find("[")
            end = text.rfind("]")
            if start < 0 or end <= start:
                return []
            try:
                data = json.loads(text[start : end + 1])
            except json.JSONDecodeError:
                return []

        if not isinstance(data, list):
            return []
        return data

    def _chat(self, user_prompt: str) -> str:
        if not self.api_key:
            return "LLM unavailable: DEEPSEEK_API_KEY is not set."

        try:
            response = requests.post(
                self.API_URL,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.MODEL,
                    "messages": [
                        {
                            "role": "system",
                            "content": "你是一个严谨的中文知识库助手。",
                        },
                        {
                            "role": "user",
                            "content": user_prompt,
                        },
                    ],
                    "temperature": 0.2,
                },
                timeout=30,
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"].strip()
        except (requests.RequestException, KeyError, IndexError) as exc:
            return f"LLM unavailable: {exc}"

    def _chat_stream(self, user_prompt: str):
        if not self.api_key:
            yield "LLM unavailable: DEEPSEEK_API_KEY is not set."
            return

        try:
            response = requests.post(
                self.API_URL,
                headers={
                    "Authorization": f"Bearer {self.api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self.MODEL,
                    "messages": [
                        {
                            "role": "system",
                            "content": "你是一个严谨的中文知识库助手。",
                        },
                        {
                            "role": "user",
                            "content": user_prompt,
                        },
                    ],
                    "temperature": 0.2,
                    "stream": True,
                },
                timeout=60,
                stream=True,
            )
            response.raise_for_status()
            for line in response.iter_lines(decode_unicode=True):
                if not line or not line.startswith("data:"):
                    continue
                payload = line.removeprefix("data:").strip()
                if payload == "[DONE]":
                    break
                try:
                    data = json.loads(payload)
                    content = data["choices"][0].get("delta", {}).get("content", "")
                except (json.JSONDecodeError, KeyError, IndexError):
                    continue
                if content:
                    yield content
        except requests.RequestException as exc:
            yield f"LLM unavailable: {exc}"
