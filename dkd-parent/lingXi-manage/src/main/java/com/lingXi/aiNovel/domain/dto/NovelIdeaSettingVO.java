package com.lingXi.aiNovel.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** 构思文档中的世界观设定条目。 */
@Schema(description = "构思文档世界观设定")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NovelIdeaSettingVO
{
    /** 世界观与规则 */
    private String worldBuilding;

    /** 时代与时间背景 */
    private String timePeriod;

    /** 主要地点 */
    private String location;

    public String getWorldBuilding()
    {
        return worldBuilding;
    }

    public void setWorldBuilding(String worldBuilding)
    {
        this.worldBuilding = worldBuilding;
    }

    public String getTimePeriod()
    {
        return timePeriod;
    }

    public void setTimePeriod(String timePeriod)
    {
        this.timePeriod = timePeriod;
    }

    public String getLocation()
    {
        return location;
    }

    public void setLocation(String location)
    {
        this.location = location;
    }
}
