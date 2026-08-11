package com.lingXi.aiNovel.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.ai.client.AgentClient;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelOutline;
import com.lingXi.aiNovel.domain.dto.NovelOutlineGeneratedDTO;
import com.lingXi.aiNovel.domain.dto.NovelOutlineGapDTO;
import com.lingXi.aiNovel.domain.dto.NovelOutlineNodeDTO;
import com.lingXi.aiNovel.mapper.AiNovelChapterMapper;
import com.lingXi.aiNovel.mapper.AiNovelOutlineMapper;
import com.lingXi.aiNovel.service.IAiNovelOutlineService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 小说三层大纲服务实现类。
 * <p>大纲按 BOOK → VOLUME → CHAPTER 三层组织；生成接口直连 Python
 * 一次性产出完整大纲并全量重建（先软删旧大纲再插入新树），
 * 断链报告由模型对比现有章节与大纲树后给出。</p>
 */
@Slf4j
@Service
public class AiNovelOutlineServiceImpl implements IAiNovelOutlineService
{
    /** 全书层 */
    private static final String LEVEL_BOOK = "BOOK";
    /** 卷层 */
    private static final String LEVEL_VOLUME = "VOLUME";
    /** 章层 */
    private static final String LEVEL_CHAPTER = "CHAPTER";

    @Autowired
    private AiNovelOutlineMapper outlineMapper;

    @Autowired
    private AiNovelChapterMapper chapterMapper;

    @Autowired
    private IAiNovelWorkService workService;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private ObjectMapper objectMapper;

    /** 查询作品的大纲节点平铺列表（按层级与排序）。 */
    @Override
    public List<AiNovelOutline> selectAiNovelOutlineList(Long workId)
    {
        workService.checkWorkOwner(workId);
        return outlineMapper.selectAiNovelOutlineListByWorkId(workId);
    }

    /** 新增大纲节点，校验层级归属与同级排序。 */
    @Override
    public int insertAiNovelOutline(Long workId, AiNovelOutline outline)
    {
        workService.checkWorkOwner(workId);
        if (!isValidLevel(outline.getOutlineLevel()))
        {
            throw new ServiceException("大纲层级不合法");
        }
        requireParent(workId, outline);
        if (StringUtils.isBlank(outline.getOutlineTitle()))
        {
            throw new ServiceException("大纲标题不能为空");
        }
        if (outline.getSeqNo() == null)
        {
            List<AiNovelOutline> siblings = outlineMapper.selectAiNovelOutlineListByParentId(
                    workId, outline.getParentId() == null ? 0L : outline.getParentId());
            outline.setSeqNo(siblings.stream().mapToInt(AiNovelOutline::getSeqNo)
                    .max().orElse(0) + 1);
        }
        outline.setOutlineId(null);
        outline.setWorkId(workId);
        outline.setCreateBy(SecurityUtils.getUsername());
        outline.setCreateTime(DateUtils.getNowDate());
        return outlineMapper.insertAiNovelOutline(outline);
    }

    /** 更新大纲节点。 */
    @Override
    public int updateAiNovelOutline(Long workId, AiNovelOutline outline)
    {
        AiNovelOutline existing = requireOutline(workId, outline.getOutlineId());
        if (outline.getOutlineTitle() != null)
        {
            existing.setOutlineTitle(outline.getOutlineTitle());
        }
        if (outline.getOutlineContent() != null)
        {
            existing.setOutlineContent(outline.getOutlineContent());
        }
        if (outline.getChapterId() != null)
        {
            existing.setChapterId(outline.getChapterId());
        }
        if (outline.getSeqNo() != null)
        {
            existing.setSeqNo(outline.getSeqNo());
        }
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        return outlineMapper.updateAiNovelOutline(existing);
    }

    /** 删除大纲节点，卷节点级联删除章节点，全书节点级联删除全部。 */
    @Override
    public int deleteAiNovelOutline(Long workId, Long outlineId)
    {
        AiNovelOutline existing = requireOutline(workId, outlineId);
        int result = 0;
        if (LEVEL_BOOK.equals(existing.getOutlineLevel()))
        {
            result = outlineMapper.deleteAiNovelOutlineByWorkIds(new Long[] { workId });
        }
        else if (LEVEL_VOLUME.equals(existing.getOutlineLevel()))
        {
            List<AiNovelOutline> children = outlineMapper.selectAiNovelOutlineListByParentId(
                    workId, outlineId);
            for (AiNovelOutline child : children)
            {
                result += outlineMapper.deleteAiNovelOutlineByOutlineId(
                        child.getOutlineId(), workId);
            }
            result += outlineMapper.deleteAiNovelOutlineByOutlineId(outlineId, workId);
        }
        else
        {
            result = outlineMapper.deleteAiNovelOutlineByOutlineId(outlineId, workId);
        }
        return result;
    }

