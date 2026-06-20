import os

from dotenv import load_dotenv

load_dotenv()


def database_url() -> str:
    url = os.getenv("DATABASE_URL", "").strip()
    if not url:
        raise RuntimeError("DATABASE_URL is not set")
    if url.startswith("postgres://"):
        return "postgresql+psycopg://" + url.removeprefix("postgres://")
    if url.startswith("postgresql://"):
        return "postgresql+psycopg://" + url.removeprefix("postgresql://")
    return url


def jwt_secret() -> str:
    secret = os.getenv("JWT_SECRET", "").strip()
    if len(secret) < 32:
        raise RuntimeError("JWT_SECRET must contain at least 32 characters")
    return secret


def jwt_access_token_minutes() -> int:
    raw_value = os.getenv("JWT_ACCESS_TOKEN_MINUTES", "60").strip()
    try:
        return max(1, int(raw_value))
    except ValueError:
        return 60


def chroma_persist_dir() -> str:
    return os.getenv("CHROMA_PERSIST_DIR", "chroma_db").strip() or "chroma_db"


def cors_allow_origins() -> list[str]:
    raw_value = os.getenv("CORS_ALLOW_ORIGINS", "").strip()
    if not raw_value:
        return []
    return [origin.strip() for origin in raw_value.split(",") if origin.strip()]
