package com.lingXi.aiVedio.tool;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingXi.aiVedio.storage.AiVideoRemoteAssetDownloader;
import org.apache.http.conn.DnsResolver;

/** 抖音 CDN 专用解析器，供通过域名白名单校验后的直连和重定向使用。 */
final class DouyinCdnDnsResolver implements DnsResolver
{
    private static final String PUBLIC_DNS_URL = "https://223.5.5.5/resolve?type=A&name=";

    private static final int MAX_DNS_BYTES = 16_384;

    private static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DnsResolver systemDns;

    private final DnsResolver publicDns;

    DouyinCdnDnsResolver()
    {
        this(InetAddress::getAllByName, DouyinCdnDnsResolver::queryPublicDns);
    }

    DouyinCdnDnsResolver(DnsResolver systemDns, DnsResolver publicDns)
    {
        this.systemDns = systemDns;
        this.publicDns = publicDns;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException
    {
        InetAddress[] addresses = systemDns.resolve(host);
        // 某些本机代理用 198.18.0.0/15 返回占位地址。整组都是占位地址时才独立重查。
        if (addresses.length > 0 && Arrays.stream(addresses).allMatch(DouyinCdnDnsResolver::isSyntheticAddress))
        {
            addresses = publicDns.resolve(host);
        }
        // 解析器返回的地址直接用于连接，禁止再回退到本机占位地址。
        return AiVideoRemoteAssetDownloader.requirePublicAddresses(addresses);
    }

    private static boolean isSyntheticAddress(InetAddress address)
    {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 0xff) == 198 && (bytes[1] == 18 || bytes[1] == 19);
    }

    private static InetAddress[] queryPublicDns(String host) throws UnknownHostException
    {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(PUBLIC_DNS_URL + URLEncoder.encode(host, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/dns-json").GET().build();
        try
        {
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body())
            {
                if (response.statusCode() != 200)
                {
                    throw new IOException("公网 DNS 返回状态码 " + response.statusCode());
                }
                byte[] bytes = input.readNBytes(MAX_DNS_BYTES + 1);
                if (bytes.length > MAX_DNS_BYTES)
                {
                    throw new IOException("公网 DNS 响应过大");
                }
                return parseAddresses(MAPPER.readTree(bytes));
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw (UnknownHostException) new UnknownHostException("抖音 CDN 公网解析已中断").initCause(exception);
        }
        catch (IOException exception)
        {
            throw (UnknownHostException) new UnknownHostException("抖音 CDN 公网解析失败").initCause(exception);
        }
    }

    private static InetAddress[] parseAddresses(JsonNode result) throws IOException
    {
        if (result == null || result.path("Status").asInt(-1) != 0)
        {
            throw new IOException("公网 DNS 返回错误");
        }
        List<InetAddress> addresses = new ArrayList<>();
        for (JsonNode answer : result.path("Answer"))
        {
            if (answer.path("type").asInt() != 1)
            {
                continue;
            }
            String value = answer.path("data").asText();
            if (!IPV4.matcher(value).matches())
            {
                throw new IOException("公网 DNS 返回了无效的 IPv4 地址");
            }
            String[] parts = value.split("\\.");
            byte[] bytes = new byte[4];
            for (int i = 0; i < parts.length; i++)
            {
                int part = Integer.parseInt(parts[i]);
                if (part > 255)
                {
                    throw new IOException("公网 DNS 返回了无效的 IPv4 地址");
                }
                bytes[i] = (byte) part;
            }
            addresses.add(InetAddress.getByAddress(bytes));
        }
        return addresses.toArray(InetAddress[]::new);
    }
}
