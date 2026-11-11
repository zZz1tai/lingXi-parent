package com.lingXi.aiVedio.controller;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.aiVedio.domain.dto.DouyinResolveRequest;
import com.lingXi.aiVedio.domain.dto.DouyinVideoInfo;
import com.lingXi.aiVedio.service.DouyinVideoToolService;
import com.lingXi.aiVedio.tool.DouyinVideoStreamRelay;
import com.lingXi.common.annotation.Anonymous;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 抖音视频解析工具控制器。
 * <p>
 * 提供“粘贴抖音分享口令直接观看”能力：解析接口返回视频信息与播放令牌，
 * 代理流接口按令牌转发视频流（支持 Range，供 &lt;video&gt; 拖动进度条使用）。
 * 代理流接口匿名可访问（令牌即访问凭证，等同签名 URL），其余接口需登录授权。
 * </p>
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Slf4j
@RestController
@RequestMapping("/aivideo/tool/douyin")
public class DouyinVideoToolController extends BaseController
{
    @Autowired
    private DouyinVideoToolService toolService;

    @Autowired
    private DouyinVideoStreamRelay streamRelay;

    /**
     * 解析抖音分享口令或链接。
     *
     * @param request 包含分享口令文本的请求
     * @return 视频信息（含播放令牌）
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @PostMapping("/resolve")
    public AjaxResult resolve(@RequestBody DouyinResolveRequest request)
    {
        try
        {
            DouyinVideoInfo info = toolService.resolve(request == null ? null : request.getText());
            return success(info);
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 代理转发视频流。
     *
     * @param token 播放令牌
     * @param range 客户端 Range 请求头（可选）
     * @param response 客户端响应
     */
    @Anonymous
    @GetMapping("/stream/{token}")
    public void stream(@PathVariable("token") String token,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletResponse response)
    {
        try
        {
            List<String> playUrls = toolService.getPlayUrls(token);
            streamRelay.relay(playUrls, range, response);
        }
        catch (ServiceException exception)
        {
            sendErrorQuietly(response, HttpServletResponse.SC_GONE, exception.getMessage());
        }
        catch (IOException exception)
        {
            log.warn("抖音视频流转发失败：{}", exception.toString());
            if (!response.isCommitted())
            {
                sendErrorQuietly(response, HttpServletResponse.SC_BAD_GATEWAY, "视频流转发失败，请重新解析");
            }
        }
    }

    private static void sendErrorQuietly(HttpServletResponse response, int status, String message)
    {
        try
        {
            response.sendError(status, message);
        }
        catch (IOException exception)
        {
            // 客户端已断开等场景，无需处理
        }
    }
}
