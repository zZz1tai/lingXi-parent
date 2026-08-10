package com.lingXi.aiVedio.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.AiVideoStoryBible;
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoChapterArchiveMapper;
import com.lingXi.aiVedio.mapper.AiVideoChapterMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.mapper.AiVideoStoryBibleMapper;
import com.lingXi.aiVedio.service.IAiVideoChapterService;
import com.lingXi.aiVedio.service.IAiVideoProjectService;
import com.lingXi.aiVedio.worker.AiVideoChapterAnalysisWorker;

/**
 * AI视频章节服务实现类。
 */
@Service
public class AiVideoChapterServiceImpl implements IAiVideoChapterService
{
    @Autowired
    private AiVideoChapterMapper chapterMapper;

    @Autowired
    private AiVideoChapterArchiveMapper chapterArchiveMapper;

    @Autowired
    private IAiVideoProjectService projectService;

    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;

    @Autowired
    private AiVideoStoryBibleMapper storyBibleMapper;

    @Autowired
    private AiVideoChapterAnalysisWorker chapterAnalysisWorker;

    /**
     * 根据项目ID查询章节列表。
     *
     * @param projectId 项目ID
     * @return 章节列表
     */
    @Override
    public List<AiVideoChapter> selectAiVideoChapterList(Long projectId)
    {
        projectService.checkProjectOwner(projectId);
        return chapterMapper.selectAiVideoChapterList(projectId);
    }

    /**
     * 新增AI视频章节，计算文本哈希和字数。
     *
     * @param chapter 章节信息
     * @return 受影响行数
     */
    @Override
    public int insertAiVideoChapter(AiVideoChapter chapter)
    {
        if (chapter.getProjectId() == null || chapter.getChapterNo() == null)
        {
            throw new ServiceException("项目和章节序号不能为空");
        }
        if (StringUtils.isEmpty(chapter.getSourceText()))
        {
            throw new ServiceException("章节原文不能为空");
        }
        projectService.checkProjectOwner(chapter.getProjectId());
        chapter.setChapterId(null);
        chapter.setSourceHash(sha256(chapter.getSourceText()));
        chapter.setWordCount(chapter.getSourceText().codePointCount(0, chapter.getSourceText().length()));
        chapter.setParseStatus("PENDING");
        chapter.setPipelineStatus("NOT_STARTED");
        chapter.setCurrentBibleVersion(0);
        chapter.setCreateBy(SecurityUtils.getUsername());
        chapter.setCreateTime(DateUtils.getNowDate());
        return chapterMapper.insertAiVideoChapter(chapter);
    }

