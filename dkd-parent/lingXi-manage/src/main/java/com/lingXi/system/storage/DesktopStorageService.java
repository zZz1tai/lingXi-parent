package com.lingXi.system.storage;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingXi.common.exception.ServiceException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 桌面版文件目录与签名访问。切换写入目录后保留旧目录的读取能力。 */
@Service
@ConditionalOnProperty(name = "lingxi.desktop.enabled", havingValue = "true")
public class DesktopStorageService
{
    public static final String PLATFORM = "desktop-local";
    private final Path dataDirectory;
    private final String baseUrl;
    private final byte[] signingKey;
    private volatile List<Path> roots;

    public DesktopStorageService(@Value("${lingxi.desktop.data-dir}") String dataDirectory,
            @Value("${lingxi.desktop.public-base-url:http://127.0.0.1:18080}") String baseUrl) throws Exception
    {
        this.dataDirectory = Path.of(dataDirectory).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/desktop/files/";
        Files.createDirectories(this.dataDirectory);
        Path key = this.dataDirectory.resolve("file-signing.key");
        if (!Files.exists(key))
        {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            Files.write(key, bytes, StandardOpenOption.CREATE_NEW);
        }
        this.signingKey = Files.readAllBytes(key);
        if (signingKey.length != 32) throw new IllegalStateException("本地文件签名配置损坏，请恢复数据目录中的签名密钥");
        List<Path> configured = new ArrayList<>();
        Path settings = this.dataDirectory.resolve("storage-settings.json");
        if (Files.exists(settings))
        {
            JSONObject saved = JSON.parseObject(Files.readString(settings));
            configured.add(Path.of(saved.getString("storagePath")).toAbsolutePath().normalize());
            for (String old : saved.getList("previousPaths", String.class))
                configured.add(Path.of(old).toAbsolutePath().normalize());
        }
        else configured.add(this.dataDirectory.resolve("uploads"));
        roots = List.copyOf(new LinkedHashSet<>(configured));
        // 外置磁盘暂时离线时仍允许启动，让用户进入设置修正目录。
        if (!Files.exists(settings)) Files.createDirectories(roots.get(0));
    }

    public Map<String, Object> settings()
    {
        List<Path> paths = roots;
        return Map.of("storagePath", paths.get(0).toString(),
            "previousPaths", paths.subList(1, paths.size()).stream().map(Path::toString).toList(),
            "dataDirectory", dataDirectory.toString(), "mode", "local");
    }

    public synchronized Map<String, Object> changeDirectory(String directory)
    {
        try
        {
            if (directory == null || directory.isBlank() || !Path.of(directory.trim()).isAbsolute())
                throw new ServiceException("请输入本机绝对路径，例如 D:\\灵犀文件");
            Path target = Path.of(directory.trim()).normalize();
            // 数据库、凭据、程序资源不能与可访问的上传文件目录混放。
            if (dataDirectory.startsWith(target) || target.startsWith(dataDirectory)
                    && !target.equals(dataDirectory.resolve("uploads")))
                throw new ServiceException("请选择独立的文件目录，不要选择应用的数据、数据库或日志目录");
            rejectLinks(target);
            Files.createDirectories(target);
            Path probe = Files.createTempFile(target, ".lingxi-write-", ".tmp");
            Files.delete(probe);
            LinkedHashSet<Path> paths = new LinkedHashSet<>();
            paths.add(target);
            paths.addAll(roots);
            List<Path> next = List.copyOf(paths);
            Map<String, Object> saved = Map.of("storagePath", target.toString(), "previousPaths",
                next.subList(1, next.size()).stream().map(Path::toString).toList());
            Path temporary = Files.createTempFile(dataDirectory, "storage-settings-", ".tmp");
            try
            {
                Files.writeString(temporary, JSON.toJSONString(saved));
                Files.move(temporary, dataDirectory.resolve("storage-settings.json"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            }
            finally { Files.deleteIfExists(temporary); }
            roots = next;
            return settings();
        }
        catch (ServiceException exception) { throw exception; }
        catch (Exception exception) { throw new ServiceException("目录不可写或设置未能保存，请检查路径和磁盘权限"); }
    }

    public long write(String key, InputStream input) throws Exception
    {
        Path target = resolve(roots.get(0), key);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try
        {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Files.size(target);
        }
        finally { Files.deleteIfExists(temporary); }
    }

    public Path read(String key)
    {
        for (Path root : roots)
        {
            Path file = resolve(root, key);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return file;
        }
        throw new ServiceException("本地文件不存在，请检查原保存目录是否仍可访问");
    }

    public boolean delete(String key)
    {
        try { return Files.deleteIfExists(read(key)); }
        catch (Exception exception) { return false; }
    }

    public String url(String key, long expires)
    {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        return baseUrl + encoded + "?expires=" + expires + "&signature=" + signature(encoded, expires);
    }

    public Path authorizedFile(String encoded, long expires, String signature)
    {
        if (expires < 0 || expires > 0 && expires < Instant.now().getEpochSecond()
                || signature == null || !MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),
                    signature(encoded, expires).getBytes(StandardCharsets.US_ASCII)))
            throw new ServiceException("本地文件访问地址无效或已过期");
        try { return read(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException exception) { throw new ServiceException("文件地址无效"); }
    }

    public boolean isLocalUrl(String url) { return url != null && url.startsWith(baseUrl); }

    public String modelImage(String url)
    {
        if (!isLocalUrl(url)) return url;
        try
        {
            URI uri = URI.create(url);
            Map<String, String> query = new HashMap<>();
            for (String part : uri.getRawQuery().split("&"))
            {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) query.put(pair[0], pair[1]);
            }
            Path file = authorizedFile(uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1),
                Long.parseLong(query.get("expires")), query.get("signature"));
            String mime = Files.probeContentType(file);
            if (mime == null || !Set.of("image/png", "image/jpeg", "image/webp", "image/gif").contains(mime)
                    || Files.size(file) > 20L * 1024 * 1024)
                throw new ServiceException("模型参考图需为 PNG、JPEG、WebP 或 GIF，且不超过 20MB");
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        }
        catch (ServiceException exception) { throw exception; }
        catch (Exception exception) { throw new ServiceException("无法读取本地参考图，请检查文件保存目录"); }
    }

    public boolean deleteUrl(String url)
    {
        if (!isLocalUrl(url)) return false;
        try
        {
            String encoded = URI.create(url).getPath().substring(URI.create(url).getPath().lastIndexOf('/') + 1);
            return delete(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8));
        }
        catch (Exception exception) { return false; }
    }

    private String signature(String key, long expires)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((key + ":" + expires).getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) { throw new IllegalStateException("无法签名本地文件地址"); }
    }

    private Path resolve(Path root, String key)
    {
        if (key == null || key.isBlank() || key.contains("\\") || key.contains(":") || key.startsWith("/"))
            throw new ServiceException("文件路径无效");
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || file.equals(root)) throw new ServiceException("文件路径无效");
        rejectLinks(file);
        return file;
    }

    private void rejectLinks(Path path)
    {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new ServiceException("文件目录不支持符号链接");
    }
}
