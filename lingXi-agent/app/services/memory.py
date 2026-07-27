"""受控的跨会话用户偏好记忆。"""

from __future__ import annotations

import hashlib
import hmac
import re
from datetime import UTC, datetime
from typing import Literal

from langgraph.store.base import BaseStore
from pydantic import BaseModel, ConfigDict, Field, model_validator


PreferenceName = Literal["answer_length", "answer_structure", "number_format"]

_ALLOWED_VALUES: dict[str, frozenset[str]] = {
    "answer_length": frozenset({"short", "balanced", "detailed"}),
    "answer_structure": frozenset({"conclusion_first", "natural"}),
    "number_format": frozenset({"two_decimals", "adaptive"}),
}


class MemoryPreference(BaseModel):
    """模型和用户界面可见的规范化回答偏好。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    preference: PreferenceName
    value: str = Field(..., min_length=1, max_length=64)
    updated_at: str = Field(..., min_length=1, max_length=64)

    @model_validator(mode="after")
    def validate_value(self) -> "MemoryPreference":
        if self.value not in _ALLOWED_VALUES[self.preference]:
            raise ValueError("unsupported preference value")
        return self


class _StoredPreference(BaseModel):
    """Store 中的严格版本化记录；不保存用户原话。"""

    model_config = ConfigDict(extra="forbid")

    schema_version: Literal[1] = 1
    memory_type: Literal["preference"] = "preference"
    preference: PreferenceName
    value: str = Field(..., min_length=1, max_length=64)
    confidence: float = Field(..., ge=0.0, le=1.0)
    source: Literal["explicit_user_statement", "user_settings"]
    updated_at: str = Field(..., min_length=1, max_length=64)

    @model_validator(mode="after")
    def validate_value(self) -> "_StoredPreference":
        if self.value not in _ALLOWED_VALUES[self.preference]:
            raise ValueError("unsupported preference value")
        return self


class LongTermMemoryService:
    """在按用户隔离的命名空间中读写少量规范化偏好。"""

    def __init__(
        self,
        store: BaseStore,
        *,
        namespace_secret: str,
        max_recall: int = 5,
        write_confidence: float = 0.9,
    ) -> None:
        if len(namespace_secret.encode("utf-8")) < 32:
            raise ValueError("memory namespace secret must contain at least 32 bytes")
        self._store = store
        self._secret = namespace_secret.encode("utf-8")
        self._max_recall = max(1, min(max_recall, 20))
        self._write_confidence = max(0.0, min(write_confidence, 1.0))

    async def recall_preferences(self, user_id: str) -> tuple[MemoryPreference, ...]:
        """读取有界且通过严格 Schema 校验的用户偏好。"""

        namespace = self._namespace(user_id)
        items = await self._store.asearch(namespace, limit=self._max_recall)
        preferences: list[MemoryPreference] = []
        for item in items:
            try:
                stored = _StoredPreference.model_validate(item.value)
            except ValueError:
                continue
            preferences.append(
                MemoryPreference(
                    preference=stored.preference,
                    value=stored.value,
                    updated_at=stored.updated_at,
                )
            )
        preferences.sort(key=lambda item: item.preference)
        return tuple(preferences[: self._max_recall])

    async def capture_explicit_preferences(
        self,
        user_id: str,
        message: str,
    ) -> tuple[MemoryPreference, ...]:
        """只从用户明确表达中提取枚举化偏好，绝不保存原始消息。"""

        candidates = extract_explicit_preferences(message)
        saved: list[MemoryPreference] = []
        for preference, value, confidence in candidates:
            if confidence < self._write_confidence:
                continue
            saved.append(
                await self.upsert_preference(
                    user_id,
                    preference=preference,
                    value=value,
                    confidence=confidence,
                    source="explicit_user_statement",
                )
            )
        return tuple(saved)

    async def upsert_preference(
        self,
        user_id: str,
        *,
        preference: PreferenceName,
        value: str,
        confidence: float = 1.0,
        source: Literal["explicit_user_statement", "user_settings"] = "user_settings",
    ) -> MemoryPreference:
        """覆盖一个确定键的偏好，避免冲突记忆并保持幂等。"""

        now = datetime.now(UTC).isoformat()
        stored = _StoredPreference(
            preference=preference,
            value=value,
            confidence=confidence,
            source=source,
            updated_at=now,
        )
        await self._store.aput(
            self._namespace(user_id),
            preference,
            stored.model_dump(mode="json"),
            index=False,
        )
        return MemoryPreference(
            preference=stored.preference,
            value=stored.value,
            updated_at=stored.updated_at,
        )

    async def clear_user(self, user_id: str) -> int:
        """幂等删除当前用户所有允许类型的长期记忆。"""

        namespace = self._namespace(user_id)
        items = await self._store.asearch(namespace, limit=100)
        for item in items:
            await self._store.adelete(namespace, item.key)
        return len(items)

    def _namespace(self, user_id: str) -> tuple[str, ...]:
        normalized = user_id.strip()
        if not normalized or len(normalized) > 128:
            raise ValueError("valid user_id is required")
        digest = hmac.new(
            self._secret,
            normalized.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()
        return ("lingxi", "users", digest, "preferences")


def validate_preference_value(preference: str, value: str) -> None:
    """供请求 Schema 复用的偏好值白名单校验。"""

    if preference not in _ALLOWED_VALUES or value not in _ALLOWED_VALUES[preference]:
        raise ValueError("unsupported preference or value")


def extract_explicit_preferences(
    message: str,
) -> tuple[tuple[PreferenceName, str, float], ...]:
    """确定性识别少量高置信偏好表达，不调用模型推断用户属性。"""

    text = re.sub(r"\s+", "", message.casefold())[:2_000]
    if not text:
        return ()

    extracted: dict[PreferenceName, tuple[str, float]] = {}
    preference_prefix = r"(?:以后|今后|后续|从现在起|请记住|帮我记住)"

    if re.search(
        preference_prefix + r".{0,10}(?:回答|回复).{0,8}(?:不要太简短|详细|展开一点)",
        text,
    ):
        extracted["answer_length"] = ("detailed", 1.0)
    elif re.search(
        preference_prefix + r".{0,10}(?:回答|回复).{0,8}(?:简短|简洁|短一点)",
        text,
    ):
        extracted["answer_length"] = ("short", 1.0)

    if re.search(
        preference_prefix + r".{0,12}(?:先说结论|结论优先|先给结论)",
        text,
    ):
        extracted["answer_structure"] = ("conclusion_first", 1.0)

    if re.search(
        preference_prefix + r".{0,12}(?:数字|小数).{0,8}(?:保留)?两位小数",
        text,
    ):
        extracted["number_format"] = ("two_decimals", 1.0)

    return tuple(
        (preference, value, confidence)
        for preference, (value, confidence) in extracted.items()
    )
