package com.lingXi.aiVedio.tool;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 抖音网页视频信息抓取客户端。
 * <p>
 * 抖音网页端的数据接口（/aweme/v1/web/aweme/detail/）需要浏览器内生成的签名参数，
 * 纯 HTTP 请求会被安全服务降级。因此这里通过 Chrome DevTools Protocol 驱动本机的
 * Edge/Chrome（无头模式）执行抖音自己的页面脚本，并从网络层捕获接口响应，
 * 解析出视频标题、作者、封面、时长与播放直链。
 * </p>
 * <p>
 * 浏览器进程随首次解析懒启动并常驻复用，解析请求串行执行；
 * 可通过环境变量 {@code LINGXI_BROWSER_PATH} 指定浏览器可执行文件。
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 浏览器进程与调试端口的生命周期锁（解析串行执行）。 */
    private final Object browserLock = new Object();

    private Process browserProcess;

    private int debugPort;

    /** 视频信息（含播放直链列表）。 */
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

    /**
     * 抓取指定作品的视频信息。
     *
     * @param awemeId 作品 ID（15~25 位数字）
     * @return 视频信息
     * @throws ServiceException 浏览器不可用、页面异常或解析失败
     */
    public MediaInfo fetch(String awemeId)
    {
        if (!DouyinShareLinkParser.isValidAwemeId(awemeId))
        {
            throw new ServiceException("抖音作品 ID 无效");
        }
        synchronized (browserLock)
        {
            String tabId = null;
            try
            {
                ensureBrowser();
                Tab tab = createTab();
                tabId = tab.getId();
                try (CdpSession session = new CdpSession(tab.getWebSocketUrl(), objectMapper))
                {
                    session.sendCommand("Network.enable", null);
                    session.sendCommand("Page.enable", null);
                    session.sendCommand("Page.navigate",
                            Map.of("url", "https://www.douyin.com/video/" + awemeId));
                    MediaInfo info = session.waitForMedia(awemeId);
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

    // ==================== 浏览器生命周期 ====================

    private void ensureBrowser() throws IOException
    {
        if (browserProcess != null && browserProcess.isAlive() && ping())
        {
            return;
        }
        stopBrowser();
        Path profile = Path.of(System.getProperty("user.home"), ".lingxi", "douyin-browser");
        Files.createDirectories(profile);
        debugPort = findFreePort();
        List<String> command = new ArrayList<>();
        command.add(findBrowserExecutable());
        command.add("--headless=new");
        command.add("--disable-gpu");
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--hide-crash-restore-bubble");
        command.add("--user-data-dir=" + profile);
        command.add("--remote-debugging-port=" + debugPort);
        command.add("about:blank");
        ProcessBuilder builder = new ProcessBuilder(command);
        // 浏览器自身输出写入日志文件，避免占用进程管道
        builder.redirectErrorStream(true);
        builder.redirectOutput(profile.resolve("browser.log").toFile());
        browserProcess = builder.start();
        for (int i = 0; i < 40; i++)
        {
            if (ping())
            {
                log.info("抖音解析浏览器已启动，调试端口：{}", debugPort);
                return;
            }
            if (!browserProcess.isAlive())
            {
                break;
            }
            sleepQuietly(250);
        }
        stopBrowser();
        throw new IOException("浏览器启动超时或异常退出");
    }

    private boolean ping()
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + debugPort + "/json/version"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> response = http().send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
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
        }
    }

    private void stopBrowser()
    {
        if (browserProcess != null)
        {
            browserProcess.destroy();
            try
            {
                browserProcess.waitFor(3, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
            browserProcess.destroyForcibly();
            browserProcess = null;
        }
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

    private static int findFreePort() throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0))
        {
            return socket.getLocalPort();
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + debugPort + "/json/new?about:blank"))
                .timeout(Duration.ofSeconds(10)).method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();
        String body;
        try
        {
            body = http().send(request, HttpResponse.BodyHandlers.ofString()).body();
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

    private void closeTabQuietly(String tabId)
    {
        if (tabId == null || tabId.isBlank())
        {
            return;
        }
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + debugPort + "/json/close/" + tabId))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            http().send(request, HttpResponse.BodyHandlers.discarding());
        }
        catch (Exception exception)
        {
            log.debug("关闭浏览器标签页失败：{}", exception.toString());
        }
    }

    private HttpClient http()
    {
        return HttpHolder.CLIENT;
    }

    private static final class HttpHolder
    {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
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

    // ==================== CDP 会话 ====================

    /** 单个标签页的 CDP 会话：发送命令、收集网络响应。 */
    private static final class CdpSession implements WebSocket.Listener, AutoCloseable
    {
        private final WebSocket webSocket;

        private final ObjectMapper objectMapper;

        private final StringBuilder partial = new StringBuilder();

        private final Map<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

        private final List<String> detailRequestIds = Collections.synchronizedList(new ArrayList<>());

        private final AtomicInteger sequence = new AtomicInteger();

        CdpSession(String webSocketUrl, ObjectMapper objectMapper) throws Exception
        {
            this.objectMapper = objectMapper;
            this.webSocket = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(webSocketUrl), this).get(10, TimeUnit.SECONDS);
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
                    if (url.contains(DETAIL_API_MARKER) && "XHR".equals(params.path("type").asText()))
                    {
                        detailRequestIds.add(params.path("requestId").asText());
                    }
                }
            }
            catch (Exception exception)
            {
                log.debug("CDP 消息解析失败：{}", exception.toString());
            }
        }

        /** 发送 CDP 命令并等待应答，返回应答的 result 节点。 */
        JsonNode sendCommand(String method, Map<String, Object> params) throws Exception
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
            String response = future.get(10, TimeUnit.SECONDS);
            JsonNode node = objectMapper.readTree(response);
            if (node.has("error"))
            {
                throw new IOException("CDP 命令失败：" + node.get("error").toString());
            }
            return node.path("result");
        }

        /**
         * 等待页面的视频信息接口响应并解析；超时后回退到页面 DOM 提取。
         *
         * @param awemeId 作品 ID
         * @return 视频信息；完全无法提取时返回 {@code null}
         */
        MediaInfo waitForMedia(String awemeId) throws Exception
        {
            long deadline = System.currentTimeMillis() + FETCH_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline)
            {
                synchronized (detailRequestIds)
                {
                    for (String requestId : new ArrayList<>(detailRequestIds))
                    {
                        MediaInfo info = tryReadDetail(requestId);
                        if (info != null)
                        {
                            return info;
                        }
                    }
                }
                Thread.sleep(500);
            }
            return fallbackFromDom();
        }

        private MediaInfo tryReadDetail(String requestId)
        {
            try
            {
                JsonNode result = sendCommand("Network.getResponseBody", Map.of("requestId", requestId));
                String body = result.path("body").asText();
                if (!body.contains("aweme_detail"))
                {
                    return null;
                }
                return parseDetail(body);
            }
            catch (Exception exception)
            {
                // 响应未加载完成时取不到正文，稍后重试
                return null;
            }
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
