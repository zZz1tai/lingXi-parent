package com.lingXi.aiNovel.domain;

import java.util.Date;
import lombok.Data;
import com.lingXi.common.core.domain.BaseEntity;

/** AI 小说章节资料同步持久化任务。 */
@Data
public class AiNovelContextTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_OBSOLETE = "OBSOLETE";

    private Long taskId;
    private Long workId;
    private Long chapterId;
    private Long ownerUserId;
    private String contentHash;
    private String status;
    private Integer attemptCount;
    private Date nextRunTime;
    private String workerId;
    private Date leaseUntil;
    private String resultJson;
    private String errorMessage;
    private Date startedTime;
    private Date finishedTime;
}
