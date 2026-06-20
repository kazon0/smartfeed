from functools import lru_cache

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from app.db.session import create_database_engine
from app.db.session import create_session_factory


@lru_cache(maxsize=1)
def get_engine() -> Engine:
    return create_database_engine()


def get_db():
    session_factory = create_session_factory(get_engine())
    session: Session = session_factory()
    try:
        yield session
    finally:
        session.close()
