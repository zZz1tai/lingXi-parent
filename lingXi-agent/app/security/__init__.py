"""API 和提供商集成共享的安全边界。"""

from app.security.outbound import validate_outbound_http_url

__all__ = ["validate_outbound_http_url"]
