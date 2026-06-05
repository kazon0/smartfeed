# SmartFeed System Specification

## 1. 系统目标

SmartFeed 是一个基于 RAG 的个人知识库 + Chat 系统。

核心目标：

用户浏览网页 → 分享 → 自动入库 → 自动总结 → 可对话 → 可全局搜索问答

---

## 2. 核心数据模型

### Document（网页）

- url
- title
- content
- chunks
- metadata

### Chunk（知识单元）

- text
- embedding
- metadata:
  - url
  - timestamp

---

## 3. 系统整体流程

### 3.1 网页入库流程（Ingestion）

输入：URL

流程：

1. HTTP request 获取 HTML
2. HTML 解析（提取正文）
3. 清洗文本（去 script/style）
4. chunk 切分（500字符 + overlap）
5. embedding 生成
6. 写入 ChromaDB

输出：存储成功 + chunks数量

---

### 3.2 网页总结流程（Summary）

触发时机：

- /upload 后自动执行

流程：

1. 输入：网页全文
2. 调用 LLM（DeepSeek）
3. 输出：
   - 100~200字中文总结
4. 保存 summary（作为 chat 第一条消息）

---

### 3.3 检索流程（Retrieval）

输入：query

流程：

1. query → embedding
2. ChromaDB vector search
3. topK chunks
4. 返回 chunks + metadata + score

---

### 3.4 Chat 流程（核心）

#### API统一入口：

POST /chat

输入：

```json
{
  "mode": "page | global | hybrid",
  "url": "optional",
  "query": "string"
}