"""可插拔、权限优先的内部知识检索服务。"""

from __future__ import annotations

import asyncio
import re
import threading
import unicodedata
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Literal, Protocol

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.utils.exceptions import ConfigurationError


_SAFE_LABEL = re.compile(r"^[A-Za-z0-9*:_./-]+$")
_ASCII_TERM = re.compile(r"[a-z0-9][a-z0-9:_./-]*")


class KnowledgeChunk(BaseModel):
    """知识索引中的单个、可独立理解的文档片段。"""

    model_config = ConfigDict(extra="forbid", frozen=True, str_strip_whitespace=True)

    document_id: str = Field(..., min_length=1, max_length=256)
    title: str = Field(..., min_length=1, max_length=300)
    section: str = Field(..., min_length=1, max_length=300)
    content: str = Field(..., min_length=1, max_length=8_000)
    document_type: str = Field(..., min_length=1, max_length=64)
    version: str = Field(..., min_length=1, max_length=64)
    effective_from: date | None = None
    effective_to: date | None = None
    visibility_roles: tuple[str, ...] = Field(default_factory=tuple, max_length=64)
    product_model: str | None = Field(default=None, min_length=1, max_length=128)
    source_id: str = Field(..., min_length=1, max_length=512)
    source_uri: str | None = Field(default=None, min_length=1, max_length=2_048)
    keywords: tuple[str, ...] = Field(default_factory=tuple, max_length=64)
    is_current: bool = True

    @field_validator("visibility_roles")
    @classmethod
    def validate_visibility_roles(cls, roles: tuple[str, ...]) -> tuple[str, ...]:
        """角色元数据必须唯一且使用稳定代码。"""
        if len(set(roles)) != len(roles):
            raise ValueError("visibility_roles must be unique")
        if any(not _SAFE_LABEL.fullmatch(role) for role in roles):
            raise ValueError("visibility_roles contains an invalid role code")
        return roles

    @field_validator("keywords")
    @classmethod
    def validate_keywords(cls, keywords: tuple[str, ...]) -> tuple[str, ...]:
        """关键词不得包含控制字符。"""
        if len(set(keywords)) != len(keywords):
            raise ValueError("keywords must be unique")
        if any(any(char in keyword for char in ("\r", "\n", "\x00")) for keyword in keywords):
            raise ValueError("keywords must be single-line values")
        return keywords

    @model_validator(mode="after")
    def validate_effective_range(self) -> "KnowledgeChunk":
        """有效期必须正向。"""
        if (
            self.effective_from is not None
            and self.effective_to is not None
            and self.effective_from > self.effective_to
        ):
            raise ValueError("effective_from must not be after effective_to")
        return self


@dataclass(frozen=True, slots=True)
class KnowledgeSearchResult:
    """经过权限、版本和有效期过滤的检索结果。"""

    chunk: KnowledgeChunk
    score: float

    def model_payload(self) -> dict[str, object]:
        """返回给模型的有界证据，不暴露内部存储 URI。"""
        return {
            "title": self.chunk.title,
            "section": self.chunk.section,
            "content": self.chunk.content,
            "document_type": self.chunk.document_type,
            "version": self.chunk.version,
            "source_id": self.chunk.source_id,
            "score": round(self.score, 4),
        }

    def citation_payload(self) -> dict[str, object]:
        """返回适合前端展示的引用元数据。"""
        return {
            "title": self.chunk.title,
            "section": self.chunk.section,
            "version": self.chunk.version,
            "source_id": self.chunk.source_id,
            "score": round(self.score, 4),
        }


class KnowledgeRetriever(Protocol):
    """知识后端统一接口；pgvector/OpenSearch 实现须保持该安全契约。"""

    async def search(
        self,
        query: str,
        *,
        role_code: str,
        document_type: str | None = None,
        product_model: str | None = None,
        top_k: int = 5,
        as_of: date | None = None,
    ) -> list[KnowledgeSearchResult]: ...


class InMemoryKnowledgeRetriever:
    """共享排序实现，也用于测试和小型预加载索引。"""

    def __init__(self, chunks: Sequence[KnowledgeChunk]) -> None:
        self._chunks = tuple(chunks)

    async def search(
        self,
        query: str,
        *,
        role_code: str,
        document_type: str | None = None,
        product_model: str | None = None,
        top_k: int = 5,
        as_of: date | None = None,
    ) -> list[KnowledgeSearchResult]:
        return _rank_chunks(
            self._chunks,
            query=query,
            role_code=role_code,
            document_type=document_type,
            product_model=product_model,
            top_k=top_k,
            as_of=as_of or date.today(),
        )


