package com.lingXi.aiNovel.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 章节节奏分析请求
 * <p>Java 只搬运参数与请求体，Prompt 拼接由 Python 负责；
 * content 由 Java 按章节正文截断后传入。</p>
 */
@Schema(description = "章节节奏分析请求")
public class NovelPacingRequestDTO
{
    /** 作品名称 */
    @NotBlank(message = "作品名称不能为空")
    @Size(max = 128, message = "作品名称不能超过128个字符")
    private String workName;

    /** 题材，可空 */
    @Size(max = 64, message = "题材不能超过64个字符")
    private String genre;

    /** 章节标题，可空 */
    @Size(max = 128, message = "章节标题不能超过128个字符")
    private String chapterTitle;

    /** 节奏档位：relaxed/steady/balanced/intense/rapid */
    private String pacingLevel;

    /** 章节正文 */
    @NotBlank(message = "章节正文不能为空")
    private String content;

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

    public String getChapterTitle()
    {
        return chapterTitle;
    }

    public void setChapterTitle(String chapterTitle)
    {
        this.chapterTitle = chapterTitle;
    }

    public String getPacingLevel()
    {
        return pacingLevel;
    }

    public void setPacingLevel(String pacingLevel)
    {
        this.pacingLevel = pacingLevel;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
