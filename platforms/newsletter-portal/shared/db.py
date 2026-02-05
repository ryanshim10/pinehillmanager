from sqlmodel import SQLModel, create_engine, Session
from contextlib import contextmanager
from typing import Generator


def make_engine(database_url: str, echo: bool = False):
    """Create database engine with connection pooling"""
    return create_engine(
        database_url, 
        pool_pre_ping=True,
        pool_size=5,
        max_overflow=10,
        pool_recycle=3600,
        echo=echo
    )


def init_db(engine):
    """Initialize database tables"""
    SQLModel.metadata.create_all(engine)


def drop_db(engine):
    """Drop all tables (use with caution)"""
    SQLModel.metadata.drop_all(engine)


@contextmanager
def get_session_context(engine) -> Generator[Session, None, None]:
    """Context manager for database sessions"""
    session = Session(engine)
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()
