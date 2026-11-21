package com.lingXi.aiVedio.tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.common.exception.ServiceException;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 抖音网页数据抓取客户端（Chrome DevTools Protocol 驱动本机 Edge/Chrome）。
 * <p>
 * 抖音网页端的数据接口需要浏览器内生成的签名参数，纯 HTTP 请求会被安全服务降级甚至弹验证码。
 * 这里以无头模式驱动本机浏览器执行抖音自己的页面脚本，并从网络层截获接口响应。
 * 实测发现“验证码中间页”只拦截整页直连（首页/搜索直连），站内 SPA 跳转不重新加载文档，
 * 因此抓取统一采用“先打开安全页面，再站内跳转/滚动”的方式，避免触发风控。
 * </p>
 * <p>
 * 浏览器进程随首次使用懒启动并常驻复用，抓取请求串行执行；
 * 可通过环境变量 {@code LINGXI_BROWSER_PATH} 指定浏览器可执行文件。
 * 登录会话（一次性扫码/验证码登录）会临时切换为可见窗口，登录态保留在同一运行目录长期复用。
 * </p>
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Slf4j
@Component
public class DouyinBrowserVideoClient
{
    /** 视频信息接口的响应匹配串。 */
    private static final String DETAIL_API_MARKER = "/aweme/v1/web/aweme/detail";

    /** 解析超时（毫秒）：页面加载 + 接口响应。 */
    private static final long FETCH_TIMEOUT_MS = 30_000L;

    /** 浏览器可执行文件候选路径。 */
    private static final String[] BROWSER_CANDIDATES = {
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            "/usr/bin/microsoft-edge", "/usr/bin/google-chrome",
            "/opt/microsoft/msedge/msedge", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"};

    private static final Pattern VIDEO_SRC_PATTERN = Pattern.compile("^https?://.+");

    /** 登录窗口落地页使用的“安全页”作品 ID（仅用于打开页面，不消费其数据）。 */
    private static final String LOGIN_LANDING_AWEME_ID = "7705975349372390656";

    private static final long LOGIN_WINDOW_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2);

    private static final long BROWSER_START_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(15);

    private static final String COMMENT_API = "https://www.douyin.com/aweme/v1/web/comment/list/?";

    private static final Pattern COMMENT_CURSOR = Pattern.compile("[?&]cursor=(\\d+)(?:&|$)");

    private static final Pattern COMMENT_AWEME = Pattern.compile("[?&]aweme_id=(\\d+)(?:&|$)");

    private static final String SCROLL_COMMENTS = """
            (() => {
              const list = document.querySelector('[data-e2e="comment-list"]');
              if (!list) return;
              const cover = list.querySelector('.video-comment-cover');
              if (cover?.getClientRects().length && cover.textContent.includes('登录后可查看更多评论')) {
                return 'login-required';
              }
              for (let node = list; node; node = node.parentElement) {
                if (node.clientHeight > 0 && node.scrollHeight > node.clientHeight + 20
                    && ['auto', 'scroll'].includes(getComputedStyle(node).overflowY)) {
                  const bottom = node.scrollHeight - node.clientHeight;
                  if (node.scrollTop >= bottom - 1) {
                    // 已在底部时，先离开加载区域，再进入，避免两次滚动被合并。
                    node.scrollTop = Math.max(0, bottom - node.clientHeight * 2);
                    requestAnimationFrame(() => requestAnimationFrame(() => {
                      node.scrollTop = node.scrollHeight;
                    }));
                  } else {
                    node.scrollTop = bottom;
                  }
                  return;
                }
              }
              list.lastElementChild?.scrollIntoView({block: 'end'});
            })()
            """;

    private static final Pattern BROWSER_DEBUG_PATH = Pattern.compile("/devtools/browser/[0-9a-fA-F-]+");

    /**
     * 站内跳转到“推荐”Tab 的脚本（SPA 跳转不触发验证码中间页），页面内自动重试适配渲染时机。
     * 刷推荐流与唤起登录面板都从这里进入。
     */
    public static final String RECOMMEND_NAV_CLICK_SCRIPT = """
            (() => {
              const tryClick = () => {
                const el = Array.from(document.querySelectorAll('a')).find(
                  e => (e.textContent || '').trim() === '推荐');
                if (!el) return false;
                el.click();
                return true;
              };
              if (tryClick()) return 'clicked';
              let tries = 0;
              const timer = setInterval(() => {
                if (tryClick() || ++tries > 30) clearInterval(timer);
              }, 500);
              return 'retrying';
            })()""";

    /**
     * 唤起扫码登录面板的脚本：先站内跳转“推荐”Tab（未登录自动弹登录层），
     * 若仍未出现扫码面板则直接点击页面内“登录”入口。页面内自动重试。
     */
    public static final String OPEN_LOGIN_PANEL_SCRIPT = """
            (() => {
              const hasPanel = () =>
                document.body && document.body.innerText.includes('扫码登录');
              const clickRecommend = () => {
                const el = Array.from(document.querySelectorAll('a')).find(
                  e => (e.textContent || '').trim() === '推荐');
                if (!el) return false;
                el.click();
                return true;
              };
              const clickLogin = () => {
                const cands = Array.from(document.querySelectorAll('button, a, div, span'))
                  .filter(e => e.offsetParent !== null && (e.textContent || '').trim() === '登录');
                const el = cands[cands.length - 1];
                if (!el) return false;
                el.click();
                return true;
              };
              let phase = 0;
              let tries = 0;
              const timer = setInterval(() => {
                if (hasPanel()) { clearInterval(timer); return; }
                if (phase === 0) {
                  if (clickRecommend() || ++tries > 20) { phase = 1; tries = 0; }
                } else {
                  clickLogin();
                  if (++tries > 12) clearInterval(timer);
                }
              }, 500);
              return 'login panel flow started';
            })()""";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 浏览器进程与调试端口的生命周期锁（抓取串行执行）。 */
    private final Object browserLock = new Object();

