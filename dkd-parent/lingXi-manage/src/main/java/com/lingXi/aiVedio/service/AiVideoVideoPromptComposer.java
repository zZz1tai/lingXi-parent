package com.lingXi.aiVedio.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoScene;
import com.lingXi.aiVedio.domain.AiVideoShot;

/**
 * 将章节分析产出的结构化镜头信息编排为可编辑、可审阅的视频生成提示词草稿。
 * 本服务只做确定性文本编排，不调用任何模型或外部服务。
 */
@Service
public class AiVideoVideoPromptComposer
{
    public static final String PROMPT_VERSION = "video-prompt-chain-v1";

    private static final int DEFAULT_DURATION_MS = 4000;
    /** 当前默认 Wanx 2.1/2.2 的官方输入上限；高版本模型也可以安全消费该长度。 */
    private static final int MAX_PROMPT_LENGTH = 800;
    /** Wanx 2.1-2.6 的官方反向提示词上限。 */
    private static final int MAX_NEGATIVE_PROMPT_LENGTH = 500;

    private final ObjectMapper objectMapper;

    @Autowired
    public AiVideoVideoPromptComposer(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    /**
     * @param keyframe 已完成且将作为图生视频首帧的 SHOT_KEYFRAME 资产
     * @param shot     关键帧所属镜头
     * @param scene    镜头所属场景；允许为空，此时从关键帧和镜头上下文回退
     * @return 正向提示词、负向提示词、时长和可落库元数据
     */
    public ComposedVideoPrompt compose(AiVideoAsset keyframe, AiVideoShot shot, AiVideoScene scene)
    {
        if (keyframe == null)
        {
            throw new IllegalArgumentException("关键帧资产不能为空");
        }
        if (shot == null)
        {
            throw new IllegalArgumentException("关键帧所属镜头不能为空");
        }

        JsonNode shotContext = parseObject(shot.getPromptContextJson());
        JsonNode sceneContext = scene == null ? objectMapper.createObjectNode()
                : parseObject(scene.getScenePackageJson());
        int durationMs = resolveDurationMs(shot, shotContext);
        String analysisVideoPrompt = text(shotContext, "videoPrompt");
        String analysisVideoNegativePrompt = text(shotContext, "videoNegativePrompt");
        DialogueSummary dialogue = summarizeDialogues(shot, shotContext);

        String promptText = composePositivePrompt(keyframe, shot, scene, sceneContext, shotContext,
                durationMs, analysisVideoPrompt, dialogue);
        String negativePromptText = composeNegativePrompt(keyframe, shotContext,
                analysisVideoNegativePrompt, dialogue.hasSpokenDialogue());
        String metadataJson = composeMetadata(keyframe, shot, scene, shotContext, durationMs,
                !analysisVideoPrompt.isEmpty(), !analysisVideoNegativePrompt.isEmpty(), dialogue);
        return new ComposedVideoPrompt(promptText, negativePromptText, durationMs, metadataJson);
    }

    private String composePositivePrompt(AiVideoAsset keyframe, AiVideoShot shot, AiVideoScene scene,
            JsonNode sceneContext, JsonNode shotContext, int durationMs, String analysisVideoPrompt,
            DialogueSummary dialogue)
    {
        List<String> sections = new ArrayList<>();
        sections.add("Use the supplied keyframe as the exact first frame and authoritative visual reference.");

        String action = firstNonBlank(shot.getActionText(), text(shotContext, "action"));
        String motion = firstNonBlank(analysisVideoPrompt, action,
                "subtle natural breathing, blinking, hair and fabric motion");
        sections.add("Motion over " + formatDuration(durationMs) + ": " + clean(motion, 110)
                + "; start exactly from the keyframe and finish in a stable coherent pose.");
        sections.add("Keep identities, faces, bodies, hair, costumes, props, layout, lighting and background "
                + "unchanged; add or remove nothing.");

        String shotSize = firstNonBlank(shot.getShotSize(), text(shotContext, "shotSize"), "MEDIUM_SHOT");
        String composition = firstNonBlank(shot.getCompositionText(), text(shotContext, "composition"));
        String cameraMovement = firstNonBlank(shot.getCameraMovement(),
                text(shotContext, "cameraMovement"), "STATIC");
        String framing = "Framing: " + clean(humanizeCode(shotSize), 24);
        if (!clean(composition, 25).isEmpty())
        {
            framing += ", " + clean(composition, 25);
        }
        sections.add(framing + "; camera: smooth controlled "
                + clean(humanizeCode(cameraMovement), 30) + ".");

        if (dialogue.hasSpokenDialogue())
        {
            sections.add("Dialogue/lips: " + clean(dialogue.getDescription(), 35)
                    + "; natural restrained mouth and jaw motion, no subtitles.");
        }
        else
        {
            sections.add("No dialogue: mouths stay naturally relaxed with no speech-like movement.");
        }

        sections.add("One continuous shot; natural anatomy and physics, smooth stable motion, no unrequested cuts "
                + "or camera moves.");

        String emotion = firstNonBlank(shot.getEmotionText(), text(shotContext, "emotion"));
        if (!clean(emotion, 600).isEmpty())
        {
            sections.add("Emotion: " + clean(emotion, 25) + ", restrained and natural.");
        }

        String environment = joinEnvironment(scene, sceneContext);
        if (!environment.isEmpty())
        {
            sections.add("Environment: " + clean(environment, 40) + ".");
        }
        return truncate(join(sections, " "), MAX_PROMPT_LENGTH);
    }

    private String composeNegativePrompt(AiVideoAsset keyframe, JsonNode shotContext,
            String analysisVideoNegativePrompt, boolean spokenDialogue)
    {
        Set<String> clauses = new LinkedHashSet<>();
        clauses.add("flicker, flashing, frame-to-frame jitter, temporal inconsistency");
        clauses.add("morphing, warping, face drift, identity swap, costume or body change");
        clauses.add("extra or missing limbs or fingers, duplicate or merged people, broken anatomy");
        clauses.add("people or objects appearing or disappearing, background or lighting drift");
        clauses.add("camera shake, unintended camera motion, reframing, jump cut, scene cut");
        clauses.add(spokenDialogue
                ? "broken or erratic lip motion, identity distortion while speaking"
                : "speaking, moving mouth, unintended speech motion");
        clauses.add("subtitles, text, watermark, logo");
        addClause(clauses, analysisVideoNegativePrompt, 60);
        if (analysisVideoNegativePrompt.isEmpty())
        {
            addClause(clauses, text(shotContext, "negativePrompt"), 40);
        }
        addClause(clauses, keyframe.getNegativePromptText(), 30);
        return truncate(join(new ArrayList<>(clauses), ", "), MAX_NEGATIVE_PROMPT_LENGTH);
    }

    private String joinEnvironment(AiVideoScene scene, JsonNode sceneContext)
    {
        List<String> values = new ArrayList<>();
        addLabelled(values, "location", firstNonBlank(scene == null ? null : scene.getLocationDescription(),
                text(sceneContext, "location")), 30);
        addLabelled(values, "time", firstNonBlank(scene == null ? null : scene.getTimeDescription(),
                text(sceneContext, "time")), 18);
        addLabelled(values, "mood", firstNonBlank(scene == null ? null : scene.getAtmosphere(),
                text(sceneContext, "atmosphere")), 24);
        return join(values, "; ");
    }

    private String composeMetadata(AiVideoAsset keyframe, AiVideoShot shot, AiVideoScene scene,
            JsonNode shotContext, int durationMs, boolean hasAnalysisVideoPrompt,
            boolean hasAnalysisVideoNegativePrompt, DialogueSummary dialogue)
    {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "video_prompt_composer");
        metadata.put("promptVersion", PROMPT_VERSION);
        metadata.put("promptSource", hasAnalysisVideoPrompt
                ? "ANALYSIS_VIDEO_PROMPT" : "STRUCTURED_FALLBACK");
        putLong(metadata, "sourceAssetId", keyframe.getAssetId());
        putLong(metadata, "projectId", keyframe.getProjectId());
        putLong(metadata, "chapterId", keyframe.getChapterId());
        putLong(metadata, "sceneId", scene == null ? keyframe.getSceneId() : scene.getSceneId());
        putLong(metadata, "shotId", shot.getShotId());
        metadata.put("durationMs", durationMs);
        metadata.put("shotSize", firstNonBlank(shot.getShotSize(), text(shotContext, "shotSize")));
        metadata.put("cameraMovement", firstNonBlank(shot.getCameraMovement(),
                text(shotContext, "cameraMovement")));
        metadata.put("compositionText", firstNonBlank(shot.getCompositionText(),
                text(shotContext, "composition")));
        metadata.put("actionText", firstNonBlank(shot.getActionText(), text(shotContext, "action")));
        metadata.put("emotionText", firstNonBlank(shot.getEmotionText(), text(shotContext, "emotion")));
        JsonNode dialogues = parseArray(shot.getDialogueJson());
        if (!dialogues.isArray() || dialogues.size() == 0)
        {
            dialogues = shotContext.path("dialogues");
        }
        if (dialogues != null && dialogues.isArray())
        {
            metadata.set("dialogues", dialogues.deepCopy());
        }
        if (shotContext.isObject())
        {
            metadata.set("promptContext", shotContext.deepCopy());
        }
        metadata.put("dialogueMode", dialogue.hasSpokenDialogue() ? "SPEAKING" : "NO_DIALOGUE");
        metadata.put("dialogueCount", dialogue.getCount());
        metadata.put("analysisVideoPrompt", hasAnalysisVideoPrompt);
        metadata.put("analysisVideoNegativePrompt", hasAnalysisVideoNegativePrompt);
        String promptContractVersion = text(shotContext, "promptContractVersion");
        if (!promptContractVersion.isEmpty())
        {
            metadata.put("promptContractVersion", promptContractVersion);
        }
        return metadata.toString();
    }

