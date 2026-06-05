# SmartFeed

## Project Overview

SmartFeed 是一个基于 RAG 的个人 AI 知识库管理平台。

用户可以：

1. 收藏网页文章
2. 自动提取正文
3. 自动向量化存储
4. 使用自然语言提问
5. 基于知识库内容获得 AI 回答

## Tech Stack

Backend:

* Python
* FastAPI
* ChromaDB
* LangChain
* WebSocket

Mobile:

* Android
* Kotlin
* Jetpack Compose
* Coroutines
* Flow

LLM:

* DeepSeek API

## Architecture

Android App
→ FastAPI
→ ChromaDB
→ DeepSeek

## Development Rules

* 优先保证项目可运行
* 先实现 MVP
* 不要过度设计
* 每一步完成后提供运行说明
* 每一步完成后说明目录结构变化

## Current Goal

阶段1：

完成 FastAPI 项目骨架

要求：

GET /

返回：

{
"status":"ok"
}
