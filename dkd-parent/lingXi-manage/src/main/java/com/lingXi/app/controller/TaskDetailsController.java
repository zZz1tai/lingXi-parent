package com.lingXi.app.controller;

import com.lingXi.app.domain.AppTaskDetails;
import com.lingXi.app.service.TaskDetailsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "APP工单详情管理")
@RestController("appTaskDetailsController")
@RequestMapping("/task-service/taskDetails")
public class TaskDetailsController {

    @Autowired
    private TaskDetailsService taskDetailsService;

    @Operation(summary = "根据工单id查询补货工单列表")
    @GetMapping("/{taskId}")
    public List<AppTaskDetails> findById(@PathVariable Long taskId) {
        return taskDetailsService.getByTaskId(taskId);
    }

}