package com.lingXi.aiNovel.util;

/**
 * 小说正文字数统计工具。
 * <p>统计非空白 UTF-16 字符数，与浏览器端移除 {@code \\s} 后读取
 * {@code String.length} 的口径保持一致。</p>
 */
public final class NovelWordCounter
{
    private NovelWordCounter()
    {
    }

    public static int count(String content)
    {
        if (content == null || content.isEmpty())
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < content.length(); i++)
        {
            char character = content.charAt(i);
            if (!isBrowserWhitespace(character))
            {
                count++;
            }
        }
        return count;
    }

    /** ECMAScript {@code \\s} 匹配的完整空白字符集合。 */
    private static boolean isBrowserWhitespace(char character)
    {
        return (character >= 0x0009 && character <= 0x000D)
                || character == 0x0020
                || character == 0x00A0
                || character == 0x1680
                || (character >= 0x2000 && character <= 0x200A)
                || character == 0x2028
                || character == 0x2029
                || character == 0x202F
                || character == 0x205F
                || character == 0x3000
                || character == 0xFEFF;
    }
}
