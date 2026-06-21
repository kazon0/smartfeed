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
- Stream authenticated chat answers and article summaries over WebSocket, with
  native ping heartbeat, one bounded pre-delta chat reconnect, and HTTP fallback.

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
- WebSocket
- Coroutines
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
SMARTFEED_RAG_PIPELINE=langchain
SMARTFEED_WS_FAST_PATH=0
CHROMA_PERSIST_DIR=chroma_db
CORS_ALLOW_ORIGINS=
```

For production, set `CHROMA_PERSIST_DIR` to a mounted persistent volume and set
`CORS_ALLOW_ORIGINS` to comma-separated HTTPS origins if a browser client is
served from a different domain.

`SMARTFEED_RAG_PIPELINE=langchain` enables the LangChain Core Runnable RAG
pipeline. `SMARTFEED_WS_FAST_PATH=0` makes WebSocket chat use the same full
rewrite, multi-query retrieval, rerank, compression, and answer path instead of
the lower-latency streaming shortcut.

Create or upgrade the cloud metadata schema:

```bash
alembic upgrade head
```

Start the backend:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

For container deployment, build from `Dockerfile` and use the default command:

```bash
sh scripts/start_server.sh
```

The deployment entrypoint runs Alembic migrations by default and starts Uvicorn
on `$PORT`. See `docs/deployment.md` for required environment variables,
ChromaDB persistent volume setup, and Android production build verification.
GitHub Actions publishes the backend image to:

```text
ghcr.io/kazon0/smartfeed-api:latest
```

The first public deployment is running on Sealos:

```text
https://lxfxyunzhlxi.sealoshzh.site
```

Verify it with:

```bash
curl https://lxfxyunzhlxi.sealoshzh.site/health
```

Health check:

```bash
curl http://127.0.0.1:8000/
```

The PostgreSQL schema provides `users`, `articles`, `conversations`, and `messages`. Authentication, article metadata, conversation sync, and message sync now use PostgreSQL; ChromaDB stores user-scoped chunks for retrieval.

## Main API Endpoints

- `GET /` health check
- `GET /health` deployment health check
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
- `WebSocket /ws/chat` authenticated chat status events and final answer

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
automatic Bearer authentication, a real Profile screen, owner-scoped Room
conversation storage, and cloud conversation/message sync with timestamp-based
merge.

The Android client uses the deployed Sealos backend by default:

```text
https://lxfxyunzhlxi.sealoshzh.site
```

Android Studio can therefore run the app directly without an extra Gradle
property. From the repository root, build, install, and launch on the first
connected real device with:

```bash
./scripts/install_android.sh
```

To target a specific device, pass its ADB serial. To use a local emulator
backend, override the URL only for that invocation:

```bash
./scripts/install_android.sh emulator-5554
SMARTFEED_BASE_URL=http://10.0.2.2:8000/ ./scripts/install_android.sh emulator-5554
```

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

The completed delivery work includes the LangChain Runnable RAG pipeline,
PostgreSQL-backed users and metadata, JWT-based user isolation across APIs and
ChromaDB, Android authentication, cloud conversation sync, a Sealos HTTPS
deployment, and authenticated WebSocket chat/upload streaming with HTTP
fallback. Remaining work is product hardening: verify the LangChain production
environment, add WebSocket heartbeat and bounded reconnect behavior, complete
real-device acceptance, measure performance before publishing latency or scale
claims, and finish the demo assets. See `docs/roadmap.md` for the execution order.

## Notes

Runtime data and secrets should not be committed:

- `.env`
- `venv/`
- `chroma_db/`
- `__pycache__/`
- `*.pyc`
