package com.lingXi.aiNovel.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** 构思文档中的关键场景条目。 */
@Schema(description = "构思文档关键场景")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NovelIdeaSceneVO
{
    /** 场景名 */
    private String title;

    /** 发生什么，为什么重要 */
    private String description;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }
}
