# Deployment

This project can be deployed as a Dockerized FastAPI service with PostgreSQL
metadata storage and a persistent ChromaDB directory.

## Required Services

- FastAPI web service built from `Dockerfile`
- PostgreSQL database
- Persistent disk or volume mounted for ChromaDB
- DeepSeek API key

## Docker Image

GitHub Actions builds and publishes the backend image on every push to `main`:

```text
ghcr.io/kazon0/smartfeed-api:latest
```

Use this image in Sealos or any container platform when deploying from a
prebuilt image instead of building from the Git repository.

## Environment Variables

```env
DEEPSEEK_API_KEY=...
DATABASE_URL=postgresql+psycopg://user:password@host:5432/database
JWT_SECRET=replace_with_at_least_32_random_characters
JWT_ACCESS_TOKEN_MINUTES=60
SMARTFEED_RAG_PIPELINE=classic
CHROMA_PERSIST_DIR=/data/chroma_db
CORS_ALLOW_ORIGINS=
RUN_MIGRATIONS=1
```

Notes:

- `DATABASE_URL` also accepts `postgres://` and `postgresql://`; the app
  normalizes both to the psycopg driver URL.
- `JWT_SECRET` must be at least 32 characters.
- `CHROMA_PERSIST_DIR` must point to a persistent mount in production.
- `RUN_MIGRATIONS=1` runs `alembic upgrade head` before starting the server.
- Set `CORS_ALLOW_ORIGINS` only when serving a browser client from another
  domain, for example `https://app.example.com,https://admin.example.com`.

## Start Command

The container default command is:

```bash
sh scripts/start_server.sh
```

The script uses `$PORT` when the hosting platform provides it, defaulting to
`8000` locally.

## Health Check

Use:

```text
/health
```

Expected response:

```json
{"status":"ok"}
```

## Sealos Deployment

The first public deployment is available at:

```text
https://lxfxyunzhlxi.sealoshzh.site
```

Current deployed components:

- Sealos app `smartfeed-api`
- GHCR image `ghcr.io/kazon0/smartfeed-api:latest`
- Sealos PostgreSQL
- Persistent ChromaDB mount at `/data`
- `CHROMA_PERSIST_DIR=/data/chroma_db`
- Public HTTPS access enabled on container port `8000`

Verify the deployed service:

```bash
curl https://lxfxyunzhlxi.sealoshzh.site/health
```

Expected response:

```json
{"status":"ok"}
```

Security note: rotate any API keys or database passwords that were exposed
during manual deployment, then update the Sealos environment variables and
restart the app.

## Local Docker Smoke Test

```bash
docker build -t smartfeed-api .
docker run --rm -p 8000:8000 \
  -e DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
  -e DATABASE_URL="$DATABASE_URL" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e CHROMA_PERSIST_DIR=/data/chroma_db \
  -v smartfeed_chroma:/data \
  smartfeed-api
```

Then verify:

```bash
curl http://127.0.0.1:8000/health
```

## Android Production Build

Build the Android app against the deployed HTTPS backend:

```bash
cd android/SmartFeedAndroid
./gradlew assembleDebug -PsmartfeedBaseUrl=https://your-api.example.com/
```

The URL must be HTTPS for a real device demo unless the Android network security
config is explicitly changed.

## Release Verification

1. `GET /health` returns `{"status":"ok"}`.
2. A new user can register and log in.
3. `/upload` stores an article for that user.
4. `/chat` answers against the uploaded article.
5. `/conversations` restores Android chat history after reinstall or login on a
   second device.
6. `/ws/chat?token=$ACCESS_TOKEN` emits `connected`, `authenticated`,
   `retrieving`, `answering`, and `completed` events.
7. Restarting the service does not erase PostgreSQL rows or ChromaDB vectors.