    /**
     * 批量删除AI视频章节，归档相关资产、故事圣经、分镜、场景和任务。
     *
     * @param projectId 项目ID
     * @param chapterIds 章节ID数组
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiVideoChapterByChapterIds(Long projectId, Long[] chapterIds)
    {
        Long[] normalizedChapterIds = normalizeChapterIds(chapterIds);
        projectService.checkProjectOwner(projectId);

        List<AiVideoChapter> chapters = chapterArchiveMapper
                .selectAiVideoChaptersForUpdate(projectId, normalizedChapterIds);
        if (chapters == null || chapters.size() != normalizedChapterIds.length)
        {
            throw new ServiceException("部分章节不存在、已删除或不属于当前项目，请刷新后重试");
        }
        Map<Long, AiVideoChapter> chapterById = new LinkedHashMap<>();
        for (AiVideoChapter chapter : chapters)
        {
            chapterById.put(chapter.getChapterId(), chapter);
        }

        List<AiVideoGenerationTask> tasks = chapterArchiveMapper
                .selectAiVideoChapterTasksForUpdate(projectId, normalizedChapterIds);
        if (tasks != null)
        {
            for (AiVideoGenerationTask task : tasks)
            {
                if (isActiveTaskStatus(task.getStatus()))
                {
                    throw new ServiceException(chapterLabel(chapterById.get(task.getChapterId()))
                            + "存在活动生成任务“" + safeName(task.getTaskName(), "任务" + task.getTaskId())
                            + "”（" + task.getStatus() + "），请等待任务完成或先处理待核对任务");
                }
            }
        }

        List<AiVideoAsset> assets = chapterArchiveMapper
                .selectAiVideoChapterAssetsForUpdate(projectId, normalizedChapterIds);
        if (assets != null)
        {
            for (AiVideoAsset asset : assets)
            {
                if (isProcessingAssetStatus(asset.getStatus()))
                {
                    throw new ServiceException(chapterLabel(chapterById.get(asset.getChapterId()))
                            + "存在生成中资产“" + safeName(asset.getAssetName(), "资产" + asset.getAssetId())
                            + "”（" + asset.getStatus() + "），请等待处理完成后再删除");
                }
            }
        }

        String username = SecurityUtils.getUsername();
        chapterArchiveMapper.archiveAiVideoAssetsByChapterIds(projectId, normalizedChapterIds, username);
        chapterArchiveMapper.archiveAiVideoStoryBiblesByChapterIds(projectId, normalizedChapterIds, username);
        chapterArchiveMapper.archiveAiVideoShotsByChapterIds(projectId, normalizedChapterIds, username);
        chapterArchiveMapper.archiveAiVideoScenesByChapterIds(projectId, normalizedChapterIds, username);
        chapterArchiveMapper.archiveAiVideoTasksByChapterIds(projectId, normalizedChapterIds, username);

        int deleted = chapterMapper.deleteAiVideoChapterByChapterIds(normalizedChapterIds, projectId);
        if (deleted != normalizedChapterIds.length)
        {
            throw new ServiceException("章节状态已变化，本次删除已回滚，请刷新后重试");
        }
        return deleted;
    }

    /**
     * 启动章节故事圣经解析，创建或恢复解析任务。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 生成任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startChapterAnalysis(Long projectId, Long chapterId)
    {
        projectService.checkProjectOwner(projectId);
        AiVideoChapter chapter = chapterArchiveMapper.selectAiVideoChapterForUpdate(projectId, chapterId);
        if (chapter == null || !projectId.equals(chapter.getProjectId()))
        {
            throw new ServiceException("章节不存在或不属于当前项目");
        }
        String key = "chapter-analysis-" + chapterId + "-" + chapter.getSourceHash();
        AiVideoGenerationTask existing = taskMapper.selectLatestStoryBibleTaskByKeyPrefix(key);
        if (existing != null && (AiVideoTaskStatus.QUEUED.is(existing.getStatus())
                || AiVideoTaskStatus.RUNNING.is(existing.getStatus())))
        {
            if (AiVideoTaskStatus.QUEUED.is(existing.getStatus()))
            {
                startAnalysisAfterCommit(existing.getTaskId(), chapterId);
            }
            return existing.getTaskId();
        }
        if (existing != null && AiVideoTaskStatus.PAUSED.is(existing.getStatus()))
        {
            String username = SecurityUtils.getUsername();
            if (taskMapper.resumeStoryBibleTask(existing.getTaskId(), username) != 1)
            {
                throw new ServiceException("章节解析暂停状态已变化，请刷新后重试");
            }
            chapterMapper.updateAiVideoChapterAnalysisStatus(chapterId, "RUNNING", "RUNNING", null,
                    chapter.getCurrentBibleVersion());
            startAnalysisAfterCommit(existing.getTaskId(), chapterId);
            return existing.getTaskId();
        }
        if ("RUNNING".equals(chapter.getParseStatus()))
        {
            throw new ServiceException("章节正在解析中");
        }

        AiVideoGenerationTask task = new AiVideoGenerationTask();
        task.setProjectId(projectId);
        task.setChapterId(chapterId);
        task.setTaskType("STORY_BIBLE");
        task.setTaskName("章节故事圣经解析");
        task.setStatus(AiVideoTaskStatus.QUEUED.name());
        task.setPriority(100);
        task.setIdempotencyKey(existing == null ? key : key + "-" + System.currentTimeMillis());
        task.setProviderCode("qwen");
        task.setModelCode("default");
        task.setProgress(0);
        task.setMaxRetry(0);
        task.setCreateBy(SecurityUtils.getUsername());
        task.setCreateTime(DateUtils.getNowDate());
        taskMapper.insertAiVideoGenerationTask(task);
        chapterMapper.updateAiVideoChapterAnalysisStatus(chapterId, "RUNNING", "RUNNING", null, chapter.getCurrentBibleVersion());
        startAnalysisAfterCommit(task.getTaskId(), chapterId);
        return task.getTaskId();
    }

    /**
     * 暂停章节故事圣经解析任务。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 生成任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long pauseChapterAnalysis(Long projectId, Long chapterId)
    {
        projectService.checkProjectOwner(projectId);
        AiVideoChapter chapter = chapterArchiveMapper.selectAiVideoChapterForUpdate(projectId, chapterId);
        if (chapter == null || !projectId.equals(chapter.getProjectId()))
        {
            throw new ServiceException("章节不存在或不属于当前项目");
        }
        String key = "chapter-analysis-" + chapterId + "-" + chapter.getSourceHash();
        AiVideoGenerationTask task = taskMapper.selectLatestStoryBibleTaskByKeyPrefix(key);
        if (task == null || !(AiVideoTaskStatus.QUEUED.is(task.getStatus())
                || AiVideoTaskStatus.RUNNING.is(task.getStatus())))
        {
            throw new ServiceException("当前没有可暂停的章节解析任务");
        }
        String username = SecurityUtils.getUsername();
        if (taskMapper.pauseStoryBibleTask(task.getTaskId(), username) != 1)
        {
            throw new ServiceException("章节解析状态已变化，请刷新后重试");
        }
        chapterMapper.updateAiVideoChapterAnalysisStatus(chapterId, "PAUSED", "PAUSED", null,
                chapter.getCurrentBibleVersion());
        return task.getTaskId();
    }

    /**
     * 在事务提交后启动异步解析线程，避免异步状态更新与当前事务争抢行锁。
     *
     * @param taskId 生成任务ID
     * @param chapterId 章节ID
     */
    private void startAnalysisAfterCommit(final Long taskId, final Long chapterId)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive())
        {
            chapterAnalysisWorker.analyze(taskId, chapterId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                chapterAnalysisWorker.analyze(taskId, chapterId);
            }
        });
    }

    /**
     * 查询章节最新版本的故事圣经。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 故事圣经信息
     */
    @Override
    public AiVideoStoryBible selectLatestStoryBible(Long projectId, Long chapterId)
    {
        projectService.checkProjectOwner(projectId);
        AiVideoChapter chapter = chapterMapper.selectAiVideoChapterByChapterId(chapterId);
        if (chapter == null || !projectId.equals(chapter.getProjectId()))
        {
            throw new ServiceException("章节不存在或不属于当前项目");
        }
        return storyBibleMapper.selectLatestAiVideoStoryBibleByChapterId(chapterId);
    }

    /**
     * 计算文本的SHA-256哈希值。
     *
     * @param source 原始文本
     * @return 十六进制哈希字符串
     */
    private String sha256(String source)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash)
            {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new ServiceException("无法计算章节文本摘要");
        }
    }

    /**
     * 标准化章节ID数组，去重并校验非空。
     *
     * @param chapterIds 章节ID数组
     * @return 去重后的章节ID数组
     */
    private Long[] normalizeChapterIds(Long[] chapterIds)
    {
        if (chapterIds == null || chapterIds.length == 0)
        {
            throw new ServiceException("请选择需要删除的章节");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long chapterId : chapterIds)
        {
            if (chapterId == null)
            {
                throw new ServiceException("章节ID不能为空");
            }
            uniqueIds.add(chapterId);
        }
        return uniqueIds.toArray(new Long[uniqueIds.size()]);
    }

    /**
     * 判断任务状态是否为活跃状态。
     *
     * @param status 任务状态
     * @return 是否为活跃状态
     */
    private boolean isActiveTaskStatus(String status)
    {
        return AiVideoTaskStatus.isActive(status);
    }

    /**
     * 判断资产状态是否为处理中状态。
     *
     * @param status 资产状态
     * @return 是否为处理中状态
     */
    private boolean isProcessingAssetStatus(String status)
    {
        return "GENERATING".equals(status) || "VALIDATING".equals(status);
    }

    /**
     * 获取章节的显示标签。
     *
     * @param chapter 章节信息
     * @return 章节标签字符串
     */
    private String chapterLabel(AiVideoChapter chapter)
    {
        if (chapter == null)
        {
            return "所选章节";
        }
        String title = chapter.getChapterTitle() == null ? "" : chapter.getChapterTitle().trim();
        String number = chapter.getChapterNo() == null ? "" : "第" + chapter.getChapterNo() + "章";
        return "章节“" + (title.isEmpty() ? number : number + " " + title) + "”";
    }

    /**
     * 获取安全的名称，为空则返回兜底值。
     *
     * @param value 原始值
     * @param fallback 兜底值
     * @return 非空名称
     */
    private String safeName(String value, String fallback)
    {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
