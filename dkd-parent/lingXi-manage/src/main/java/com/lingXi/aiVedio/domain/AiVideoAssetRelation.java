package com.lingXi.aiVedio.domain;

import lombok.Data;

/**
 * AI 视频资产关系实体类，对应数据库表 ai_video_asset_relation。
 * <p>记录资产之间的引用及血缘关系，用于追溯资产的生成来源
 * 和依赖关系，支持资产的版本回溯和影响分析。</p>
 */
@Data
public class AiVideoAssetRelation
{
    /** 关系主键ID */
    private Long relationId;

    /** 所属项目ID */
    private Long projectId;

    /** 源资产ID（引用方） */
    private Long fromAssetId;

    /** 目标资产ID（被引用方） */
    private Long toAssetId;

    /** 关系类型，如生成来源、关键帧引用等 */
    private String relationType;

    /** 关系排序，用于多引用时的顺序 */
    private Integer relationOrder;

    /** 关系元数据，JSON格式存储 */
    private String metadataJson;
}
