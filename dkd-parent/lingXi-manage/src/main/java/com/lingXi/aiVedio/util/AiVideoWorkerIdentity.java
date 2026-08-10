package com.lingXi.aiVedio.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * AI 视频任务执行者（Worker）标识工具。
 * <p>每次领取任务时写入 worker_id 与租约过期时间，用于多实例部署下
 * 崩溃任务的回收判定；实例级唯一即可。</p>
 */
public final class AiVideoWorkerIdentity
{
    /** 本实例执行者标识：主机名 + 随机后缀。 */
    public static final String WORKER_ID = buildWorkerId();

    /** 默认租约时长（秒），执行中的长任务通过续租延长。 */
    public static final int DEFAULT_LEASE_SECONDS = 300;

    private AiVideoWorkerIdentity()
    {
    }

    private static String buildWorkerId()
    {
        String hostname = "unknown-host";
        try
        {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException ignored)
        {
        }
        return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