    private DialogueSummary summarizeDialogues(AiVideoShot shot, JsonNode shotContext)
    {
        JsonNode dialogues = parseArray(shot.getDialogueJson());
        if (!dialogues.isArray() || dialogues.size() == 0)
        {
            dialogues = shotContext.path("dialogues");
        }
        if (!dialogues.isArray() || dialogues.size() == 0)
        {
            return new DialogueSummary(false, 0, "");
        }

        List<String> descriptions = new ArrayList<>();
        int spokenCount = 0;
        for (int index = 0; index < dialogues.size() && descriptions.size() < 6; index++)
        {
            JsonNode dialogue = dialogues.get(index);
            String line = clean(text(dialogue, "line"), 500);
            if (line.isEmpty())
            {
                continue;
            }
            spokenCount++;
            String speaker = clean(text(dialogue, "speaker"), 120);
            String emotion = clean(text(dialogue, "emotion"), 160);
            String action = clean(text(dialogue, "action"), 240);
            StringBuilder description = new StringBuilder();
            description.append(speaker.isEmpty() ? "the speaking character" : speaker)
                    .append(" says '").append(line.replace('\'', '\u2019')).append("'");
            if (!emotion.isEmpty())
            {
                description.append(" with ").append(emotion);
            }
            if (!action.isEmpty())
            {
                description.append(" while ").append(action);
            }
            descriptions.add(description.toString());
        }
        if (spokenCount == 0)
        {
            return new DialogueSummary(false, 0, "");
        }
        return new DialogueSummary(true, spokenCount, clean(join(descriptions, "; then "), 1800));
    }

