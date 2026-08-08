package com.lingXi.aiNovel.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 小说作品上下文，由 Java 服务端从作品库组装后提交给 Python 智能体。
 * <p>字段命名与 lingXi-agent 的 NovelWorkContext 契约保持一致。</p>
 */
@Data
public class NovelWorkContextDTO {

    /** 作品ID */
    private Long workId;

    /** 作品名称 */
    private String workName;

    /** 作品类型：short/novel */
    private String workType;

    /** 题材类型 */
    private String genre;

    /** 作品梗概 */
    private String synopsis;

    /** 当前章节标题 */
    private String chapterTitle;

    /** 本章梗概 */
    private String chapterSynopsis;

    /** 当前正文末尾片段，用于无缝续写 */
    private String manuscriptTail;

    /** 设定卡列表 */
    private List<NovelSettingItemDTO> settings;
}
