package com.lingXi.aiVedio.outbox;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.mapper.AiVideoTaskOutboxMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * AI视频投递事件箱监控指标。
 * <p>通过 Micrometer 暴露派发积压情况：未投递事件数量、最老事件等待时长
 * （反映派发器健康度），以及累计成功/失败派发次数。指标经 Actuator
 * /actuator/metrics 查询，供运维告警使用。</p>
 */
@Component
public class AiVideoOutboxMetrics
{
    /** 指标刷新间隔（毫秒），gauge 值来自数据库聚合，无需实时。 */
    private static final long REFRESH_INTERVAL_MS = 30_000L;

    private final AiVideoTaskOutboxMapper outboxMapper;
    private final AtomicLong pendingCount = new AtomicLong(0);
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong(0);
    private final Counter dispatchedCounter;
    private final Counter failedCounter;

    @Autowired
    public AiVideoOutboxMetrics(AiVideoTaskOutboxMapper outboxMapper, MeterRegistry meterRegistry)
    {
        this.outboxMapper = outboxMapper;
        Gauge.builder("aivideo.outbox.pending.events", pendingCount, AtomicLong::get)
                .description("未投递事件数量（PENDING 与等待重试）")
                .register(meterRegistry);
        Gauge.builder("aivideo.outbox.oldest.pending.age.seconds", oldestPendingAgeSeconds, AtomicLong::get)
                .description("最老未投递事件的等待时长（秒）")
                .register(meterRegistry);
        this.dispatchedCounter = Counter.builder("aivideo.outbox.dispatched.total")
                .description("累计成功派发事件数")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aivideo.outbox.dispatch.failed.total")
                .description("累计派发失败事件数")
                .register(meterRegistry);
    }

    /**
     * 定时刷新积压指标。
     */
    @Scheduled(fixedDelay = REFRESH_INTERVAL_MS)
    public void refresh()
    {
        pendingCount.set(outboxMapper.countPendingOutboxEvents());
        Long oldestAge = outboxMapper.selectOldestPendingEventAgeSeconds();
        oldestPendingAgeSeconds.set(oldestAge == null ? 0 : oldestAge);
    }

    /**
     * 记录一次成功派发。
     */
    public void recordDispatchSuccess()
    {
        dispatchedCounter.increment();
    }

    /**
     * 记录一次失败派发（含重试与放弃）。
     */
    public void recordDispatchFailure()
    {
        failedCounter.increment();
    }
}