    private int resolveDurationMs(AiVideoShot shot, JsonNode shotContext)
    {
        if (shot.getDurationMs() != null && shot.getDurationMs() > 0)
        {
            return shot.getDurationMs();
        }
        int contextDuration = shotContext.path("durationMs").asInt(DEFAULT_DURATION_MS);
        return contextDuration > 0 ? contextDuration : DEFAULT_DURATION_MS;
    }

    private JsonNode parseObject(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return objectMapper.createObjectNode();
        }
        try
        {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        }
        catch (Exception ignored)
        {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode parseArray(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return objectMapper.createArrayNode();
        }
        try
        {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isArray() ? node : objectMapper.createArrayNode();
        }
        catch (Exception ignored)
        {
            return objectMapper.createArrayNode();
        }
    }

    private String text(JsonNode node, String field)
    {
        return node == null ? "" : clean(node.path(field).asText(""), 4000);
    }

    private String firstNonBlank(String... values)
    {
        if (values != null)
        {
            for (String value : values)
            {
                String cleaned = clean(value, 4000);
                if (!cleaned.isEmpty())
                {
                    return cleaned;
                }
            }
        }
        return "";
    }

    private void addLabelled(List<String> values, String label, String value, int maxLength)
    {
        String cleaned = clean(value, maxLength);
        if (!cleaned.isEmpty())
        {
            values.add(label + ": " + cleaned);
        }
    }

