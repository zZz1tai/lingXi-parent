package com.lingXi.aiNovel.controller;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lingXi.ai.client.AgentClient;
import com.lingXi.aiNovel.service.NovelSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import com.lingXi.aiNovel.domain.dto.NovelWorkContextDTO;
import com.lingXi.aiNovel.domain.dto.NovelIdeaDocVO;
import com.lingXi.aiNovel.domain.dto.NovelIdeaRequestVO;
import com.lingXi.aiNovel.domain.dto.NovelWriteRequestVO;
import com.lingXi.aiNovel.domain.dto.NovelSynopsisRequestVO;
import com.lingXi.aiNovel.service.IAiNovelWorkService;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 小说创作智能体接口控制器
 * <p>接收小说创作指令，服务端从作品库组装上下文并调用 Python 创作智能体，
 * 以结构化 SSE 事件流返回；创作过程与作品归属均按当前登录用户隔离。</p>
 */
@Tag(name = "AI小说创作接口")
@RestController
@RequestMapping("/novel")
public class NovelController {

    private static final Logger log = LoggerFactory.getLogger(NovelController.class);

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private IAiNovelWorkService workService;

    @Autowired
    private NovelSessionService novelSessions;

    /**
     * 流式调用小说创作智能体
     *
     * @param request 创作请求，包含用户消息、作品会话ID、作品ID与章节ID
     * @return 结构化 SSE 事件流
     */
    @Operation(summary = "流式调用小说创作智能体")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @PostMapping("/write/stream")
    public SseEmitter writeStream(@Validated @RequestBody NovelWriteRequestVO request) {
        try {
            String userId = currentUserId();
            String username = currentUsername();
            String message = request.getMessage() == null ? "" : request.getMessage().trim();
            if (message.isEmpty()) {
                throw new ServiceException("创作指令不能为空");
            }
            Long workId = request.getWorkId();
            if (workId == null) {
                throw new ServiceException("作品ID不能为空");
            }
            String sessionId = request.getSessionId() == null
                    ? null
                    : request.getSessionId().trim();
            if (sessionId == null || sessionId.isEmpty()) {
                throw new ServiceException("会话ID不能为空");
            }

            // 1. 校验作品归属并组装持久上下文（章节尾文/设定/伏笔/相关大纲）。
            workService.checkWorkOwner(workId);
            NovelWorkContextDTO workContext = workService.buildNovelWorkContext(
                    workId, request.getChapterId());

            long sessionKey = novelSessions.startTurn(
                    sessionId, userId, workContext.getWorkName(), username, message);

            AtomicBoolean assistantSaved = new AtomicBoolean(false);
            return agentClient.streamNovelWrite(
                    message,
                    sessionId,
                    userId,
                    workContext,
                    request.getMemoryMode(),
                    reply -> {
                        if (reply != null && !reply.isBlank() && assistantSaved.compareAndSet(false, true)) {
                            novelSessions.saveReply(sessionKey, username, reply);
                        }
                    });
        } catch (Exception e) {
            log.warn("小说创作流式调用失败，errorType={}",
                    e.getClass().getSimpleName());
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data(
                        "{\"type\":\"error\",\"content\":\""
                                + safeSseText(e.getMessage()) + "\"}"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
            return emitter;
        }
    }

    /**
     * 流式调用小说构思智能体（模糊创意 → 追问补全 → 构思文档）
     * <p>构思会话独立于作品创作会话：同一 sessionId 的多轮消息
     * 被 Python checkpoint 自动续接；事件流中的 clarification 为
     * 追问问题，idea_doc 为结构化构思文档。</p>
     *
     * @param request 构思请求，包含用户消息与会话ID
     * @return 结构化 SSE 事件流
     */
    @Operation(summary = "流式调用小说构思智能体")
    @PreAuthorize("@ss.hasPermi('novel:work:add')")
    @PostMapping(value = "/idea/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ideaStream(@Validated @RequestBody NovelIdeaRequestVO request) {
        try {
            String userId = currentUserId();
            String message = request.getMessage() == null ? "" : request.getMessage().trim();
            if (message.isEmpty()) {
                throw new ServiceException("构思描述不能为空");
            }
            String sessionId = request.getSessionId() == null
                    ? null
                    : request.getSessionId().trim();
            if (sessionId == null || sessionId.isEmpty()) {
                throw new ServiceException("会话ID不能为空");
            }
            return agentClient.streamNovelIdea(message, sessionId, userId, null);
        } catch (Exception e) {
            log.warn("小说构思流式调用失败，errorType={}",
                    e.getClass().getSimpleName());
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data(
                        "{\"type\":\"error\",\"content\":\""
                                + safeSseText(e.getMessage()) + "\"}"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
            return emitter;
        }
    }

    @Operation(summary = "由构思文档一键开书")
    @PreAuthorize("@ss.hasPermi('novel:work:add')")
    @Log(title = "AI小说构思开书", businessType = BusinessType.INSERT)
    @PostMapping("/idea/create-work")
    public AjaxResult createWorkFromIdea(
            @Validated @RequestBody NovelIdeaDocVO idea) {
        try {
            Long workId = workService.createAiNovelWorkFromIdea(idea);
            return AjaxResult.success("开书成功", workId);
        } catch (Exception e) {
            log.warn("构思开书失败，errorType={}",
                    e.getClass().getSimpleName());
            return AjaxResult.error(e.getMessage() == null
                    ? "构思开书失败"
                    : e.getMessage());
        }
    }

    /** 删除尚未绑定作品的构思会话 checkpoint，避免废弃构思长期占用存储。 */
    @Operation(summary = "删除小说构思会话记忆")
    @PreAuthorize("@ss.hasPermi('novel:work:add')")
    @DeleteMapping("/idea/thread")
    public AjaxResult deleteIdeaThread(@RequestParam String sessionId) {
        try {
            String normalizedSessionId = sessionId == null ? "" : sessionId.trim();
            if (normalizedSessionId.isEmpty()) {
                throw new ServiceException("会话ID不能为空");
            }
            if (!normalizedSessionId.matches("^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$")) {
                throw new ServiceException("会话ID格式无效");
            }
            agentClient.deleteNovelThreadMemory(normalizedSessionId, currentUserId());
            return AjaxResult.success(true);
        } catch (Exception e) {
            log.warn("删除小说构思会话记忆失败，errorType={}",
                    e.getClass().getSimpleName());
            return AjaxResult.error(e.getMessage() == null
                    ? "删除构思会话失败"
                    : e.getMessage());
        }
    }

    /**
     * 根据书名自动拟写故事梗概
     *
     * @param request 书名、作品类型与题材
     * @return 生成的梗概文本
     */
    @Operation(summary = "根据书名自动拟写故事梗概")
    @PreAuthorize("@ss.hasPermi('novel:work:add')")
    @PostMapping("/synopsis/generate")
    public AjaxResult generateSynopsis(@RequestBody NovelSynopsisRequestVO request) {
        try {
            String workName = request.getWorkName() == null
                    ? ""
                    : request.getWorkName().trim();
            if (workName.isEmpty()) {
                throw new ServiceException("书名不能为空");
            }
            if (workName.length() > 128) {
                throw new ServiceException("书名过长（最多 128 字）");
            }
            String synopsis = agentClient.generateNovelSynopsis(
                    workName, request.getWorkType(), request.getGenre());
            return AjaxResult.success("操作成功", synopsis);
        } catch (Exception e) {
            log.warn("AI 拟写梗概失败，errorType={}",
                    e.getClass().getSimpleName());
            return AjaxResult.error(e.getMessage() == null
                    ? "AI 拟写梗概失败"
                    : e.getMessage());
        }
    }

    /**
     * 根据书名流式拟写故事梗概（SSE），梗概框内逐字呈现
     *
     * @param request 书名、作品类型与题材
     * @return 流式梗概文本事件
     */
    @Operation(summary = "根据书名流式拟写故事梗概")
    @PreAuthorize("@ss.hasPermi('novel:work:add')")
    @PostMapping(value = "/synopsis/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateSynopsisStream(
            @RequestBody NovelSynopsisRequestVO request) {
        String workName = request.getWorkName() == null
                ? ""
                : request.getWorkName().trim();
        if (workName.isEmpty()) {
            throw new ServiceException("书名不能为空");
        }
        if (workName.length() > 128) {
            throw new ServiceException("书名过长（最多 128 字）");
        }
        return agentClient.streamNovelSynopsis(
                workName, request.getWorkType(), request.getGenre());
    }

    /**
     * 删除小说创作会话记忆（Python checkpoint）与本地会话及历史
     *
     * @param sessionId 作品会话唯一标识
     * @return 删除结果
     */
    @Operation(summary = "删除小说创作会话记忆")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @DeleteMapping("/thread")
    public AjaxResult deleteThread(@RequestParam String sessionId) {
        try {
            String userId = currentUserId();
            String normalizedSessionId = sessionId == null
                    ? ""
                    : sessionId.trim();
            if (normalizedSessionId.isEmpty()) {
                throw new ServiceException("会话ID不能为空");
            }
            long sessionKey = novelSessions.requireSession(normalizedSessionId, userId);
            agentClient.deleteNovelThreadMemory(normalizedSessionId, userId);
            return AjaxResult.success(novelSessions.delete(sessionKey, normalizedSessionId, userId));
        } catch (Exception e) {
            log.warn("删除小说创作会话记忆失败，errorType={}",
                    e.getClass().getSimpleName());
            return AjaxResult.error(e.getMessage() == null
                    ? "删除小说创作会话记忆失败"
                    : e.getMessage());
        }
    }

    @Operation(summary = "读取当前用户的小说创作历史")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @GetMapping("/history")
    public AjaxResult history(@RequestParam String sessionId) {
        return AjaxResult.success(novelSessions.history(sessionId.trim(), currentUserId()));
    }

    private static String currentUserId() {
        return String.valueOf(SecurityUtils.getUserId());
    }

    private static String currentUsername() {
        return SecurityUtils.getUsername();
    }

    /** 去掉错误消息中的换行与引号，避免破坏 SSE 载荷。 */
    private static String safeSseText(String message) {
        if (message == null) {
            return "创作失败，请稍后重试";
        }
        String escaped = message.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
        return escaped.length() > 200 ? escaped.substring(0, 200) : escaped;
    }
}
