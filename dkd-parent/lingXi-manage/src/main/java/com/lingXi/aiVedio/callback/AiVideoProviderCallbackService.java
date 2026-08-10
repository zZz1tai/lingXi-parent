package com.lingXi.aiVedio.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.service.AiVideoProviderTaskOutcomeService;
import com.lingXi.aiVedio.util.AiVideoWorkerIdentity;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频供应商回调处理服务。
 * <p>供应商在任务完成或失败时回调 {@code POST /aivideo/callbacks/{provider}}，
 * 与轮询器并发推进同一任务。回调与轮询通过领取（WAITING_CALLBACK→RUNNING 条件更新）
 * 互斥，先到达者处理，后到达者忽略；同 eventId 的重复回调幂等忽略。</p>
 *
 * <p>回调契约（v1）：</p>
 * <pre>
 * POST /aivideo/callbacks/happyhorse
 * Headers:
 *   X-Timestamp: &lt;epoch毫秒&gt;
 *   X-Signature: HMAC-SHA256(sharedSecret, timestamp + "." + 原始请求体) 的十六进制小写
 * Body:
 * {
 *   "eventId": "evt-xxx",              // 必填，回调事件唯一ID
 *   "providerTaskId": "hh-xxx",        // 必填，提交时返回的供应商任务ID
 *   "status": "SUCCEEDED",             // 必填：SUCCEEDED/FAILED/CANCELED
 *   "videoUrl": "https://...",         // SUCCEEDED 时必填
 *   "error": "可读失败原因"             // FAILED/CANCELED 时可选
 * }
 * </pre>
 */
@Service
@Slf4j
public class AiVideoProviderCallbackService
{
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoProviderTaskOutcomeService outcomeService;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${aivideo.callback.shared-secret:}")
    private String sharedSecret;
    @Value("${aivideo.callback.clock-skew-seconds:300}")
    private long clockSkewSeconds;

    /**
     * 校验签名并处理供应商回调。
     *
     * @param providerCode 供应商编码（路径参数）
     * @param timestamp    请求时间戳（X-Timestamp 头）
     * @param signature    请求签名（X-Signature 头）
     * @param rawBody      原始请求体
     * @throws CallbackVerificationException 签名或时间戳校验失败
     * @throws IllegalArgumentException      回调体非法
     */
    public void handle(String providerCode, String timestamp, String signature, String rawBody)
    {
        verifySignature(timestamp, signature, rawBody);

        JsonNode payload;
        try
        {
            payload = objectMapper.readTree(rawBody);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("回调体不是合法JSON");
        }
        String eventId = text(payload, "eventId");
        String providerTaskId = text(payload, "providerTaskId");
        String status = text(payload, "status");
        if (eventId == null || providerTaskId == null || status == null)
        {
            throw new IllegalArgumentException("回调缺少 eventId/providerTaskId/status 字段");
        }
        if (!AiVideoTaskStatus.SUCCEEDED.is(status)
                && !AiVideoTaskStatus.FAILED.is(status)
                && !AiVideoTaskStatus.CANCELED.is(status))
        {
            throw new IllegalArgumentException("不支持的视频任务状态：" + status);
        }
        String videoUrl = text(payload, "videoUrl");
        if (AiVideoTaskStatus.SUCCEEDED.is(status) && (videoUrl == null || videoUrl.trim().isEmpty()))
        {
            throw new IllegalArgumentException("成功回调缺少视频地址");
        }
        String error = text(payload, "error");

        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByProviderTaskId(
                providerTaskId, providerCode);
        if (task == null)
        {
            throw new IllegalArgumentException("未找到供应商任务：" + providerTaskId);
        }
        if (eventId.equals(task.getCallbackEventId()))
        {
            log.info("收到重复视频回调事件，已处理过，eventId={}, taskId={}", eventId, task.getTaskId());
            return;
        }

        if (AiVideoTaskStatus.WAITING_CALLBACK.is(task.getStatus()))
        {
            if (taskMapper.claimVideoProviderTask(task.getTaskId(), providerCode,
                    AiVideoWorkerIdentity.WORKER_ID, AiVideoWorkerIdentity.DEFAULT_LEASE_SECONDS) != 1)
            {
                log.info("视频任务已被轮询器领取，忽略回调，taskId={}", task.getTaskId());
                return;
            }
            try
            {
                applyOutcome(task, status, videoUrl, error, providerCode);
            }
            catch (Exception ex)
            {
                taskMapper.updateClaimedVideoProviderTaskStatus(
                        task.getTaskId(), providerCode, AiVideoTaskStatus.WAITING_CALLBACK.name(), 40,
                        "VIDEO_PROVIDER_CALLBACK_ERROR", ex.getMessage());
                throw new IllegalStateException("回调结果处理失败：" + ex.getMessage());
            }
            taskMapper.updateAiVideoGenerationTaskCallbackEventId(task.getTaskId(), eventId);
            log.info("视频供应商回调处理完成，taskId={}, status={}", task.getTaskId(), status);
            return;
        }
        if (AiVideoTaskStatus.RUNNING.is(task.getStatus()))
        {
            log.info("视频任务正在轮询处理中，忽略回调，taskId={}", task.getTaskId());
            return;
        }
        log.warn("视频任务状态已终态或异常，忽略回调，taskId={}, status={}",
                task.getTaskId(), task.getStatus());
    }

