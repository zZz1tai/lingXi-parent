package com.lingXi.ai.domain.dto;

import lombok.Data;
import lombok.ToString;

/** Java 服务端发给 Python OCR 端点的私有图片请求。 */
@Data
public class AiImageOcrRequestDTO {
    private String name;
    private String mimeType;
    @ToString.Exclude
    private String imageUrl;
}
