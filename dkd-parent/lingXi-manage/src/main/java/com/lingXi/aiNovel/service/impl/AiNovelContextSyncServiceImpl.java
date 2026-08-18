package com.lingXi.aiNovel.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.ai.client.AgentClient;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelContextTask;
import com.lingXi.aiNovel.domain.AiNovelForeshadow;
import com.lingXi.aiNovel.domain.AiNovelSetting;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.domain.dto.NovelContextAgentRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextAnalyzeRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyResultDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextChangeDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextTaskVO;
import com.lingXi.aiNovel.mapper.AiNovelChapterMapper;
import com.lingXi.aiNovel.mapper.AiNovelContextTaskMapper;
import com.lingXi.aiNovel.mapper.AiNovelForeshadowMapper;
import com.lingXi.aiNovel.mapper.AiNovelSettingMapper;
import com.lingXi.aiNovel.mapper.AiNovelWorkMapper;
import com.lingXi.aiNovel.service.IAiNovelChapterService;
import com.lingXi.aiNovel.service.IAiNovelContextSyncService;
import com.lingXi.aiNovel.service.IAiNovelForeshadowService;
import com.lingXi.aiNovel.service.IAiNovelSettingService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;

/**
 * 章节连续性闭环：自动保存本章事实摘要；设定/伏笔只生成候选，用户确认后事务写回。
 */
@Service
public class AiNovelContextSyncServiceImpl implements IAiNovelContextSyncService
{
    private static final int MAX_CONTEXT_ITEMS = 100;
    private static final int MAX_CHANGES = 40;
    private static final int MAX_CHAPTER_CONTENT_CHARS = 100_000;
    private static final int MAX_CHAPTER_BRIEF_CHARS = 500;
    private static final Set<String> OPERATIONS = Set.of("ADD", "UPDATE");
    private static final Set<String> RESOURCE_TYPES = Set.of("setting", "foreshadow");
    private static final Set<String> SETTING_TYPES = Set.of(
            "character", "world", "outline", "item", "organization", "event", "style", "other");
    private static final Set<String> STATUSES = Set.of("buried", "pending", "resolved");
    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");

    private final IAiNovelWorkService workService;
    private final IAiNovelChapterService chapterService;
    private final IAiNovelSettingService settingService;
    private final IAiNovelForeshadowService foreshadowService;
    private final AgentClient agentClient;

    @Autowired private AiNovelContextTaskMapper contextTaskMapper;
    @Autowired private AiNovelWorkMapper workMapper;
    @Autowired private AiNovelChapterMapper chapterMapper;
    @Autowired private AiNovelSettingMapper settingMapper;
    @Autowired private AiNovelForeshadowMapper foreshadowMapper;
    @Autowired private ObjectMapper objectMapper;

    public AiNovelContextSyncServiceImpl(
            IAiNovelWorkService workService,
            IAiNovelChapterService chapterService,
            IAiNovelSettingService settingService,
            IAiNovelForeshadowService foreshadowService,
            AgentClient agentClient)
    {
        this.workService = workService;
        this.chapterService = chapterService;
        this.settingService = settingService;
        this.foreshadowService = foreshadowService;
        this.agentClient = agentClient;
    }

