# SmartFeed

SmartFeed is a personal AI knowledge base for saving web articles, turning them into searchable knowledge, and asking questions about saved content.

The project currently includes a FastAPI backend and an Android Jetpack Compose client. The backend ingests web pages, extracts readable article content, chunks and embeds the text, stores it in ChromaDB, and uses DeepSeek to generate summaries and answers. The Android app provides article saving, article-based chat, global knowledge chat, local conversation history, and knowledge analysis.

## Features

- Save web articles by URL.
- Parse article content with Jina Reader first, then HTML fallback.
- Extract structured sections and chunks for RAG ingestion.
- Store embeddings in ChromaDB.
- Ask questions against the current article or the global knowledge base.
- Generate article summaries and knowledge base insights with DeepSeek.
- Support lightweight chat history context for follow-up questions.
- Show saved articles grouped by topic.
- Display Android knowledge analysis, including topic distribution, content depth, source domains, and AI-generated insights.
- Persist Android conversation history locally with Room.

## Tech Stack

### Backend

- Python
- FastAPI
- ChromaDB
- PostgreSQL
- SQLAlchemy 2
- Alembic
- psycopg
- sentence-transformers
- requests
- BeautifulSoup
- python-dotenv
- pytest
- DeepSeek API

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit
- OkHttp
- kotlinx.serialization
- Room

## Backend Setup

Create a Python environment and install dependencies:

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Create a `.env` file:

```env
DEEPSEEK_API_KEY=your_api_key_here
DATABASE_URL=postgresql+psycopg://smartfeed:smartfeed@localhost:5432/smartfeed
JWT_SECRET=replace_with_a_long_random_secret
JWT_ACCESS_TOKEN_MINUTES=60
```

Create or upgrade the cloud metadata schema:

```bash
alembic upgrade head
```

Start the backend:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Health check:

```bash
curl http://127.0.0.1:8000/
```

The PostgreSQL schema provides `users`, `articles`, `conversations`, and `messages`. Authentication and article metadata now use PostgreSQL; ChromaDB stores user-scoped chunks for retrieval. Conversation and message sync APIs are the next repository integration step.

## Main API Endpoints

- `GET /` health check
- `POST /auth/register` create an account and return a bearer token
- `POST /auth/login` authenticate and return a bearer token
- `GET /auth/me` return the authenticated user
- `GET /debug` browser-based debug page
- `POST /upload` upload and parse a web article
- `POST /search` semantic search over saved chunks
- `POST /chat` ask questions with optional `url` and chat `history`
- `GET /stats` knowledge base statistics
- `GET /insights` AI-generated knowledge base summary
- `GET /articles` saved articles
- `DELETE /articles` delete an article from the knowledge base by URL
- `GET /conversations` restore the authenticated user's cloud conversations
- `PUT /conversations/{id}` replace one conversation and its messages
- `DELETE /conversations/{id}` delete one owned conversation

All business endpoints require `Authorization: Bearer $ACCESS_TOKEN` and operate
only on the authenticated user's data. Articles stored before user isolation must
be uploaded again because their Chroma metadata has no owner.

Example upload:

```bash
curl -X POST http://127.0.0.1:8000/upload \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

Example chat:

```bash
curl -X POST http://127.0.0.1:8000/chat \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query":"What is this article about?","url":"https://example.com"}'
```

## Android App

Android project path:

```text
android/SmartFeedAndroid
```

The Android app now provides registration, login, encrypted token persistence,
automatic Bearer authentication, a real Profile screen, and owner-scoped Room
conversation storage. Cloud conversation synchronization is the next Android step.

The Android client currently uses:

```text
http://10.0.2.2:8000
```

as the backend base URL for the emulator.

Build check:

```bash
cd android/SmartFeedAndroid
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugKotlin --offline
```

## Tests

Run backend tests:

```bash
venv/bin/python -m compileall app tests
venv/bin/python -m pytest tests/test_mvp.py -q
```

## Project Status

SmartFeed is beyond a minimal MVP and now has a working RAG loop:

```text
URL -> article parsing -> sections/chunks -> embeddings -> ChromaDB -> retrieval -> DeepSeek answer
```

The current delivery roadmap is: complete the LangChain Runnable RAG pipeline, add PostgreSQL-backed users and cloud metadata, enforce JWT-based multi-user isolation across APIs and ChromaDB, connect Android authentication and conversation sync, deploy the HTTPS backend with persistent storage, and add authenticated WebSocket streaming while retaining `POST /chat` as a fallback. See `docs/roadmap.md` for the execution order.

## Notes

Runtime data and secrets should not be committed:

- `.env`
- `venv/`
- `chroma_db/`
- `__pycache__/`
- `*.pyc`