class JsonlKnowledgeRetriever:
    """本地 JSONL 索引后端，适合第一批 SOP 和故障码灰度接入。"""

    def __init__(self, index_path: Path, *, max_index_bytes: int) -> None:
        self._index_path = index_path.resolve()
        self._max_index_bytes = max_index_bytes
        self._cache_key: tuple[int, int] | None = None
        self._chunks: tuple[KnowledgeChunk, ...] = ()
        self._lock = threading.Lock()

    def validate(self) -> None:
        """启动时解析一次索引，避免配置错误延迟到首个用户请求。"""
        self._load_chunks()

    async def search(
        self,
        query: str,
        *,
        role_code: str,
        document_type: str | None = None,
        product_model: str | None = None,
        top_k: int = 5,
        as_of: date | None = None,
    ) -> list[KnowledgeSearchResult]:
        chunks = await asyncio.to_thread(self._load_chunks)
        return _rank_chunks(
            chunks,
            query=query,
            role_code=role_code,
            document_type=document_type,
            product_model=product_model,
            top_k=top_k,
            as_of=as_of or date.today(),
        )

    def _load_chunks(self) -> tuple[KnowledgeChunk, ...]:
        try:
            stat = self._index_path.stat()
        except OSError as exc:
            raise ConfigurationError("Knowledge index is unavailable") from exc
        if not self._index_path.is_file():
            raise ConfigurationError("Knowledge index is not a regular file")
        if stat.st_size > self._max_index_bytes:
            raise ConfigurationError("Knowledge index exceeds the configured size limit")

        cache_key = (stat.st_mtime_ns, stat.st_size)
        with self._lock:
            if cache_key == self._cache_key:
                return self._chunks
            parsed: list[KnowledgeChunk] = []
            try:
                with self._index_path.open("r", encoding="utf-8") as source:
                    for line_number, raw_line in enumerate(source, start=1):
                        line = raw_line.strip()
                        if not line:
                            continue
                        try:
                            parsed.append(KnowledgeChunk.model_validate_json(line))
                        except Exception as exc:
                            raise ConfigurationError(
                                f"Knowledge index contains an invalid record at line {line_number}"
                            ) from exc
            except UnicodeError as exc:
                raise ConfigurationError("Knowledge index must be UTF-8 encoded") from exc
            if not parsed:
                raise ConfigurationError("Knowledge index contains no records")
            source_ids = [chunk.source_id for chunk in parsed]
            if len(set(source_ids)) != len(source_ids):
                raise ConfigurationError("Knowledge index contains duplicate source_id values")
            self._chunks = tuple(parsed)
            self._cache_key = cache_key
            return self._chunks


def create_knowledge_retriever(
    *,
    backend: Literal["disabled", "jsonl"],
    index_path: Path | None,
    max_index_bytes: int,
) -> KnowledgeRetriever | None:
    """根据配置创建知识后端；启用但配置不完整时失败关闭。"""
    if backend == "disabled":
        return None
    if backend == "jsonl":
        if index_path is None:
            raise ConfigurationError("KNOWLEDGE_INDEX_PATH is required for jsonl backend")
        retriever = JsonlKnowledgeRetriever(
            index_path,
            max_index_bytes=max_index_bytes,
        )
        retriever.validate()
        return retriever
    raise ConfigurationError("Unsupported knowledge backend")


def _rank_chunks(
    chunks: Sequence[KnowledgeChunk],
    *,
    query: str,
    role_code: str,
    document_type: str | None,
    product_model: str | None,
    top_k: int,
    as_of: date,
) -> list[KnowledgeSearchResult]:
    normalized_query = _normalize_text(query)
    query_terms = _terms(normalized_query)
    if not normalized_query or not query_terms:
        return []

    visible = [
        chunk
        for chunk in chunks
        if _is_visible(chunk, role_code=role_code, as_of=as_of)
        and (document_type is None or chunk.document_type == document_type)
        and (product_model is None or chunk.product_model == product_model)
    ]
    current = _latest_current_versions(visible)
    ranked: list[KnowledgeSearchResult] = []
    for chunk in current:
        score = _score_chunk(chunk, normalized_query, query_terms)
        if score > 0:
            ranked.append(KnowledgeSearchResult(chunk=chunk, score=score))
    ranked.sort(key=lambda item: (-item.score, item.chunk.source_id))
    return ranked[: max(1, min(top_k, 20))]


def _is_visible(chunk: KnowledgeChunk, *, role_code: str, as_of: date) -> bool:
    if not chunk.is_current:
        return False
    if chunk.effective_from is not None and chunk.effective_from > as_of:
        return False
    if chunk.effective_to is not None and chunk.effective_to < as_of:
        return False
    if not role_code:
        return False
    if not chunk.visibility_roles:
        return True
    return role_code in chunk.visibility_roles or "*" in chunk.visibility_roles


def _latest_current_versions(
    chunks: Sequence[KnowledgeChunk],
) -> list[KnowledgeChunk]:
    selected: dict[tuple[str, str, str | None], KnowledgeChunk] = {}
    for chunk in chunks:
        key = (chunk.document_id, chunk.section, chunk.product_model)
        existing = selected.get(key)
        if existing is None or _version_key(chunk) > _version_key(existing):
            selected[key] = chunk
    return list(selected.values())


def _version_key(chunk: KnowledgeChunk) -> tuple[date, str]:
    return (chunk.effective_from or date.min, chunk.version)


def _score_chunk(
    chunk: KnowledgeChunk,
    normalized_query: str,
    query_terms: set[str],
) -> float:
    fields = (
        (_normalize_text(chunk.title), 4.0),
        (_normalize_text(chunk.section), 3.0),
        (_normalize_text(" ".join(chunk.keywords)), 2.5),
        (_normalize_text(chunk.content), 1.0),
    )
    score = 0.0
    for text, weight in fields:
        if not text:
            continue
        overlap = len(query_terms & _terms(text))
        score += weight * overlap / len(query_terms)
        if normalized_query in text:
            score += weight
    return score


def _normalize_text(value: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def _terms(value: str) -> set[str]:
    terms = set(_ASCII_TERM.findall(value))
    chinese = "".join(char for char in value if "\u4e00" <= char <= "\u9fff")
    if len(chinese) == 1:
        terms.add(chinese)
    else:
        terms.update(chinese[index : index + 2] for index in range(len(chinese) - 1))
    return terms
