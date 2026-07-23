package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 智能快捷提问生成请求视图对象
 * <p>继承 ChatBaseVO，包含对话历史字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GenerateQuestionsVO extends ChatBaseVO {
    /**
     * 用于生成快捷问题的对话历史；每项至少包含 content，并兼容 role、messageType 或 isUser。
     */
    private List<Map<String, Object>> chatHistory;
}
