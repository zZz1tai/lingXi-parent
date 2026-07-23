"""出站提供商目的地的失败关闭验证。

提供商 URL 通过 Java 到 Python 的传输层传递以保持兼容性，
但调用方只能选择运营人员明确加入白名单的目标地址。
这可以防止请求控制的 URL 成为 SSRF 攻击途径，
或接收提供商的凭证信息。
"""

from __future__ import annotations

import ipaddress
from collections.abc import Iterable
from urllib.parse import urlsplit, urlunsplit

from app.config.settings import settings
from app.utils.exceptions import InputValidationError


def _normalized_allowlist(values: Iterable[str]) -> set[str]:
    return {value.strip().lower().rstrip(".") for value in values if value.strip()}


def _host_is_allowed(host: str, authority: str, configured: set[str]) -> bool:
    """匹配精确目标地址或显式配置的子域名后缀。"""

    if host in configured or authority in configured:
        return True
    return any(
        pattern.startswith("*.")
        and host.endswith(pattern[1:])
        and host != pattern[2:]
        for pattern in configured
    )


def validate_outbound_http_url(
    url: str,
    *,
    allowed_hosts: set[str] | None = None,
) -> str:
    """验证并规范化出站 HTTP(S) URL。

    白名单接受主机名（所有显式指定的端口）或
    ``host:port`` 形式的授权信息。即使意外添加了私有地址也会被拒绝，
    除非启用了仅用于开发的 HTTP 覆盖选项。DNS 名称不受调用方控制的
    重绑定攻击影响，因为攻击者无法在运营人员白名单之外引入新的主机名。
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
    if not _host_is_allowed(host, authority, configured) or (
        non_default_port and authority not in configured
    ):
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
