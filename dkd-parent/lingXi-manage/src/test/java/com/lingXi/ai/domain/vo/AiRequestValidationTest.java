package com.lingXi.ai.domain.vo;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
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
        assertViolation(validator.validate(valid), "message");

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
