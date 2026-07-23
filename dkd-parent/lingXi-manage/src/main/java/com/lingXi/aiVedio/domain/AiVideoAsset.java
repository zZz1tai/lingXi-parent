package com.lingXi.aiVedio.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频统一资产实体类，对应数据库表 ai_video_asset。
 * <p>资产是视频创作过程中所有可存储内容的统一抽象，涵盖图片、
 * 关键帧、视频片段等类型。支持多版本管理、归档、审核等生命周期操作。</p>
 */
@Data
public class AiVideoAsset extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 资产主键ID */
    private Long assetId;

    /** 所属项目ID */
    private Long projectId;

    /** 所属章节ID，全局资产可为空 */
    private Long chapterId;

    /** 所属场景ID，非场景级资产可为空 */
    private Long sceneId;

    /** 所属分镜ID，非分镜级资产可为空 */
    private Long shotId;

    /** 关联的人物ID，非人物相关资产可为空 */
    private Long characterId;

    /** 资产编码，项目内唯一标识 */
    private String assetCode;

    /** 资产名称 */
    private String assetName;

    /** 资产类型，如图片、视频、音频等 */
    private String assetType;

    /** 资产作用域，如全局、章节、场景、分镜级别 */
    private String assetScope;

    /** 是否为标准版本，1-是 0-否 */
    private Integer canonicalFlag;

    /** 资产状态，如草稿、已审核、已归档等 */
    private String status;

    /** 版本号，支持资产内容迭代 */
    private Integer versionNo;

    /** 源资产ID，标识该版本的前一个版本 */
    private Long sourceAssetId;

    /** 存储提供者标识 */
    private String storageProvider;

    /** 对象存储键名 */
    private String objectKey;

    /** 预览图对象存储键名 */
    private String previewObjectKey;

    /** 文件MIME类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 内容哈希值，用于去重校验 */
    private String contentHash;

    /** 图片/视频宽度（像素） */
    private Integer width;

    /** 图片/视频高度（像素） */
    private Integer height;

    /** 视频持续时长（毫秒） */
    private Integer durationMs;

    /** 正向提示词 */
    private String promptText;

    /** 反向提示词 */
    private String negativePromptText;

    /** 生成参数，JSON格式存储 */
    private String generationParamsJson;

    /** 元数据，JSON格式存储 */
    private String metadataJson;

    /** 是否已归档，由 metadataJson 中的 archived 标记派生，不改变资产原始生成状态 */
    private Boolean archived;

    /** 查询参数：是否包含已归档版本 */
    private Boolean includeArchived;

    /** 审核人 */
    private String approvedBy;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date approvedTime;
}
