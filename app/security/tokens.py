from datetime import datetime
from datetime import timedelta
from datetime import timezone

import jwt

from app.core.config import jwt_access_token_minutes
from app.core.config import jwt_secret

JWT_ALGORITHM = "HS256"


def create_access_token(user_id: str) -> str:
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(minutes=jwt_access_token_minutes())
    return jwt.encode(
        {
            "sub": user_id,
            "iat": now,
            "exp": expires_at,
        },
        jwt_secret(),
        algorithm=JWT_ALGORITHM,
    )


def decode_access_token(token: str) -> str | None:
    try:
        payload = jwt.decode(
            token,
            jwt_secret(),
            algorithms=[JWT_ALGORITHM],
        )
    except jwt.InvalidTokenError:
        return None

    user_id = payload.get("sub")
    if not isinstance(user_id, str) or not user_id:
        return None
    return user_id
