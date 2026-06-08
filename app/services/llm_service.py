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

        prompt = (
            "请用中文总结以下网页内容，控制在100到200字之间。"
            "总结应准确、简洁，覆盖核心信息。\n\n"
            f"{content[:8000]}"
        )
        return self._chat(prompt)

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
        context = "\n\n".join(chunk.strip() for chunk in context_chunks if chunk.strip())
        prompt = (
            "你是 SmartFeed 的知识库问答助手。"
            "下面的 context 是向量检索返回的知识库片段，可能包含噪声。"
            "每个片段都有内部来源编号、title、url 和 chunk_index。"
            "请先在 context 中寻找与 question 相关的片段。"
            "只要 context 中存在相关信息，就必须基于这些相关片段回答，不要说没有找到。"
            "最终回答要面向普通用户，不要使用“来源[1]”“来源[2]”这类编号。"
            "如果相关内容主要来自同一篇文章，请用“根据《文章标题》...”或“根据当前网页...”自然引用。"
            "如果来自多篇文章，请用文章标题自然说明来源差异。"
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
        return self._chat(prompt)

    def answer_without_context(self, question: str, reason: str) -> str:
        prompt = (
            f"请先明确说明：{reason}。"
            "然后可以使用通用知识回答用户问题，回答应简洁、准确。\n\n"
            f"question:\n{question.strip()}"
        )
        return self._chat(prompt)

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