    /** 按给定顺序重排同一父级下的大纲节点。 */
    @Override
    public int sortAiNovelOutline(Long workId, Long parentId, List<Long> outlineIds)
    {
        workService.checkWorkOwner(workId);
        if (outlineIds == null || outlineIds.isEmpty())
        {
            throw new ServiceException("大纲顺序不能为空");
        }
        int result = 0;
        int seqNo = 1;
        for (Long outlineId : outlineIds)
        {
            AiNovelOutline outline = requireOutline(workId, outlineId);
            if (!String.valueOf(outline.getParentId() == null ? 0L : outline.getParentId())
                    .equals(String.valueOf(parentId == null ? 0L : parentId)))
            {
                throw new ServiceException("大纲节点不属于该父级");
            }
            result += outlineMapper.updateOutlineSeqNo(outlineId, seqNo++, workId);
        }
        return result;
    }

    /** 调用 AI 生成三层大纲并全量保存。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelOutlineGeneratedDTO generateOutline(Long workId)
    {
        workService.checkWorkOwner(workId);
        List<AiNovelChapter> chapters = chapterMapper.selectAiNovelChapterListByWorkId(workId);
        List<AiNovelOutline> existing = outlineMapper.selectAiNovelOutlineListByWorkId(workId);

        JsonNode data = agentClient.generateNovelOutline(
                workService.buildNovelWorkContext(workId, null),
                buildChapterPayload(chapters),
                buildExistingTree(existing));

        NovelOutlineGeneratedDTO generated = parseResult(data);
        if (generated.getTree() == null || generated.getTree().isEmpty())
        {
            throw new ServiceException("AI 返回了大纲为空，未保存任何改动");
        }

        outlineMapper.deleteAiNovelOutlineByWorkIds(new Long[] { workId });
        Map<Integer, Long> chapterIdByNo = indexChapters(chapters);
        int saved = saveTree(workId, generated.getTree(), chapterIdByNo);
        log.info("AI 小说大纲生成完成，workId={}，保存节点数={}，断链报告数={}",
                workId, saved, generated.getGaps() == null ? 0 : generated.getGaps().size());
        return generated;
    }

    /** 把现有平铺大纲组装成树，供模型参考（不携带 ID）。 */
    private List<NovelOutlineNodeDTO> buildExistingTree(List<AiNovelOutline> outlines)
    {
        List<NovelOutlineNodeDTO> tree = new ArrayList<>();
        for (AiNovelOutline outline : outlines)
        {
            NovelOutlineNodeDTO node = new NovelOutlineNodeDTO();
            node.setLevel(outline.getOutlineLevel());
            node.setTitle(outline.getOutlineTitle());
            node.setContent(outline.getOutlineContent());
            if (LEVEL_CHAPTER.equals(outline.getOutlineLevel()))
            {
                node.setChapterNo(outline.getChapterNo());
            }
            tree.add(node);
        }
        return tree;
    }

