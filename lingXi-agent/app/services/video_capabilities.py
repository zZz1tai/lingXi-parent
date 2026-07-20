"""Deterministic video-model capability rules shared by analysis and transport."""

from __future__ import annotations


MAX_VIDEO_DURATION_MS = 15_000
VIDEO_PROMPT_LIMITS = {
    "2.5": 1500,
    "2.6": 1500,
}


def get_model_version(model: str) -> str:
    """Return the capability family encoded in a video model name."""

    model_lower = (model or "").lower()
    if "happyhorse" in model_lower:
        return "happyhorse"
    if "2.6" in model_lower:
        return "2.6"
    if "2.5" in model_lower:
        return "2.5"
    if "2.1" in model_lower:
        if "turbo" in model_lower:
            return "2.1-turbo"
        return "2.1"
    if "2.2" in model_lower:
        return "2.2"
    return "unknown"


def normalize_video_duration_ms(duration_ms: int, model: str) -> int:
    """Normalize a requested duration to a value accepted by ``model``."""

    bounded_ms = max(1000, min(MAX_VIDEO_DURATION_MS, int(duration_ms)))
    # Match Java Math.round for positive millisecond values rather than
    # Python's banker's rounding.
    rounded_seconds = (bounded_ms + 500) // 1000
    version = get_model_version(model)

    if version == "2.1-turbo":
        return max(3, min(5, rounded_seconds)) * 1000
    if version in ("2.1", "2.2"):
        return 5000
    if version == "2.5":
        return 5000 if rounded_seconds <= 7 else 10000
    if version == "2.6":
        return max(2, min(10, rounded_seconds)) * 1000
    if version == "happyhorse":
        return max(3, min(15, rounded_seconds)) * 1000
    return bounded_ms


def get_video_prompt_limit(model: str) -> int:
    """Return the provider's positive-prompt character limit."""

    if get_model_version(model) == "happyhorse":
        # HappyHorse allows 2500 Chinese or 5000 non-Chinese characters.
        # Use the conservative bound because this helper has no prompt text.
        return 2500
    return VIDEO_PROMPT_LIMITS.get(get_model_version(model), 800)


def should_include_duration(model: str) -> bool:
    """Whether the provider request accepts an explicit duration field."""

    return get_model_version(model) in ("2.1-turbo", "2.5", "2.6", "happyhorse")


__all__ = [
    "MAX_VIDEO_DURATION_MS",
    "get_model_version",
    "get_video_prompt_limit",
    "normalize_video_duration_ms",
    "should_include_duration",
]
