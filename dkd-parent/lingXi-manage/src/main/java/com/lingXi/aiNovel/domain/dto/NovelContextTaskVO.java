package com.lingXi.aiNovel.domain.dto;

import java.util.Date;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** 资料同步异步任务的前端查询视图。 */
@Data
public class NovelContextTaskVO
{
    private Long taskId;
    private Long workId;
    private Long chapterId;
    private String contentHash;
    private String status;
    private Integer attemptCount;
    private JsonNode result;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
    private Date startedTime;
    private Date finishedTime;
}
