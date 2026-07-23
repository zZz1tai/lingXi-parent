"""分析和传输共享的确定性视频模型能力规则。"""

from __future__ import annotations


MAX_VIDEO_DURATION_MS = 15_000
VIDEO_PROMPT_LIMITS = {
    "2.5": 1500,
    "2.6": 1500,
}


def get_model_version(model: str) -> str:
    """返回视频模型名称中编码的能力族。"""

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
    """将请求的时长规范化为 ``model`` 接受的值。"""

    bounded_ms = max(1000, min(MAX_VIDEO_DURATION_MS, int(duration_ms)))
    # 正毫秒值采用与 Java Math.round 一致的四舍五入规则，
    # 避免 Python 银行家舍入造成跨服务结果不一致。
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
    """返回提供商的正向提示词字符限制。"""

    if get_model_version(model) == "happyhorse":
        # HappyHorse 对中文和非中文提示词分别允许 2500 与 5000 个字符。
        # 当前辅助函数拿不到提示词内容，因此采用更保守的上限。
        return 2500
    return VIDEO_PROMPT_LIMITS.get(get_model_version(model), 800)


def should_include_duration(model: str) -> bool:
    """判断提供商请求是否接受显式时长字段。"""

    return get_model_version(model) in ("2.1-turbo", "2.5", "2.6", "happyhorse")


__all__ = [
    "MAX_VIDEO_DURATION_MS",
    "get_model_version",
    "get_video_prompt_limit",
    "normalize_video_duration_ms",
    "should_include_duration",
]
