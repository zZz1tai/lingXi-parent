package com.lingXi.aiNovel.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小说构思智能体请求。
 * <p>构思发生在作品创建之前，因此只携带创意描述与独立会话标识，
 * 不复用要求作品 ID 的正文创作请求。</p>
 */
@Data
public class NovelIdeaRequestVO {

    /** 构思描述。 */
    @NotBlank(message = "构思描述不能为空")
    @Size(max = NovelWriteRequestVO.MAX_MESSAGE_LENGTH,
            message = "构思描述不能超过32000个字符")
    private String message;

    /** 本次构思的独立多轮会话标识。 */
    @NotBlank(message = "会话ID不能为空")
    @Size(max = 128, message = "会话ID不能超过128个字符")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
            message = "会话ID格式无效")
    private String sessionId;
}
