package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class GenerateQuestionsVO extends ChatBaseVO {
    private List<Map<String, Object>> chatHistory;
}
