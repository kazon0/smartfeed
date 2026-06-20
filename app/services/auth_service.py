from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.user import User
from app.security.passwords import hash_password
from app.security.passwords import verify_password


class EmailAlreadyRegisteredError(Exception):
    pass


class AuthService:
    def register(
        self,
        db: Session,
        *,
        email: str,
        password: str,
        display_name: str = "",
    ) -> User:
        normalized_email = self.normalize_email(email)
        existing_user = db.scalar(select(User).where(User.email == normalized_email))
        if existing_user is not None:
            raise EmailAlreadyRegisteredError

        user = User(
            email=normalized_email,
            password_hash=hash_password(password),
            display_name=display_name.strip(),
        )
        db.add(user)
        try:
            db.commit()
        except IntegrityError as exc:
            db.rollback()
            raise EmailAlreadyRegisteredError from exc
        db.refresh(user)
        return user

    def authenticate(self, db: Session, *, email: str, password: str) -> User | None:
        normalized_email = self.normalize_email(email)
        user = db.scalar(select(User).where(User.email == normalized_email))
        if user is None or not verify_password(password, user.password_hash):
            return None
        return user

    def get_user(self, db: Session, user_id: str) -> User | None:
        return db.get(User, user_id)

    def normalize_email(self, email: str) -> str:
        return email.strip().lower()