    /** 校验当前用户与正文快照后持久化任务；HTTP 请求不等待模型。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelContextTaskVO submitAnalysis(Long workId, NovelContextAnalyzeRequestDTO request)
    {
        if (request == null || request.getChapterId() == null)
        {
            throw new ServiceException("章节ID不能为空");
        }
        AiNovelWork work = workService.checkWorkOwner(workId);
        AiNovelChapter chapter = chapterService.selectAiNovelChapterByChapterId(
                workId, request.getChapterId());
        if (StringUtils.isBlank(chapter.getContent()))
        {
            throw new ServiceException("章节正文为空，无需整理资料");
        }
        if (chapter.getContent().length() > MAX_CHAPTER_CONTENT_CHARS)
        {
            throw new ServiceException("章节正文过长，暂无法整理资料");
        }

        String sourceHash = contentHash(chapter.getContent());
        AiNovelContextTask task = new AiNovelContextTask();
        task.setWorkId(workId);
        task.setChapterId(chapter.getChapterId());
        task.setOwnerUserId(work.getOwnerUserId());
        task.setContentHash(sourceHash);
        task.setCreateBy(SecurityUtils.getUsername());
        contextTaskMapper.insertIgnore(task);

        AiNovelContextTask persisted = contextTaskMapper.selectByChapterAndHash(
                chapter.getChapterId(), sourceHash);
        if (persisted == null)
        {
            throw new ServiceException("资料同步任务创建失败");
        }
        boolean force = Boolean.TRUE.equals(request.getForce());
        if (force || AiNovelContextTask.STATUS_FAILED.equals(persisted.getStatus())
                || AiNovelContextTask.STATUS_OBSOLETE.equals(persisted.getStatus()))
        {
            contextTaskMapper.resetTask(persisted.getTaskId());
            persisted = contextTaskMapper.selectByTaskId(persisted.getTaskId());
        }
        return toTaskVO(persisted);
    }

    @Override
    public NovelContextTaskVO getAnalysisTask(Long workId, Long taskId)
    {
        workService.checkWorkOwner(workId);
        AiNovelContextTask task = contextTaskMapper.selectByTaskId(taskId);
        if (task == null || !workId.equals(task.getWorkId()))
        {
            throw new ServiceException("资料同步任务不存在或无权访问");
        }
        return toTaskVO(task);
    }

    @Override
    public NovelContextTaskVO getLatestAnalysisTask(Long workId, Long chapterId)
    {
        workService.checkWorkOwner(workId);
        AiNovelChapter chapter = chapterService.selectAiNovelChapterByChapterId(workId, chapterId);
        AiNovelContextTask task = contextTaskMapper.selectLatestByChapterId(chapterId);
        if (task == null || !workId.equals(task.getWorkId())
                || !task.getContentHash().equalsIgnoreCase(contentHash(chapter.getContent())))
        {
            return null;
        }
        return toTaskVO(task);
    }

    /** Worker 读取当前数据库快照执行分析，不依赖 Web 线程登录态。 */
    @Override
    public JsonNode executeAnalysisTask(AiNovelContextTask task)
    {
        if (task == null)
        {
            return null;
        }
        AiNovelWork work = workMapper.selectAiNovelWorkByWorkId(task.getWorkId());
        AiNovelChapter chapter = chapterMapper.selectAiNovelChapterByChapterId(task.getChapterId());
        if (work == null || chapter == null || !task.getWorkId().equals(chapter.getWorkId())
                || !task.getContentHash().equalsIgnoreCase(contentHash(chapter.getContent())))
        {
            return null;
        }

        List<AiNovelSetting> settings = settingMapper.selectAiNovelSettingList(task.getWorkId(), null);
        List<AiNovelForeshadow> foreshadows =
                foreshadowMapper.selectAiNovelForeshadowList(task.getWorkId(), null);
        if (settings.size() > MAX_CONTEXT_ITEMS || foreshadows.size() > MAX_CONTEXT_ITEMS)
        {
            throw new ServiceException("作品资料超过单次分析上限，请先人工归并重复条目");
        }
        JsonNode data = agentClient.analyzeNovelContext(
                buildAgentRequest(work, chapter, settings, foreshadows));
        if (!data.isObject())
        {
            throw new ServiceException("AI 返回了无效的资料变更清单");
        }
        ObjectNode result = (ObjectNode) data.deepCopy();
        String chapterBrief = result.path("chapterBrief").asText().trim();
        if (StringUtils.isBlank(chapterBrief) || chapterBrief.length() > MAX_CHAPTER_BRIEF_CHARS)
        {
            throw new ServiceException("AI 返回了无效的章节摘要");
        }
        int briefSaved = chapterMapper.updateChapterBriefIfContentHashMatches(
                task.getWorkId(), task.getChapterId(), task.getContentHash(), chapterBrief,
                StringUtils.isBlank(task.getCreateBy()) ? "async-worker" : task.getCreateBy());
        if (briefSaved < 1)
        {
            return null;
        }
        result.put("chapterBrief", chapterBrief);
        result.put("chapterBriefSaved", true);
        result.put("workId", task.getWorkId());
        result.put("chapterId", task.getChapterId());
        result.put("contentHash", task.getContentHash());
        return result;
    }

