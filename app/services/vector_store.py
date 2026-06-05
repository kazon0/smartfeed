import hashlib
import uuid
from typing import Any

import chromadb


class VectorStoreService:
    COLLECTION_NAME = "smartfeed"
    PERSIST_DIR = "chroma_db"
    EMBEDDING_DIMENSION = 384
    _embedding_model = None
    _embedding_model_loaded = False

    def __init__(self) -> None:
        self.client = chromadb.PersistentClient(path=self.PERSIST_DIR)
        self.collection = self.client.get_or_create_collection(
            name=self.COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
        self.embedding_model = self._load_embedding_model()

    def add_chunks(self, chunks: list[str], metadata: dict) -> int:
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

    def query(self, text: str, top_k: int = 5) -> list[dict[str, Any]]:
        query_text = text.strip()
        if not query_text:
            return []

        query_embedding = self._embed([query_text])[0]
        result = self.collection.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
            include=["documents", "metadatas", "distances"],
        )

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
