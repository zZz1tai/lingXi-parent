package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 对话历史查询请求视图对象
 * <p>继承 ChatBaseVO，包含查询范围字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HistoryQueryVO extends ChatBaseVO {
    /** 查询范围；值为 all 时查询当前用户全部历史，否则按 sessionId 查询。 */
    private String queryScope;
}
