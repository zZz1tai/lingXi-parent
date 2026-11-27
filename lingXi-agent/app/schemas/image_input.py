"""模型图片输入：保留远程 URL，并支持无需对象存储的本地图片数据。"""

import base64
import re
from typing import Annotated

from pydantic import AfterValidator, StringConstraints

MAX_IMAGE_BYTES = 20 * 1024 * 1024
MAX_IMAGE_INPUT_CHARS = ((MAX_IMAGE_BYTES + 2) // 3) * 4 + 64


def validate_image_input(value: str) -> str:
    if value.startswith(("https://", "http://")):
        if len(value) > 4096 or any(char.isspace() for char in value):
            raise ValueError("image URL is invalid or too long")
        return value
    match = re.fullmatch(r"data:image/(png|jpeg|webp|gif);base64,([A-Za-z0-9+/=]+)", value)
    if not match:
        raise ValueError("image must be an HTTP URL or a base64 PNG/JPEG/WebP/GIF image")
    try:
        content = base64.b64decode(match[2], validate=True)
    except ValueError as exc:
        raise ValueError("image base64 data is invalid") from exc
    if not content or len(content) > MAX_IMAGE_BYTES:
        raise ValueError("image must contain at most 20 MB of data")
    return value


ImageInput = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=8, max_length=MAX_IMAGE_INPUT_CHARS),
    AfterValidator(validate_image_input),
]
