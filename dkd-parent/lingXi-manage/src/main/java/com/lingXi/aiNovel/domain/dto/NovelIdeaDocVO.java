package com.lingXi.aiNovel.domain.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 小说构思文档（构思 Agent 结构化产物），用于「一键开书」。
 * <p>Java 只负责解析文档并创建作品与首批设定卡，Prompt 与
 * 文档结构约定都在 Python 侧。</p>
 */
@Schema(description = "小说构思文档")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NovelIdeaDocVO
{
    /** 建议的书名 */
    @NotBlank(message = "构思文档缺少书名")
    @Size(max = 128, message = "书名不能超过128个字符")
    private String workName;

    /** 题材 */
    @NotBlank(message = "构思文档缺少题材")
    @Size(max = 64, message = "题材不能超过64个字符")
    private String genre;

    /** 一句话卖点/钩子 */
    private String oneLiner;

    /** 一句话故事概要 */
    private String logline;

    /** 主角（至少一位） */
    @NotEmpty(message = "构思文档至少需要一位主角")
    @Size(max = 10, message = "主角不能超过10位")
    private List<@Valid NovelIdeaPersonVO> protagonists;

    /** 配角 */
    @Size(max = 10, message = "配角不能超过10位")
    private List<@Valid NovelIdeaPersonVO> supporting;

    /** 反派 */
    @Size(max = 10, message = "反派不能超过10位")
    private List<@Valid NovelIdeaPersonVO> antagonists;

    /** 核心冲突 */
    private String coreConflict;

    /** 主题立意 */
    private String theme;

    /** 基调 */
    private String tone;

    /** 世界观设定 */
    private NovelIdeaSettingVO setting;

    /** 金手指/特殊设定 */
    private String magicSystem;

    /** 关键场景 */
    private List<NovelIdeaSceneVO> keyScenes;

    /** 收束方向 */
    private String endingHint;

    /** 卖点列表 */
    private List<String> sellingPoints;

    public String getWorkName()
    {
        return workName;
    }

    public void setWorkName(String workName)
    {
        this.workName = workName;
    }

    public String getGenre()
    {
        return genre;
    }

    public void setGenre(String genre)
    {
        this.genre = genre;
    }

    public String getOneLiner()
    {
        return oneLiner;
    }

    public void setOneLiner(String oneLiner)
    {
        this.oneLiner = oneLiner;
    }

    public String getLogline()
    {
        return logline;
    }

    public void setLogline(String logline)
    {
        this.logline = logline;
    }

    public List<NovelIdeaPersonVO> getProtagonists()
    {
        return protagonists;
    }

    public void setProtagonists(List<NovelIdeaPersonVO> protagonists)
    {
        this.protagonists = protagonists;
    }

    public List<NovelIdeaPersonVO> getSupporting()
    {
        return supporting;
    }

    public void setSupporting(List<NovelIdeaPersonVO> supporting)
    {
        this.supporting = supporting;
    }

    public List<NovelIdeaPersonVO> getAntagonists()
    {
        return antagonists;
    }

    public void setAntagonists(List<NovelIdeaPersonVO> antagonists)
    {
        this.antagonists = antagonists;
    }

    public String getCoreConflict()
    {
        return coreConflict;
    }

    public void setCoreConflict(String coreConflict)
    {
        this.coreConflict = coreConflict;
    }

    public String getTheme()
    {
        return theme;
    }

    public void setTheme(String theme)
    {
        this.theme = theme;
    }

    public String getTone()
    {
        return tone;
    }

    public void setTone(String tone)
    {
        this.tone = tone;
    }

    public NovelIdeaSettingVO getSetting()
    {
        return setting;
    }

    public void setSetting(NovelIdeaSettingVO setting)
    {
        this.setting = setting;
    }

    public String getMagicSystem()
    {
        return magicSystem;
    }

    public void setMagicSystem(String magicSystem)
    {
        this.magicSystem = magicSystem;
    }

    public List<NovelIdeaSceneVO> getKeyScenes()
    {
        return keyScenes;
    }

    public void setKeyScenes(List<NovelIdeaSceneVO> keyScenes)
    {
        this.keyScenes = keyScenes;
    }

    public String getEndingHint()
    {
        return endingHint;
    }

    public void setEndingHint(String endingHint)
    {
        this.endingHint = endingHint;
    }

    public List<String> getSellingPoints()
    {
        return sellingPoints;
    }

    public void setSellingPoints(List<String> sellingPoints)
    {
        this.sellingPoints = sellingPoints;
    }
}
