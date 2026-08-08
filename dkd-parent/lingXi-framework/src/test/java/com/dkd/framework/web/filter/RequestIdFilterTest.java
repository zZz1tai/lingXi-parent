package com.dkd.framework.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class RequestIdFilterTest {

    /** 运行一次过滤器，返回响应头与链内 MDC 值。 */
    private static Result run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInside = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcInside.set(RequestIdFilter.current());
        new RequestIdFilter().doFilter(request, response, chain);
        return new Result(response.getHeader("X-Request-Id"), mdcInside.get());
    }

    private static Result run() throws Exception {
        return run(new MockHttpServletRequest());
    }

    private record Result(String responseHeader, String mdcInside) {
    }

    @Test
    void generatesRequestIdWhenHeaderAbsent() throws Exception {
        Result result = run();

        assertTrue(RequestIdFilter.GENERATED_PATTERN.matcher(result.responseHeader()).matches());
        assertEquals(result.responseHeader(), result.mdcInside());
    }

    @Test
    void passesThroughValidInboundHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-0123456789abcdef0123456789abcdef");

        Result result = run(request);
        assertEquals("req-0123456789abcdef0123456789abcdef", result.responseHeader());
        assertEquals("req-0123456789abcdef0123456789abcdef", result.mdcInside());
    }

    @Test
    void regeneratesRejectedInboundHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "bad value\nwith newline");

        assertTrue(RequestIdFilter.GENERATED_PATTERN.matcher(run(request).responseHeader()).matches());
    }

    @Test
    void cleansMdcAfterRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(
                new MockHttpServletRequest(), response, (req, res) -> {
                });

        assertNull(RequestIdFilter.current());
        assertNull(MDC.get("request_id"));
    }

    @Test
    void requestIdsAreUniqueAcrossRequests() throws Exception {
        assertFalse(run().responseHeader().equals(run().responseHeader()));
    }

    @Test
    void mdcIsPerThread() throws Exception {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();
        Thread workerA = new Thread(() -> {
            try {
                first.set(run().mdcInside());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread workerB = new Thread(() -> {
            try {
                second.set(run().mdcInside());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        workerA.start();
        workerB.start();
        workerA.join();
        workerB.join();

        assertFalse(first.get().isEmpty());
        assertFalse(first.get().equals(second.get()));
        assertNull(MDC.get("request_id"));
    }
}
