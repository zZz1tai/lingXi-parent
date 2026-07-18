"""Security boundaries shared by API and provider integrations."""

from app.security.outbound import validate_outbound_http_url

__all__ = ["validate_outbound_http_url"]
