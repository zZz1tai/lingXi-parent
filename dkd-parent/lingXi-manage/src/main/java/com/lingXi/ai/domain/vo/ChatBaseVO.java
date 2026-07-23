package com.lingXi.ai.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 聊天基础视图对象
 * <p>定义会话ID、用户ID、用户名等通用字段及校验规则。</p>
 */
@Data
public class ChatBaseVO {
    /** 会话标识最大长度。 */
    public static final int MAX_SESSION_ID_LENGTH = 128;
    /** 单次聊天或分析问题允许的最大字符数。 */
    public static final int MAX_CHAT_TEXT_LENGTH = 32_000;
    /** 会话标识白名单：首字符为字母或数字，其余仅允许安全分隔符。 */
    public static final String SESSION_ID_REGEX =
            "^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$";

    /** 当前请求关联的会话标识。 */
    @NotBlank(message = "会话ID不能为空")
    @Size(max = MAX_SESSION_ID_LENGTH, message = "会话ID不能超过128个字符")
    @Pattern(regexp = SESSION_ID_REGEX, message = "会话ID格式无效")
    private String sessionId;
    /** 客户端兼容字段；实际用户身份由服务端登录上下文覆盖。 */
    private String userId;
    /** 客户端兼容字段；实际用户名由服务端登录上下文覆盖。 */
    private String userName;
}