    private ProcessHandle browserProcess;

    private int debugPort;

    private String browserWebSocketUrl;

    /** 只发布已就绪的无头实例，评论标签可以和推荐抓取并行。 */
    private volatile BrowserEndpoint headlessEndpoint;
    private final Object commentLock = new Object();
    private final ScheduledExecutorService commentCleanup = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "douyin-comment-cleanup");
        thread.setDaemon(true);
        return thread;
    });
    private CommentTab commentTab;
    private ScheduledFuture<?> commentExpiry;

    private record BrowserEndpoint(ProcessHandle process, int port)
    {
    }

    private record CommentTab(String awemeId, BrowserEndpoint browser, Tab tab, CdpSession session,
            List<JsonNode> pages)
    {
    }

    /** 当前是否以可见（登录）模式运行。 */
    private boolean loginWindowMode;

    /** 视频信息（含播放直链列表），供“粘贴链接解析”旧接口使用。 */
    @Data
    public static class MediaInfo
    {
        private String title;
        private String author;
        private String authorAvatar;
        private String coverUrl;
        private Long durationMs;
        private List<String> playUrls = new ArrayList<>();
        private List<String> imageUrls = new ArrayList<>();
    }

    /** 抓取动作：eval 执行 JS、input 输入文本、enter 回车。 */
    public record CaptureAction(String type, String value)
    {
        public static CaptureAction eval(String script)
        {
            return new CaptureAction("eval", script);
        }

        public static CaptureAction input(String text)
        {
            return new CaptureAction("input", text);
        }

        public static CaptureAction enter()
        {
            return new CaptureAction("enter", null);
        }
    }

    /** 抓取请求。 */
    @Data
    public static class CaptureRequest
    {
        /** 要打开的抖音页面地址 */
        private String pageUrl;

        /** 要截获的接口 URL 特征串（不区分大小写，命中任一即可） */
        private List<String> markers = new ArrayList<>();

        /** 期望截获的最少响应数（达到后停止等待） */
        private int minResponses = 1;

        /** 最短等待时间（毫秒）：即使已达到 minResponses 也等满该时长，防止页面加载期的干扰接口提前收线 */
        private long minWaitMs = 0;

        /** 整体等待超时（毫秒） */
        private long timeoutMs = 25_000L;

        /** 页面打开后的稳定等待（毫秒）：执行动作/收集响应前的页面渲染时间 */
        private long navDelayMs = 5_000L;

        /** 页面加载后依次执行的动作（站内跳转、聚焦输入框等） */
        private List<CaptureAction> actions = new ArrayList<>();

        /** 达到 minResponses 后继续滚动加载的次数（翻页用） */
        private int scrollTimes;

        /** 必须截获到的响应标记（如详情接口）：未命中前不算等待完成，防止被先到的次要接口提前收线 */
        private String requiredMarker;

        /** 每次滚动的间隔（毫秒） */
        private long scrollIntervalMs = 2_200L;

        /** 翻页滚动脚本（默认滚窗口；评论区等独立滚动容器需自定义） */
        private String scrollScript;
    }

    /** 截获的接口响应体（body 为原始 JSON 文本）。 */
    public record CapturedBody(String marker, String body)
    {
    }

    /**
     * 打开抖音页面并截获匹配接口的响应体。
     *
     * @param request 抓取请求
     * @return 按到达顺序排列的响应体
     * @throws ServiceException 浏览器不可用或抓取失败
     */
    public List<CapturedBody> capture(CaptureRequest request)
    {
        return capture(request, null);
    }

    /** 在同一标签页沿抖音自己的评论游标继续滚动。 */
    public JsonNode captureComments(String awemeId, int page)
    {
        synchronized (commentLock)
        {
            if (commentExpiry != null)
            {
                commentExpiry.cancel(false);
            }
            try
            {
                BrowserEndpoint browser = headlessEndpoint;
                if (browser == null || !browser.process().isAlive())
                {
                    synchronized (browserLock)
                    {
                        requireCaptureMode();
                        ensureBrowser(false, null);
                        browser = headlessEndpoint;
                    }
                }
                if (commentTab == null || commentTab.browser() != browser || !awemeId.equals(commentTab.awemeId())
                        || commentTab.session().webSocket.isInputClosed())
                {
                    closeCommentTab();
                    commentTab = openCommentTab(awemeId, browser);
                }
                List<JsonNode> pages = commentTab.pages();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
                while (pages.size() <= page)
                {
                    long cursor = 0;
                    if (!pages.isEmpty())
                    {
                        JsonNode previous = pages.get(pages.size() - 1);
                        if (!previous.path("has_more").asBoolean() && previous.path("has_more").asInt() != 1)
                        {
                            return objectMapper.createObjectNode().put("has_more", 0);
                        }
                        cursor = previous.path("cursor").asLong();
                        if (cursor <= 0)
                        {
                            throw new ServiceException("抖音没有返回有效的评论游标");
                        }
                    }
                    pages.add(commentTab.session().awaitCommentPage(cursor, deadline));
                }
                return pages.get(page);
            }
            catch (ServiceException exception)
            {
                throw exception;
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
                throw new ServiceException("评论加载已中断");
            }
            catch (Exception exception)
            {
                log.debug("加载抖音评论失败：{}", exception.toString());
                throw new ServiceException("评论加载失败，请稍后重试");
            }
            finally
            {
                if (commentTab != null && commentTab.pages().isEmpty())
                {
                    closeCommentTab();
                }
                if (commentTab != null)
                {
                    commentExpiry = commentCleanup.schedule(this::expireCommentTab, 2, TimeUnit.MINUTES);
                }
            }
        }
    }

    private CommentTab openCommentTab(String awemeId, BrowserEndpoint browser) throws Exception
    {
        Tab tab = createTab(browser.port());
        CdpSession session = null;
        try
        {
            session = new CdpSession(tab.getWebSocketUrl(), objectMapper, 3);
            session.commentAwemeId = awemeId;
            session.sendCommand("Network.enable", null, 3);
            session.sendCommand("Page.navigate", Map.of("url", "https://www.douyin.com/video/" + awemeId), 3);
            return new CommentTab(awemeId, browser, tab, session, new ArrayList<>());
        }
        catch (Exception exception)
        {
            if (session != null)
            {
                session.close();
            }
            closeTabQuietly(tab.getId(), browser.port());
            throw exception;
        }
    }

    private void expireCommentTab()
    {
        synchronized (commentLock)
        {
            if (commentExpiry != null && commentExpiry.getDelay(TimeUnit.MILLISECONDS) <= 0)
            {
                closeCommentTab();
            }
        }
    }

    private void closeCommentTab()
    {
        if (commentTab != null)
        {
            commentTab.session().close();
            if (commentTab.browser() == headlessEndpoint)
            {
                closeTabQuietly(commentTab.tab().getId(), commentTab.browser().port());
            }
            commentTab = null;
        }
    }

    /** 打开官方可见页面，让用户完成验证，只在收到实际结果后结束。 */
    public List<CapturedBody> captureAfterUserVerification(CaptureRequest request, Predicate<String> resultReceived)
    {
        return capture(request, resultReceived);
    }

    private List<CapturedBody> capture(CaptureRequest request, Predicate<String> resultReceived)
    {
        synchronized (browserLock)
        {
            requireCaptureMode();
            boolean interactive = resultReceived != null;
            String tabId = null;
            try
            {
                if (interactive)
                {
                    stopBrowser();
                }
                ensureBrowser(interactive, null);
                Tab tab = interactive ? findFirstPageTab() : null;
                if (tab == null)
                {
                    tab = createTab();
                }
                tabId = tab.getId();
                try (CdpSession session = new CdpSession(tab.getWebSocketUrl(), objectMapper))
                {
                    session.sendCommand("Network.enable", null);
                    session.sendCommand("Page.enable", null);
                    // 必须在导航前挂好拦截，否则页面加载期的响应会漏掉
                    session.beginCapture(request.getMarkers());
                    session.sendCommand("Page.navigate", Map.of("url", request.getPageUrl()));
                    sleepQuietly(Math.max(500, request.getNavDelayMs()));
                    for (CaptureAction action : request.getActions())
                    {
                        session.perform(action);
                        sleepQuietly(2_500);
                    }
                    return interactive
                            ? session.waitForVerifiedResponse(request.getTimeoutMs(), resultReceived)
                            : session.waitCaptures(request);
                }
            }
            catch (ServiceException exception)
            {
                throw exception;
            }
            catch (Exception exception)
            {
                log.warn("抖音数据抓取失败：{}", exception.toString());
                throw new ServiceException("抓取抖音数据失败：" + exception.getMessage());
            }
            finally
            {
                if (interactive)
                {
                    stopBrowser();
                }
                else
                {
                    closeTabQuietly(tabId);
                }
            }
        }
    }

    /**
     * 抓取指定作品的视频信息（兼容“粘贴链接解析”旧流程）。
     *
     * @param awemeId 作品 ID
     * @return 视频信息
     */
    public MediaInfo fetch(String awemeId)
    {
        if (!DouyinShareLinkParser.isValidAwemeId(awemeId))
        {
            throw new ServiceException("抖音作品 ID 无效");
        }
        synchronized (browserLock)
        {
            requireCaptureMode();
            String tabId = null;
            try
            {
                ensureBrowser(false, null);
                Tab tab = createTab();
                tabId = tab.getId();
                try (CdpSession session = new CdpSession(tab.getWebSocketUrl(), objectMapper))
                {
                    session.sendCommand("Network.enable", null);
                    session.sendCommand("Page.enable", null);
                    session.sendCommand("Page.navigate",
                            Map.of("url", "https://www.douyin.com/video/" + awemeId));
                    MediaInfo info = session.waitForMedia();
                    if (info == null || info.getPlayUrls().isEmpty() && info.getImageUrls().isEmpty())
                    {
                        throw new ServiceException("未能从抖音页面解析出视频数据，可能被安全校验拦截，请稍后重试");
                    }
                    return info;
                }
            }
            catch (ServiceException exception)
            {
                throw exception;
            }
            catch (Exception exception)
            {
                log.warn("抖音视频信息抓取失败：{}", exception.toString());
                throw new ServiceException("解析抖音视频失败：" + exception.getMessage());
            }
            finally
            {
                closeTabQuietly(tabId);
            }
        }
    }

    // ==================== 登录会话 ====================

    private void requireCaptureMode()
    {
        if (loginWindowMode)
        {
            throw new ServiceException("请先完成抖音登录，或关闭登录窗口后重试");
        }
    }

    /** 打开登录窗口，使用同一配置目录保存登录态。 */
    public void startLoginBrowser()
    {
        synchronized (browserLock)
        {
            if (loginWindowMode && browserProcess != null && browserProcess.isAlive())
            {
                return;
            }
            loginWindowMode = false;
            stopBrowser();
            try
            {
                ensureBrowser(true, "https://www.douyin.com/video/" + LOGIN_LANDING_AWEME_ID);
            }
            catch (IOException exception)
            {
                throw new ServiceException("浏览器启动失败：" + exception.getMessage());
            }
            loginWindowMode = true;
            ProcessHandle loginProcess = browserProcess;
            Thread watcher = new Thread(() -> watchLoginWindow(loginProcess), "douyin-login-window");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    /** 成功或超时后由服务端回收窗口，即使前端已停止轮询。 */
    private void watchLoginWindow(ProcessHandle loginProcess)
    {
        long deadline = System.nanoTime() + LOGIN_WINDOW_TIMEOUT_NANOS;
        boolean panelRequested = false;
        try
        {
            while (true)
            {
                Thread.sleep(2_000);
                synchronized (browserLock)
                {
                    if (!loginWindowMode || browserProcess != loginProcess
                            || !loginProcess.isAlive() || System.nanoTime() >= deadline || readLoginCookies())
                    {
                        return;
                    }
                    if (!panelRequested)
                    {
                        panelRequested = nudgeLoginPanel();
                    }
                }
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
        }
        finally
        {
            synchronized (browserLock)
            {
                if (loginWindowMode && browserProcess == loginProcess)
                {
                    closeLoginBrowser();
                }
            }
        }
    }

    private boolean nudgeLoginPanel()
    {
        Tab tab = findFirstPageTab();
        if (tab == null)
        {
            return false;
        }
        try (CdpSession session = new CdpSession(tab.getWebSocketUrl(), objectMapper))
        {
            session.perform(CaptureAction.eval(OPEN_LOGIN_PANEL_SCRIPT));
            return true;
        }
        catch (Exception exception)
        {
            log.debug("唤起登录面板失败（可手动点击页面内“登录”）：{}", exception.toString());
            return false;
        }
    }

    /** 关闭可见窗口，后续抓取按需启动无头浏览器。 */
    public void closeLoginBrowser()
    {
        synchronized (browserLock)
        {
            if (loginWindowMode)
            {
                stopBrowser();
                loginWindowMode = false;
            }
        }
    }

    /** 当前是否处于登录窗口模式。 */
    public boolean isLoginWindowMode()
    {
        synchronized (browserLock)
        {
            return loginWindowMode;
        }
    }

    public boolean isDouyinLoggedIn()
    {
        synchronized (browserLock)
        {
            try
            {
                if (!loginWindowMode)
                {
                    ensureBrowser(false, null);
                }
                boolean loggedIn = browserProcess.isAlive() && readLoginCookies();
                if (loginWindowMode && (loggedIn || !browserProcess.isAlive()))
                {
                    closeLoginBrowser();
                }
                return loggedIn;
            }
            catch (Exception exception)
            {
                log.warn("抖音登录态检查失败：{}", exception.toString());
                return false;
            }
        }
    }

    private boolean readLoginCookies()
    {
        try (CdpSession session = openBrowserSession())
        {
            JsonNode cookies = session.sendCommand("Storage.getCookies", null, 3).path("cookies");
            for (JsonNode cookie : cookies)
            {
                String domain = cookie.path("domain").asText();
                String name = cookie.path("name").asText();
                if (("douyin.com".equals(domain) || domain.endsWith(".douyin.com"))
                        && ("sid_tt".equals(name) || "sid_guard".equals(name))
                        && !cookie.path("value").asText().isBlank())
                {
                    return true;
                }
            }
        }
        catch (Exception exception)
        {
            log.debug("读取抖音登录态失败：{}", exception.toString());
        }
        return false;
    }

    private CdpSession openBrowserSession() throws Exception
    {
        return new CdpSession(browserWebSocketUrl, objectMapper, 2);
    }

    // ==================== 浏览器生命周期 ====================

    private void ensureBrowser(boolean headful, String startupUrl) throws IOException
    {
        if (browserProcess != null && browserProcess.isAlive())
        {
            if (ping())
            {
                return;
            }
            // 进程存活但调试端口未就绪：可能仍在启动或正在切换登录窗口，给宽限期，
            // 直接杀掉重启会造成连续重启抖动
            for (int i = 0; i < 20; i++)
            {
                sleepQuietly(500);
                if (ping())
                {
                    return;
                }
                if (!browserProcess.isAlive())
                {
                    break;
                }
            }
        }
        stopBrowser();
        Path profile = Path.of(System.getProperty("user.home"), ".lingxi", "douyin-browser");
        Files.createDirectories(profile);
        // 上一个实例异常退出后残留的进程会锁住配置目录，启动前按配置目录扫描清理自愈
        killAllOnProfile(profile);
        Path endpoint = profile.resolve("DevToolsActivePort");
        Files.deleteIfExists(endpoint);
        List<String> command = new ArrayList<>();
        String browserExecutable = findBrowserExecutable();
        command.add(browserExecutable);
        if (isWindows() && "msedge.exe".equalsIgnoreCase(Path.of(browserExecutable).getFileName().toString()))
        {
            // Edge 会把专用实例中的标签注册给 Windows，即使这些标签在无头窗口里。
            command.add("--disable-features=msWindowTabManagerPublic");
        }
        if (!headful)
        {
            command.add("--headless=new");
        }
        command.add("--disable-gpu");
        command.add("--disable-background-mode");
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--hide-crash-restore-bubble");
        command.add("--user-data-dir=" + profile);
        command.add("--remote-debugging-port=0");
        if (headful)
        {
            command.add("--start-maximized");
        }
        command.add(startupUrl == null || startupUrl.isBlank() ? "about:blank" : startupUrl);
        ProcessBuilder builder = new ProcessBuilder(command);
        // 浏览器自身输出写入日志文件，避免占用进程管道
        builder.redirectErrorStream(true);
        builder.redirectOutput(profile.resolve("browser.log").toFile());
        Process launcher = builder.start();
        browserProcess = launcher.toHandle();
        long deadline = System.nanoTime() + BROWSER_START_TIMEOUT_NANOS;
        while (System.nanoTime() < deadline)
        {
            // Windows Edge 的启动器可能提前正常退出，调试端口由接手的浏览器进程稍后写入。
            ProcessHandle actualBrowser = discoverBrowser(endpoint);
            if (actualBrowser != null)
            {
                browserProcess = actualBrowser;
                if (!headful)
                {
                    headlessEndpoint = new BrowserEndpoint(actualBrowser, debugPort);
                }
                log.info("抖音抓取浏览器已启动（{}），调试端口：{}", headful ? "登录窗口" : "无头", debugPort);
                return;
            }
            if (!launcher.isAlive() && launcher.exitValue() != 0)
            {
                break;
            }
            sleepQuietly(250);
        }
        stopBrowser();
        killAllOnProfile(profile);
        throw new IOException("浏览器启动超时或异常退出");
    }

    private ProcessHandle discoverBrowser(Path endpoint)
    {
        try
        {
            List<String> lines = Files.readAllLines(endpoint);
            if (lines.size() < 2 || !BROWSER_DEBUG_PATH.matcher(lines.get(1)).matches())
            {
                return null;
            }
            int port = Integer.parseInt(lines.get(0));
            if (port < 1 || port > 65535)
            {
                return null;
            }
            debugPort = port;
            browserWebSocketUrl = "ws://127.0.0.1:" + port + lines.get(1);
            try (CdpSession session = openBrowserSession())
            {
                for (JsonNode process : session.sendCommand("SystemInfo.getProcessInfo", null, 2).path("processInfo"))
                {
                    if ("browser".equalsIgnoreCase(process.path("type").asText()))
                    {
                        return ProcessHandle.of(process.path("id").asLong())
                                .filter(ProcessHandle::isAlive).orElse(null);
                    }
                }
            }
        }
        catch (NoSuchFileException exception)
        {
            // 浏览器还未写出调试端口。
        }
        catch (Exception exception)
        {
            log.debug("读取浏览器调试端口或进程失败：{}", exception.toString());
        }
        return null;
    }

    private boolean ping()
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + debugPort + "/json/version"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = HttpHolder.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && browserWebSocketUrl.equals(
                    objectMapper.readTree(response.body()).path("webSocketDebuggerUrl").asText());
        }
        catch (Exception exception)
        {
            return false;
        }
    }

    @PreDestroy
    public void shutdown()
    {
        synchronized (browserLock)
        {
            stopBrowser();
            loginWindowMode = false;
        }
        synchronized (commentLock)
        {
            closeCommentTab();
            commentCleanup.shutdownNow();
        }
    }

    private void stopBrowser()
    {
        headlessEndpoint = null;
        if (browserProcess != null)
        {
            List<ProcessHandle> descendants = browserProcess.descendants().toList();
            try
            {
                // 正常退出会把登录 Cookie 写入配置目录。
                if (browserWebSocketUrl != null)
                {
                    try (CdpSession session = openBrowserSession())
                    {
                        session.sendCommand("Browser.close", null, 2);
                    }
                    catch (Exception exception)
                    {
                        log.debug("浏览器正常退出命令未完成：{}", exception.toString());
                    }
                }
                browserProcess.onExit().get(8, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException | TimeoutException exception)
            {
                log.debug("等待浏览器进程退出失败：{}", exception.toString());
            }
            browserProcess.destroyForcibly();
            descendants.forEach(ProcessHandle::destroyForcibly);
            browserProcess = null;
        }
        browserWebSocketUrl = null;
        debugPort = 0;
    }

    private static String findBrowserExecutable()
    {
        String fromEnv = System.getenv("LINGXI_BROWSER_PATH");
        if (fromEnv != null && !fromEnv.isBlank() && Files.isExecutable(Path.of(fromEnv.trim())))
        {
            return fromEnv.trim();
        }
        for (String candidate : BROWSER_CANDIDATES)
        {
            if (Files.isExecutable(Path.of(candidate)))
            {
                return candidate;
            }
        }
        throw new ServiceException(
                "未找到可用的浏览器，请安装 Microsoft Edge 或 Chrome，或通过环境变量 LINGXI_BROWSER_PATH 指定浏览器路径");
    }

    /** 扫描并终止仍在使用本配置目录的浏览器进程树（启动前自愈，避免残留实例锁住配置目录）。 */
    private static void killAllOnProfile(Path profile)
    {
        if (isWindows())
        {
            killAllOnProfileWindows(profile);
        }
        else
        {
            String marker = profile.toString();
            ProcessHandle.allProcesses().forEach(handle -> {
                if (handle.pid() == ProcessHandle.current().pid())
                {
                    return;
                }
                String commandLine = handle.info().commandLine().orElse("");
                if (commandLine.contains(marker))
                {
                    handle.descendants().forEach(ProcessHandle::destroyForcibly);
                    handle.destroyForcibly();
                }
            });
        }
        sleepQuietly(800);
    }

    private static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /**
     * Windows 上 JDK 的 ProcessHandle 读不到其他进程命令行，无法按命令行识别残留实例，
     * 这里借助 PowerShell CIM 按“命令行包含本配置目录”精确清理（不误伤用户自己的浏览器）。
     * 脚本只用单引号：Java 在 Windows 传参会吞掉双引号，导致 -Filter 查询失效。
     */
    private static void killAllOnProfileWindows(Path profile)
    {
        try
        {
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive",
                    "-Command",
                    "$m = $env:LX_BROWSER_PROFILE; Get-CimInstance Win32_Process "
                            + "| Where-Object { $_.Name -in @('msedge.exe', 'chrome.exe') -and $_.CommandLine -like ('*' + $m + '*') } "
                            + "| ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }");
            builder.environment().put("LX_BROWSER_PROFILE", profile.toString());
            Process process = builder.start();
            process.waitFor(15, TimeUnit.SECONDS);
        }
        catch (Exception exception)
        {
            log.debug("清理残留浏览器进程失败：{}", exception.toString());
        }
    }

    // ==================== CDP 标签页管理 ====================

    /** 浏览器标签页信息。 */
    private static final class Tab
    {
        private final String id;

        private final String webSocketUrl;

        private Tab(String id, String webSocketUrl)
        {
            this.id = id;
            this.webSocketUrl = webSocketUrl;
        }

        String getId()
        {
            return id;
        }

        String getWebSocketUrl()
        {
            return webSocketUrl;
        }
    }

    private Tab createTab() throws IOException, InterruptedException
    {
        return createTab(debugPort);
    }

    private Tab createTab(int port) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/json/new?about:blank"))
                .timeout(Duration.ofSeconds(10)).method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();
        String body;
        try
        {
            body = HttpHolder.CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        }
        catch (Exception exception)
        {
            throw new IOException("创建浏览器标签页失败", exception);
        }
        try
        {
            JsonNode node = objectMapper.readTree(body);
            return new Tab(node.path("id").asText(), node.path("webSocketDebuggerUrl").asText());
        }
        catch (Exception exception)
        {
            throw new IOException("创建浏览器标签页失败", exception);
        }
    }

    /** 查找浏览器中已打开的第一个页面标签（登录窗口唤起登录面板用）。 */
    private Tab findFirstPageTab()
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + debugPort + "/json/list"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            String body = HttpHolder.CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
            for (JsonNode node : objectMapper.readTree(body))
            {
                if ("page".equals(node.path("type").asText())
                        && node.path("webSocketDebuggerUrl").asText().isBlank() == false)
                {
                    return new Tab(node.path("id").asText(), node.path("webSocketDebuggerUrl").asText());
                }
            }
        }
        catch (Exception exception)
        {
            log.debug("获取浏览器标签列表失败：{}", exception.toString());
        }
        return null;
    }

    private void closeTabQuietly(String tabId)
    {
        closeTabQuietly(tabId, debugPort);
    }

    private void closeTabQuietly(String tabId, int port)
    {
        if (tabId == null || tabId.isBlank())
        {
            return;
        }
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/json/close/" + tabId))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpHolder.CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        }
        catch (Exception exception)
        {
            log.debug("关闭浏览器标签页失败：{}", exception.toString());
        }
    }

    private static void sleepQuietly(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static final class HttpHolder
    {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    // ==================== CDP 会话 ====================

    /** 浏览器或标签页的 CDP 会话：发送命令、收集网络响应。 */
    private static final class CdpSession implements WebSocket.Listener, AutoCloseable
    {
        private final WebSocket webSocket;

        private final ObjectMapper objectMapper;

        private final StringBuilder partial = new StringBuilder();

        private final Map<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

        private final List<String> detailRequestIds = Collections.synchronizedList(new ArrayList<>());

        private final List<CapturedBody> capturedBodies = Collections.synchronizedList(new ArrayList<>());

        private final List<String> captureMarkers = new ArrayList<>();

        private volatile String commentAwemeId;

        private final Map<Long, String> commentRequests = new ConcurrentHashMap<>();

        private final AtomicInteger sequence = new AtomicInteger();

        CdpSession(String webSocketUrl, ObjectMapper objectMapper) throws Exception
        {
            this(webSocketUrl, objectMapper, 10);
        }

        CdpSession(String webSocketUrl, ObjectMapper objectMapper, long timeoutSeconds) throws Exception
        {
            this.objectMapper = objectMapper;
            Duration timeout = Duration.ofSeconds(timeoutSeconds);
            this.webSocket = HttpHolder.CLIENT.newWebSocketBuilder().connectTimeout(timeout)
                    .buildAsync(URI.create(webSocketUrl), this).get(timeoutSeconds, TimeUnit.SECONDS);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)
        {
            partial.append(data);
            if (last)
            {
                String text = partial.toString();
                partial.setLength(0);
                dispatch(text);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error)
        {
            pending.values().forEach(future -> future.completeExceptionally(error));
            pending.clear();
        }

        private void dispatch(String text)
        {
            try
            {
                JsonNode node = objectMapper.readTree(text);
                if (node.has("id"))
                {
                    CompletableFuture<String> future = pending.remove(node.get("id").asInt());
                    if (future != null)
                    {
                        future.complete(text);
                    }
                    return;
                }
                if ("Network.responseReceived".equals(node.path("method").asText()))
                {
                    JsonNode params = node.path("params");
                    String url = params.path("response").path("url").asText();
                    String responseType = params.path("type").asText();
                    // fetch() 发出的请求（如搜索结果流）类型为 Fetch，与 XHR 一并截获
                    boolean isAjax = "XHR".equals(responseType) || "Fetch".equals(responseType);
                    if (isAjax && commentAwemeId != null && url.startsWith(COMMENT_API))
                    {
                        var video = COMMENT_AWEME.matcher(url);
                        var cursor = COMMENT_CURSOR.matcher(url);
                        if (video.find() && commentAwemeId.equals(video.group(1)) && cursor.find())
                        {
                            commentRequests.put(Long.parseLong(cursor.group(1)), params.path("requestId").asText());
                        }
                        return;
                    }
                    if (url.contains(DETAIL_API_MARKER) && isAjax)
                    {
                        detailRequestIds.add(params.path("requestId").asText());
                    }
                    if (!captureMarkers.isEmpty() && isAjax)
                    {
                        String lowerUrl = toLowerCase(url);
                        boolean excluded = false;
                        for (String marker : captureMarkers)
                        {
                            if (marker.startsWith("!") && lowerUrl.contains(toLowerCase(marker.substring(1))))
                            {
                                excluded = true;
                                break;
                            }
                        }
                        if (!excluded)
                        {
                            for (String marker : captureMarkers)
                            {
                                if (!marker.startsWith("!") && lowerUrl.contains(toLowerCase(marker)))
                                {
                                    synchronized (capturedBodies)
                                    {
                                        capturedBodies.add(new CapturedBody(marker, params.path("requestId").asText()));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception exception)
            {
                log.debug("CDP 消息解析失败：{}", exception.toString());
            }
        }

        private static String toLowerCase(String value)
        {
            return value.toLowerCase(java.util.Locale.ROOT);
        }

        /** 发送 CDP 命令并等待应答，返回应答的 result 节点。 */
        JsonNode sendCommand(String method, Map<String, Object> params) throws Exception
        {
            return sendCommand(method, params, 10);
        }

        JsonNode sendCommand(String method, Map<String, Object> params, long timeoutSeconds) throws Exception
        {
            int id = sequence.incrementAndGet();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("id", id);
            message.put("method", method);
            if (params != null)
            {
                message.set("params", objectMapper.valueToTree(params));
            }
            CompletableFuture<String> future = new CompletableFuture<>();
            pending.put(id, future);
            webSocket.sendText(objectMapper.writeValueAsString(message), true);
            String response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            JsonNode node = objectMapper.readTree(response);
            if (node.has("error"))
            {
                throw new IOException("CDP 命令失败：" + node.get("error").toString());
            }
            return node.path("result");
        }

        /** 执行一个抓取动作，返回 eval 类型动作的执行结果。 */
        JsonNode perform(CaptureAction action) throws Exception
        {
            switch (action.type())
            {
                case "eval":
                    return sendCommand("Runtime.evaluate", Map.of(
                            "expression", action.value() == null ? "" : action.value(),
                            "returnByValue", true)).path("result");
                case "input":
                    sendCommand("Input.insertText", Map.of("text", action.value() == null ? "" : action.value()));
                    return null;
                case "enter":
                    sendCommand("Input.dispatchKeyEvent", Map.of("type", "rawKeyDown",
                            "windowsVirtualKeyCode", 13, "code", "Enter", "key", "Enter"));
                    sendCommand("Input.dispatchKeyEvent", Map.of("type", "keyUp",
                            "windowsVirtualKeyCode", 13, "code", "Enter", "key", "Enter"));
                    return null;
                default:
                    return null;
            }
        }

        /**
         * 挂好响应拦截（必须在导航前调用）。
         */
        void beginCapture(List<String> markers)
        {
            captureMarkers.addAll(markers);
        }

        /**
         * 按请求等待并收集响应体：先等到 minResponses 个响应，再按需滚动加载（翻页）。
         */
        List<CapturedBody> waitCaptures(CaptureRequest request) throws Exception
        {
            long startedAt = System.currentTimeMillis();
            long deadline = startedAt + request.getTimeoutMs();
            long minWaitUntil = startedAt + Math.max(0, request.getMinWaitMs());
            // 等待达到最少响应数且必截获标记命中，同时至少等满最短等待时间
            // （防止页面加载期的干扰接口提前收线打断页面动作或漏掉晚到的关键响应）
            while ((System.currentTimeMillis() < deadline
                    && (capturedCount() < request.getMinResponses()
                            || (request.getRequiredMarker() != null
                                    && !capturedContains(request.getRequiredMarker()))))
                    || System.currentTimeMillis() < minWaitUntil)
            {
                Thread.sleep(400);
            }
            // 翻页滚动：滚窗口 + 把所有可滚动容器拉到底（抖音内容主滚动区是内部容器，
            // 仅滚窗口翻不动评论/推荐分页）
            String scrollScript = request.getScrollScript() != null ? request.getScrollScript()
                    : "(() => { window.scrollBy(0, Math.max(window.innerHeight, 800));"
                            + " const boxes = Array.from(document.querySelectorAll('div, ul, section')).filter("
                            + " e => e.clientHeight > 100 && e.scrollHeight > e.clientHeight + 40);"
                            + " boxes.forEach(e => { e.scrollTop = e.scrollHeight; });"
                            + " return 'scrolled ' + boxes.length; })()";
            for (int i = 0; i < request.getScrollTimes(); i++)
            {
                int before = capturedCount();
                sendCommand("Runtime.evaluate", Map.of(
                        "expression", scrollScript,
                        "returnByValue", true));
                long scrollDeadline = System.currentTimeMillis() + request.getScrollIntervalMs();
                while (System.currentTimeMillis() < scrollDeadline && capturedCount() <= before)
                {
                    Thread.sleep(300);
                }
            }
            // 少量富余时间让最后一个响应完成加载
            sleepQuietly(800);
            return resolveCapturedBodies();
        }

        private int capturedCount()
        {
            synchronized (capturedBodies)
            {
                return capturedBodies.size();
            }
        }

        /** 是否已截获到包含指定标记的响应 */
        private boolean capturedContains(String marker)
        {
            synchronized (capturedBodies)
            {
                return capturedBodies.stream().anyMatch(body -> body.marker().contains(marker));
            }
        }

        JsonNode awaitCommentPage(long cursor, long deadline) throws Exception
        {
            long nextScroll = 0;
            while (!webSocket.isInputClosed() && System.nanoTime() < deadline)
            {
                String requestId = commentRequests.get(cursor);
                if (requestId != null)
                {
                    String body = tryReadBody(requestId, 1);
                    if (body != null)
                    {
                        JsonNode result = objectMapper.readTree(body);
                        if (result.path("status_code").asInt(-1) == 0)
                        {
                            commentRequests.remove(cursor, requestId);
                            return result;
                        }
                        commentRequests.remove(cursor, requestId);
                        throw new ServiceException("抖音评论暂不可用，请稍后重试");
                    }
                }
                else if (cursor > 0 && System.nanoTime() >= nextScroll)
                {
                    // 避免并行抓取推荐时，后台页的滚动监听被 Chromium 节流。
                    sendCommand("Page.bringToFront", null, 2);
                    JsonNode position = sendCommand("Runtime.evaluate",
                            Map.of("expression", SCROLL_COMMENTS, "returnByValue", true), 2);
                    if ("login-required".equals(position.path("result").path("value").asText()))
                    {
                        throw new ServiceException("抖音要求登录后才能查看更多评论");
                    }
                    nextScroll = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1250);
                }
                Thread.sleep(100);
            }
            throw new ServiceException("未收到抖音评论，请稍后重试");
        }

        List<CapturedBody> waitForVerifiedResponse(long timeoutMs, Predicate<String> resultReceived)
                throws InterruptedException
        {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            Set<String> inspected = new HashSet<>();
            while (!webSocket.isInputClosed() && System.nanoTime() < deadline)
            {
                for (CapturedBody entry : List.copyOf(capturedBodies))
                {
                    if (inspected.contains(entry.body()))
                    {
                        continue;
                    }
                    String body = tryReadBody(entry.body(), 2);
                    if (body != null)
                    {
                        inspected.add(entry.body());
                        if (resultReceived.test(body))
                        {
                            return List.of(new CapturedBody(entry.marker(), body));
                        }
                    }
                }
                Thread.sleep(400);
            }
            return List.of();
        }

        /** 将已记录的 requestId 解析为响应正文。 */
        private List<CapturedBody> resolveCapturedBodies()
        {
            List<CapturedBody> requests;
            synchronized (capturedBodies)
            {
                requests = List.copyOf(capturedBodies);
            }
            // 接收事件和命令应答共用回调线程，等待应答时必须释放事件列表锁。
            List<CapturedBody> resolved = new ArrayList<>(requests.size());
            for (CapturedBody entry : requests)
            {
                String body = tryReadBody(entry.body());
                resolved.add(new CapturedBody(entry.marker(), body != null ? body : ""));
            }
            return resolved;
        }

        /** 读取响应正文；响应可能尚未加载完成，做有限重试。 */
        private String tryReadBody(String requestId)
        {
            return tryReadBody(requestId, 10);
        }

        private String tryReadBody(String requestId, long timeoutSeconds)
        {
            for (int attempt = 0; attempt < 4; attempt++)
            {
                try
                {
                    JsonNode result = sendCommand("Network.getResponseBody", Map.of("requestId", requestId), timeoutSeconds);
                    String body = result.path("body").asText("");
                    if (!body.isBlank())
                    {
                        return result.path("base64Encoded").asBoolean()
                                ? new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8) : body;
                    }
                }
                catch (Exception exception)
                {
                    // 响应未加载完成时取不到正文，稍后重试
                }
                sleepQuietly(600);
            }
            return null;
        }

        /**
         * 等待视频信息接口响应并解析；超时后回退到页面 DOM 提取（“粘贴链接解析”旧流程）。
         */
        MediaInfo waitForMedia() throws Exception
        {
            long deadline = System.currentTimeMillis() + FETCH_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline)
            {
                List<String> requests;
                synchronized (detailRequestIds)
                {
                    requests = List.copyOf(detailRequestIds);
                }
                for (String requestId : requests)
                {
                    String body = tryReadBody(requestId);
                    if (body != null && body.contains("aweme_detail"))
                    {
                        return parseDetail(body);
                    }
                }
                Thread.sleep(500);
            }
            return fallbackFromDom();
        }

        private MediaInfo parseDetail(String body) throws IOException
        {
            JsonNode detail = objectMapper.readTree(body).path("aweme_detail");
            if (detail.isMissingNode())
            {
                return null;
            }
            MediaInfo info = new MediaInfo();
            info.setTitle(detail.path("desc").asText(""));
            info.setAuthor(detail.path("author").path("nickname").asText(""));
            info.setAuthorAvatar(firstUrl(detail.path("author").path("avatar_thumb").path("url_list")));
            info.setDurationMs(detail.path("duration").asLong(0));
            JsonNode video = detail.path("video");
            info.setCoverUrl(firstOr(firstUrl(video.path("cover").path("url_list")),
                    firstUrl(video.path("origin_cover").path("url_list"))));
            List<String> playUrls = urlList(video.path("play_addr").path("url_list"));
            if (playUrls.isEmpty())
            {
                playUrls = urlList(video.path("download_addr").path("url_list"));
            }
            info.setPlayUrls(playUrls);
            List<String> imageUrls = new ArrayList<>();
            for (JsonNode image : detail.path("images"))
            {
                String cover = firstUrl(image.path("url_list"));
                if (cover != null)
                {
                    imageUrls.add(cover);
                }
            }
            info.setImageUrls(imageUrls);
            return info;
        }

        /** 接口未捕获时的回退：从播放器 DOM 提取直链与标题。 */
        private MediaInfo fallbackFromDom() throws Exception
        {
            JsonNode result = sendCommand("Runtime.evaluate", Map.of(
                    "expression", "(() => { const v = document.querySelector('video');"
                            + " return JSON.stringify({ src: v ? (v.currentSrc || v.src || '') : '',"
                            + " title: document.title }); })()",
                    "returnByValue", true));
            String value = result.path("result").path("value").asText("");
            if (value.isBlank())
            {
                return null;
            }
            JsonNode node = objectMapper.readTree(value);
            String src = node.path("src").asText("");
            if (!VIDEO_SRC_PATTERN.matcher(src).matches())
            {
                return null;
            }
            MediaInfo info = new MediaInfo();
            info.setTitle(node.path("title").asText("").replace(" - 抖音", "").trim());
            info.setPlayUrls(List.of(src));
            return info;
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

        @Override
        public void close()
        {
            try
            {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }
            catch (Exception exception)
            {
                log.debug("关闭 CDP 会话失败：{}", exception.toString());
            }
        }
    }
}
