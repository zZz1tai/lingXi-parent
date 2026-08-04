package com.lingXi.aiVedio.util;

import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 统一生成可写入 MySQL JSON 列的 AI 视频元数据。 */
public final class AiVideoJsonMetadata
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiVideoJsonMetadata()
    {
    }

    /**
     * 生成仅包含错误信息的元数据JSON。
     *
     * @param message 错误信息
     * @return 元数据JSON字符串
     */
    public static String generationFailure(String message)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("generationError", sanitize(message));
        return metadata.toString();
    }

    /**
     * 在已有元数据基础上追加错误信息，保留原有字段。
     *
     * @param existingMetadataJson 已有元数据JSON
     * @param message              错误信息
     * @return 更新后的元数据JSON字符串
     */
    public static String generationFailure(String existingMetadataJson, String message)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (existingMetadataJson != null && !existingMetadataJson.trim().isEmpty())
        {
            try
            {
                tools.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(existingMetadataJson);
                if (existing != null && existing.isObject())
                {
                    metadata.setAll((ObjectNode) existing);
                }
            }
            catch (Exception ignored)
            {
            }
        }
        metadata.put("generationError", sanitize(message));
        return metadata.toString();
    }

    /**
     * 生成重新生成时的元数据，继承已有业务数据并清除旧失败状态。
     *
     * @param existingMetadataJson   已有元数据JSON
     * @param regeneratedFromAssetId 来源资产ID
     * @return 更新后的元数据JSON字符串
     */
    public static String regenerationMetadata(String existingMetadataJson, Long regeneratedFromAssetId)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (existingMetadataJson != null && !existingMetadataJson.trim().isEmpty())
        {
            try
            {
                tools.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(existingMetadataJson);
                if (existing != null && existing.isObject())
                {
                    metadata.setAll((ObjectNode) existing);
                }
            }
            catch (Exception ignored)
            {
            }
        }
        metadata.remove("generationError");
        if (regeneratedFromAssetId != null)
        {
            metadata.put("regeneratedFromAssetId", regeneratedFromAssetId.longValue());
        }
        return metadata.toString();
    }

    /**
     * 更新关键帧元数据中的场景和人物参考素材ID。
     *
     * @param metadataJson               已有元数据JSON
     * @param sceneReferenceAssetId      场景参考素材ID
     * @param characterReferenceAssetIds 人物参考素材ID列表
     * @return 更新后的元数据JSON字符串
     */
    public static String withImageReferenceIds(String metadataJson, Long sceneReferenceAssetId,
            List<Long> characterReferenceAssetIds)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (metadataJson != null && !metadataJson.trim().isEmpty())
        {
            try
            {
                tools.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(metadataJson);
                if (existing != null && existing.isObject())
                {
                    metadata.setAll((ObjectNode) existing);
                }
            }
            catch (Exception ignored)
            {
            }
        }
        if (sceneReferenceAssetId == null)
        {
            metadata.remove("sceneReferenceAssetId");
            metadata.remove("sourceAssetId");
        }
        else
        {
            metadata.put("sceneReferenceAssetId", sceneReferenceAssetId.longValue());
            metadata.put("sourceAssetId", sceneReferenceAssetId.longValue());
        }
        tools.jackson.databind.node.ArrayNode characters =
                metadata.putArray("characterReferenceAssetIds");
        if (characterReferenceAssetIds != null)
        {
            for (Long characterReferenceAssetId : characterReferenceAssetIds)
            {
                if (characterReferenceAssetId != null)
                {
                    characters.add(characterReferenceAssetId.longValue());
                }
            }
        }
        return metadata.toString();
    }

    /**
     * 更新元数据中的参考素材ID及绑定模式。
     *
     * @param metadataJson               已有元数据JSON
     * @param sceneReferenceAssetId      场景参考素材ID
     * @param characterReferenceAssetIds 人物参考素材ID列表
     * @param bindingMode                绑定模式
     * @return 更新后的元数据JSON字符串
     */
    public static String withImageReferenceBinding(String metadataJson, Long sceneReferenceAssetId,
            List<Long> characterReferenceAssetIds, String bindingMode)
    {
        String updated = withImageReferenceIds(
                metadataJson, sceneReferenceAssetId, characterReferenceAssetIds);
        try
        {
            ObjectNode metadata = (ObjectNode) OBJECT_MAPPER.readTree(updated);
            metadata.put("referenceBindingMode", sanitize(bindingMode));
            return metadata.toString();
        }
        catch (Exception ignored)
        {
            return updated;
        }
    }

    /**
     * 更新元数据中的视频关键帧来源绑定信息。
     *
     * @param metadataJson       已有元数据JSON
     * @param keyframeAssetId    关键帧素材ID
     * @param keyframeVersionNo  关键帧版本号
     * @param bindingMode        绑定模式
     * @return 更新后的元数据JSON字符串
     */
    public static String withVideoSourceBinding(String metadataJson, Long keyframeAssetId,
            Integer keyframeVersionNo, String bindingMode)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (metadataJson != null && !metadataJson.trim().isEmpty())
        {
            try
            {
                tools.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(metadataJson);
                if (existing != null && existing.isObject()) metadata.setAll((ObjectNode) existing);
            }
            catch (Exception ignored)
            {
            }
        }
        if (keyframeAssetId != null) metadata.put("sourceKeyframeAssetId", keyframeAssetId.longValue());
        if (keyframeVersionNo != null) metadata.put("sourceKeyframeVersionNo", keyframeVersionNo.intValue());
        metadata.put("sourceBindingMode", sanitize(bindingMode));
        return metadata.toString();
    }

    /**
     * 从元数据中提取分析版本号。
     *
     * @param metadataJson 元数据JSON
     * @return 分析版本号，不存在则返回null
     */
    public static Integer analysisVersion(String metadataJson)
    {
        if (metadataJson == null || metadataJson.trim().isEmpty()) return null;
        try
        {
            tools.jackson.databind.JsonNode value = OBJECT_MAPPER.readTree(metadataJson).path("analysisVersion");
            return value.canConvertToInt() && value.asInt() > 0 ? Integer.valueOf(value.asInt()) : null;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    /**
     * 在元数据中设置分析版本号。
     *
     * @param metadataJson    已有元数据JSON
     * @param analysisVersion 分析版本号
     * @return 更新后的元数据JSON字符串
     */
    public static String withAnalysisVersion(String metadataJson, Integer analysisVersion)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (metadataJson != null && !metadataJson.trim().isEmpty())
        {
            try
            {
                tools.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(metadataJson);
                if (existing != null && existing.isObject()) metadata.setAll((ObjectNode) existing);
            }
            catch (Exception ignored)
            {
            }
        }
        if (analysisVersion != null && analysisVersion.intValue() > 0)
        {
            metadata.put("analysisVersion", analysisVersion.intValue());
        }
        return metadata.toString();
    }

    /**
     * 生成图片生成参数的元数据JSON（不含画幅比例）。
     *
     * @param provider 供应商名称
     * @param model    模型名称
     * @return 参数JSON字符串
     */
    public static String generationParameters(String provider, String model)
    {
        return generationParameters(provider, model, null);
    }

    /**
     * 生成图片生成参数的元数据JSON（含画幅比例）。
     *
     * @param provider    供应商名称
     * @param model       模型名称
     * @param aspectRatio 画幅比例
     * @return 参数JSON字符串
     */
    public static String generationParameters(String provider, String model, String aspectRatio)
    {
        ObjectNode parameters = OBJECT_MAPPER.createObjectNode();
        parameters.put("provider", sanitize(provider));
        parameters.put("model", sanitize(model));
        if (aspectRatio != null) parameters.put("aspectRatio", sanitize(aspectRatio));
        return parameters.toString();
    }

    /**
     * 生成视频生成参数的元数据JSON。
     *
     * @param provider     供应商名称
     * @param model        模型名称
     * @param durationMs   视频时长（毫秒）
     * @param promptVersion 提示词版本
     * @return 参数JSON字符串
     */
    public static String videoGenerationParameters(String provider, String model, Integer durationMs,
            String promptVersion)
    {
        ObjectNode parameters = OBJECT_MAPPER.createObjectNode();
        parameters.put("provider", sanitize(provider));
        parameters.put("model", sanitize(model));
        if (durationMs != null) parameters.put("durationMs", durationMs.intValue());
        if (promptVersion != null) parameters.put("promptVersion", sanitize(promptVersion));
        return parameters.toString();
    }

    /**
     * 生成图片生成请求的元数据JSON。
     *
     * @param prompt           正向提示词
     * @param negativePrompt   反向提示词
     * @param model            模型名称
     * @param assetType        资产类型
     * @param aspectRatio      画幅比例
     * @param referenceAssetIds 参考素材ID列表
     * @return 请求JSON字符串
     */
    public static String imageGenerationRequest(String prompt, String negativePrompt, String model,
            String assetType, String aspectRatio,
            List<Long> referenceAssetIds)
    {
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("trigger", "USER_CONFIRMED");
        request.put("prompt", sanitizePayload(prompt));
        if (negativePrompt != null && !negativePrompt.trim().isEmpty())
        {
            request.put("negativePrompt", sanitizePayload(negativePrompt));
        }
        request.put("model", sanitize(model));
        if (assetType != null) request.put("assetType", sanitize(assetType));
        if (aspectRatio != null) request.put("aspectRatio", sanitize(aspectRatio));
        if (referenceAssetIds != null && !referenceAssetIds.isEmpty())
        {
            tools.jackson.databind.node.ArrayNode references = request.putArray("referenceAssetIds");
            for (Long referenceAssetId : referenceAssetIds)
            {
                if (referenceAssetId != null) references.add(referenceAssetId.longValue());
            }
        }
        return request.toString();
    }

    /**
     * 判断图片生成请求是否经过用户确认。
     *
     * @param requestJson 请求JSON
     * @return 是否已用户确认
     */
    public static boolean isUserConfirmedImageRequest(String requestJson)
    {
        if (requestJson == null || requestJson.trim().isEmpty()) return false;
        try
        {
            return "USER_CONFIRMED".equals(OBJECT_MAPPER.readTree(requestJson).path("trigger").asText());
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    /**
     * 上游 HTTP 错误体可能携带控制字符或不成对的 UTF-16 代理项；MySQL JSON 不接受它们。
     */
    private static String sanitize(String value)
    {
        return sanitize(value, 4000);
    }

    private static String sanitizePayload(String value)
    {
        return sanitize(value, value == null ? 0 : value.length());
    }

    private static String sanitize(String value, int maxLength)
    {
        if (value == null || value.isEmpty()) return "unknown";
        StringBuilder result = new StringBuilder(Math.min(value.length(), maxLength));
        for (int index = 0; index < value.length() && result.length() < maxLength; index++)
        {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current))
            {
                if (index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1)))
                {
                    result.append(current).append(value.charAt(++index));
                }
                else result.append('\uFFFD');
            }
            else if (Character.isLowSurrogate(current)) result.append('\uFFFD');
            else if (current < 0x20) result.append(' ');
            else result.append(current);
        }
        return result.toString();
    }
}
