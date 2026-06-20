import os
from datetime import datetime
from datetime import timedelta
from datetime import timezone

import jwt
from dotenv import load_dotenv

load_dotenv()

JWT_ALGORITHM = "HS256"


def create_access_token(user_id: str) -> str:
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(minutes=_access_token_minutes())
    return jwt.encode(
        {
            "sub": user_id,
            "iat": now,
            "exp": expires_at,
        },
        _jwt_secret(),
        algorithm=JWT_ALGORITHM,
    )


def decode_access_token(token: str) -> str | None:
    try:
        payload = jwt.decode(
            token,
            _jwt_secret(),
            algorithms=[JWT_ALGORITHM],
        )
    except jwt.InvalidTokenError:
        return None

    user_id = payload.get("sub")
    if not isinstance(user_id, str) or not user_id:
        return None
    return user_id


def _jwt_secret() -> str:
    secret = os.getenv("JWT_SECRET", "").strip()
    if len(secret) < 32:
        raise RuntimeError("JWT_SECRET must contain at least 32 characters")
    return secret


def _access_token_minutes() -> int:
    raw_value = os.getenv("JWT_ACCESS_TOKEN_MINUTES", "60").strip()
    try:
        return max(1, int(raw_value))
    except ValueError:
        return 60
