package com.lingXi.app.controller;

import com.lingXi.app.domain.AppTask;
import com.lingXi.app.domain.dto.CancelTaskDto;
import com.lingXi.app.domain.vo.Pager;
import com.lingXi.app.domain.vo.TaskSearchVo;
import com.lingXi.app.service.TaskService;
import com.lingXi.common.utils.SecurityUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import javax.validation.constraints.NotNull;

// 工单
@Api(tags = "APP工单管理")
@RestController("appTaskController")
@RequestMapping("/task-service/task")
@Slf4j
public class TaskController{

    @Autowired
    private TaskService taskService;



    /**
     * 搜索工单
     *
     * @param taskSearchVo 工单搜索参数
     * @return
     */
    @ApiOperation("搜索工单")
    @GetMapping("/search")
    public Pager<AppTask> search(TaskSearchVo taskSearchVo) {
        taskService.checkTaskPermission(null);
        return taskService.search(taskSearchVo);
    }

    @ApiOperation("获取用户排名情况")
    @GetMapping("/rank")
    public Map<String, Object> rank() {
        taskService.checkEmpBinding();
        Long userId = SecurityUtils.getUserId();
        return taskService.getRank(userId);
    }

    @ApiOperation("获取用户排名情况（兼容旧路径）")
    @GetMapping("/rank/{userId}")
    public Map<String, Object> rank(@PathVariable("userId") Long userId) {
        taskService.checkEmpBinding();
        return taskService.getRank(userId);
    }


    @ApiOperation("接受工单")
    @GetMapping("/accept/{taskId}")
    public Boolean accept(@PathVariable("taskId") Long taskId ){
        taskService.checkTaskPermission(taskId);
        // 获取登录人id
        Long userId = SecurityUtils.getUserId();
        // 调用service
        return taskService.accept(taskId,userId);
    }


    @ApiOperation("拒绝/取消工单")
    @PostMapping("/cancel/{taskId}")
    public Boolean cancel(@PathVariable("taskId")Long taskId,@RequestBody CancelTaskDto cancelTaskDto){
        taskService.checkTaskPermission(taskId);
        // 获取登录人id
        Long userId = SecurityUtils.getUserId();
        // 调用service
        return taskService.cancel(taskId,cancelTaskDto,userId);
    }


    @ApiOperation("完成工单")
    @GetMapping("/complete/{taskId}")
    public Boolean complete(@PathVariable("taskId")Long taskId){
        taskService.checkTaskPermission(taskId);
        // 获取登录人id
        Long userId = SecurityUtils.getUserId();
        // 调用service
        return taskService.complete(taskId,userId);
    }
}