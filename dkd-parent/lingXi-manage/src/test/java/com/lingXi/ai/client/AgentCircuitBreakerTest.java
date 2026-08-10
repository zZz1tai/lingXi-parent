package com.lingXi.ai.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentCircuitBreakerTest
{
    @Test
    void staysClosedBelowFailureThreshold()
    {
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(3, 60_000L);
        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.tryAcquire());
        assertFalse(breaker.isOpen());
    }

    @Test
    void opensAfterFailureThreshold()
    {
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(3, 60_000L);
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.isOpen());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void successResetsFailuresWhileClosed()
    {
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(3, 60_000L);
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.isOpen());
    }

    @Test
    void halfOpenAllowsProbeAfterWindowAndClosesAfterTwoSuccesses() throws Exception
    {
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(2, 1000L);
        breaker.recordFailure();
        breaker.recordFailure();
        assertTrue(breaker.isOpen());
        assertFalse(breaker.tryAcquire());

        Thread.sleep(1100L);
        assertTrue(breaker.tryAcquire());
        breaker.recordSuccess();
        breaker.recordSuccess();
        assertFalse(breaker.isOpen());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    void halfOpenProbeFailureReopensImmediately() throws Exception
    {
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(2, 1000L);
        breaker.recordFailure();
        breaker.recordFailure();
        Thread.sleep(1100L);
        assertTrue(breaker.tryAcquire());

        breaker.recordFailure();
        assertTrue(breaker.isOpen());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void windowStillOpenAfterTimePassesWithoutProbe()
    {
        AgentCircuitBreaker breaker = new AgentCircuitBreaker(1, 60_000L);
        breaker.recordFailure();
        assertTrue(breaker.isOpen());
        assertFalse(breaker.tryAcquire());
        assertTrue(breaker.isOpen());
    }
}
