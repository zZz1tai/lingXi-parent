package com.lingXi.manage.domain.enums;

/**
 * 聊天会话状态。
 * <p>状态流转：ACTIVE（正常）→ DELETING（删除中，拒绝新消息并清理 Checkpoint）
 * → 物理删除。删除失败时 DELETING 恢复为 ACTIVE。</p>
 */
public enum ChatSessionStatus
{
    /** 正常，允许发送消息和继续 Checkpoint */
    ACTIVE("ACTIVE", "正常"),

    /** 删除中，拒绝新消息，正在清理 Checkpoint 与本地数据 */
    DELETING("DELETING", "删除中");

    private final String code;
    private final String label;

    ChatSessionStatus(String code, String label)
    {
        this.code = code;
        this.label = label;
    }

    public String getCode()
    {
        return code;
    }

    public String getLabel()
    {
        return label;
    }

    /**
     * 判断指定状态是否为当前枚举值。
     *
     * @param status 状态字符串
     * @return 是否匹配
     */
    public boolean is(String status)
    {
        return code.equals(status);
    }
}
