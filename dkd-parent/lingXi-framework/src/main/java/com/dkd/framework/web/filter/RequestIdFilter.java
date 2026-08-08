package com.dkd.framework.web.filter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 为每个 HTTP 请求建立统一的 request_id。
 * <p>合法上游值（{@code X-Request-Id} 请求头）按与 Python Agent 一致的
 * 宽松格式透传；否则生成 {@code req-} 前缀的 32 位十六进制标识。
 * 生成或透传的值同时写入 MDC（日志关联）和响应头（客户端可见），
 * 并可在后续调用 Agent 时透传，形成 Java ↔ Agent 统一链路。</p>
 */
public class RequestIdFilter extends OncePerRequestFilter {

    /** HTTP 请求头与响应头名称。 */
    public static final String HEADER = "X-Request-Id";

    /** 日志 MDC 键。 */
    public static final String MDC_KEY = "request_id";

    /** 透传值的宽松格式，与 Python Agent 的 X-Request-ID 校验保持一致。 */
    static final Pattern INBOUND_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    /** 生成值的格式，与 Agent 契约的 agent_request_id（req-[a-f0-9]{32}）一致。 */
    public static final Pattern GENERATED_PATTERN =
            Pattern.compile("^req-[a-f0-9]{32}$");

    /** 从当前线程的 MDC 读取 request_id；为空时返回 {@code null}。 */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String requestId = supplied != null && INBOUND_PATTERN.matcher(supplied).matches()
                ? supplied
                : "req-" + UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_KEY, requestId);
        try {
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
