package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频场景实体类，对应数据库表 ai_video_scene。
 * <p>场景是对章节进一步拆分的叙事单元，包含场景标题、
 * 对应的源段落范围、时间/地点描述、氛围及戏剧目标等信息。</p>
 */
@Data
public class AiVideoScene extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 场景主键ID */
    private Long sceneId;

    /** 所属项目ID */
    private Long projectId;

    /** 所属章节ID */
    private Long chapterId;

    /** 场景序号，在章节内从1开始 */
    private Integer sceneNo;

    /** 场景标题 */
    private String sceneTitle;

    /** 对应源文本的起始段落编号 */
    private Integer sourceParagraphFrom;

    /** 对应源文本的结束段落编号 */
    private Integer sourceParagraphTo;

    /** 时间描述，如"夜晚""三天后" */
    private String timeDescription;

    /** 地点描述，如"山洞内""城市街道" */
    private String locationDescription;

    /** 场景氛围描述 */
    private String atmosphere;

    /** 戏剧目标，该场景需要实现的叙事目的 */
    private String dramaticGoal;

    /** 出场人物ID列表，逗号分隔 */
    private String characterIds;

    /** 场景包信息，JSON格式存储 */
    private String scenePackageJson;

    /** 场景状态，如待生成、已生成等 */
    private String status;

    /** 版本号，支持场景内容迭代 */
    private Integer versionNo;
}
