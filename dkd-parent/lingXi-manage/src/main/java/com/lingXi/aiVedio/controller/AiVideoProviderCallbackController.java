package com.lingXi.aiVedio.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.aiVedio.callback.AiVideoProviderCallbackService;
import com.lingXi.aiVedio.callback.AiVideoProviderCallbackService.CallbackVerificationException;
import com.lingXi.common.annotation.Anonymous;
import com.lingXi.common.core.domain.AjaxResult;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频供应商回调接口。
 * <p>匿名访问，Controller 内强制验签（HMAC-SHA256 签名 + 时间戳防重放），
 * 不依赖登录态与权限体系。</p>
 */
@Slf4j
@Anonymous
@RestController
@RequestMapping("/aivideo/callbacks")
public class AiVideoProviderCallbackController
{
    @Autowired
    private AiVideoProviderCallbackService callbackService;

    /**
     * 接收供应商任务完成/失败回调。
     *
     * @param provider  供应商编码，如 happyhorse
     * @param timestamp X-Timestamp 请求时间戳（epoch毫秒）
     * @param signature X-Signature 请求签名
     * @param rawBody   原始请求体
     * @return 处理结果
     */
    @PostMapping("/{provider}")
    public AjaxResult handleCallback(@PathVariable("provider") String provider,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawBody)
    {
        try
        {
            callbackService.handle(provider, timestamp, signature, rawBody);
        }
        catch (CallbackVerificationException ex)
        {
            log.warn("视频供应商回调验签失败，provider={}, reason={}", provider, ex.getMessage());
            return AjaxResult.error(401, "callback verification failed");
        }
        catch (IllegalArgumentException ex)
        {
            log.warn("视频供应商回调请求非法，provider={}, reason={}", provider, ex.getMessage());
            return AjaxResult.error(400, ex.getMessage());
        }
        catch (Exception ex)
        {
            log.error("视频供应商回调处理异常，provider={}, errorType={}",
                    provider, ex.getClass().getSimpleName());
            return AjaxResult.error(500, "callback processing failed");
        }
        return AjaxResult.success();
    }
}
