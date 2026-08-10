package com.lingXi.ai.client;

/**
 * Agent 服务轻量熔断器。
 * <p>仅统计连接层故障（连接失败、读取超时、HTTP 5xx），业务错误不计入：
 * Agent 进程或网络不可用时快速失败，避免每个请求都等待超时并加剧雪崩；
 * 打开后经过等待窗口进入半开状态放行探测请求，连续成功则自动恢复，
 * 半开探测失败立即回到打开状态。</p>
 */
public class AgentCircuitBreaker
{
    private final int failureThreshold;
    private final long openTimeoutMs;
    private boolean halfOpen;
    private int consecutiveFailures;
    private long openedAt;
    private int halfOpenSuccesses;

    public AgentCircuitBreaker(int failureThreshold, long openTimeoutMs)
    {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openTimeoutMs = Math.max(1000L, openTimeoutMs);
    }

    /**
     * 尝试获取一次调用许可。
     *
     * @return 允许调用返回 true；熔断打开且未到恢复时间返回 false
     */
    public synchronized boolean tryAcquire()
    {
        if (consecutiveFailures >= failureThreshold)
        {
            if (System.currentTimeMillis() - openedAt >= openTimeoutMs)
            {
                halfOpen = true;
                halfOpenSuccesses = 0;
            }
            else
            {
                return false;
            }
        }
        return true;
    }

    /**
     * 记录一次成功调用；半开状态连续成功后关闭熔断。
     */
    public synchronized void recordSuccess()
    {
        if (halfOpen)
        {
            halfOpenSuccesses++;
            if (halfOpenSuccesses >= 2)
            {
                close();
            }
            return;
        }
        consecutiveFailures = 0;
    }

    /**
     * 记录一次连接层失败；达到阈值后打开熔断，半开探测失败立即回到打开状态。
     */
    public synchronized void recordFailure()
    {
        if (halfOpen)
        {
            openNow();
            return;
        }
        halfOpenSuccesses = 0;
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold)
        {
            openNow();
        }
    }

    /**
     * 熔断是否处于打开状态（快速失败窗口内）。
     *
     * @return 打开返回 true
     */
    public synchronized boolean isOpen()
    {
        return consecutiveFailures >= failureThreshold
                && System.currentTimeMillis() - openedAt < openTimeoutMs;
    }

    /**
     * 当前熔断状态描述，供日志与监控使用。
     *
     * @return 状态、失败计数和剩余打开时间
     */
    public synchronized String describe()
    {
        if (isOpen())
        {
            long remaining = openTimeoutMs - (System.currentTimeMillis() - openedAt);
            return "OPEN(remainingMs=" + remaining + ", failures=" + consecutiveFailures + ")";
        }
        if (halfOpen)
        {
            return "HALF_OPEN(successes=" + halfOpenSuccesses + ")";
        }
        return "CLOSED(failures=" + consecutiveFailures + ")";
    }

    private void close()
    {
        halfOpen = false;
        halfOpenSuccesses = 0;
        consecutiveFailures = 0;
        openedAt = 0;
    }

    private void openNow()
    {
        halfOpen = false;
        halfOpenSuccesses = 0;
        consecutiveFailures = failureThreshold;
        openedAt = System.currentTimeMillis();
    }
}
