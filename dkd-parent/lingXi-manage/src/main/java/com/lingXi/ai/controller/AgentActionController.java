package com.lingXi.ai.controller;

import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.vo.AgentActionDecisionVO;
import com.lingXi.ai.service.AgentWriteActionService;
import com.lingXi.ai.service.IChatSessionService;
import com.lingXi.ai.service.IQwenService;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.domain.model.LoginUser;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.manage.domain.ChatSession;
import com.lingXi.manage.domain.Emp;
import com.lingXi.manage.service.IEmpService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 登录用户审批 AI 受控动作并恢复原 LangGraph 会话。 */
@Api(tags = "AI受控写操作")
@Slf4j
@RestController
@RequestMapping("/api/ai/actions")
public class AgentActionController {
    private final AgentWriteActionService actionService;
    private final IQwenService qwenService;
    private final IChatSessionService chatSessionService;
    private final IEmpService empService;

    public AgentActionController(
            AgentWriteActionService actionService,
            IQwenService qwenService,
            IChatSessionService chatSessionService,
            IEmpService empService) {
        this.actionService = actionService;
        this.qwenService = qwenService;
        this.chatSessionService = chatSessionService;
        this.empService = empService;
    }

    @ApiOperation("批准或拒绝一个待确认的AI受控动作")
    @PreAuthorize("@ss.hasPermi('manage:task:add')")
    @Log(title = "AI受控写操作审批", businessType = BusinessType.GRANT)
    @PostMapping("/{actionId}/decision")
    public SseEmitter decide(
            @PathVariable String actionId,
            @Validated @RequestBody AgentActionDecisionVO decision) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        AgentUserContext context = currentContext(loginUser);
        requireOwnedSession(decision.getSessionId(), context.getUserId());
        actionService.decide(
                actionId,
                decision.getSessionId(),
                decision.getDecision(),
                decision.getDescription(),
                context);
        return qwenService.resumeActionV2(
                decision.getSessionId(), context, actionId, decision.getDecision());
    }

    private AgentUserContext currentContext(LoginUser loginUser) {
        Emp employee = null;
        if (empService != null && loginUser.getUserId() != null) {
            try {
                Emp query = new Emp();
                query.setUserId(loginUser.getUserId());
                List<Emp> employees = empService.selectEmpList(query);
                if (employees != null && !employees.isEmpty()) {
                    employee = employees.get(0);
                }
            } catch (RuntimeException exception) {
                log.warn("构造受控动作用户上下文时员工档案查询失败，errorType={}",
                        exception.getClass().getSimpleName());
            }
        }
        return AgentUserContext.fromAuthenticated(loginUser, employee);
    }

    private ChatSession requireOwnedSession(String sessionId, String userId) {
        ChatSession session = chatSessionService.selectChatSessionBySessionId(sessionId.trim());
        if (session == null || !userId.equals(session.getUserId())) {
            throw new ServiceException("会话不存在或无权访问");
        }
        return session;
    }
}
