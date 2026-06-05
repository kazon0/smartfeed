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
            "请先在 context 中寻找与 question 相关的片段。"
            "只要 context 中存在相关信息，就必须基于这些相关片段回答，不要说没有找到。"
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
