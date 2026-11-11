package com.lingXi.aiVedio.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.lingXi.aiVedio.domain.dto.DouyinVideoInfo;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient.MediaInfo;
import com.lingXi.aiVedio.tool.DouyinShareLinkParser;
import com.lingXi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 抖音视频解析服务。
 * <p>
 * 负责“粘贴分享口令 → 提取链接 → 跟随短链跳转 → 抓取视频信息 → 发放播放令牌”流程。
 * 播放令牌对应的 CDN 直链缓存在内存中（默认 30 分钟），管理端通过代理流接口播放，
 * 不在前端暴露带签名的临时直链。
 * </p>
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Slf4j
@Service
public class DouyinVideoToolService
{
    /** 播放令牌有效期（毫秒）。 */
    private static final long PLAY_SESSION_TTL_MS = 30 * 60 * 1000L;

    /** 短链跳转跟随上限。 */
    private static final int MAX_REDIRECTS = 5;

    @Autowired
    private DouyinBrowserVideoClient browserClient;

    /** 播放令牌 → 播放直链会话。 */
    private final Map<String, PlaySession> playSessions = new ConcurrentHashMap<>();

    private static final class PlaySession
    {
        private final List<String> playUrls;

        private final long expireAt;

        private PlaySession(List<String> playUrls, long expireAt)
        {
            this.playUrls = playUrls;
            this.expireAt = expireAt;
        }

        private List<String> getPlayUrls()
        {
            return playUrls;
        }

        private boolean isExpired()
        {
            return System.currentTimeMillis() > expireAt;
        }
    }

    /**
     * 解析抖音分享口令或链接，返回视频信息与播放令牌。
     *
     * @param text 用户粘贴的分享口令（允许包含无关文案）
     * @return 视频信息
     * @throws ServiceException 链接无效、解析失败或内容不可播放
     */
    public DouyinVideoInfo resolve(String text)
    {
        String url = DouyinShareLinkParser.extractShareUrl(text);
        if (url == null)
        {
            throw new ServiceException("未识别到抖音链接，请粘贴完整的抖音分享口令");
        }
        String awemeId = DouyinShareLinkParser.extractAwemeId(url);
        if (awemeId == null)
        {
            awemeId = resolveAwemeIdByRedirect(url);
        }
        MediaInfo media = browserClient.fetch(awemeId);
        if (media.getPlayUrls().isEmpty())
        {
            throw new ServiceException("该作品不是可在线播放的视频（可能是图文），暂不支持");
        }
        cleanupExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        playSessions.put(token, new PlaySession(media.getPlayUrls(),
                System.currentTimeMillis() + PLAY_SESSION_TTL_MS));

        DouyinVideoInfo info = new DouyinVideoInfo();
        info.setAwemeId(awemeId);
        info.setTitle(media.getTitle());
        info.setAuthor(media.getAuthor());
        info.setAuthorAvatar(media.getAuthorAvatar());
        info.setCoverUrl(media.getCoverUrl());
        info.setDurationMs(media.getDurationMs());
        info.setPlayToken(token);
        info.setSourceUrl(url);
        return info;
    }

    /**
     * 获取播放令牌对应的播放直链列表。
     *
     * @param token 播放令牌
     * @return 播放直链（按优先级排列）
     * @throws ServiceException 令牌不存在或已过期
     */
    public List<String> getPlayUrls(String token)
    {
        PlaySession session = token == null ? null : playSessions.get(token);
        if (session == null || session.isExpired())
        {
            throw new ServiceException("播放地址已过期，请重新解析");
        }
        return session.getPlayUrls();
    }

    /** 跟随短链跳转，在跳转链路上提取作品 ID。 */
    private String resolveAwemeIdByRedirect(String url)
    {
        HttpClient client = HttpHolder.CLIENT;
        String current = url;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++)
        {
            if (!DouyinShareLinkParser.isDouyinHost(current))
            {
                throw new ServiceException("链接跳转到了非抖音地址，已中止解析");
            }
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(current))
                    .timeout(Duration.ofSeconds(15)).header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .GET().build();
            try
            {
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 300 && status < 400)
                {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (location == null)
                    {
                        break;
                    }
                    current = URI.create(current).resolve(location).toString();
                    String awemeId = DouyinShareLinkParser.extractAwemeId(current);
                    if (awemeId != null)
                    {
                        return awemeId;
                    }
                    continue;
                }
                break;
            }
            catch (Exception exception)
            {
                log.warn("抖音短链跳转失败：{}", exception.toString());
                throw new ServiceException("解析抖音短链失败，请稍后重试");
            }
        }
        throw new ServiceException("无法从链接中解析出视频，请使用抖音“分享 - 复制链接”生成的口令");
    }

    private void cleanupExpiredSessions()
    {
        playSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private static final class HttpHolder
    {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
    }
}