    /** 章节列表载荷：只携带生成大纲所需的编号/标题/梗概。 */
    private List<Map<String, Object>> buildChapterPayload(List<AiNovelChapter> chapters)
    {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (AiNovelChapter chapter : chapters)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapterNo", chapter.getChapterNo());
            item.put("chapterTitle", chapter.getChapterTitle());
            item.put("chapterBrief", chapter.getChapterBrief());
            payload.add(item);
        }
        return payload;
    }

    /** 解析 Python 响应为生成结果 DTO。 */
    private NovelOutlineGeneratedDTO parseResult(JsonNode data)
    {
        try
        {
            return objectMapper.treeToValue(data, NovelOutlineGeneratedDTO.class);
        }
        catch (Exception e)
        {
            log.error("解析大纲生成结果失败，errorType={}", e.getClass().getSimpleName());
            throw new ServiceException("AI 返回的大纲格式不合法");
        }
    }

    /** 章节号 → 章节ID 索引。 */
    private Map<Integer, Long> indexChapters(List<AiNovelChapter> chapters)
    {
        Map<Integer, Long> index = new HashMap<>();
        for (AiNovelChapter chapter : chapters)
        {
            if (chapter.getChapterNo() != null)
            {
                index.put(chapter.getChapterNo(), chapter.getChapterId());
            }
        }
        return index;
    }

    /** 递归保存大纲树，返回保存节点数。 */
    private int saveTree(Long workId, List<NovelOutlineNodeDTO> nodes,
            Map<Integer, Long> chapterIdByNo)
    {
        int saved = 0;
        for (NovelOutlineNodeDTO node : nodes)
        {
            AiNovelOutline outline = new AiNovelOutline();
            outline.setWorkId(workId);
            outline.setOutlineLevel(node.getLevel());
            outline.setOutlineTitle(node.getTitle());
            outline.setOutlineContent(node.getContent());
            if (node.getChapterNo() != null)
            {
                outline.setChapterId(chapterIdByNo.get(node.getChapterNo()));
            }
            outline.setCreateBy("ai-outline-generator");
            outline.setCreateTime(DateUtils.getNowDate());
            outlineMapper.insertAiNovelOutline(outline);
            saved++;
            if (node.getChildren() != null && !node.getChildren().isEmpty())
            {
                saved += saveTreeChildren(workId, outline.getOutlineId(), node.getChildren(),
                        chapterIdByNo);
            }
        }
        return saved;
    }

    /** 保存子节点并把父级ID写回。 */
    private int saveTreeChildren(Long workId, Long parentId, List<NovelOutlineNodeDTO> children,
            Map<Integer, Long> chapterIdByNo)
    {
        int saved = 0;
        int seqNo = 1;
        for (NovelOutlineNodeDTO node : children)
        {
            AiNovelOutline outline = new AiNovelOutline();
            outline.setWorkId(workId);
            outline.setOutlineLevel(node.getLevel());
            outline.setParentId(parentId);
            outline.setSeqNo(seqNo++);
            outline.setOutlineTitle(node.getTitle());
            outline.setOutlineContent(node.getContent());
            if (node.getChapterNo() != null)
            {
                outline.setChapterId(chapterIdByNo.get(node.getChapterNo()));
            }
            outline.setCreateBy("ai-outline-generator");
            outline.setCreateTime(DateUtils.getNowDate());
            outlineMapper.insertAiNovelOutline(outline);
            saved++;
            if (node.getChildren() != null && !node.getChildren().isEmpty())
            {
                saved += saveTreeChildren(workId, outline.getOutlineId(), node.getChildren(),
                        chapterIdByNo);
            }
        }
        return saved;
    }

    private boolean isValidLevel(String level)
    {
        return LEVEL_BOOK.equals(level) || LEVEL_VOLUME.equals(level) || LEVEL_CHAPTER.equals(level);
    }

    private void requireParent(Long workId, AiNovelOutline outline)
    {
        Long parentId = outline.getParentId() == null ? 0L : outline.getParentId();
        outline.setParentId(parentId);
        if (parentId == 0L)
        {
            if (!LEVEL_BOOK.equals(outline.getOutlineLevel()))
            {
                throw new ServiceException("非全书层大纲必须指定父级");
            }
            return;
        }
        AiNovelOutline parent = requireOutline(workId, parentId);
        boolean valid = (LEVEL_BOOK.equals(parent.getOutlineLevel())
                && LEVEL_VOLUME.equals(outline.getOutlineLevel()))
                || (LEVEL_VOLUME.equals(parent.getOutlineLevel())
                && LEVEL_CHAPTER.equals(outline.getOutlineLevel()));
        if (!valid)
        {
            throw new ServiceException("大纲层级与父级不匹配");
        }
    }

    private AiNovelOutline requireOutline(Long workId, Long outlineId)
    {
        if (outlineId == null)
        {
            throw new ServiceException("大纲ID不能为空");
        }
        AiNovelOutline outline = outlineMapper.selectAiNovelOutlineByOutlineId(outlineId);
        if (outline == null || !outline.getWorkId().equals(workId))
        {
            throw new ServiceException("大纲节点不存在或无权访问");
        }
        return outline;
    }
}
