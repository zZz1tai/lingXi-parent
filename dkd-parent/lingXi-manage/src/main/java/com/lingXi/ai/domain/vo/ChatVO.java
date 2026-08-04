package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天请求视图对象
 * <p>继承 ChatBaseVO，包含用户消息字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatVO extends ChatBaseVO {
    /** 用户本轮发送给 Agent 的消息正文。 */
    @Size(max = MAX_CHAT_TEXT_LENGTH, message = "消息不能超过32000个字符")
    private String message;

    /** 本轮引用的会话附件ID；对象地址由服务端按登录态解析。 */
    @Size(max = 5, message = "每条消息最多上传5个附件")
    private List<@Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            message = "附件ID格式无效") String> attachmentIds = new ArrayList<>();

    /** 允许只发附件，但文字和附件不能同时为空。 */
    @AssertTrue(message = "消息或附件至少填写一项")
    public boolean isPayloadPresent() {
        return (message != null && !message.trim().isEmpty())
                || (attachmentIds != null && !attachmentIds.isEmpty());
    }
}
