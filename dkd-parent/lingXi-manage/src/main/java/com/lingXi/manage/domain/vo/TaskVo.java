package com.lingXi.manage.domain.vo;

import com.lingXi.manage.domain.Task;
import com.lingXi.manage.domain.TaskType;
import lombok.Data;

@Data
public class TaskVo extends Task {
    //工单类型
    private TaskType taskType;
}
