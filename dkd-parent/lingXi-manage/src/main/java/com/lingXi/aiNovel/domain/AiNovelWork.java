package com.lingXi.aiNovel.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 小说作品实体类，对应数据库表 ai_novel_work。
 * <p>作品是小说创作工作台的顶层容器，长篇小说正文按章节存储，
 * 短篇小说正文直接存放在本表的 manuscript 字段。</p>
 */
@Data
public class AiNovelWork extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 作品主键ID */
    private Long workId;

    /** 作品名称 */
    private String workName;

    /** 作品类型：short-短篇，novel-长篇小说 */
    private String workType;

    /** 题材类型，如东方玄幻 */
    private String genre;

    /** 作品梗概 */
    private String synopsis;

    /** 短篇正文（长篇按章节存储） */
    private String manuscript;

    /** 作品状态：draft-草稿，writing-写作中，finished-已完成 */
    private String status;

    /** 作品节奏档位：relaxed-舒缓，steady-平稳，balanced-均衡，intense-紧凑，rapid-激烈 */
    private String pacingLevel;

    /** 创建者用户ID */
    private Long ownerUserId;

    /** 总字数（非持久化，列表查询时按正文动态统计，不含空白字符） */
    private Long wordCount;
}
