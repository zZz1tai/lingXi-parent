package com.lingXi.aiVedio.tool;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音分享口令/链接解析工具。
 * <p>
 * 仅做纯文本与 URL 的解析（不发起网络请求）：从用户粘贴的整段分享口令中提取抖音链接，
 * 并尝试从链接中直接提取作品 ID。短链（v.douyin.com）需要先跳转才能拿到作品 ID，
 * 跳转由上层服务负责。
 * </p>
 *
 * @author lingXi
 * @since 2026-11-11
 */
public final class DouyinShareLinkParser
{
    /** 分享口令中可能出现的抖音链接（允许省略协议头，遇空白或中文标点结束）。 */
    private static final Pattern SHARE_URL = Pattern.compile(
            "(?:https?://)?(?:[a-z0-9-]+\\.)*(?:v\\.douyin\\.com|(?:www\\.|m\\.)?douyin\\.com|"
                    + "(?:www\\.|m\\.)?iesdouyin\\.com)/[A-Za-z0-9_/?=&.%#\\-]*",
            Pattern.CASE_INSENSITIVE);

    /** 作品 ID 的常见出现位置：/video/{id}、/share/video/{id}、modal_id={id}、/note/{id}。 */
    private static final Pattern AWEME_ID = Pattern.compile(
            "(?:/video/|/share/video/|/note/|modal_id=)(\\d{15,25})");

    /** 作品 ID 合法格式：15~25 位纯数字。 */
    private static final Pattern AWEME_ID_FORMAT = Pattern.compile("^\\d{15,25}$");

    /** 允许跟随跳转的抖音域名后缀。 */
    private static final String[] ALLOWED_HOST_SUFFIXES = {
            "douyin.com", "iesdouyin.com", "douyinvod.com", "douyinpic.com", "douyinstatic.com"};

    private DouyinShareLinkParser()
    {
    }

    /**
     * 从分享口令文本中提取第一个抖音链接。
     *
     * @param text 用户粘贴的整段分享口令
     * @return 规范化后的链接（自动补全 https:// 协议头）；未找到返回 {@code null}
     */
    public static String extractShareUrl(String text)
    {
        if (text == null || text.isBlank())
        {
            return null;
        }
        Matcher matcher = SHARE_URL.matcher(text.trim());
        if (!matcher.find())
        {
            return null;
        }
        String url = matcher.group();
        // 去掉可能粘连的结尾标点
        while (url.length() > 1 && ".,;:!?/，。；：！？、".indexOf(url.charAt(url.length() - 1)) >= 0
                && !url.endsWith("/"))
        {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith("http"))
        {
            url = "https://" + url;
        }
        return url;
    }

    /**
     * 从链接中直接提取作品 ID。
     *
     * @param url 抖音链接
     * @return 作品 ID；短链等无法直接提取时返回 {@code null}（需先跳转短链）
     */
    public static String extractAwemeId(String url)
    {
        if (url == null || url.isBlank())
        {
            return null;
        }
        Matcher matcher = AWEME_ID.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 校验作品 ID 格式是否合法。
     *
     * @param awemeId 作品 ID
     * @return 是否为 15~25 位数字
     */
    public static boolean isValidAwemeId(String awemeId)
    {
        return awemeId != null && AWEME_ID_FORMAT.matcher(awemeId).matches();
    }

    /**
     * 判断链接是否属于抖音域名（用于短链跳转时的白名单校验，防止被当作跳板访问内网）。
     *
     * @param url 待校验链接
     * @return 是否为抖音域名
     */
    public static boolean isDouyinHost(String url)
    {
        if (url == null || url.isBlank())
        {
            return false;
        }
        String host;
        try
        {
            host = java.net.URI.create(url).getHost();
        }
        catch (IllegalArgumentException exception)
        {
            return false;
        }
        if (host == null)
        {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        for (String suffix : ALLOWED_HOST_SUFFIXES)
        {
            if (host.equals(suffix) || host.endsWith("." + suffix))
            {
                return true;
            }
        }
        return false;
    }
}
