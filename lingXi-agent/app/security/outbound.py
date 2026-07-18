"""Fail-closed validation for outbound provider destinations.

Provider URLs arrive over the Java-to-Python transport for compatibility, but
callers may only select a destination that an operator explicitly allowlists.
This prevents request-controlled URLs from becoming an SSRF primitive or from
receiving provider credentials.
"""

from __future__ import annotations

import ipaddress
from collections.abc import Iterable
from urllib.parse import urlsplit, urlunsplit

from app.config.settings import settings
from app.utils.exceptions import InputValidationError


def _normalized_allowlist(values: Iterable[str]) -> set[str]:
    return {value.strip().lower().rstrip(".") for value in values if value.strip()}


def validate_outbound_http_url(
    url: str,
    *,
    allowed_hosts: set[str] | None = None,
) -> str:
    """Validate and normalize an outbound HTTP(S) URL.

    The allowlist accepts either a hostname (all explicitly specified ports) or
    an authority in ``host:port`` form. Literal private addresses are rejected
    even if accidentally added unless the development-only HTTP override is
    enabled. DNS names are safe from caller-controlled rebinding because an
    attacker cannot introduce a new hostname outside the operator allowlist.
    """

    if not isinstance(url, str) or not url.strip():
        raise InputValidationError("Provider URL is required")

    candidate = url.strip()
    parsed = urlsplit(candidate)
    if parsed.scheme not in {"https", "http"}:
        raise InputValidationError("Provider URL must use HTTPS")
    if parsed.username is not None or parsed.password is not None:
        raise InputValidationError("Provider URL must not contain user information")
    if not parsed.hostname:
        raise InputValidationError("Provider URL must include a hostname")
    if parsed.fragment:
        raise InputValidationError("Provider URL must not contain a fragment")

    host = parsed.hostname.lower().rstrip(".")
    try:
        port = parsed.port
    except ValueError as exc:
        raise InputValidationError("Provider URL contains an invalid port") from exc

    configured = _normalized_allowlist(
        allowed_hosts if allowed_hosts is not None else settings.outbound_host_allowlist
    )
    authority = f"{host}:{port}" if port is not None else host
    default_port = 443 if parsed.scheme == "https" else 80
    non_default_port = port is not None and port != default_port
    if (
        host not in configured
        and authority not in configured
    ) or (non_default_port and authority not in configured):
        raise InputValidationError("Provider destination is not allowed")

    if parsed.scheme == "http" and not settings.allow_insecure_outbound_http:
        raise InputValidationError("Provider URL must use HTTPS")

    try:
        address = ipaddress.ip_address(host.strip("[]"))
    except ValueError:
        address = None
    if address is not None and (
        address.is_private
        or address.is_loopback
        or address.is_link_local
        or address.is_multicast
        or address.is_reserved
        or address.is_unspecified
    ):
        if not settings.allow_insecure_outbound_http:
            raise InputValidationError("Private provider destinations are not allowed")

    netloc = authority
    normalized_path = parsed.path or ""
    return urlunsplit((parsed.scheme, netloc, normalized_path, parsed.query, ""))
