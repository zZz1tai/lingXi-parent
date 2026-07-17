package com.lingXi.manage.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 用户工单关联对象 tb_user_task_relation
 * 
 * @author lingXi
 * @date 2025-12-16
 */
public class UserTaskRelation
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 系统用户ID */
    private Long userId;

    /** 工单ID */
    private Long taskId;

    /** 关联类型：0-执行人 1-指派人 */
    private Integer relationType;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }
    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public Long getTaskId()
    {
        return taskId;
    }
    public void setRelationType(Integer relationType)
    {
        this.relationType = relationType;
    }

    public Integer getRelationType()
    {
        return relationType;
    }
    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }
    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    @Override
    public String toString() {
        return "UserTaskRelation{" +
                "id=" + id +
                ", userId=" + userId +
                ", taskId=" + taskId +
                ", relationType=" + relationType +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}