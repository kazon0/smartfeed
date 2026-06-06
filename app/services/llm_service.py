import json
import os

from dotenv import load_dotenv
import requests

load_dotenv()


class LLMService:
    API_URL = "https://api.deepseek.com/chat/completions"
    MODEL = "deepseek-chat"

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