    /** 再次校验正文版本与全部候选字段，并在一个事务中写回。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelContextApplyResultDTO apply(Long workId, NovelContextApplyRequestDTO request)
    {
        if (request == null || request.getChapterId() == null)
        {
            throw new ServiceException("章节ID不能为空");
        }
        AiNovelChapter chapter = chapterService.selectAiNovelChapterByChapterId(
                workId, request.getChapterId());
        String currentHash = contentHash(chapter.getContent());
        if (StringUtils.isBlank(request.getContentHash())
                || !currentHash.equalsIgnoreCase(request.getContentHash().trim()))
        {
            throw new ServiceException("章节正文已发生变化，请重新分析后再应用建议");
        }
        List<NovelContextChangeDTO> changes = request.getChanges();
        if (changes == null || changes.isEmpty())
        {
            throw new ServiceException("请至少选择一条资料变更");
        }
        if (changes.size() > MAX_CHANGES)
        {
            throw new ServiceException("单次应用的资料变更不能超过40条");
        }

        Map<Long, AiNovelSetting> settings = settingService.selectAiNovelSettingList(workId, null)
                .stream().collect(Collectors.toMap(AiNovelSetting::getSettingId, item -> item));
        Map<Long, AiNovelForeshadow> foreshadows =
                foreshadowService.selectAiNovelForeshadowList(workId, null)
                        .stream().collect(Collectors.toMap(
                                AiNovelForeshadow::getForeshadowId, item -> item));
        validateChanges(changes, settings, foreshadows);

        int settingCount = 0;
        int foreshadowCount = 0;
        for (NovelContextChangeDTO change : changes)
        {
            if ("setting".equals(change.getResourceType()))
            {
                applySetting(workId, change);
                settingCount++;
            }
            else
            {
                applyForeshadow(workId, change);
                foreshadowCount++;
            }
        }
        return new NovelContextApplyResultDTO(
                settingCount + foreshadowCount, settingCount, foreshadowCount);
    }

    private static NovelContextAgentRequestDTO buildAgentRequest(
            AiNovelWork work,
            AiNovelChapter chapter,
            List<AiNovelSetting> settings,
            List<AiNovelForeshadow> foreshadows)
    {
        NovelContextAgentRequestDTO request = new NovelContextAgentRequestDTO();
        request.setWorkId(work.getWorkId());
        request.setWorkName(work.getWorkName());
        request.setWorkType(work.getWorkType());
        request.setGenre(work.getGenre());
        request.setSynopsis(work.getSynopsis());
        request.setChapterId(chapter.getChapterId());
        request.setChapterNo(chapter.getChapterNo());
        request.setChapterTitle(chapter.getChapterTitle());
        request.setChapterContent(chapter.getContent());

        List<NovelContextAgentRequestDTO.SettingItem> settingItems = new ArrayList<>();
        for (AiNovelSetting setting : settings)
        {
            NovelContextAgentRequestDTO.SettingItem item =
                    new NovelContextAgentRequestDTO.SettingItem();
            item.setSettingId(setting.getSettingId());
            item.setSettingType(setting.getSettingType());
            item.setTitle(setting.getTitle());
            item.setContent(setting.getContent());
            settingItems.add(item);
        }
        request.setSettings(settingItems);

        List<NovelContextAgentRequestDTO.ForeshadowItem> foreshadowItems = new ArrayList<>();
        for (AiNovelForeshadow foreshadow : foreshadows)
        {
            NovelContextAgentRequestDTO.ForeshadowItem item =
                    new NovelContextAgentRequestDTO.ForeshadowItem();
            item.setForeshadowId(foreshadow.getForeshadowId());
            item.setTitle(foreshadow.getTitle());
            item.setDescription(foreshadow.getDescription());
            item.setStatus(foreshadow.getStatus());
            item.setPriority(foreshadow.getPriority());
            item.setKeyword(foreshadow.getKeyword());
            item.setResolveChapterNo(foreshadow.getResolveChapterNo());
            foreshadowItems.add(item);
        }
        request.setForeshadows(foreshadowItems);
        return request;
    }

    private static void validateChanges(
            List<NovelContextChangeDTO> changes,
            Map<Long, AiNovelSetting> settings,
            Map<Long, AiNovelForeshadow> foreshadows)
    {
        Set<String> seenTargets = new HashSet<>();
        Set<String> seenAdds = new HashSet<>();
        Map<String, Long> settingTitleIds = new HashMap<>();
        settings.values().forEach(item -> settingTitleIds.putIfAbsent(
                normalizedTitle(item.getTitle()), item.getSettingId()));
        Map<String, Long> foreshadowTitleIds = new HashMap<>();
        foreshadows.values().forEach(item -> foreshadowTitleIds.putIfAbsent(
                normalizedTitle(item.getTitle()), item.getForeshadowId()));

        for (NovelContextChangeDTO change : changes)
        {
            if (change == null || !RESOURCE_TYPES.contains(change.getResourceType())
                    || !OPERATIONS.contains(change.getOperation()))
            {
                throw new ServiceException("资料变更操作无效");
            }
            requireText(change.getTitle(), "资料标题", 128);
            requireText(change.getEvidence(), "正文依据", 500);
            requireText(change.getReason(), "变更理由", 500);

            if ("ADD".equals(change.getOperation()))
            {
                if (change.getTargetId() != null)
                {
                    throw new ServiceException("新增资料不能携带目标ID");
                }
                String normalized = normalizedTitle(change.getTitle());
                Long matchedId = "setting".equals(change.getResourceType())
                        ? settingTitleIds.get(normalized)
                        : foreshadowTitleIds.get(normalized);
                if (matchedId != null)
                {
                    // 标题与现有资料重复时自动转为更新对应资料
                    change.setOperation("UPDATE");
                    change.setTargetId(matchedId);
                }
                else if (!seenAdds.add(change.getResourceType() + ":" + normalized))
                {
                    throw new ServiceException("新增资料与已选建议重复");
                }
            }
            else
            {
                if (change.getTargetId() == null)
                {
                    throw new ServiceException("更新资料必须携带目标ID");
                }
                boolean exists = "setting".equals(change.getResourceType())
                        ? settings.containsKey(change.getTargetId())
                        : foreshadows.containsKey(change.getTargetId());
                if (!exists)
                {
                    throw new ServiceException("待更新资料不存在或不属于当前作品");
                }
            }
            if (change.getTargetId() != null
                    && !seenTargets.add(change.getResourceType() + ":" + change.getTargetId()))
            {
                throw new ServiceException("同一资料不能在一批建议中重复更新");
            }

            if ("setting".equals(change.getResourceType()))
            {
                validateSettingChange(change);
            }
            else
            {
                validateForeshadowChange(change);
            }
        }
    }

    private static void validateSettingChange(NovelContextChangeDTO change)
    {
        if (!SETTING_TYPES.contains(change.getSettingType()))
        {
            throw new ServiceException("设定类型无效");
        }
        requireText(change.getContent(), "设定内容", 4_000);
        if (change.getDescription() != null || change.getStatus() != null
                || change.getPriority() != null || change.getKeyword() != null
                || change.getResolveChapterNo() != null)
        {
            throw new ServiceException("设定建议包含了伏笔专用字段");
        }
    }

    private static void validateForeshadowChange(NovelContextChangeDTO change)
    {
        if (!STATUSES.contains(change.getStatus()) || !PRIORITIES.contains(change.getPriority()))
        {
            throw new ServiceException("伏笔状态或等级无效");
        }
        if (change.getSettingType() != null || change.getContent() != null)
        {
            throw new ServiceException("伏笔建议包含了设定专用字段");
        }
        requireOptionalText(change.getDescription(), "伏笔详情", 4_000);
        requireOptionalText(change.getKeyword(), "伏笔关键词", 128);
        if (change.getResolveChapterNo() != null && change.getResolveChapterNo() < 1)
        {
            throw new ServiceException("计划回收章节号必须为正整数");
        }
    }

    private void applySetting(Long workId, NovelContextChangeDTO change)
    {
        AiNovelSetting setting = new AiNovelSetting();
        setting.setSettingId("UPDATE".equals(change.getOperation()) ? change.getTargetId() : null);
        setting.setSettingType(change.getSettingType());
        setting.setTitle(change.getTitle().trim());
        setting.setContent(change.getContent().trim());
        int affected = "ADD".equals(change.getOperation())
                ? settingService.insertAiNovelSetting(workId, setting)
                : settingService.updateAiNovelSetting(workId, setting);
        requireSingleWrite(affected);
    }

    private void applyForeshadow(Long workId, NovelContextChangeDTO change)
    {
        AiNovelForeshadow foreshadow = new AiNovelForeshadow();
        foreshadow.setForeshadowId(
                "UPDATE".equals(change.getOperation()) ? change.getTargetId() : null);
        foreshadow.setTitle(change.getTitle().trim());
        foreshadow.setDescription(normalizeOptional(change.getDescription()));
        foreshadow.setStatus(change.getStatus());
        foreshadow.setPriority(change.getPriority());
        foreshadow.setKeyword(normalizeOptional(change.getKeyword()));
        foreshadow.setResolveChapterNo(change.getResolveChapterNo());
        int affected = "ADD".equals(change.getOperation())
                ? foreshadowService.insertAiNovelForeshadow(workId, foreshadow)
                : foreshadowService.updateAiNovelForeshadow(workId, foreshadow);
        requireSingleWrite(affected);
    }

    private static void requireSingleWrite(int affected)
    {
        if (affected != 1)
        {
            throw new ServiceException("资料写入失败，请重新分析后重试");
        }
    }

    private static void requireText(String value, String label, int maxLength)
    {
        if (StringUtils.isBlank(value))
        {
            throw new ServiceException(label + "不能为空");
        }
        if (value.trim().length() > maxLength)
        {
            throw new ServiceException(label + "不能超过" + maxLength + "个字符");
        }
    }

    private static void requireOptionalText(String value, String label, int maxLength)
    {
        if (value != null && value.length() > maxLength)
        {
            throw new ServiceException(label + "不能超过" + maxLength + "个字符");
        }
    }

    private static String normalizeOptional(String value)
    {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private static String normalizedTitle(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private NovelContextTaskVO toTaskVO(AiNovelContextTask task)
    {
        if (task == null)
        {
            return null;
        }
        NovelContextTaskVO result = new NovelContextTaskVO();
        result.setTaskId(task.getTaskId());
        result.setWorkId(task.getWorkId());
        result.setChapterId(task.getChapterId());
        result.setContentHash(task.getContentHash());
        result.setStatus(task.getStatus());
        result.setAttemptCount(task.getAttemptCount());
        result.setErrorMessage(task.getErrorMessage());
        result.setCreateTime(task.getCreateTime());
        result.setUpdateTime(task.getUpdateTime());
        result.setStartedTime(task.getStartedTime());
        result.setFinishedTime(task.getFinishedTime());
        if (StringUtils.isNotBlank(task.getResultJson()))
        {
            try
            {
                result.setResult(objectMapper.readTree(task.getResultJson()));
            }
            catch (Exception exception)
            {
                throw new ServiceException("资料同步任务结果损坏");
            }
        }
        return result;
    }

    private static String contentHash(String content)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException("SHA-256不可用", impossible);
        }
    }
}