    private void addClause(Set<String> clauses, String value, int maxLength)
    {
        String cleaned = clean(value, maxLength);
        if (!cleaned.isEmpty())
        {
            clauses.add(cleaned);
        }
    }

    private void putLong(ObjectNode node, String field, Long value)
    {
        if (value != null)
        {
            node.put(field, value.longValue());
        }
    }

    private String cameraInstruction(String cameraMovement)
    {
        String code = clean(cameraMovement, 200).toUpperCase(Locale.ROOT);
        if ("STATIC".equals(code) || "LOCKED_OFF".equals(code))
        {
            return "a locked-off static camera; allow no unintended camera motion";
        }
        if ("DOLLY_IN".equals(code) || "PUSH_IN".equals(code))
        {
            return "a smooth controlled dolly-in toward the subject at constant speed";
        }
        if ("DOLLY_OUT".equals(code) || "PULL_OUT".equals(code))
        {
            return "a smooth controlled dolly-out from the subject at constant speed";
        }
        if ("PAN_LEFT".equals(code) || "PAN_RIGHT".equals(code))
        {
            return "a smooth controlled " + humanizeCode(code) + " while maintaining subject scale and eyeline";
        }
        if ("TILT_UP".equals(code) || "TILT_DOWN".equals(code))
        {
            return "a smooth controlled " + humanizeCode(code) + " with no roll or horizon drift";
        }
        if ("TRACKING".equals(code) || "FOLLOW".equals(code))
        {
            return "a smooth tracking movement that follows the primary subject while preserving framing";
        }
        if ("ORBIT".equals(code) || "ARC".equals(code))
        {
            return "a smooth restrained arc around the primary subject with stable background geometry";
        }
        if ("HANDHELD".equals(code))
        {
            return "restrained cinematic handheld motion with controlled micro-movement and no erratic shake";
        }
        return humanizeCode(firstNonBlank(cameraMovement, "STATIC"));
    }

    private String humanizeCode(String value)
    {
        return clean(value, 200).replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String formatDuration(int durationMs)
    {
        String seconds = String.format(Locale.ROOT, "%.1f", durationMs / 1000.0d);
        if (seconds.endsWith(".0"))
        {
            seconds = seconds.substring(0, seconds.length() - 2);
        }
        return seconds + " seconds";
    }

    private String join(List<String> values, String separator)
    {
        StringBuilder result = new StringBuilder();
        for (String value : values)
        {
            if (value == null || value.isEmpty())
            {
                continue;
            }
            if (result.length() > 0)
            {
                result.append(separator);
            }
            result.append(value);
        }
        return result.toString();
    }

    private String clean(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return truncate(cleaned, maxLength);
    }

    private String truncate(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value == null ? "" : value;
        }
        int end = maxLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1)))
        {
            end--;
        }
        int wordBoundary = value.lastIndexOf(' ', end);
        if (wordBoundary >= Math.max(1, (int) (maxLength * 0.85d)))
        {
            end = wordBoundary;
        }
        return value.substring(0, end);
    }

    /** 编排结果，可直接写入 VIDEO_CLIP 草稿资产。 */
    public static final class ComposedVideoPrompt
    {
        private final String promptText;
        private final String negativePromptText;
        private final Integer durationMs;
        private final String metadataJson;

        public ComposedVideoPrompt(String promptText, String negativePromptText,
                Integer durationMs, String metadataJson)
        {
            this.promptText = promptText;
            this.negativePromptText = negativePromptText;
            this.durationMs = durationMs;
            this.metadataJson = metadataJson;
        }

        public String getPromptText()
        {
            return promptText;
        }

        public String getNegativePromptText()
        {
            return negativePromptText;
        }

        public Integer getDurationMs()
        {
            return durationMs;
        }

        public String getMetadataJson()
        {
            return metadataJson;
        }
    }

    private static final class DialogueSummary
    {
        private final boolean spokenDialogue;
        private final int count;
        private final String description;

        private DialogueSummary(boolean spokenDialogue, int count, String description)
        {
            this.spokenDialogue = spokenDialogue;
            this.count = count;
            this.description = description;
        }

        private boolean hasSpokenDialogue()
        {
            return spokenDialogue;
        }

        private int getCount()
        {
            return count;
        }

        private String getDescription()
        {
            return description;
        }
    }
}
