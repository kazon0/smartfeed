import os

import requests


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
            "请只基于下面提供的 context 回答问题，不允许编造。"
            "如果 context 中没有足够信息，请直接说明无法从当前知识库内容中确定。\n\n"
            f"context:\n{context}\n\n"
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
