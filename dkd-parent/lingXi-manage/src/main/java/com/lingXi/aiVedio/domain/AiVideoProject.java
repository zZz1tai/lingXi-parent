package com.lingXi.aiVedio.domain;

import java.math.BigDecimal;
import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频项目实体类，对应数据库表 ai_video_project。
 * <p>项目是整个视频创作流程的顶层容器，包含项目元信息、
 * 视觉风格配置、存储用量以及成本估算等字段。</p>
 */
@Data
public class AiVideoProject extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 项目主键ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 来源类型，如原创、改编等 */
    private String sourceType;

    /** 改编模式，描述原文本与视频的适配方式 */
    private String adaptationMode;

    /** 项目状态，如草稿、进行中、已完成等 */
    private String status;

    /** 拥有者用户ID */
    private Long ownerUserId;

    /** 项目封面图片URL */
    private String coverUrl;

    /** 视觉风格描述 */
    private String visualStyle;

    /** 风格指南，JSON格式存储 */
    private String styleGuideJson;

    /** 默认画面宽高比 */
    private String defaultAspectRatio;

    /** 默认语言 */
    private String defaultLanguage;

    /** 已占用存储空间（字节） */
    private Long storageBytes;

    /** 预估成本 */
    private BigDecimal estimatedCost;

    /** 实际花费成本 */
    private BigDecimal actualCost;
}