    /**
     * 按回调状态推进任务结果。
     *
     * @param task         生成任务实体
     * @param status       回调状态
     * @param videoUrl     视频地址
     * @param error        失败原因
     * @param providerCode 供应商编码
     * @throws Exception 处理失败时抛出异常
     */
    private void applyOutcome(AiVideoGenerationTask task, String status,
            String videoUrl, String error, String providerCode) throws Exception
    {
        if (AiVideoTaskStatus.SUCCEEDED.is(status))
        {
            outcomeService.complete(task, videoUrl, providerCode, "ai-video-callback");
        }
        else
        {
            outcomeService.fail(task, error == null ? "视频供应商任务" + status : error,
                    providerCode, "ai-video-callback");
        }
    }

    /**
     * 校验时间戳新鲜度与 HMAC-SHA256 签名。
     *
     * @param timestamp 请求时间戳（epoch毫秒）
     * @param signature 请求签名
     * @param rawBody   原始请求体
     */
    private void verifySignature(String timestamp, String signature, String rawBody)
    {
        if (sharedSecret == null || sharedSecret.isEmpty())
        {
            throw new CallbackVerificationException("回调共享密钥未配置，拒绝回调");
        }
        if (timestamp == null || timestamp.isEmpty())
        {
            throw new CallbackVerificationException("缺少 X-Timestamp 请求头");
        }
        long requestTime;
        try
        {
            requestTime = Long.parseLong(timestamp);
        }
        catch (NumberFormatException ex)
        {
            throw new CallbackVerificationException("X-Timestamp 不是合法时间戳");
        }
        long skewMillis = Math.abs(System.currentTimeMillis() - requestTime);
        if (skewMillis > clockSkewSeconds * 1000L)
        {
            throw new CallbackVerificationException("回调时间戳超出允许偏差，疑似重放");
        }
        if (signature == null || signature.isEmpty())
        {
            throw new CallbackVerificationException("缺少 X-Signature 请求头");
        }
        String expected = hmacSha256(sharedSecret, timestamp + "." + rawBody);
        if (!constantTimeEquals(expected, signature))
        {
            throw new CallbackVerificationException("回调签名校验失败");
        }
    }

    /**
     * 计算 HMAC-SHA256 十六进制摘要。
     *
     * @param secret 共享密钥
     * @param content 待签名内容
     * @return 十六进制小写摘要
     */
    private String hmacSha256(String secret, String content)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest)
            {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("HMAC 计算失败", ex);
        }
    }

    /**
     * 恒定时间字符串比较，防时序侧信道。
     *
     * @param a 期望值
     * @param b 实际值
     * @return 是否相等
     */
    private boolean constantTimeEquals(String a, String b)
    {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 读取 JSON 节点文本字段。
     *
     * @param node  JSON节点
     * @param field 字段名
     * @return 字段值，缺失或非文本时返回null
     */
    private String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 回调验签异常。
     */
    public static class CallbackVerificationException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public CallbackVerificationException(String message)
        {
            super(message);
        }
    }
}
