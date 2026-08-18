package com.lingXi.aiNovel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小说创作智能体流式写作请求。
 * <p>作品上下文由服务端从作品库组装，浏览器只提交作品、章节与创作指令。</p>
 */
@Data
public class NovelWriteRequestVO {

    /** 创作指令最大长度。 */
    public static final int MAX_MESSAGE_LENGTH = 32_000;

    /** 创作指令。 */
    @NotBlank(message = "创作指令不能为空")
    @Size(max = MAX_MESSAGE_LENGTH, message = "创作指令不能超过32000个字符")
    private String message;

    /** 作品会话标识，每个作品一个独立智能体会话。 */
    @NotBlank(message = "会话ID不能为空")
    @Size(max = 128, message = "会话ID不能超过128个字符")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$", message = "会话ID格式无效")
    private String sessionId;

    /** 作品ID。 */
    @NotNull(message = "作品ID不能为空")
    private Long workId;

    /** 当前章节ID（长篇小说）。 */
    private Long chapterId;

    /** 记忆模式：conversation-作品主会话，stateless-仅使用本次作品上下文。 */
    @Pattern(regexp = "^(conversation|stateless)$", message = "记忆模式无效")
    private String memoryMode = "conversation";
}
