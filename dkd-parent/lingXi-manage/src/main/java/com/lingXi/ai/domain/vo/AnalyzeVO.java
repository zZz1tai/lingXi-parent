package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AnalyzeVO extends ChatBaseVO {
    private String question;
    private String start;
    private String end;
}
