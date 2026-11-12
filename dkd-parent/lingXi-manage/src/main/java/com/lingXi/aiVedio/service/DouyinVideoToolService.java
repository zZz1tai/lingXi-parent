package com.lingXi.aiVedio.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingXi.aiVedio.domain.dto.DouyinComment;
import com.lingXi.aiVedio.domain.dto.DouyinDanmakuItem;
import com.lingXi.aiVedio.domain.dto.DouyinPageResult;
import com.lingXi.aiVedio.domain.dto.DouyinUserPage;
import com.lingXi.aiVedio.domain.dto.DouyinUserProfile;
import com.lingXi.aiVedio.domain.dto.DouyinVideoCard;
import com.lingXi.aiVedio.domain.dto.DouyinVideoInfo;
import com.lingXi.aiVedio.domain.dto.DouyinVideoPage;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient.CaptureAction;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient.CaptureRequest;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient.CapturedBody;
import com.lingXi.aiVedio.tool.DouyinBrowserVideoClient.MediaInfo;
import com.lingXi.aiVedio.tool.DouyinShareLinkParser;
import com.lingXi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 抖音数据服务：刷视频流、评论、弹幕、作者主页、搜索与“粘贴链接解析”。
 * <p>
 * 所有数据经由 {@link DouyinBrowserVideoClient} 在抖音页面上下文中抓取（截获其自身签名接口），
 * 并统一转换为管理端 DTO。播放直链不外发：视频条目一律发放播放令牌，由代理流接口转发播放。
 * 抓取结果做短时缓存（默认 5 分钟），配合串行浏览器降低抖音侧的访问频率。
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

    /** 抓取结果缓存有效期（毫秒）。 */
    private static final long CAPTURE_CACHE_TTL_MS = 5 * 60 * 1000L;

    /** 短链跳转跟随上限。 */
    private static final int MAX_REDIRECTS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DouyinBrowserVideoClient browserClient;

    /** 播放令牌 → 播放直链会话。 */
    private final Map<String, PlaySession> playSessions = new ConcurrentHashMap<>();

    /** 抓取结果短时缓存：特征键 → (过期时间, 结果)。 */
    private final Map<String, CacheEntry<?>> captureCache = new ConcurrentHashMap<>();

    private static final class PlaySession
    {
        private final String awemeId;

        private List<String> playUrls;

        private long expireAt;

        private PlaySession(String awemeId, List<String> playUrls, long expireAt)
        {
            this.awemeId = awemeId;
            this.playUrls = playUrls;
            this.expireAt = expireAt;
        }

        private boolean isExpired()
        {
            return System.currentTimeMillis() > expireAt;
        }

        private String getAwemeId()
        {
            return awemeId;
        }

        private synchronized List<String> getPlayUrls()
        {
            return playUrls;
        }

        /** 用新抓取的直链替换（直链签名时效短，过期后可原令牌换新） */
        private synchronized void refresh(List<String> newPlayUrls)
        {
            this.playUrls = newPlayUrls;
            this.expireAt = System.currentTimeMillis() + PLAY_SESSION_TTL_MS;
        }
    }

    private record CacheEntry<T>(long expireAt, T value)
    {
        private boolean isExpired()
        {
            return System.currentTimeMillis() > expireAt;
        }
    }

    // ==================== 粘贴链接解析（旧流程） ====================

    /**
     * 解析抖音分享口令或链接，返回视频信息与播放令牌。
     *
     * @param text 用户粘贴的分享口令
     * @return 视频信息
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
        String token = mintPlayToken(awemeId, media.getPlayUrls());

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

    // ==================== 视频详情页（详情 + 弹幕 + 评论 + 相关） ====================

    /**
     * 抓取视频详情页数据：一次页面访问同时截获详情、弹幕、首屏评论与相关推荐。
     *
     * @param awemeId 作品 ID
     * @return 页面数据
     */
    public DouyinVideoPage videoPage(String awemeId)
    {
        requireAwemeId(awemeId);
        String cacheKey = "page:" + awemeId;
        DouyinVideoPage cached = fromCache(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        DouyinVideoPage page = captureVideoPage(awemeId);
        if (page.getVideo() == null)
        {
            // 详情接口偶发漏抓（被次要接口提前收线或响应未就绪），换新标签页重抓一次
            log.info("视频详情页抓取不完整，自动重试一次：awemeId={}", awemeId);
            page = captureVideoPage(awemeId);
        }
        if (page.getVideo() == null)
        {
            throw new ServiceException("未能获取视频信息，可能被抖音安全校验拦截，请稍后重试或先登录抖音");
        }
        putCache(cacheKey, page);
        return page;
    }

    /** 抓取并解析视频详情页数据（不抛异常，详情缺失时返回 video=null）。 */
    private DouyinVideoPage captureVideoPage(String awemeId)
    {
        CaptureRequest request = new CaptureRequest();
        request.setPageUrl("https://www.douyin.com/video/" + awemeId);
        request.setMarkers(List.of("/aweme/v1/web/aweme/detail", "/danmaku/get_v2",
                "/comment/list", "/aweme/related"));
        // 详情响应体最大、往往最晚到达：必须等到它才收线，避免被弹幕等次要接口提前结束等待
        request.setRequiredMarker("/aweme/v1/web/aweme/detail");
        request.setMinResponses(3);
        request.setTimeoutMs(35_000);
        request.setScrollTimes(1);
        List<CapturedBody> bodies = browserClient.capture(request);

        DouyinVideoPage page = new DouyinVideoPage();
        JsonNode detail = findBody(bodies, "/aweme/v1/web/aweme/detail");
        if (detail != null && detail.path("aweme_detail").isObject())
        {
            page.setVideo(toCard(detail.path("aweme_detail"), awemeId));
        }
        JsonNode danmaku = findBody(bodies, "/danmaku/get_v2");
        page.setDanmaku(parseDanmaku(danmaku));
        JsonNode comments = findBody(bodies, "/comment/list");
        DouyinPageResult<DouyinComment> commentPage = parseComments(comments);
        page.setComments(commentPage.getItems());
        page.setCommentsHasMore(commentPage.isHasMore());
        JsonNode related = findBody(bodies, "/aweme/related");
        DouyinPageResult<DouyinVideoCard> relatedPage = parseAwemeList(related);
        page.setRelated(relatedPage.getItems());
        page.setRelatedHasMore(relatedPage.isHasMore());
        return page;
    }

    /**
     * 评论翻页。
     *
     * @param awemeId 作品 ID
     * @param page    页号（0 起）
     * @return 评论分页
     */
    public DouyinPageResult<DouyinComment> comments(String awemeId, int page)
    {
        requireAwemeId(awemeId);
        String cacheKey = "comments:" + awemeId + ":" + page;
        DouyinPageResult<DouyinComment> cached = fromCache(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        CaptureRequest request = new CaptureRequest();
        request.setPageUrl("https://www.douyin.com/video/" + awemeId);
        request.setMarkers(List.of("/comment/list"));
        request.setMinResponses(1);
        request.setTimeoutMs(28_000);
        request.setNavDelayMs(3_000);
        request.setScrollTimes(Math.max(1, page) + 1);
        request.setScrollIntervalMs(2_500);
        DouyinPageResult<DouyinComment> result = null;
        for (int attempt = 0; attempt < 2; attempt++)
        {
            List<CapturedBody> bodies = browserClient.capture(request);
            result = parseComments(nthBody(bodies, "/comment/list", page));
            if (!result.getItems().isEmpty())
            {
                break;
            }
            log.info("评论页抓取为空，自动重试一次：awemeId={}, page={}", awemeId, page);
        }
        if (result.getItems().isEmpty())
        {
            throw new ServiceException("评论加载失败，请稍后重试");
        }
        putCache(cacheKey, result);
        return result;
    }

    /**
     * 相关推荐翻页。
     *
     * @param awemeId 作品 ID
     * @param page    页号（0 起）
     * @return 视频分页
     */
    public DouyinPageResult<DouyinVideoCard> related(String awemeId, int page)
    {
        requireAwemeId(awemeId);
        String cacheKey = "related:" + awemeId + ":" + page;
        DouyinPageResult<DouyinVideoCard> cached = fromCache(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        CaptureRequest request = new CaptureRequest();
        request.setPageUrl("https://www.douyin.com/video/" + awemeId);
        request.setMarkers(List.of("/aweme/related"));
        request.setMinResponses(1);
        request.setTimeoutMs(25_000);
        request.setScrollTimes(Math.max(0, page));
        List<CapturedBody> bodies = browserClient.capture(request);
        DouyinPageResult<DouyinVideoCard> result =
                parseAwemeList(nthBody(bodies, "/aweme/related", page));
        putCache(cacheKey, result);
        return result;
    }

    // ==================== 刷视频流（频道流 + 推荐流） ====================

    /**
     * 刷视频流。
     * <p>
     * 频道流（精选频道页 module/feed）游客即可抓取：recommend 映射“精选-全部”综合流，
     * 其余为体育/音乐/游戏等精选频道。抖音 PC 网页的个性化推荐 Tab（tab/feed）对网页端
     * 不返回内容，故推荐位以精选全量流实现。
     * </p>
     *
     * @param channel 频道（recommend=推荐综合流，其余为精选频道英文标识）
     * @param page    页号（0 起）
     * @return 视频分页
     */
    public DouyinPageResult<DouyinVideoCard> feed(String channel, int page)
    {
        String slug = channel == null || channel.isBlank() ? "recommend" : channel.trim().toLowerCase();
        String cacheKey = "feed:" + slug + ":" + page;
        DouyinPageResult<DouyinVideoCard> cached = fromCache(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        String pageUrl;
        if ("recommend".equals(slug))
        {
            pageUrl = "https://www.douyin.com/jingxuan";
        }
        else
        {
            if (!slug.matches("^[a-z]{2,20}$"))
            {
                throw new ServiceException("频道无效");
            }
            pageUrl = "https://www.douyin.com/jingxuan/" + slug;
        }
        CaptureRequest request = new CaptureRequest();
        request.setPageUrl(pageUrl);
        request.setMarkers(List.of("/module/feed/"));
        request.setMinResponses(1);
        request.setTimeoutMs(30_000);
        request.setScrollTimes(Math.max(0, page));
        DouyinPageResult<DouyinVideoCard> result = null;
        for (int attempt = 0; attempt < 2; attempt++)
        {
            List<CapturedBody> bodies = browserClient.capture(request);
            result = parseAwemeList(nthBody(bodies, "/module/feed/", page));
            if (!result.getItems().isEmpty())
            {
                break;
            }
            log.info("视频流抓取为空，自动重试一次：channel={}", slug);
        }
        if (result.getItems().isEmpty())
        {
            throw new ServiceException("视频流加载失败，可能被抖音安全校验拦截，请稍后重试");
        }
        putCache(cacheKey, result);
        return result;
    }

    /** 无参数进入推荐流时借用的“安全页”作品 ID（只用于建立身份，不消费其数据）。 */
    private static final String FALLBACK_SAFE_AWEME_ID = "7705975349372390656";

    // ==================== 作者主页 ====================

    /**
     * 作者主页：资料 + 作品分页。
     *
     * @param secUid 作者 sec_uid
     * @param page   作品页号（0 起）
     * @return 主页数据
     */
    public DouyinUserPage user(String secUid, int page)
    {
        if (secUid == null || !secUid.matches("^[A-Za-z0-9_\\-]{8,128}$"))
        {
            throw new ServiceException("作者标识无效");
        }
        String cacheKey = "user:" + secUid + ":" + page;
        DouyinUserPage cached = fromCache(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        CaptureRequest request = new CaptureRequest();
        request.setPageUrl("https://www.douyin.com/user/" + secUid);
        request.setMarkers(List.of("/user/profile/other/", "/aweme/post/"));
        request.setMinResponses(2);
        request.setTimeoutMs(30_000);
        request.setScrollTimes(Math.max(0, page));
        List<CapturedBody> bodies = browserClient.capture(request);

        DouyinUserPage userPage = new DouyinUserPage();
        JsonNode profile = findBody(bodies, "/user/profile/other/");
        userPage.setProfile(parseProfile(profile));
        userPage.setPosts(parseAwemeList(nthBody(bodies, "/aweme/post/", page)));
        if (userPage.getProfile() == null && userPage.getPosts().getItems().isEmpty())
        {
            throw new ServiceException("作者主页加载失败，可能被抖音安全校验拦截，请稍后重试或先登录抖音");
        }
        putCache(cacheKey, userPage);
        return userPage;
    }

    // ==================== 搜索 ====================

    /**
     * 搜索视频。
     * <p>
     * 直连搜索结果页会触发验证码中间页，这里在安全页上操作抖音自己的搜索框
     * （输入关键词 → 点击官方搜索按钮 → SPA 站内跳转），截获搜索结果流接口。
     * 搜索结果为抖音登录墙能力，需一次性登录后使用。
     * </p>
     *
     * @param keyword 关键词
     * @param page    页号（0 起）
     * @return 视频分页
     */
    public DouyinPageResult<DouyinVideoCard> search(String keyword, int page)
    {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty() || trimmed.length() > 30)
        {
            throw new ServiceException("搜索关键词无效（1-30 个字符）");
        }
        String cacheKey = "search:" + trimmed + ":" + page;
        DouyinPageResult<DouyinVideoCard> cached = fromCache(cacheKey);
        if (cached != null)
        {
            return cached;
        }
        if (!isLoggedInCached())
        {
            throw new ServiceException("搜索需要登录抖音后使用，请点击“浏览器登录”完成一次性登录");
        }
        DouyinPageResult<DouyinVideoCard> result = captureSearch(trimmed, page);
        if (result.getItems().isEmpty())
        {
            // 搜索流程偶发被抖动打断，换新标签页自动重抓一次
            log.info("搜索结果为空，自动重试一次：keyword={}", trimmed);
            result = captureSearch(trimmed, page);
        }
        if (result.getItems().isEmpty())
        {
            log.warn("搜索未截获到有效结果：keyword={}", trimmed);
            throw new ServiceException("搜索失败（可能触发抖音安全校验），请稍后重试，或在“浏览器登录”中登录抖音后使用");
        }
        putCache(cacheKey, result);
        return result;
    }

    /** 执行一次搜索抓取并解析（不抛异常，失败返回空结果）。 */
    private DouyinPageResult<DouyinVideoCard> captureSearch(String keyword, int page)
    {
        CaptureRequest request = new CaptureRequest();
        request.setPageUrl("https://www.douyin.com/video/" + FALLBACK_SAFE_AWEME_ID);
        // 宽匹配搜索接口（点搜索按钮后的实际路径不固定），排除联想词/热搜榜等干扰接口
        request.setMarkers(List.of("search", "!search/sug", "!suggest", "!hot/search"));
        request.setMinResponses(1);
        request.setNavDelayMs(2_500);
        request.setMinWaitMs(10_000);
        request.setTimeoutMs(28_000);
        request.setActions(List.of(CaptureAction.eval(searchFlowScript(keyword))));
        request.setScrollTimes(Math.max(0, page));
        List<CapturedBody> bodies = browserClient.capture(request);
        return parseSearchBody(firstUsableSearchBody(bodies, page));
    }

    /** 挑选搜索结果响应：跳过空结果等无效响应，取第 page 个可用结果（原始流式文本）。 */
    private String firstUsableSearchBody(List<CapturedBody> bodies, int page)
    {
        List<String> usable = new ArrayList<>();
        for (CapturedBody body : bodies)
        {
            if (!body.marker().contains("search") || body.body().isBlank())
            {
                continue;
            }
            if (!parseSearchBody(body.body()).getItems().isEmpty())
            {
                usable.add(body.body());
            }
        }
        if (usable.isEmpty())
        {
            return null;
        }
        return usable.get(Math.min(Math.max(page, 0), usable.size() - 1));
    }

    /**
     * 搜索结果流解析：抖音搜索接口为“十六进制长度前缀 + JSON”的分块流，
     * 逐块提取顶层 JSON 对象后合并结果条目（兼容 data[].aweme_info、data[] 直接条目与 aweme_list）。
     */
    private DouyinPageResult<DouyinVideoCard> parseSearchBody(String raw)
    {
        DouyinPageResult<DouyinVideoCard> result = new DouyinPageResult<>();
        if (raw == null || raw.isBlank())
        {
            return result;
        }
        List<DouyinVideoCard> items = new ArrayList<>();
        for (String object : extractJsonObjects(raw))
        {
            try
            {
                JsonNode node = MAPPER.readTree(object);
                for (JsonNode entry : node.path("data"))
                {
                    JsonNode info = entry.path("aweme_info");
                    JsonNode item = info.isObject() ? info : entry;
                    if (item.path("aweme_id").asText("").isBlank() && item.path("video").isObject() == false)
                    {
                        continue;
                    }
                    DouyinVideoCard card = toCard(item, null);
                    if (card != null)
                    {
                        items.add(card);
                    }
                }
                for (JsonNode entry : node.path("aweme_list"))
                {
                    DouyinVideoCard card = toCard(entry, null);
                    if (card != null)
                    {
                        items.add(card);
                    }
                }
                if (node.path("has_more").asBoolean(false) || node.path("has_more").asInt(0) == 1)
                {
                    result.setHasMore(true);
                }
                long cursor = node.path("cursor").asLong(0);
                if (cursor > 0)
                {
                    result.setCursor(cursor);
                }
            }
            catch (Exception exception)
            {
                // 跳过无法解析的块
            }
        }
        result.setItems(items);
        return result;
    }

    /** 从流式文本中提取所有顶层 JSON 对象（忽略长度前缀等夹杂文本，正确处理字符串转义）。 */
    private static List<String> extractJsonObjects(String body)
    {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (c == '\\')
                {
                    escaped = true;
                }
                else if (c == '"')
                {
                    inString = false;
                }
                continue;
            }
            if (c == '"')
            {
                inString = true;
            }
            else if (c == '{')
            {
                if (depth == 0)
                {
                    start = i;
                }
                depth++;
            }
            else if (c == '}')
            {
                depth--;
                if (depth == 0 && start >= 0)
                {
                    objects.add(body.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    /**
     * 搜索流程脚本（页面内状态机）：聚焦搜索框 → 输入关键词 → 反复点击官方搜索按钮直至站内导航完成。
     * 由应用自身完成站内导航（不触发验证码中间页）。关键词以 ASCII 转义内联，规避脚本传输编码问题。
     */
    private static String searchFlowScript(String keyword)
    {
        String encoded = java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return """
                (() => {
                  const kw = decodeURIComponent('%s');
                  const type = () => {
                    // 只认官方搜索框；渲染未就绪时返回 false 由外层重试，避免误打到其它输入框
                    const box = document.querySelector('[data-e2e="searchbar-input"]');
                    if (!box) return false;
                    box.focus();
                    const setter = Object.getOwnPropertyDescriptor(
                      window.HTMLInputElement.prototype, 'value').set;
                    setter.call(box, kw);
                    box.dispatchEvent(new Event('input', { bubbles: true }));
                    return box.value === kw;
                  };
                  const clickSearch = () => {
                    const btn = document.querySelector('[data-e2e="searchbar-button"]');
                    if (!btn) return false;
                    btn.click();
                    return true;
                  };
                  const navigated = () => (location.pathname || '').includes('/search');
                  let phase = 0;
                  let tries = 0;
                  const timer = setInterval(() => {
                    if (phase === 0) {
                      if (type()) { phase = 1; tries = 0; }
                      else if (++tries > 24) { clearInterval(timer); }
                    } else {
                      // 按钮点击偶发无效，持续点击直至检测到站内导航
                      if (navigated()) { clearInterval(timer); return; }
                      clickSearch();
                      if (++tries > 24) clearInterval(timer);
                    }
                  }, 500);
                  return 'search flow started';
                })()""".formatted(encoded);
    }

    // ==================== 浏览器登录 ====================

    /** 登录状态检查结果缓存（毫秒）：避免频繁建立临时标签页查询 Cookie。 */
    private static final long LOGIN_STATUS_CACHE_MS = 60_000L;

    private volatile long loginStatusCheckedAt;

    private volatile boolean loginStatusCached;

    /** 登录态（带 60 秒缓存）。 */
    private boolean isLoggedInCached()
    {
        if (System.currentTimeMillis() - loginStatusCheckedAt < LOGIN_STATUS_CACHE_MS)
        {
            return loginStatusCached;
        }
        loginStatusCached = browserClient.isDouyinLoggedIn();
        loginStatusCheckedAt = System.currentTimeMillis();
        return loginStatusCached;
    }

    /** 打开可见浏览器窗口，供用户一次性登录抖音。 */
    public void startBrowserLogin()
    {
        browserClient.startLoginBrowser();
    }

    /** 关闭登录窗口并恢复无头模式。 */
    public void closeBrowserLogin()
    {
        browserClient.closeLoginBrowser();
        loginStatusCheckedAt = 0;
    }

    /**
     * 登录状态（实时查询，不走缓存）。
     *
     * @return [是否已登录抖音, 登录窗口是否打开]
     */
    public boolean[] browserLoginStatus()
    {
        boolean loggedIn = browserClient.isDouyinLoggedIn();
        loginStatusCached = loggedIn;
        loginStatusCheckedAt = System.currentTimeMillis();
        return new boolean[] {loggedIn, browserClient.isLoginWindowMode() };
    }

    // ==================== 抓取结果解析 ====================

    /** 在抓取结果中查找首个命中 marker 的响应并解析 JSON。 */
    private static JsonNode findBody(List<CapturedBody> bodies, String marker)
    {
        for (CapturedBody body : bodies)
        {
            if (marker.equals(body.marker()) && !body.body().isBlank())
            {
                try
                {
                    return MAPPER.readTree(body.body());
                }
                catch (Exception exception)
                {
                    return null;
                }
            }
        }
        return null;
    }

    /** 取同 marker 的第 page 个响应（不足时取最后一个）。 */
    private static JsonNode nthBody(List<CapturedBody> bodies, String marker, int page)    {
        List<JsonNode> matched = new ArrayList<>();
        for (CapturedBody body : bodies)
        {
            if (marker.equals(body.marker()) && !body.body().isBlank())
            {
                try
                {
                    matched.add(MAPPER.readTree(body.body()));
                }
                catch (Exception exception)
                {
                    // 跳过无法解析的响应
                }
            }
        }
        if (matched.isEmpty())
        {
            return null;
        }
        return matched.get(Math.min(Math.max(page, 0), matched.size() - 1));
    }

    /** 通用视频列表解析：aweme_list，或 data[].aweme_info（搜索结果）。 */
    private DouyinPageResult<DouyinVideoCard> parseAwemeList(JsonNode body)
    {
        DouyinPageResult<DouyinVideoCard> result = new DouyinPageResult<>();
        if (body == null)
        {
            return result;
        }
        List<DouyinVideoCard> items = new ArrayList<>();
        for (JsonNode item : body.path("aweme_list"))
        {
            DouyinVideoCard card = toCard(item, null);
            if (card != null)
            {
                items.add(card);
            }
        }
        result.setItems(items);
        result.setHasMore(body.path("has_more").asBoolean(false) || body.path("has_more").asInt(0) == 1);
        result.setCursor(body.path("cursor").asLong(body.path("max_cursor").asLong(0)));
        return result;
    }

    private DouyinPageResult<DouyinComment> parseComments(JsonNode body)
    {
        DouyinPageResult<DouyinComment> result = new DouyinPageResult<>();
        if (body == null)
        {
            return result;
        }
        List<DouyinComment> items = new ArrayList<>();
        for (JsonNode item : body.path("comments"))
        {
            DouyinComment comment = new DouyinComment();
            comment.setCid(item.path("cid").asText(""));
            comment.setText(item.path("text").asText(""));
            comment.setNickname(item.path("user").path("nickname").asText(""));
            comment.setAvatar(firstUrl(item.path("user").path("avatar_thumb").path("url_list")));
            comment.setLikeCount(item.path("digg_count").asLong(0));
            comment.setCreateTime(item.path("create_time").asLong(0));
            comment.setReplyCount(item.path("reply_comment_total").asLong(0));
            items.add(comment);
        }
        result.setItems(items);
        result.setHasMore(body.path("has_more").asBoolean(false) || body.path("has_more").asInt(0) == 1);
        result.setCursor(body.path("cursor").asLong(0));
        return result;
    }

    private List<DouyinDanmakuItem> parseDanmaku(JsonNode body)
    {
        List<DouyinDanmakuItem> items = new ArrayList<>();
        if (body == null)
        {
            return items;
        }
        for (JsonNode item : body.path("danmaku_list"))
        {
            DouyinDanmakuItem danmaku = new DouyinDanmakuItem();
            danmaku.setOffsetMs(item.path("offset_time").asLong(0));
            danmaku.setText(item.path("text").asText(""));
            if (!danmaku.getText().isBlank())
            {
                items.add(danmaku);
            }
        }
        items.sort(java.util.Comparator.comparing(DouyinDanmakuItem::getOffsetMs));
        return items;
    }

    private DouyinUserProfile parseProfile(JsonNode body)
    {
        JsonNode user = body == null ? null : body.path("user");
        if (user == null || !user.isObject())
        {
            return null;
        }
        DouyinUserProfile profile = new DouyinUserProfile();
        profile.setSecUid(user.path("sec_uid").asText(""));
        profile.setNickname(user.path("nickname").asText(""));
        profile.setAvatar(firstUrl(user.path("avatar_thumb").path("url_list")));
        profile.setSignature(user.path("signature").asText(""));
        profile.setFollowerCount(user.path("follower_count").asLong(0));
        profile.setTotalFavorited(user.path("total_favorited").asLong(0));
        profile.setAwemeCount(user.path("aweme_count").asLong(0));
        return profile;
    }

    /** 抖音作品条目 → 视频卡片（顺带发放播放令牌）。 */
    private DouyinVideoCard toCard(JsonNode item, String fallbackAwemeId)
    {
        String awemeId = item.path("aweme_id").asText("");
        if (awemeId.isBlank())
        {
            awemeId = fallbackAwemeId == null ? "" : fallbackAwemeId;
        }
        if (awemeId.isBlank())
        {
            return null;
        }
        DouyinVideoCard card = new DouyinVideoCard();
        card.setAwemeId(awemeId);
        card.setTitle(item.path("desc").asText(""));
        card.setAuthor(item.path("author").path("nickname").asText(""));
        card.setAuthorSecUid(item.path("author").path("sec_uid").asText(""));
        card.setAuthorAvatar(firstUrl(item.path("author").path("avatar_thumb").path("url_list")));
        JsonNode video = item.path("video");
        card.setCoverUrl(firstOr(firstUrl(video.path("cover").path("url_list")),
                firstUrl(video.path("origin_cover").path("url_list"))));
        card.setDurationMs(video.path("duration").asLong(item.path("duration").asLong(0)));
        JsonNode statistics = item.path("statistics");
        card.setLikeCount(statistics.path("digg_count").asLong(0));
        card.setCommentCount(statistics.path("comment_count").asLong(0));
        card.setPlayCount(statistics.path("play_count").asLong(0));
        List<String> playUrls = urlList(video.path("play_addr").path("url_list"));
        if (playUrls.isEmpty())
        {
            playUrls = urlList(video.path("download_addr").path("url_list"));
        }
        if (!playUrls.isEmpty())
        {
            card.setPlayToken(mintPlayToken(awemeId, playUrls));
        }
        return card;
    }

    // ==================== 通用工具 ====================

    private String mintPlayToken(String awemeId, List<String> playUrls)
    {
        cleanupExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        playSessions.put(token, new PlaySession(awemeId, playUrls,
                System.currentTimeMillis() + PLAY_SESSION_TTL_MS));
        return token;
    }

    /**
     * 刷新播放令牌对应的播放直链。
     * <p>
     * 抖音 CDN 直链带签名且时效较短，过期后转发会 403；此时按作品 ID 重新抓取新鲜直链，
     * 原令牌原地换新，前端播放无感知。
     * </p>
     *
     * @param token 播放令牌
     * @return 新的播放直链列表
     */
    public List<String> refreshPlayUrls(String token)
    {
        PlaySession session = token == null ? null : playSessions.get(token);
        if (session == null || session.isExpired() || session.getAwemeId() == null)
        {
            throw new ServiceException("播放地址已过期，请重新解析");
        }
        MediaInfo media = browserClient.fetch(session.getAwemeId());
        if (media.getPlayUrls().isEmpty())
        {
            throw new ServiceException("刷新播放地址失败，请重新解析视频");
        }
        session.refresh(media.getPlayUrls());
        return session.getPlayUrls();
    }

    private void requireAwemeId(String awemeId)
    {
        if (!DouyinShareLinkParser.isValidAwemeId(awemeId))
        {
            throw new ServiceException("抖音作品 ID 无效");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T fromCache(String key)
    {
        CacheEntry<?> entry = captureCache.get(key);
        if (entry == null)
        {
            return null;
        }
        if (entry.isExpired())
        {
            captureCache.remove(key);
            return null;
        }
        return (T) entry.value();
    }

    private <T> void putCache(String key, T value)
    {
        captureCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        captureCache.put(key, new CacheEntry<>(System.currentTimeMillis() + CAPTURE_CACHE_TTL_MS, value));
    }

    private void cleanupExpiredSessions()
    {
        playSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private static String firstOr(String primary, String fallback)
    {
        return primary != null ? primary : fallback;
    }

    private static String firstUrl(JsonNode urlList)
    {
        List<String> urls = urlList(urlList);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private static List<String> urlList(JsonNode urlList)
    {
        List<String> urls = new ArrayList<>();
        if (urlList != null && urlList.isArray())
        {
            for (JsonNode item : urlList)
            {
                String value = item.asText("");
                if (value.startsWith("http"))
                {
                    urls.add(value);
                }
            }
        }
        return urls;
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

    private static final class HttpHolder
    {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
    }
}
