package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频分镜实体类，对应数据库表 ai_video_shot。
 * <p>分镜是视频创作的最小镜头单元，包含镜头时长、景别、
 * 运镜方式、构图描述、动作描述及情绪描述等信息。</p>
 */
@Data
public class AiVideoShot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分镜主键ID */
    private Long shotId;

    /** 所属项目ID */
    private Long projectId;

    /** 所属章节ID */
    private Long chapterId;

    /** 所属场景ID */
    private Long sceneId;

    /** 分镜序号，在场景内从1开始 */
    private Integer shotNo;

    /** 镜头持续时长（毫秒） */
    private Integer durationMs;

    /** 景别，如特写、中景、远景等 */
    private String shotSize;

    /** 运镜方式，如推、拉、摇、移、跟等 */
    private String cameraMovement;

    /** 构图描述 */
    private String compositionText;

    /** 动作描述，镜头中人物或物体的运动 */
    private String actionText;

    /** 情绪描述，镜头需要传达的情感基调 */
    private String emotionText;

    /** 对话内容，JSON格式存储 */
    private String dialogueJson;

    /** 提示词上下文，JSON格式存储，用于AI生成时的上下文信息 */
    private String promptContextJson;

    /** 分镜状态，如待生成、生成中、已完成等 */
    private String status;

    /** 版本号，支持分镜内容迭代 */
    private Integer versionNo;
}
