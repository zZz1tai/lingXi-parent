package com.lingXi.aiVedio.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiVideoRemoteAssetDownloaderTest
{
    @Test
    void rejectsPrivateReservedAndEmbeddedAddresses() throws Exception
    {
        for (String ip : new String[] {"0.0.0.0", "10.0.0.1", "127.0.0.1", "169.254.169.254",
                "172.16.0.1", "192.168.0.1", "100.64.0.1", "198.18.0.1", "192.0.2.1",
                "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1", "::", "::1",
                "fc00::1", "fe80::1", "::ffff:127.0.0.1", "64:ff9b::7f00:1", "2002:7f00:1::1"})
        {
            assertFalse(AiVideoRemoteAssetDownloader.isPublicAddress(InetAddress.getByName(ip)), ip);
        }
        assertTrue(AiVideoRemoteAssetDownloader.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(AiVideoRemoteAssetDownloader.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void rejectsInvalidSchemesAndCredentialsBeforeConnecting()
    {
        AiVideoRemoteAssetDownloader downloader = new AiVideoRemoteAssetDownloader();
        for (String url : new String[] {"file:///etc/passwd", "ftp://example.com/file",
                "http://user:password@example.com/file", "http://example.com/file#fragment"})
        {
            assertThrows(IllegalArgumentException.class, () -> downloader.download(url, 1000, 100));
        }
    }

    @Test
    void actualConnectionAndRedirectsCannotReachLoopback() throws Exception
    {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/target", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, 1);
            exchange.getResponseBody().write(1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:"
                    + server.getAddress().getPort() + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try
        {
            int port = server.getAddress().getPort();
            assertThrows(IOException.class, () -> new AiVideoRemoteAssetDownloader()
                    .download("http://127.0.0.1:" + port + "/target", 1000, 100));
            // 初始测试域名映射到本机；重定向中的数字地址仍走真实的公网校验器。
            AiVideoRemoteAssetDownloader downloader = new AiVideoRemoteAssetDownloader(host ->
                    "public.example.test".equals(host)
                            ? new InetAddress[] {InetAddress.getByName("127.0.0.1")}
                            : AiVideoRemoteAssetDownloader.resolvePublicAddresses(host));
            assertThrows(IOException.class, () -> downloader.download(
                    "http://public.example.test:" + port + "/redirect", 1000, 100));
            assertTrue(hits.get() == 0);
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void downloadsWithinLimitAndRejectsChunkedOversize(@TempDir Path directory) throws Exception
    {
        byte[] body = new byte[] {1, 2, 3, 4};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try
        {
            AiVideoRemoteAssetDownloader downloader = new AiVideoRemoteAssetDownloader(InetAddress::getAllByName);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            assertArrayEquals(body, downloader.download(url, 1000, 4));
            assertThrows(IOException.class, () -> downloader.download(url, 1000, 3));
            Path target = directory.resolve("video.mp4");
            downloader.downloadToFile(url, 1000, 4, target);
            assertArrayEquals(body, Files.readAllBytes(target));
            assertThrows(IOException.class, () -> downloader.downloadToFile(url, 1000, 3, target));
            assertTrue(Files.size(target) <= 3);
        }
        finally
        {
            server.stop(0);
        }
    }

    @Test
    void oversizeStopsWithoutDrainingTheRemainingBody() throws Exception
    {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[] {1, 2, 3, 4});
            exchange.getResponseBody().flush();
            try
            {
                release.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception)
            {
                Thread.currentThread().interrupt();
            }
            finally
            {
                exchange.close();
            }
        });
        server.start();
        try
        {
            AiVideoRemoteAssetDownloader downloader = new AiVideoRemoteAssetDownloader(InetAddress::getAllByName);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThrows(IOException.class, () -> downloader.download(url, 10000, 3)));
        }
        finally
        {
            release.countDown();
            server.stop(0);
        }
    }
}
