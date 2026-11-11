package com.lingXi.aiVedio.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import com.lingXi.aiVedio.storage.AiVideoRemoteAssetDownloader;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

/**
 * 抖音视频流代理转发器。
 * <p>
 * 抖音 CDN 直链存在防盗链与时效限制，管理端 <code>&lt;video&gt;</code> 直连会被拒绝。
 * 这里由 Java 端转发视频流：透传 Range 请求头以支持拖动进度条，
 * 并对转发目标做域名白名单与公网地址校验（复用资产下载器的 DNS 校验），防止被用作内网探测跳板。
 * </p>
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Slf4j
@Component
public class DouyinVideoStreamRelay
{
    /** 允许转发的视频源域名后缀。 */
    private static final String[] ALLOWED_HOST_SUFFIXES = {
            "douyinvod.com", "idouyinvod.com", "douyin.com", "douyinpic.com", "douyinstatic.com"};

    /** 合法的 Range 请求头（单区间或逗号分隔多区间）。 */
    private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=\\d*-\\d*(,\\d*-\\d*)*$");

    private static final int MAX_REDIRECTS = 3;

    /**
     * 将视频流转发给客户端。
     *
     * @param playUrls  候选播放直链（按优先级排列，首个失败自动尝试下一个）
     * @param rangeHeader 客户端 Range 请求头，可为 {@code null}
     * @param response  客户端响应
     * @throws IOException 全部候选地址不可用或写出失败
     */
    public void relay(List<String> playUrls, String rangeHeader, HttpServletResponse response)
            throws IOException
    {
        IOException lastFailure = null;
        for (String playUrl : playUrls)
        {
            try
            {
                transfer(playUrl, rangeHeader, response);
                return;
            }
            catch (IllegalArgumentException | UnknownHostException exception)
            {
                // 地址不合法或指向非公网：不作为可用源，也不向客户端透出细节
                log.warn("视频源地址被拒绝转发：{}", exception.getMessage());
                lastFailure = new IOException("视频源地址不合法");
            }
            catch (IOException exception)
            {
                if (response.isCommitted())
                {
                    // 已开始向客户端写数据，无法更换地址
                    throw exception;
                }
                log.warn("视频源转发失败，尝试下一个候选地址：{}", exception.toString());
                lastFailure = exception;
            }
        }
        throw lastFailure != null ? lastFailure : new IOException("没有可用的视频源地址");
    }

    private void transfer(String playUrl, String rangeHeader, HttpServletResponse response)
            throws IOException
    {
        URI uri = requireAllowedUri(playUrl);
        RequestConfig config = RequestConfig.custom().setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000).setSocketTimeout(60000).build();
        // 复用 OSS 已引入的 HttpClient；DNS 解析点做公网校验，手动跟随重定向并逐跳校验。
        try (CloseableHttpClient client = HttpClients.custom()
                .setDnsResolver(AiVideoRemoteAssetDownloader::resolvePublicAddresses)
                .setDefaultRequestConfig(config).disableRedirectHandling().disableAutomaticRetries()
                .build())
        {
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++)
            {
                HttpGet get = new HttpGet(uri);
                get.setHeader("Referer", "https://www.douyin.com/");
                get.setHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
                get.setHeader("Accept", "*/*");
                if (rangeHeader != null && RANGE_PATTERN.matcher(rangeHeader.trim()).matches())
                {
                    get.setHeader("Range", rangeHeader.trim());
                }
                try (CloseableHttpResponse upstream = client.execute(get))
                {
                    int status = upstream.getStatusLine().getStatusCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
                    {
                        Header location = upstream.getFirstHeader("Location");
                        if (location == null || redirects == MAX_REDIRECTS)
                        {
                            throw new IOException("视频源重定向无效或次数过多");
                        }
                        uri = requireAllowedUri(uri.resolve(location.getValue()).toString());
                        continue;
                    }
                    HttpEntity entity = upstream.getEntity();
                    if ((status != 200 && status != 206) || entity == null)
                    {
                        throw new IOException("视频源返回状态码：" + status);
                    }
                    writeResponse(status, upstream, entity, response);
                    return;
                }
            }
        }
        throw new IOException("视频源重定向次数过多");
    }

    private static void writeResponse(int status, CloseableHttpResponse upstream, HttpEntity entity,
            HttpServletResponse response) throws IOException
    {
        response.setStatus(status);
        Header contentType = upstream.getFirstHeader("Content-Type");
        response.setContentType(contentType != null ? contentType.getValue() : "video/mp4");
        copyHeader(upstream, "Content-Length", response);
        copyHeader(upstream, "Content-Range", response);
        copyHeader(upstream, "Accept-Ranges", response);
        InputStream input = entity.getContent();
        OutputStream output = response.getOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1)
        {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static void copyHeader(CloseableHttpResponse upstream, String name,
            HttpServletResponse response)
    {
        Header header = upstream.getFirstHeader(name);
        if (header != null)
        {
            response.setHeader(name, header.getValue());
        }
    }

    /** 校验转发目标：仅允许抖音 CDN 域名的无凭据 HTTP(S) 地址，公网校验由 DNS 解析点完成。 */
    private static URI requireAllowedUri(String value)
    {
        URI uri;
        try
        {
            uri = URI.create(value == null ? "" : value);
        }
        catch (IllegalArgumentException exception)
        {
            // 签名查询参数可能含临时凭据，不把原始 URL 放进错误消息
            throw new IllegalArgumentException("视频源地址无效");
        }
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || host == null || uri.getUserInfo() != null || uri.getFragment() != null)
        {
            throw new IllegalArgumentException("视频源地址必须是无用户凭据的 HTTP(S) 地址");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        for (String suffix : ALLOWED_HOST_SUFFIXES)
        {
            if (lowerHost.equals(suffix) || lowerHost.endsWith("." + suffix))
            {
                return uri;
            }
        }
        throw new IllegalArgumentException("视频源地址不在允许的域名范围内");
    }
}
