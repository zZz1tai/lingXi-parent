package com.lingXi.aiNovel.service;

import java.util.List;
import tools.jackson.databind.JsonNode;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.domain.dto.NovelIdeaDocVO;
import com.lingXi.aiNovel.domain.dto.NovelPacingRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelWorkContextDTO;

/**
 * AI 小说作品服务接口，提供作品增删改查、归属校验与智能体上下文组装。
 */
public interface IAiNovelWorkService
{
    /** 根据作品ID查询作品，并校验当前用户为作品所有者。 */
    AiNovelWork selectAiNovelWorkByWorkId(Long workId);

    /** 查询当前用户拥有的作品列表。 */
    List<AiNovelWork> selectAiNovelWorkList(AiNovelWork work);

    /** 新增作品。 */
    int insertAiNovelWork(AiNovelWork work);

    /** 更新作品。 */
    int updateAiNovelWork(AiNovelWork work);

    /** 更新作品正文（短篇）。 */
    int updateAiNovelWorkManuscript(Long workId, String content);

    /** 批量删除作品及其章节、设定卡。 */
    int deleteAiNovelWorkByWorkIds(Long[] workIds);

    /** 校验当前用户是否为作品所有者，不存在或无权时抛出异常。 */
    AiNovelWork checkWorkOwner(Long workId);

    /** 组装提交给创作智能体的作品上下文。 */
    NovelWorkContextDTO buildNovelWorkContext(Long workId, Long chapterId);

    /** 导出作品全文：长篇按章节顺序拼接标题与正文，短篇取整篇正文。 */
    String exportWorkText(Long workId);

    /** 分析章节节奏（评分/档位/维度/问题建议），转发给 Python 节奏分析链。 */
    JsonNode analyzeChapterPacing(NovelPacingRequestDTO request);

    /** 由构思文档一键开书：创建作品并生成首批设定卡，返回新作品ID。 */
    Long createAiNovelWorkFromIdea(NovelIdeaDocVO idea);
}
