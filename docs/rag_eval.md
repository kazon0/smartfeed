# SmartFeed RAG Evaluation

本文档记录当前 SmartFeed 后端的最小 RAG 回归评测方式。

目标不是评测模型智商，而是防止后续修改检索、chunk、sources 或 prompt 时，把已经修好的核心场景改坏。

## 自动化评测

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
- 全局知识库健康问题：`吃菌子中毒会怎么样`
  - 应命中已保存健康/新闻文章。
- 全局学习问题：`如何学习 Kotlin`
  - 应优先使用已保存知识库内容。
- 无 URL 的文章指代：`这篇文章讲了什么`
  - 不允许随机全局检索。
  - 应返回 `need_page_context`。
- 未入库 URL 的当前网页问题
  - 不允许假装回答该网页。
  - 应返回 `page_not_found_with_suggestions`。
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
