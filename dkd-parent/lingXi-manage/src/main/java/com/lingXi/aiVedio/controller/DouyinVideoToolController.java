package com.lingXi.aiVedio.controller;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.aiVedio.domain.dto.DouyinComment;
import com.lingXi.aiVedio.domain.dto.DouyinPageResult;
import com.lingXi.aiVedio.domain.dto.DouyinResolveRequest;
import com.lingXi.aiVedio.domain.dto.DouyinUserPage;
import com.lingXi.aiVedio.domain.dto.DouyinVideoCard;
import com.lingXi.aiVedio.domain.dto.DouyinVideoInfo;
import com.lingXi.aiVedio.domain.dto.DouyinVideoPage;
import com.lingXi.aiVedio.service.DouyinVideoToolService;
import com.lingXi.aiVedio.tool.DouyinVideoStreamRelay;
import com.lingXi.common.annotation.Anonymous;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 抖音视频工具控制器：刷视频流、评论、弹幕、作者主页、搜索与链接解析。
 * <p>
 * 数据由后端浏览器执行抖音页面脚本抓取，视频一律通过代理流接口播放（支持 Range）。
 * 代理流接口匿名可访问（播放令牌即访问凭证，等同签名 URL），其余接口需登录授权。
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
     * 解析抖音分享口令或链接（粘贴即看）。
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
     * 视频详情页数据：详情、弹幕、首屏评论与相关推荐（一次抓取）。
     *
     * @param awemeId 作品 ID
     * @return 页面数据
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/page/{awemeId}")
    public AjaxResult videoPage(@PathVariable("awemeId") String awemeId)
    {
        try
        {
            return success(toolService.videoPage(awemeId));
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 评论翻页。
     *
     * @param awemeId 作品 ID
     * @param page    页号（0 起）
     * @return 评论分页
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/comments/{awemeId}")
    public AjaxResult comments(@PathVariable("awemeId") String awemeId,
            @RequestParam(value = "page", defaultValue = "0") int page)
    {
        try
        {
            DouyinPageResult<DouyinComment> result = toolService.comments(awemeId, page);
            return success(result);
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 相关推荐翻页。
     *
     * @param awemeId 作品 ID
     * @param page    页号（0 起）
     * @return 视频分页
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/related/{awemeId}")
    public AjaxResult related(@PathVariable("awemeId") String awemeId,
            @RequestParam(value = "page", defaultValue = "0") int page)
    {
        try
        {
            DouyinPageResult<DouyinVideoCard> result = toolService.related(awemeId, page);
            return success(result);
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 刷视频流（频道流/推荐流）。
     *
     * @param channel 频道（recommend=推荐流，其余为精选频道英文标识）
     * @param page    页号（0 起）
     * @return 视频分页
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/feed")
    public AjaxResult feed(@RequestParam(value = "channel", defaultValue = "recommend") String channel,
            @RequestParam(value = "page", defaultValue = "0") int page)
    {
        try
        {
            return success(toolService.feed(channel, page));
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 作者主页：资料 + 作品分页。
     *
     * @param secUid 作者 sec_uid
     * @param page   作品页号（0 起）
     * @return 主页数据
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/user/{secUid}")
    public AjaxResult user(@PathVariable("secUid") String secUid,
            @RequestParam(value = "page", defaultValue = "0") int page)
    {
        try
        {
            DouyinUserPage result = toolService.user(secUid, page);
            return success(result);
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 搜索视频。
     *
     * @param keyword 关键词
     * @param page    页号（0 起）
     * @return 视频分页
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/search")
    public AjaxResult search(@RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page)
    {
        try
        {
            return success(toolService.search(keyword, page));
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 打开可见浏览器窗口，供用户一次性登录抖音（解锁搜索等受限能力）。
     *
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @PostMapping("/browser-login")
    public AjaxResult startBrowserLogin()
    {
        try
        {
            toolService.startBrowserLogin();
            return success("登录窗口已打开，请在弹出的浏览器中完成抖音登录");
        }
        catch (ServiceException exception)
        {
            return error(exception.getMessage());
        }
    }

    /**
     * 登录状态查询。
     *
     * @return {loggedIn, windowOpen}
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/browser-login/status")
    public AjaxResult browserLoginStatus()
    {
        boolean[] status = toolService.browserLoginStatus();
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("loggedIn", status[0]);
        result.put("windowOpen", status[1]);
        return success(result);
    }

    /**
     * 关闭登录窗口并恢复无头抓取模式。
     *
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @PostMapping("/browser-login/close")
    public AjaxResult closeBrowserLogin()
    {
        try
        {
            toolService.closeBrowserLogin();
            return success("登录窗口已关闭");
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
            try
            {
                streamRelay.relay(playUrls, range, response);
            }
            catch (IOException exception)
            {
                if (response.isCommitted())
                {
                    throw exception;
                }
                // CDN 直链签名时效短，过期会 403：按作品 ID 重新抓取新鲜直链后重试一次
                log.warn("视频源转发失败，刷新播放地址后重试：{}", exception.toString());
                streamRelay.relay(toolService.refreshPlayUrls(token), range, response);
            }
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
