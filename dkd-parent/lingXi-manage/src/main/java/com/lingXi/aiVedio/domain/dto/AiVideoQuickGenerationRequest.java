package com.lingXi.aiVedio.domain.dto;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 对话页快速视频生成的 multipart 请求。 */
@Data
public class AiVideoQuickGenerationRequest
{
    @NotBlank(message = "请输入视频画面描述")
    @Size(max = 2500, message = "视频画面描述不能超过2500个字符")
    private String prompt;

    @NotNull(message = "请选择视频时长")
    @Min(value = 1000, message = "视频时长不能少于1秒")
    @Max(value = 15000, message = "视频时长不能超过15秒")
    private Integer durationMs;

    @NotEmpty(message = "请至少添加1张参考图片")
    @Size(max = 5, message = "最多添加5张参考图片")
    private List<MultipartFile> images;
}
