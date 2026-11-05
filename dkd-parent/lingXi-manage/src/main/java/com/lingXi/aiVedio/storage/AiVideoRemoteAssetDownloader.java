package com.lingXi.aiVedio.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.DnsResolver;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;

/** 在实际连接的 DNS 解析点校验公网地址，避免临时产物下载访问内网或被 DNS 重绑定。 */
@Component
public class AiVideoRemoteAssetDownloader
{
    private final DnsResolver dnsResolver;

    public AiVideoRemoteAssetDownloader()
    {
        this(AiVideoRemoteAssetDownloader::resolvePublicAddresses);
    }

    AiVideoRemoteAssetDownloader(DnsResolver dnsResolver)
    {
        this.dnsResolver = dnsResolver;
    }

    public byte[] download(String remoteUrl, int readTimeout, long maxBytes) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        transfer(remoteUrl, readTimeout, maxBytes, output);
        return output.toByteArray();
    }

    /** 大体积视频直接落临时文件，避免整个文件及复制缓冲同时占用堆内存。 */
    public void downloadToFile(String remoteUrl, int readTimeout, long maxBytes, Path target) throws IOException
    {
        try (OutputStream output = Files.newOutputStream(target))
        {
            transfer(remoteUrl, readTimeout, maxBytes, output);
        }
    }

    private void transfer(String remoteUrl, int readTimeout, long maxBytes, OutputStream output) throws IOException
    {
        URI uri = requireHttpUri(remoteUrl);
        RequestConfig config = RequestConfig.custom().setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000).setSocketTimeout(readTimeout).build();
        // 复用 OSS 已引入的 HttpClient；解析结果直接用于 socket 连接，不再二次解析主机名。
        try (CloseableHttpClient client = HttpClients.custom().setDnsResolver(dnsResolver)
                .setDefaultRequestConfig(config).disableRedirectHandling().disableAutomaticRetries().build())
        {
            for (int redirects = 0; redirects <= 5; redirects++)
            {
                try (CloseableHttpResponse response = client.execute(new HttpGet(uri)))
                {
                    int status = response.getStatusLine().getStatusCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
                    {
                        Header location = response.getFirstHeader("Location");
                        if (location == null || redirects == 5)
                        {
                            throw new IOException("远程文件重定向无效或次数过多");
                        }
                        try
                        {
                            uri = requireHttpUri(uri.resolve(location.getValue()).toString());
                        }
                        catch (IllegalArgumentException exception)
                        {
                            throw new IOException("远程文件重定向地址无效");
                        }
                        continue;
                    }
                    HttpEntity entity = response.getEntity();
                    if (status != 200 || entity == null)
                    {
                        throw new IOException("远程文件下载失败，状态码：" + status);
                    }
                    if (entity.getContentLength() > maxBytes)
                    {
                        throw new IOException("远程文件超过大小上限");
                    }
                    // 响应负责关闭连接；单独 close 正文流会为了复用连接而排空超限数据。
                    InputStream input = entity.getContent();
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1)
                    {
                        total += read;
                        if (total > maxBytes)
                        {
                            throw new IOException("远程文件超过大小上限");
                        }
                        output.write(buffer, 0, read);
                    }
                    return;
                }
            }
        }
        throw new IOException("远程文件下载失败");
    }

    private static URI requireHttpUri(String value)
    {
        URI uri;
        try
        {
            uri = URI.create(value == null ? "" : value);
        }
        catch (IllegalArgumentException exception)
        {
            // 签名查询参数可能含临时凭据，不把原始 URL 放进任务错误消息。
            throw new IllegalArgumentException("远程文件地址无效");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535)
        {
            throw new IllegalArgumentException("远程文件地址必须是无用户凭据的 HTTP(S) 地址");
        }
        return uri;
    }

    static InetAddress[] resolvePublicAddresses(String host) throws UnknownHostException
    {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses)
        {
            if (!isPublicAddress(address))
            {
                throw new UnknownHostException("远程文件地址禁止访问非公网网络");
            }
        }
        return addresses;
    }

    static boolean isPublicAddress(InetAddress address)
    {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress())
        {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if (bytes.length == 4)
        {
            int third = bytes[2] & 0xff;
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 192 && second == 88 && third == 99)
                    && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }
        // 仅接受全球单播；排除特殊用途、文档网段以及可封装私网 IPv4 的 6to4 地址。
        return (first & 0xe0) == 0x20
                && !(first == 0x20 && second == 0x01 && (bytes[2] & 0xff) < 2)
                && !(first == 0x20 && second == 0x01 && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8)
                && !(first == 0x3f && second == 0xff && (bytes[2] & 0xf0) == 0)
                && !(first == 0x20 && second == 0x02);
    }
}
