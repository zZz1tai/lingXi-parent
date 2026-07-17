package com.lingXi.app.controller;

import com.lingXi.app.domain.AppTaskDetails;
import com.lingXi.app.service.TaskDetailsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "APP工单详情管理")
@RestController("appTaskDetailsController")
@RequestMapping("/task-service/taskDetails")
public class TaskDetailsController {

    @Autowired
    private TaskDetailsService taskDetailsService;

    @ApiOperation("根据工单id查询补货工单列表")
    @GetMapping("/{taskId}")
    public List<AppTaskDetails> findById(@PathVariable Long taskId) {
        return taskDetailsService.getByTaskId(taskId);
    }

}