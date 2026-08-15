package com.lingXi.aiNovel.domain.dto;

import lombok.Data;

/** AI 提出的单条设定/伏笔候选变更；不定义删除操作。 */
@Data
public class NovelContextChangeDTO
{
    /** setting 或 foreshadow。 */
    private String resourceType;
    /** ADD 或 UPDATE。 */
    private String operation;
    /** UPDATE 对应的现有资料主键；ADD 必须为空。 */
    private Long targetId;
    private String settingType;
    private String title;
    private String content;
    private String description;
    private String status;
    private String priority;
    private String keyword;
    private Integer resolveChapterNo;
    /** 模型引用的本章原文，仅用于用户复核。 */
    private String evidence;
    /** 模型给出的变更理由，仅用于用户复核。 */
    private String reason;
}
