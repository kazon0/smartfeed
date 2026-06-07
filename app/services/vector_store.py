import hashlib
import uuid
from collections import defaultdict
from urllib.parse import urlparse
from typing import Any

import chromadb


class VectorStoreService:
    COLLECTION_NAME = "smartfeed"
    PERSIST_DIR = "chroma_db"
    EMBEDDING_DIMENSION = 384
    TOPIC_KEYWORDS = {
        "科技": [
            "ai",
            "人工智能",
            "算法",
            "编程",
            "代码",
            "软件",
            "数据",
            "模型",
            "rag",
            "fastapi",
            "android",
            "kotlin",
            "python",
            "开发",
            "技术",
        ],
        "学习": [
            "学习",
            "知识",
            "课程",
            "考试",
            "高考",
            "教育",
            "笔记",
            "教程",
            "方法",
            "总结",
            "复习",
        ],
        "健康": [
            "健康",
            "医生",
            "疾病",
            "中毒",
            "症状",
            "治疗",
            "医院",
            "睡眠",
            "心理",
            "饮食",
        ],
        "职业": [
            "职业",
            "实习",
            "面试",
            "招聘",
            "简历",
            "工作",
            "岗位",
            "职场",
            "薪资",
        ],
        "财经": [
            "财经",
            "股票",
            "基金",
            "投资",
            "价格",
            "汇率",
            "美元",
            "经济",
            "市场",
            "公司",
        ],
        "生活": [
            "生活",
            "旅行",
            "美食",
            "家庭",
            "情感",
            "娱乐",
            "消费",
            "家长",
            "孩子",
        ],
        "新闻": [
            "新闻",
            "通报",
            "发布",
            "记者",
            "官方",
            "事件",
            "社会",
            "政策",
            "最新",
        ],
    }
    _embedding_model = None
    _embedding_model_loaded = False

    def __init__(self) -> None:
        self.client = chromadb.PersistentClient(path=self.PERSIST_DIR)
        self.collection = self.client.get_or_create_collection(
            name=self.COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
        self.embedding_model = self._load_embedding_model()

    def add_chunks(
        self,
        chunks: list[str],
        metadata: dict,
        chunk_metadata: list[dict] | None = None,
    ) -> int:
        clean_chunks = [chunk.strip() for chunk in chunks if chunk.strip()]
        if not clean_chunks:
            return 0

        embeddings = self._embed(clean_chunks)
        ids = [
            self._build_chunk_id(chunk, index, metadata)
            for index, chunk in enumerate(clean_chunks)
        ]
        metadatas = [
            {
                **self._normalize_metadata(metadata),
                **self._normalize_metadata(self._chunk_metadata_at(chunk_metadata, index)),
                "chunk_index": index,
            }
            for index in range(len(clean_chunks))
        ]

        self.collection.upsert(
            ids=ids,
            documents=clean_chunks,
            embeddings=embeddings,
            metadatas=metadatas,
        )
        return len(clean_chunks)

    def _chunk_metadata_at(self, chunk_metadata: list[dict] | None, index: int) -> dict:
        if not chunk_metadata or index >= len(chunk_metadata):
            return {}
        return chunk_metadata[index] or {}

    def delete_by_url(self, url: str) -> None:
        if not url:
            return

        result = self.collection.get(where={"url": url})
        ids = result.get("ids", [])
        if ids:
            self.collection.delete(ids=ids)

    def list_sources(self, limit: int = 10) -> list[dict[str, Any]]:
        result = self.collection.get(include=["metadatas"])
        metadatas = result.get("metadatas", [])
        sources = []
        seen_urls = set()

        for metadata in metadatas:
            url = metadata.get("url", "")
            if not url or url in seen_urls:
                continue

            seen_urls.add(url)
            sources.append(
                {
                    "url": url,
                    "title": metadata.get("title", ""),
                    "chunk_index": None,
                    "score": None,
                    "content_preview": "",
                }
            )

            if len(sources) >= limit:
                break

        return sources

    def get_chunks_by_url(self, url: str) -> list[dict[str, Any]]:
        if not url:
            return []

        result = self.collection.get(
            where={"url": url},
            include=["documents", "metadatas"],
        )
        documents = result.get("documents", [])
        metadatas = result.get("metadatas", [])
        chunks = [
            {
                "content": document,
                "metadata": metadata,
                "score": 1.0,
            }
            for document, metadata in zip(documents, metadatas)
        ]
        return sorted(
            chunks,
            key=lambda chunk: chunk.get("metadata", {}).get("chunk_index", 0),
        )

    def get_all_chunks(self, limit: int = 1000) -> list[dict[str, Any]]:
        result = self.collection.get(
            include=["documents", "metadatas"],
            limit=limit,
        )
        documents = result.get("documents", [])
        metadatas = result.get("metadatas", [])
        return [
            {
                "content": document,
                "metadata": metadata,
                "score": 1.0,
            }
            for document, metadata in zip(documents, metadatas)
        ]

    def stats(self, limit: int = 5000) -> dict[str, Any]:
        chunks = self.get_all_chunks(limit=limit)
        total_chunks = len(chunks)
        articles: dict[str, dict[str, Any]] = {}
        domains: dict[str, int] = defaultdict(int)
        topics: dict[str, int] = defaultdict(int)

        for chunk in chunks:
            content = chunk.get("content", "") or ""
            metadata = chunk.get("metadata", {}) or {}
            url = metadata.get("url", "") or ""
            title = metadata.get("title", "") or url or "Untitled"
            domain = self._domain_from_url(url)
            topic = self._classify_topic(f"{title}\n{content}")

            if url not in articles:
                articles[url] = {
                    "url": url,
                    "title": title,
                    "domain": domain,
                    "chunk_count": 0,
                    "percentage": 0.0,
                }

            articles[url]["chunk_count"] += 1
            domains[domain] += 1
            topics[topic] += 1

        article_items = sorted(
            articles.values(),
            key=lambda item: item["chunk_count"],
            reverse=True,
        )
        domain_items = [
            {
                "domain": domain,
                "chunk_count": chunk_count,
                "percentage": self._percentage(chunk_count, total_chunks),
            }
            for domain, chunk_count in sorted(
                domains.items(),
                key=lambda item: item[1],
                reverse=True,
            )
        ]
        topic_items = [
            {
                "topic": topic,
                "chunk_count": chunk_count,
                "percentage": self._percentage(chunk_count, total_chunks),
            }
            for topic, chunk_count in sorted(
                topics.items(),
                key=lambda item: item[1],
                reverse=True,
            )
        ]

        for article in article_items:
            article["percentage"] = self._percentage(article["chunk_count"], total_chunks)

        return {
            "total_chunks": total_chunks,
            "total_articles": len(article_items),
            "topics": topic_items,
            "domains": domain_items,
            "articles": article_items,
        }

    def query(
        self,
        text: str,
        top_k: int = 5,
        metadata_filter: dict | None = None,
    ) -> list[dict[str, Any]]:
        query_text = text.strip()
        if not query_text:
            return []

        query_embedding = self._embed([query_text])[0]
        query_args = {
            "query_embeddings": [query_embedding],
            "n_results": top_k,
            "include": ["documents", "metadatas", "distances"],
        }
        if metadata_filter:
            query_args["where"] = metadata_filter

        result = self.collection.query(**query_args)

        documents = result.get("documents", [[]])[0]
        metadatas = result.get("metadatas", [[]])[0]
        distances = result.get("distances", [[]])[0]

        results = [
            {
                "content": document,
                "metadata": metadata,
                "score": self._distance_to_score(distance),
            }
            for document, metadata, distance in zip(documents, metadatas, distances)
        ]
        return sorted(results, key=lambda item: item["score"], reverse=True)

    def _load_embedding_model(self):
        if self.__class__._embedding_model_loaded:
            return self.__class__._embedding_model

        self.__class__._embedding_model_loaded = True
        try:
            from sentence_transformers import SentenceTransformer

            self.__class__._embedding_model = SentenceTransformer("all-MiniLM-L6-v2")
        except Exception:
            self.__class__._embedding_model = None

        return self.__class__._embedding_model

    def _embed(self, texts: list[str]) -> list[list[float]]:
        if self.embedding_model is not None:
            embeddings = self.embedding_model.encode(texts)
            return [embedding.tolist() for embedding in embeddings]

        return [self._mock_embedding(text) for text in texts]

    def _mock_embedding(self, text: str) -> list[float]:
        vector = []
        counter = 0

        while len(vector) < self.EMBEDDING_DIMENSION:
            digest = hashlib.sha256(f"{text}:{counter}".encode("utf-8")).digest()
            vector.extend((byte / 255.0) for byte in digest)
            counter += 1

        return vector[: self.EMBEDDING_DIMENSION]

    def _distance_to_score(self, distance: float) -> float:
        return float(1 - distance)

    def _build_chunk_id(self, chunk: str, index: int, metadata: dict) -> str:
        source = metadata.get("url", "")
        digest = hashlib.sha256(f"{source}:{index}:{chunk}".encode("utf-8")).hexdigest()
        return str(uuid.uuid5(uuid.NAMESPACE_URL, digest))

    def _normalize_metadata(self, metadata: dict) -> dict:
        normalized = {}
        for key, value in metadata.items():
            if value is None:
                normalized[key] = ""
            elif isinstance(value, (str, int, float, bool)):
                normalized[key] = value
            else:
                normalized[key] = str(value)
        return normalized

    def _domain_from_url(self, url: str) -> str:
        domain = urlparse(url).netloc.lower()
        return domain.removeprefix("www.") or "unknown"

    def _percentage(self, value: int, total: int) -> float:
        if total <= 0:
            return 0.0
        return round(value * 100 / total, 2)

    def _classify_topic(self, text: str) -> str:
        lowered = text.lower()
        scores = {
            topic: sum(1 for keyword in keywords if keyword.lower() in lowered)
            for topic, keywords in self.TOPIC_KEYWORDS.items()
        }
        topic, score = max(scores.items(), key=lambda item: item[1])
        if score <= 0:
            return "其他"
        return topic
