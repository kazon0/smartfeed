import re


class QueryIntentService:
    def classify(self, query: str, has_url: bool = False) -> dict:
        normalized = re.sub(r"\s+", "", query.strip().lower())

        if self._is_unsupported_action(normalized):
            return {
                "intent": "unsupported_action",
                "retrieval_scope": "none",
                "requires_page_context": False,
                "fallback_policy": "unsupported_action",
                "reason": "用户请求当前系统不支持的管理操作。",
            }

        is_page_reference = self._is_page_reference(normalized)
        is_search_history = self._is_search_history(normalized)

        if is_search_history and not (has_url and is_page_reference):
            return {
                "intent": "search_history",
                "retrieval_scope": "global",
                "requires_page_context": False,
                "fallback_policy": "no_llm_general_answer",
                "reason": "用户明确想查找已保存或已收藏的知识库内容。",
            }

        if is_page_reference:
            if has_url:
                return {
                    "intent": "page_reference",
                    "retrieval_scope": "page_first",
                    "requires_page_context": True,
                    "fallback_policy": "knowledge_then_llm",
                    "reason": "用户问题依赖当前网页上下文，且请求中提供了 url。",
                }

            return {
                "intent": "page_reference",
                "retrieval_scope": "none",
                "requires_page_context": True,
                "fallback_policy": "ask_for_page",
                "reason": "用户问题依赖文章指代，但请求中没有 url。",
            }

        if self._is_realtime_or_current(normalized):
            return {
                "intent": "realtime_or_current_query",
                "retrieval_scope": "global",
                "requires_page_context": False,
                "fallback_policy": "no_guess_realtime",
                "reason": "用户问题包含实时、当前、最新或日期价格类信息需求。",
            }

        return {
            "intent": "knowledge_or_general_query",
            "retrieval_scope": "global",
            "requires_page_context": False,
            "fallback_policy": "llm_allowed",
            "reason": "默认作为普通知识、学习、技术、解释、建议或代码类问题处理。",
        }

    def _is_unsupported_action(self, query: str) -> bool:
        patterns = (
            "删除这篇文章",
            "删除文章",
            "修改标题",
            "改标题",
            "导出所有内容",
            "导出内容",
            "登录账号",
            "注册账号",
            "同步到云端",
            "同步云端",
            "打标签",
            "添加标签",
            "修改标签",
        )
        return any(pattern in query for pattern in patterns)

    def _is_page_reference(self, query: str) -> bool:
        patterns = (
            "这篇文章",
            "这个网页",
            "这篇",
            "本文",
            "这段",
            "上面",
            "刚刚",
            "刚才",
            "总结一下",
            "帮我总结",
            "概括一下",
            "作者想表达什么",
            "文章讲了什么",
            "这篇讲了什么",
            "主要内容",
            "核心观点",
        )
        return any(pattern in query for pattern in patterns)

    def _is_search_history(self, query: str) -> bool:
        history_markers = (
            "我之前看过",
            "之前看过",
            "我保存过",
            "我保存的",
            "保存的文章",
            "已保存文章",
            "已保存的文章",
            "保存过",
            "我收藏",
            "收藏的",
            "找一下我收藏",
            "知识库里",
            "知识库中",
            "我看过",
        )
        return any(marker in query for marker in history_markers)

    def _is_realtime_or_current(self, query: str) -> bool:
        patterns = (
            "今天",
            "现在",
            "最新",
            "当前",
            "实时",
            "价格",
            "天气",
            "汇率",
            "股价",
            "新闻",
            "现任",
            "今年",
            "今天星期几",
            "现在几点",
        )
        return any(pattern in query for pattern in patterns)
