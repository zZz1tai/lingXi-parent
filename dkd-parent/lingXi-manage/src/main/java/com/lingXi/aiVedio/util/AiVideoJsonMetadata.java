package com.lingXi.aiVedio.util;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 统一生成可写入 MySQL JSON 列的 AI 视频元数据。 */
public final class AiVideoJsonMetadata
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiVideoJsonMetadata()
    {
    }

    public static String generationFailure(String message)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("generationError", sanitize(message));
        return metadata.toString();
    }

    public static String generationFailure(String existingMetadataJson, String message)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (existingMetadataJson != null && !existingMetadataJson.trim().isEmpty())
        {
            try
            {
                com.fasterxml.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(existingMetadataJson);
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

    /** 新版本继承业务元数据，但清除旧失败状态并记录直接重生成来源。 */
    public static String regenerationMetadata(String existingMetadataJson, Long regeneratedFromAssetId)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (existingMetadataJson != null && !existingMetadataJson.trim().isEmpty())
        {
            try
            {
                com.fasterxml.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(existingMetadataJson);
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

    /** 让关键帧元数据与实际引用关系保持一致，便于刷新后继续选择正确的参考版本。 */
    public static String withImageReferenceIds(String metadataJson, Long sceneReferenceAssetId,
            List<Long> characterReferenceAssetIds)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (metadataJson != null && !metadataJson.trim().isEmpty())
        {
            try
            {
                com.fasterxml.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(metadataJson);
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
        }
        else
        {
            metadata.put("sceneReferenceAssetId", sceneReferenceAssetId.longValue());
        }
        com.fasterxml.jackson.databind.node.ArrayNode characters =
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

    public static Integer analysisVersion(String metadataJson)
    {
        if (metadataJson == null || metadataJson.trim().isEmpty()) return null;
        try
        {
            com.fasterxml.jackson.databind.JsonNode value = OBJECT_MAPPER.readTree(metadataJson).path("analysisVersion");
            return value.canConvertToInt() && value.asInt() > 0 ? Integer.valueOf(value.asInt()) : null;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    public static String withAnalysisVersion(String metadataJson, Integer analysisVersion)
    {
        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        if (metadataJson != null && !metadataJson.trim().isEmpty())
        {
            try
            {
                com.fasterxml.jackson.databind.JsonNode existing = OBJECT_MAPPER.readTree(metadataJson);
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

    public static String generationParameters(String provider, String model)
    {
        return generationParameters(provider, model, null);
    }

    public static String generationParameters(String provider, String model, String aspectRatio)
    {
        ObjectNode parameters = OBJECT_MAPPER.createObjectNode();
        parameters.put("provider", sanitize(provider));
        parameters.put("model", sanitize(model));
        if (aspectRatio != null) parameters.put("aspectRatio", sanitize(aspectRatio));
        return parameters.toString();
    }

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
            com.fasterxml.jackson.databind.node.ArrayNode references = request.putArray("referenceAssetIds");
            for (Long referenceAssetId : referenceAssetIds)
            {
                if (referenceAssetId != null) references.add(referenceAssetId.longValue());
            }
        }
        return request.toString();
    }

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
