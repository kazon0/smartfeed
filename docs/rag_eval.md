# SmartFeed RAG Evaluation

本文档记录当前 SmartFeed 后端的最小 RAG 回归评测方式。

目标不是评测模型智商，而是防止后续修改检索、chunk、sources 或 prompt 时，把已经修好的核心场景改坏。

回答上下文保留正文中的 URL、Markdown 和代码，但会在原创声明、相关推荐、作者精选等尾部噪声前截断，避免同一 chunk 中的推荐内容进入 LLM。

## 自动化评测

- 长文章跨章节问题会在 context chunk 上限内均衡保留每个命中 section，避免前置长章节挤掉后续章节依据。
- 页面级总结会在多个正文 section 间均衡取样，避免只总结召回最强的单一章节。

运行：

```bash
venv/bin/python -m pytest tests/test_rag_eval.py -q
```

当前评测使用内存 fake corpus，不访问真实网页、不调用 DeepSeek、不写入 ChromaDB。

覆盖场景：

- 当前网页列表问题：`十大算法是什么`
  - 必须使用当前 URL。
  - answer 应包含正文里的算法名称。
  - sources 不应混入推荐文章、原创声明等展示噪声。
- 当前网页具体问题：`二分查找怎么理解`
  - 必须留在当前网页上下文。
  - 不应跳到全局知识库。
  - 传给 LLM 的 context 应包含同 section 的前后正文 chunks，不应只给孤立命中片段。
  - context 不应包含推荐文章等低质量噪声 chunks。
- 回答 prompt 策略
  - 应要求模型先综合相关片段的上下文关系，再自然解释给用户。
  - 不应退化成按 chunk 顺序机械拼接摘要。
- 全局知识库健康问题：`吃菌子中毒会怎么样`
  - 应命中已保存健康/新闻文章。
- 全局学习问题：`如何学习 Kotlin`
  - 应优先使用已保存知识库内容。
- 全局案例问题：`有没有什么作弊案例`
  - 应命中已保存的考试/高考作弊案例文章。
  - 不应被健康、算法或其他无关文章抢走。
- 无 URL 的文章指代：`这篇文章讲了什么`
  - 不允许随机全局检索。
  - 应返回 `need_page_context`。
- 未入库 URL 的当前网页问题
  - 不允许假装回答该网页。
  - 应返回 `page_not_found_with_suggestions`。
  - sources 应作为已保存文章链接建议，不应伪装成回答该网页的依据片段。
- 实时问题：`今天星期几`
  - 没有相关知识库内容时不允许 LLM 猜。
  - 应返回 `unsupported_realtime`。

## 手动真实网页评测

修改 RAG 后，建议至少手动验证以下真实场景：

也可以直接运行真实网页评测脚本。先启动后端：

```bash
venv/bin/uvicorn app.main:app --reload
```

然后运行：

```bash
venv/bin/python scripts/rag_real_eval.py
```

默认会：

- 上传腾讯云算法文章和野生菌中毒文章。
- 执行固定 `/chat` 用例。
- 在终端输出 Markdown 报告。
- 如果有用例失败，命令会返回非 0 exit code。

保存报告：

```bash
venv/bin/python scripts/rag_real_eval.py --output /tmp/smartfeed_rag_eval.md
```

如果已经上传过文章，只想跑 `/chat`：

```bash
venv/bin/python scripts/rag_real_eval.py --skip-upload
```

### 1. 腾讯云算法文章

```bash
curl -X POST http://127.0.0.1:8000/upload \
  -H "Content-Type: application/json" \
  -d '{"url":"https://cloud.tencent.com/developer/article/2352039"}'

curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"十大算法是什么","url":"https://cloud.tencent.com/developer/article/2352039"}'
```

期望：

- `source_type` 为 `page`。
- answer 能列出排序、搜索、图算法、动态规划相关内容。
- sources 来自当前 URL。
- sources 的 `content_preview` 不应主要展示广告、作者推荐、相关推荐或原创声明。

### 2. 野生菌中毒文章

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"吃菌子中毒会怎么样"}'
```

期望：

- 如果文章已入库，`source_type` 为 `knowledge_base`。
- answer 应提到症状、就医或风险。
- sources 应指向相关健康/新闻文章。

### 3. 模糊文章指代

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"这篇文章讲了什么"}'
```

期望：

- 没有 URL 时返回 `need_page_context`。
- 不应随机总结知识库中的某篇文章。

## 判定标准

一次 RAG 修改至少需要满足：

- `tests/test_mvp.py` 通过。
- `tests/test_rag_eval.py` 通过。
- 对当前正在优化的真实网页做一次手动验证。

## 注意

- 自动化评测不替代真实网页测试。
- 如果网页已用旧解析逻辑上传过，修改 parser 或 source 清洗后建议重新上传该 URL。
- Android 前端应优先展示 `display_title`、`section_title`、`source_summary`，将 `content_preview` 作为可展开依据。
