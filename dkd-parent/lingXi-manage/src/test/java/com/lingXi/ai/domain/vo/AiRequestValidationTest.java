package com.lingXi.ai.domain.vo;

import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import com.lingXi.aiVedio.domain.dto.AiVideoQuickGenerationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void chatRequestMatchesPythonMessageAndSessionBounds() {
        ChatVO valid = new ChatVO();
        valid.setSessionId("session-valid_1");
        valid.setMessage(repeat('x', ChatBaseVO.MAX_CHAT_TEXT_LENGTH));
        assertTrue(validator.validate(valid).isEmpty());

        valid.setMessage("   ");
        assertViolation(validator.validate(valid), "payloadPresent");

        valid.setAttachmentIds(java.util.List.of(
                "123e4567-e89b-42d3-a456-426614174000"));
        assertTrue(validator.validate(valid).isEmpty());
        valid.setAttachmentIds(java.util.List.of());

        valid.setMessage(repeat('x', ChatBaseVO.MAX_CHAT_TEXT_LENGTH + 1));
        assertViolation(validator.validate(valid), "message");

        valid.setMessage("问题");
        valid.setSessionId(repeat('s', ChatBaseVO.MAX_SESSION_ID_LENGTH + 1));
        assertViolation(validator.validate(valid), "sessionId");

        valid.setSessionId("invalid session");
        assertViolation(validator.validate(valid), "sessionId");
    }

    @Test
    void analyzeRequestRejectsBlankAndOversizedQuestions() {
        AnalyzeVO request = new AnalyzeVO();
        request.setSessionId("session-analysis");
        request.setQuestion("\t");
        assertViolation(validator.validate(request), "question");

        request.setQuestion(repeat('x', ChatBaseVO.MAX_CHAT_TEXT_LENGTH + 1));
        assertViolation(validator.validate(request), "question");
    }

    @Test
    void smartQuestionRequestInheritsSessionValidation() {
        GenerateQuestionsVO request = new GenerateQuestionsVO();
        request.setSessionId("");
        assertViolation(validator.validate(request), "sessionId");

        request.setSessionId("session-questions");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void attachmentRequestViewsValidateSessionAndMultipartFile() {
        AiChatAttachmentSessionVO session = new AiChatAttachmentSessionVO();
        session.setSessionId("   ");
        assertViolation(validator.validate(session), "sessionId");

        AiChatAttachmentUploadVO upload = new AiChatAttachmentUploadVO();
        upload.setSessionId("session-upload");
        assertViolation(validator.validate(upload), "file");
    }

    @Test
    void quickVideoRequestAllowsNoReferenceImagesAndLimitsOptionalImages() {
        AiVideoQuickGenerationRequest request = new AiVideoQuickGenerationRequest();
        request.setPrompt("云海上方的日出延时摄影，镜头缓慢前移");
        request.setDurationMs(Integer.valueOf(5000));
        assertTrue(validator.validate(request).isEmpty());

        request.setImages(Collections.<MultipartFile>nCopies(6, null));
        assertViolation(validator.validate(request), "images");
    }

    private static void assertViolation(
            Set<? extends ConstraintViolation<?>> violations,
            String propertyName) {
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(
                violation -> propertyName.equals(
                        violation.getPropertyPath().toString())));
    }

    private static String repeat(char character, int count) {
        return new String(new char[count]).replace('\0', character);
    }
}
