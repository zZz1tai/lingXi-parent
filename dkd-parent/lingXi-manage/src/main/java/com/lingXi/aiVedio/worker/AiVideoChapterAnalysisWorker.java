package com.lingXi.aiVedio.worker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.client.ChapterAnalysisClient;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoAssetRelation;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoCharacter;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.AiVideoScene;
import com.lingXi.aiVedio.domain.AiVideoShot;
import com.lingXi.aiVedio.domain.AiVideoStoryBible;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.aiVedio.mapper.AiVideoChapterMapper;
import com.lingXi.aiVedio.mapper.AiVideoAssetRelationMapper;
import com.lingXi.aiVedio.mapper.AiVideoCharacterMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.mapper.AiVideoSceneMapper;
import com.lingXi.aiVedio.mapper.AiVideoShotMapper;
import com.lingXi.aiVedio.mapper.AiVideoStoryBibleMapper;
import com.lingXi.aiVedio.service.AiVideoQwenAssetService;
import lombok.extern.slf4j.Slf4j;

/** 将章节原文转换为下游图片、视频和配音智能体可消费的 ScenePackage。 */
@Service
@Slf4j
public class AiVideoChapterAnalysisWorker
{
    private static final int TASK_STATUS_LOCK_RETRY_ATTEMPTS = 3;
    private static final long TASK_STATUS_LOCK_RETRY_DELAY_MS = 100L;
    private static final Set<String> GENERIC_CHARACTER_ALIASES =
            java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
                    "他", "她", "它", "他们", "她们", "它们", "父亲", "母亲", "爸爸", "妈妈", "爸", "妈",
                    "老师", "先生", "女士", "医生", "护士", "警察", "老板", "店员", "服务员", "路人",
                    "众人", "人群", "男主", "女主", "主角", "旁白", "未知", "角色",
                    "he", "she", "it", "they", "him", "her", "father", "mother", "dad", "mom",
                    "teacher", "sir", "madam", "doctor", "nurse", "boss", "narrator", "protagonist",
                    "man", "woman", "person")));

    @Autowired
    private ChapterAnalysisClient chapterAnalysisClient;

    @Autowired
    private AiVideoModelConfigService modelConfigService;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiVideoChapterMapper chapterMapper;
    @Autowired
    private AiVideoAssetRelationMapper assetRelationMapper;
    @Autowired
    private AiVideoCharacterMapper characterMapper;
    @Autowired
    private AiVideoSceneMapper sceneMapper;
    @Autowired
    private AiVideoShotMapper shotMapper;
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoStoryBibleMapper storyBibleMapper;
    @Autowired
    private AiVideoQwenAssetService qwenAssetService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Async("aiVideoExecutor")
    public void analyze(Long taskId, Long chapterId)
    {
        if (!claimStoryBibleTaskWithLockRetry(taskId))
        {
            log.info("AI视频章节解析任务已被领取或不再排队，跳过重复执行，taskId={}, chapterId={}", taskId, chapterId);
            return;
        }
        try
        {
            AiVideoChapter chapter = chapterMapper.selectAiVideoChapterByChapterId(chapterId);
            if (chapter == null)
            {
                throw new IllegalStateException("章节不存在或已删除");
            }
            List<AiVideoCharacter> projectCharacters = characterMapper
                    .selectAiVideoCharactersByProjectId(chapter.getProjectId());

            // Build project characters list for Python
            List<ObjectNode> projectCharacterNodes = new ArrayList<>();
            for (AiVideoCharacter character : projectCharacters)
            {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("characterCode", character.getCharacterCode());
                node.put("name", character.getCharacterName());
                node.put("gender", character.getGender());
                node.put("ageRange", character.getAgeRange());
                node.put("appearance", character.getAppearanceText());
                node.put("speakingStyle", character.getSpeakingStyle());
                node.put("visualPromptBase", character.getVisualPromptBase());
                try
                {
                    JsonNode aliases = objectMapper.readTree(
                            character.getAliasesJson() == null ? "[]" : character.getAliasesJson());
                    node.set("aliases", aliases != null && aliases.isArray()
                            ? aliases : objectMapper.createArrayNode());
                }
                catch (Exception ignored)
                {
                    node.set("aliases", objectMapper.createArrayNode());
                }
                projectCharacterNodes.add(node);
            }

            // Call Python Agent for chapter analysis
            AiVideoModelConfig runtimeConfig = modelConfigService.getRequiredConfig();
            ChapterAnalysisClient.AnalysisResult result = chapterAnalysisClient.analyzeChapter(
                    runtimeConfig.getApiKey(),
                    runtimeConfig.getTextModel(),
                    runtimeConfig.getWorkspaceBaseUrl(),
                    runtimeConfig.getVideoModel(),
                    chapter.getChapterTitle(),
                    chapter.getSourceText(),
                    projectCharacterNodes,
                    (stage, progress, message) -> updateStoryBibleProgress(
                            taskId, stage, progress, message));

            if (isStoryBibleTaskPaused(taskId))
            {
                log.info("AI视频章节解析已暂停，忽略本次返回结果，taskId={}, chapterId={}", taskId, chapterId);
                return;
            }

            if (!result.isSuccess())
            {
                String errorCode = firstNonBlank(result.getErrorCode(), "CHAPTER_ANALYSIS_FAILED");
                if (errorCode.length() > 128)
                {
                    errorCode = errorCode.substring(0, 128);
                }
                String message = firstNonBlank(result.getError(), "章节分析失败");
                String persistedMessage = "retryable=" + result.isRetryable() + " | " + message;
                log.warn("AI视频章节解析被 Python 拒绝，taskId={}, chapterId={}, errorCode={}, retryable={}, error={}",
                        taskId, chapterId, errorCode, result.isRetryable(), message);
                updateTaskStatusWithLockRetry(
                        taskId, "FAILED", 100, errorCode, abbreviate(persistedMessage));
                chapterMapper.updateAiVideoChapterAnalysisStatus(chapterId, "FAILED", "FAILED", null, 0);
                return;
            }

            JsonNode document = result.getStoryBible();
            updateStoryBibleProgress(taskId, "PERSISTING", 95, "正在保存人物、场景、分镜和素材草稿");
            persistResult(taskId, chapter, document, runtimeConfig.getTextModel());
        }
        catch (Exception ex)
        {
            if (ex instanceof AnalysisPausedException || isStoryBibleTaskPaused(taskId))
            {
                log.info("AI视频章节解析已暂停，停止保存和状态回写，taskId={}, chapterId={}", taskId, chapterId);
                return;
            }
            log.error("AI视频章节解析失败，taskId={}, chapterId={}, errorType={}",
                    taskId, chapterId, ex.getClass().getSimpleName());
            String message = ex.getMessage() == null ? "章节解析失败" : ex.getMessage();
            updateTaskStatusWithLockRetry(taskId, "FAILED", 100, "CHAPTER_ANALYSIS_FAILED", abbreviate(message));
            chapterMapper.updateAiVideoChapterAnalysisStatus(chapterId, "FAILED", "FAILED", null, 0);
        }
    }

    private boolean claimStoryBibleTaskWithLockRetry(Long taskId)
    {
        for (int attempt = 1; attempt <= TASK_STATUS_LOCK_RETRY_ATTEMPTS; attempt++)
        {
            try
            {
                return taskMapper.claimStoryBibleTask(taskId) == 1;
            }
            catch (PessimisticLockingFailureException ex)
            {
                waitBeforeTaskStatusRetry(taskId, "CLAIM", attempt, ex);
            }
        }
        return false;
    }

    private void updateTaskStatusWithLockRetry(Long taskId, String status, Integer progress,
            String errorCode, String errorMessage)
    {
        for (int attempt = 1; attempt <= TASK_STATUS_LOCK_RETRY_ATTEMPTS; attempt++)
        {
            try
            {
                int updated = taskMapper.updateStoryBibleTaskStatusIfRunning(
                        taskId, status, progress, errorCode, errorMessage);
                if (updated != 1)
                {
                    if (isStoryBibleTaskPaused(taskId))
                    {
                        throw new AnalysisPausedException();
                    }
                    throw new IllegalStateException("AI视频任务不存在或已删除，taskId=" + taskId);
                }
                return;
            }
            catch (PessimisticLockingFailureException ex)
            {
                waitBeforeTaskStatusRetry(taskId, status, attempt, ex);
            }
        }
    }

    private void updateStoryBibleProgress(Long taskId, String stage, Integer progress, String message)
    {
        String stageCode = firstNonBlank(stage, "RUNNING");
        if (stageCode.length() > 64)
        {
            stageCode = stageCode.substring(0, 64);
        }
        String stageLabel = firstNonBlank(message, "章节分析进行中");
        if (stageLabel.length() > 256)
        {
            stageLabel = stageLabel.substring(0, 256);
        }
        int boundedProgress = Math.max(10, Math.min(progress == null ? 10 : progress, 95));
        int updated = taskMapper.updateStoryBibleTaskProgress(
                taskId, boundedProgress, stageCode, stageLabel);
        if (updated != 1)
        {
            if (isStoryBibleTaskPaused(taskId))
            {
                throw new AnalysisPausedException();
            }
            throw new IllegalStateException("章节分析任务进度更新失败，taskId=" + taskId);
        }
    }

    private boolean isStoryBibleTaskPaused(Long taskId)
    {
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
        return task != null && "PAUSED".equals(task.getStatus());
    }

    private void waitBeforeTaskStatusRetry(Long taskId, String operation, int attempt,
            PessimisticLockingFailureException failure)
    {
        if (attempt >= TASK_STATUS_LOCK_RETRY_ATTEMPTS)
        {
            throw failure;
        }
        long delayMs = TASK_STATUS_LOCK_RETRY_DELAY_MS * attempt;
        log.warn("AI视频任务状态更新遇到锁冲突，将重试，taskId={}, operation={}, attempt={}/{}",
                taskId, operation, attempt, TASK_STATUS_LOCK_RETRY_ATTEMPTS);
        try
        {
            Thread.sleep(delayMs);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI视频任务状态更新重试被中断", interrupted);
        }
    }

    private void persistResult(final Long taskId, final AiVideoChapter chapter, final JsonNode document,
            final String textModel)
    {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            try
            {
                if (taskMapper.selectRunningStoryBibleTaskForUpdate(taskId) == null)
                {
                    throw new AnalysisPausedException();
                }
                persistResultInTransaction(chapter, document, textModel);
                if (taskMapper.updateStoryBibleTaskStatusIfRunning(
                        taskId, "SUCCEEDED", 100, null, null) != 1)
                {
                    throw new IllegalStateException("章节解析完成状态保存失败，taskId=" + taskId);
                }
                return null;
            }
            catch (RuntimeException ex)
            {
                throw ex;
            }
            catch (Exception ex)
            {
                throw new IllegalStateException("保存章节分析结果失败，已回滚本次全部素材", ex);
            }
        });
    }

    private static final class AnalysisPausedException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
    }

    private void persistResultInTransaction(AiVideoChapter chapter, JsonNode document,
            String textModel) throws Exception
    {
        String promptVersion = document.path("promptVersion").asText("").trim();
        if (promptVersion.isEmpty())
        {
            throw new IllegalStateException("章节分析结果缺少 promptVersion");
        }
        AiVideoStoryBible latest = storyBibleMapper.selectLatestAiVideoStoryBibleByChapterId(chapter.getChapterId());
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        AiVideoStoryBible bible = new AiVideoStoryBible();
        bible.setProjectId(chapter.getProjectId());
        bible.setChapterId(chapter.getChapterId());
        bible.setVersionNo(versionNo);
        bible.setStatus("APPROVED");
        bible.setWorldSetting(document.path("worldSetting").asText(""));
        bible.setTimelineJson(objectMapper.writeValueAsString(document.path("timeline")));
        bible.setRelationshipJson(objectMapper.writeValueAsString(document.path("relationships")));
        bible.setImmutableFactsJson(objectMapper.writeValueAsString(document.path("immutableFacts")));
        bible.setContentJson(objectMapper.writeValueAsString(document));
        bible.setSourceReferenceJson("{\"chapterId\":" + chapter.getChapterId() + ",\"sourceHash\":\"" + chapter.getSourceHash() + "\"}");
        bible.setModelName(textModel);
        bible.setPromptVersion(promptVersion);
        bible.setCreateBy("ai-video-worker");
        storyBibleMapper.insertAiVideoStoryBible(bible);
        materializeScenePackage(chapter, document, versionNo);
        chapterMapper.updateAiVideoChapterAnalysisStatus(chapter.getChapterId(), "SUCCEEDED", "SCRIPT_READY",
                document.path("summary").asText(), versionNo);
    }

    private void materializeScenePackage(AiVideoChapter chapter, JsonNode document, int versionNo) throws Exception
    {
        Map<String, Long> characterReferenceAssetIdsByKey = new LinkedHashMap<>();
        JsonNode characters = document.path("characters");
        for (int index = 0; index < characters.size(); index++)
        {
            JsonNode item = characters.get(index);
            String name = item.path("name").asText("角色" + (index + 1));
            AiVideoCharacter character = new AiVideoCharacter();
            character.setProjectId(chapter.getProjectId());
            character.setCharacterCode(buildCharacterCode(name));
            character.setCharacterName(name);
            ArrayNode analyzedAliases = sanitizeCharacterAliases(item.path("aliases"));
            character.setAliasesJson(objectMapper.writeValueAsString(analyzedAliases));
            character.setGender(item.path("gender").asText("未知"));
            character.setAgeRange(item.path("ageRange").asText(""));
            character.setPersonalityJson(objectMapper.writeValueAsString(item.path("personality")));
            character.setAppearanceText(item.path("appearance").asText(""));
            character.setSpeakingStyle(item.path("speakingStyle").asText(""));
            character.setVisualPromptBase(item.path("visualPromptBase").asText(""));
            character.setStatus("ACTIVE");
            character.setCreateBy("ai-video-worker");
            List<AiVideoCharacter> identityMatches = characterMapper
                    .selectAiVideoCharacterByProjectIdentityForUpdate(character);
            if (identityMatches.size() > 1)
            {
                throw new IllegalStateException("人物“" + name
                        + "”的姓名或别名同时匹配多个项目人物，请先修改重复姓名或别名后重新分析");
            }
            AiVideoCharacter canonicalCharacter = identityMatches.isEmpty() ? null : identityMatches.get(0);
            if (canonicalCharacter == null)
            {
                characterMapper.upsertAiVideoCharacter(character);
                canonicalCharacter = characterMapper
                        .selectAiVideoCharacterByProjectAndCodeForUpdate(character);
            }
            else
            {
                mergeCharacterAliases(canonicalCharacter, character);
            }
            if (canonicalCharacter == null)
            {
                throw new IllegalStateException("人物身份规范保存后无法读取：" + character.getCharacterCode());
            }
            AiVideoAsset characterReference = qwenAssetService.getOrCreateProjectCharacterReference(
                    chapter.getProjectId(), canonicalCharacter.getCharacterId(),
                    canonicalCharacter.getCharacterCode(), canonicalCharacter.getCharacterName(),
                    item.path("characterReferencePrompt").asText(""),
                    item.path("characterReferenceNegativePrompt").asText(""),
                    buildCharacterReferenceMetadata(canonicalCharacter, chapter, versionNo));
            registerCharacterReferenceKeys(characterReferenceAssetIdsByKey,
                    canonicalCharacter, item, characterReference.getAssetId());
        }

        JsonNode scenes = document.path("scenes");
        for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++)
        {
            JsonNode item = scenes.get(sceneIndex);
            AiVideoScene scene = new AiVideoScene();
            scene.setProjectId(chapter.getProjectId());
            scene.setChapterId(chapter.getChapterId());
            scene.setSceneNo(sceneIndex + 1);
            scene.setSceneTitle(item.path("title").asText("场景" + (sceneIndex + 1)));
            scene.setSourceParagraphFrom(nullableInt(item.path("sourceParagraphFrom")));
            scene.setSourceParagraphTo(nullableInt(item.path("sourceParagraphTo")));
            scene.setTimeDescription(item.path("time").asText(""));
            scene.setLocationDescription(item.path("location").asText(""));
            scene.setAtmosphere(item.path("atmosphere").asText(""));
            scene.setDramaticGoal(item.path("dramaticGoal").asText(""));
            scene.setCharacterIds(objectMapper.writeValueAsString(item.path("characters")));
            scene.setScenePackageJson(objectMapper.writeValueAsString(item));
            scene.setStatus("APPROVED");
            scene.setVersionNo(versionNo);
            scene.setCreateBy("ai-video-worker");
            sceneMapper.insertAiVideoScene(scene);
            AiVideoAsset sceneReference = qwenAssetService.createDraftImageAsset(
                    chapter.getProjectId(), chapter.getChapterId(), scene.getSceneId(), null,
                    "scene-c" + chapter.getChapterId() + "-s" + scene.getSceneId(), scene.getSceneTitle() + "场景设定",
                    "SCENE_REFERENCE", "SCENE", 1, versionNo,
                    item.path("sceneImagePrompt").asText(""),
                    item.path("sceneImageNegativePrompt").asText(""),
                    buildSceneReferenceMetadata(scene, versionNo));

            JsonNode shots = item.path("shots");
            for (int shotIndex = 0; shotIndex < shots.size(); shotIndex++)
            {
                ObjectNode shotNode = ((ObjectNode) shots.get(shotIndex)).deepCopy();
                shotNode.put("shotNo", shotIndex + 1);
                List<Long> characterReferenceAssetIds = resolveShotCharacterReferenceAssetIds(
                        shotNode, item, characterReferenceAssetIdsByKey,
                        "场景" + scene.getSceneNo() + "-镜头" + (shotIndex + 1));
                shotNode.put("sceneReferenceAssetId", sceneReference.getAssetId());
                shotNode.set("characterReferenceAssetIds",
                        longArrayNode(characterReferenceAssetIds));
                AiVideoShot shot = new AiVideoShot();
                shot.setProjectId(chapter.getProjectId());
                shot.setChapterId(chapter.getChapterId());
                shot.setSceneId(scene.getSceneId());
                shot.setShotNo(shotIndex + 1);
                shot.setDurationMs(shotNode.path("durationMs").asInt());
                shot.setShotSize(shotNode.path("shotSize").asText(""));
                shot.setCameraMovement(shotNode.path("cameraMovement").asText(""));
                shot.setCompositionText(shotNode.path("composition").asText(""));
                shot.setActionText(shotNode.path("action").asText(""));
                shot.setEmotionText(shotNode.path("emotion").asText(""));
                shot.setDialogueJson(objectMapper.writeValueAsString(shotNode.path("dialogues")));
                shot.setPromptContextJson(objectMapper.writeValueAsString(shotNode));
                shot.setStatus("APPROVED");
                shot.setVersionNo(versionNo);
                shot.setCreateBy("ai-video-worker");
                shotMapper.insertAiVideoShot(shot);
                AiVideoAsset keyframeAsset = qwenAssetService.createDraftImageAsset(
                        chapter.getProjectId(), chapter.getChapterId(), scene.getSceneId(), shot.getShotId(),
                        "shot-c" + chapter.getChapterId() + "-s" + scene.getSceneId() + "-t" + shot.getShotNo(),
                        scene.getSceneTitle() + "镜头" + shot.getShotNo(), "SHOT_KEYFRAME", "SHOT", 0, versionNo,
                        shotNode.path("keyframePrompt").asText(""),
                        shotNode.path("imageNegativePrompt").asText(""),
                        buildShotKeyframeMetadata(shot, sceneReference.getAssetId(),
                                characterReferenceAssetIds, versionNo),
                        null, sceneReference.getAssetId());
                insertShotReferenceRelations(chapter.getProjectId(), sceneReference.getAssetId(),
                        characterReferenceAssetIds, keyframeAsset.getAssetId());
            }
        }
    }

    private String buildCharacterReferenceMetadata(AiVideoCharacter character,
            AiVideoChapter chapter, int versionNo)
    {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "story_bible");
        metadata.put("characterId", character.getCharacterId());
        metadata.put("characterCode", character.getCharacterCode());
        metadata.put("characterView", "FRONT_SIDE_BACK");
        metadata.put("projectCanonical", true);
        metadata.put("firstMaterializedFromChapterId", chapter.getChapterId());
        metadata.put("analysisVersion", versionNo);
        return metadata.toString();
    }

    private String buildSceneReferenceMetadata(AiVideoScene scene, int versionNo)
    {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "story_bible");
        metadata.put("sceneNo", scene.getSceneNo());
        metadata.put("analysisVersion", versionNo);
        return metadata.toString();
    }

    private String buildShotKeyframeMetadata(AiVideoShot shot, Long sceneReferenceAssetId,
            List<Long> characterReferenceAssetIds, int versionNo)
    {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "story_bible");
        metadata.put("shotNo", shot.getShotNo());
        metadata.put("analysisVersion", versionNo);
        metadata.put("sourceAssetId", sceneReferenceAssetId);
        metadata.put("sceneReferenceAssetId", sceneReferenceAssetId);
        metadata.set("characterReferenceAssetIds", longArrayNode(characterReferenceAssetIds));
        metadata.put("referenceBindingMode", "AUTO");
        return metadata.toString();
    }

    private ArrayNode longArrayNode(List<Long> values)
    {
        ArrayNode array = objectMapper.createArrayNode();
        if (values != null)
        {
            for (Long value : values)
            {
                if (value != null)
                {
                    array.add(value.longValue());
                }
            }
        }
        return array;
    }

    private String buildCharacterCode(String characterName)
    {
        String normalized = normalizeCharacterKey(characterName);
        if (normalized.isEmpty())
        {
            throw new IllegalStateException("人物名称不能为空，无法建立项目级身份编码");
        }
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder code = new StringBuilder("char_");
            // character_code 为 varchar(64)，保留 SHA-256 的前 224 位已足够避免身份碰撞。
            for (int index = 0; index < 28; index++)
            {
                code.append(String.format(Locale.ROOT, "%02x", digest[index] & 0xff));
            }
            return code.toString();
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256，无法建立人物身份编码", ex);
        }
    }

    private void mergeCharacterAliases(AiVideoCharacter canonicalCharacter,
            AiVideoCharacter analyzedCharacter) throws Exception
    {
        Map<String, String> aliasesByKey = new LinkedHashMap<>();
        collectCharacterAliasValues(aliasesByKey, objectMapper.readTree(
                canonicalCharacter.getAliasesJson() == null ? "[]" : canonicalCharacter.getAliasesJson()));
        collectCharacterAliasValues(aliasesByKey, objectMapper.readTree(
                analyzedCharacter.getAliasesJson() == null ? "[]" : analyzedCharacter.getAliasesJson()));
        addCharacterAliasValue(aliasesByKey, analyzedCharacter.getCharacterName());
        aliasesByKey.remove(normalizeCharacterKey(canonicalCharacter.getCharacterName()));

        ArrayNode mergedAliases = objectMapper.createArrayNode();
        for (String alias : aliasesByKey.values())
        {
            mergedAliases.add(alias);
        }
        String mergedAliasesJson = objectMapper.writeValueAsString(mergedAliases);
        if (!mergedAliasesJson.equals(canonicalCharacter.getAliasesJson()))
        {
            if (characterMapper.updateAiVideoCharacterAliases(canonicalCharacter.getCharacterId(),
                    mergedAliasesJson, "ai-video-worker") != 1)
            {
                throw new IllegalStateException("人物别名规范更新失败："
                        + canonicalCharacter.getCharacterName());
            }
            canonicalCharacter.setAliasesJson(mergedAliasesJson);
        }
    }

    private void collectCharacterAliasValues(Map<String, String> aliasesByKey, JsonNode aliases)
    {
        if (aliases == null || !aliases.isArray())
        {
            return;
        }
        for (JsonNode alias : aliases)
        {
            addCharacterAliasValue(aliasesByKey, characterReferenceKey(alias));
        }
    }

    private ArrayNode sanitizeCharacterAliases(JsonNode aliases)
    {
        Map<String, String> aliasesByKey = new LinkedHashMap<>();
        collectCharacterAliasValues(aliasesByKey, aliases);
        ArrayNode sanitized = objectMapper.createArrayNode();
        for (String alias : aliasesByKey.values())
        {
            sanitized.add(alias);
        }
        return sanitized;
    }

    private void addCharacterAliasValue(Map<String, String> aliasesByKey, String alias)
    {
        String normalized = normalizeCharacterKey(alias);
        if (!normalized.isEmpty() && !isGenericCharacterAlias(normalized)
                && !aliasesByKey.containsKey(normalized))
        {
            aliasesByKey.put(normalized, alias.trim());
        }
    }

    private boolean isGenericCharacterAlias(String alias)
    {
        return GENERIC_CHARACTER_ALIASES.contains(normalizeCharacterKey(alias));
    }

    private void registerCharacterReferenceKeys(Map<String, Long> referenceIdsByKey,
            AiVideoCharacter character, JsonNode analyzedCharacter, Long assetId) throws Exception
    {
        registerCharacterReferenceKey(referenceIdsByKey, character.getCharacterCode(), assetId);
        registerCharacterReferenceKey(referenceIdsByKey, character.getCharacterName(), assetId);
        registerCharacterReferenceKey(referenceIdsByKey, String.valueOf(character.getCharacterId()), assetId);
        registerCharacterAliases(referenceIdsByKey, objectMapper.readTree(
                character.getAliasesJson() == null ? "[]" : character.getAliasesJson()), assetId);
        registerCharacterAliases(referenceIdsByKey,
                analyzedCharacter == null ? null : analyzedCharacter.path("aliases"), assetId);
    }

    private void registerCharacterAliases(Map<String, Long> referenceIdsByKey,
            JsonNode aliases, Long assetId)
    {
        if (aliases == null || !aliases.isArray())
        {
            return;
        }
        for (JsonNode alias : aliases)
        {
            String aliasKey = characterReferenceKey(alias);
            if (!isGenericCharacterAlias(aliasKey))
            {
                registerCharacterReferenceKey(referenceIdsByKey, aliasKey, assetId);
            }
        }
    }

    private void registerCharacterReferenceKey(Map<String, Long> referenceIdsByKey,
            String key, Long assetId)
    {
        String normalized = normalizeCharacterKey(key);
        if (!normalized.isEmpty() && assetId != null)
        {
            Long existingAssetId = referenceIdsByKey.get(normalized);
            if (existingAssetId != null && !existingAssetId.equals(assetId))
            {
                throw new IllegalStateException("人物标识或别名“" + key
                        + "”在同一项目中对应多个人物，无法安全绑定人物参考图；请修改重复姓名或别名后重新分析");
            }
            referenceIdsByKey.put(normalized, assetId);
        }
    }

    private List<Long> resolveShotCharacterReferenceAssetIds(ObjectNode shotNode, JsonNode sceneNode,
            Map<String, Long> referenceIdsByKey, String shotPath)
    {
        Set<Long> resolved = new LinkedHashSet<>();
        JsonNode shotCharacters = shotNode.path("characters");
        boolean shotCharactersProvided = shotCharacters.isArray();
        addCharacterReferenceIds(resolved, shotCharacters, referenceIdsByKey, shotPath);
        JsonNode dialogues = shotNode.path("dialogues");
        if (dialogues.isArray())
        {
            for (JsonNode dialogue : dialogues)
            {
                addCharacterReferenceId(resolved, dialogue.path("speaker"), referenceIdsByKey, shotPath);
            }
        }
        if (!shotCharactersProvided)
        {
            addCharacterReferenceIds(resolved,
                    sceneNode == null ? null : sceneNode.path("characters"), referenceIdsByKey, shotPath);
        }
        return new ArrayList<>(resolved);
    }

    private void addCharacterReferenceIds(Set<Long> resolved, JsonNode characters,
            Map<String, Long> referenceIdsByKey, String shotPath)
    {
        if (characters == null || characters.isMissingNode() || characters.isNull())
        {
            return;
        }
        if (characters.isArray())
        {
            for (JsonNode character : characters)
            {
                addCharacterReferenceId(resolved, character, referenceIdsByKey, shotPath);
            }
        }
        else
        {
            addCharacterReferenceId(resolved, characters, referenceIdsByKey, shotPath);
        }
    }

    private void addCharacterReferenceId(Set<Long> resolved, JsonNode character,
            Map<String, Long> referenceIdsByKey, String shotPath)
    {
        String rawKey = characterReferenceKey(character);
        String key = normalizeCharacterKey(rawKey);
        if (key.isEmpty())
        {
            throw new IllegalStateException(shotPath + " 存在未命名的可见人物，无法绑定人物参考图");
        }
        Long assetId = referenceIdsByKey.get(key);
        if (assetId == null)
        {
            throw new IllegalStateException(shotPath + " 的人物“" + rawKey
                    + "”未匹配到项目人物规范，不能在缺少三视图参考的情况下生成分镜");
        }
        resolved.add(assetId);
    }

    private String characterReferenceKey(JsonNode character)
    {
        if (character == null || character.isMissingNode() || character.isNull())
        {
            return "";
        }
        if (character.isTextual() || character.isNumber())
        {
            return character.asText("");
        }
        return firstNonBlank(character.path("characterCode").asText(""),
                character.path("name").asText(""), character.path("characterName").asText(""),
                character.path("speaker").asText(""));
    }

    private String normalizeCharacterKey(String key)
    {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private void insertShotReferenceRelations(Long projectId, Long sceneReferenceAssetId,
            List<Long> characterReferenceAssetIds, Long keyframeAssetId)
    {
        insertReferenceRelation(projectId, sceneReferenceAssetId, keyframeAssetId,
                0, "SCENE_REFERENCE");
        for (int index = 0; index < characterReferenceAssetIds.size(); index++)
        {
            insertReferenceRelation(projectId, characterReferenceAssetIds.get(index), keyframeAssetId,
                    index + 1, "CHARACTER_REFERENCE");
        }
    }

    private void insertReferenceRelation(Long projectId, Long fromAssetId, Long toAssetId,
            int relationOrder, String referenceRole)
    {
        AiVideoAssetRelation relation = new AiVideoAssetRelation();
        relation.setProjectId(projectId);
        relation.setFromAssetId(fromAssetId);
        relation.setToAssetId(toAssetId);
        relation.setRelationType("REFERENCE_IMAGE");
        relation.setRelationOrder(relationOrder);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("referenceRole", referenceRole);
        relation.setMetadataJson(metadata.toString());
        assetRelationMapper.insertAiVideoAssetRelation(relation);
    }

    private String firstNonBlank(String... values)
    {
        if (values == null)
        {
            return "";
        }
        for (String value : values)
        {
            if (value != null && !value.trim().isEmpty())
            {
                return value.trim();
            }
        }
        return "";
    }

    private Integer nullableInt(JsonNode node)
    {
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }
    private String abbreviate(String text)
    {
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }
}
