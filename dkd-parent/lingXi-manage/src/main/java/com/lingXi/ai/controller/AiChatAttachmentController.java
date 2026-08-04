package com.lingXi.ai.controller;

import com.lingXi.ai.domain.vo.AiChatAttachmentSessionVO;
import com.lingXi.ai.domain.vo.AiChatAttachmentUploadVO;
import com.lingXi.ai.service.AiChatAttachmentService;
import com.lingXi.ai.service.IChatSessionService;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.manage.domain.ChatSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录用户的 AI 会话附件入口。 */
@Tag(name = "AI聊天附件")
@RestController
@RequestMapping("/api/ai/attachments")
public class AiChatAttachmentController {
    private final AiChatAttachmentService attachmentService;
    private final IChatSessionService chatSessionService;

    public AiChatAttachmentController(
            AiChatAttachmentService attachmentService,
            IChatSessionService chatSessionService) {
        this.attachmentService = attachmentService;
        this.chatSessionService = chatSessionService;
    }

    @Operation(summary = "上传会话附件到私有OSS")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult upload(@Validated @ModelAttribute AiChatAttachmentUploadVO request) {
        String userId = currentUserId();
        requireOwnedSession(request.getSessionId(), userId);
        return AjaxResult.success(attachmentService.upload(
                request.getSessionId().trim(), userId, request.getFile()));
    }

    @Operation(summary = "删除尚未发送的会话附件")
    @DeleteMapping("/{attachmentId}")
    public AjaxResult deletePending(
            @PathVariable String attachmentId,
            @Validated @ModelAttribute AiChatAttachmentSessionVO request) {
        String userId = currentUserId();
        requireOwnedSession(request.getSessionId(), userId);
        attachmentService.deletePending(
                attachmentId, request.getSessionId().trim(), userId);
        return AjaxResult.success();
    }

    private String currentUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("用户未登录");
        }
        return String.valueOf(userId);
    }

    private void requireOwnedSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new ServiceException("会话ID不能为空");
        }
        ChatSession session = chatSessionService.selectChatSessionBySessionId(sessionId.trim());
        if (session == null || !userId.equals(session.getUserId())) {
            throw new ServiceException("会话不存在或无权访问");
        }
    }
}
