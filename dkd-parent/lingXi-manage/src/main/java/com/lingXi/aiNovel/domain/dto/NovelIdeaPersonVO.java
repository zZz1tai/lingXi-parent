package com.lingXi.aiNovel.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** 构思文档中的人物条目（主角/配角/反派）。 */
@Schema(description = "构思文档人物条目")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NovelIdeaPersonVO
{
    /** 姓名 */
    @NotBlank(message = "人物姓名不能为空")
    @Size(max = 128, message = "人物姓名不能超过128个字符")
    private String name;

    /** 身份/职业 */
    private String role;

    /** 性格特征 */
    private String trait;

    /** 个人目标 */
    private String goal;

    /** 金手指/特殊能力 */
    private String gimmick;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
    }

    public String getTrait()
    {
        return trait;
    }

    public void setTrait(String trait)
    {
        this.trait = trait;
    }

    public String getGoal()
    {
        return goal;
    }

    public void setGoal(String goal)
    {
        this.goal = goal;
    }

    public String getGimmick()
    {
        return gimmick;
    }

    public void setGimmick(String gimmick)
    {
        this.gimmick = gimmick;
    }
}
