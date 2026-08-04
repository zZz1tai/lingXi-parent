package com.lingXi.ai.domain.dto;

import lombok.Data;

/** Python OCR 端点返回的有界图片文字结果。 */
@Data
public class AiImageOcrResultDTO {
    private String text;
    private Boolean truncated;
}
