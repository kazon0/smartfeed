from urllib.parse import urlparse

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.article import Article


class ArticleService:
    def upsert(
        self,
        db: Session,
        *,
        owner_id: str,
        url: str,
        title: str,
        topic: str,
        summary: str,
        chunk_count: int,
    ) -> Article:
        article = self.get(db, owner_id=owner_id, url=url)
        if article is None:
            article = Article(owner_id=owner_id, url=url)
            db.add(article)

        article.title = title
        article.topic = topic
        article.summary = summary
        article.chunk_count = chunk_count
        db.commit()
        db.refresh(article)
        return article

    def get(self, db: Session, *, owner_id: str, url: str) -> Article | None:
        return db.scalar(
            select(Article).where(
                Article.owner_id == owner_id,
                Article.url == url,
            )
        )

    def list(self, db: Session, *, owner_id: str) -> list[Article]:
        return list(
            db.scalars(
                select(Article)
                .where(Article.owner_id == owner_id)
                .order_by(Article.updated_at.desc())
            )
        )

    def delete(self, db: Session, *, owner_id: str, url: str) -> bool:
        article = self.get(db, owner_id=owner_id, url=url)
        if article is None:
            return False
        db.delete(article)
        db.commit()
        return True

    def to_list_item(self, article: Article) -> dict:
        return {
            "url": article.url,
            "title": article.title,
            "domain": self.domain_from_url(article.url),
            "chunk_count": article.chunk_count,
            "topic": article.topic,
        }

    def status(self, article: Article | None, *, url: str) -> dict:
        if article is None:
            return {
                "exists": False,
                "url": url,
                "title": "",
                "domain": self.domain_from_url(url),
                "topic": "",
                "chunk_count": 0,
            }
        return {
            "exists": True,
            **self.to_list_item(article),
        }

    def domain_from_url(self, url: str) -> str:
        domain = urlparse(url).netloc.lower()
        return domain.removeprefix("www.") or "unknown"
