package com.lingXi.aiNovel.domain.dto;

import java.util.List;
import lombok.Data;

/** Java 提交给 Python 的章节资料分析快照，仅包含受控业务字段。 */
@Data
public class NovelContextAgentRequestDTO
{
    private Long workId;
    private String workName;
    private String workType;
    private String genre;
    private String synopsis;
    private Long chapterId;
    private Integer chapterNo;
    private String chapterTitle;
    private String chapterContent;
    private List<SettingItem> settings;
    private List<ForeshadowItem> foreshadows;

    /** 带数据库主键的现有设定卡。 */
    @Data
    public static class SettingItem
    {
        private Long settingId;
        private String settingType;
        private String title;
        private String content;
    }

    /** 带数据库主键的现有伏笔。 */
    @Data
    public static class ForeshadowItem
    {
        private Long foreshadowId;
        private String title;
        private String description;
        private String status;
        private String priority;
        private String keyword;
        private Integer resolveChapterNo;
    }
}
