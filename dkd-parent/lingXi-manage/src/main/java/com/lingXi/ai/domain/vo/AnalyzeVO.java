package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 数据看板分析请求视图对象
 * <p>继承 ChatBaseVO，包含问题、开始时间、结束时间字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AnalyzeVO extends ChatBaseVO {
    /** 用户针对数据看板提出的分析问题。 */
    @NotBlank(message = "问题不能为空")
    @Size(max = MAX_CHAT_TEXT_LENGTH, message = "问题不能超过32000个字符")
    private String question;
    /** 看板数据查询的开始时间，由看板服务解释具体格式。 */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "开始日期格式必须为YYYY-MM-DD")
    private String start;
    /** 看板数据查询的结束时间，由看板服务解释具体格式。 */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "结束日期格式必须为YYYY-MM-DD")
    private String end;
}
