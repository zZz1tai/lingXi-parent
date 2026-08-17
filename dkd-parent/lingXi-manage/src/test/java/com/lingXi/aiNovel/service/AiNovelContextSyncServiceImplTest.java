package com.lingXi.aiNovel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.ai.client.AgentClient;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelForeshadow;
import com.lingXi.aiNovel.domain.AiNovelSetting;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.domain.dto.NovelContextAgentRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextAnalyzeRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyResultDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextChangeDTO;
import com.lingXi.aiNovel.service.impl.AiNovelContextSyncServiceImpl;
import com.lingXi.common.exception.ServiceException;

/** 章节资料同步的归属、过期校验与人工确认写回测试。 */
@ExtendWith(MockitoExtension.class)
class AiNovelContextSyncServiceImplTest
{
    @Mock private IAiNovelWorkService workService;
    @Mock private IAiNovelChapterService chapterService;
    @Mock private IAiNovelSettingService settingService;
    @Mock private IAiNovelForeshadowService foreshadowService;
    @Mock private AgentClient agentClient;

    private AiNovelContextSyncServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new AiNovelContextSyncServiceImpl(
                workService, chapterService, settingService, foreshadowService, agentClient);
    }

    @Test
    void analyzeBuildsOwnedSnapshotAndReturnsContentHash() throws Exception
    {
        AiNovelWork work = work();
        AiNovelChapter chapter = chapter("江离认出断手镯属于失踪的姐姐。");
        AiNovelSetting setting = setting(11L);
        AiNovelForeshadow foreshadow = foreshadow(22L);
        when(workService.checkWorkOwner(7L)).thenReturn(work);
        when(chapterService.selectAiNovelChapterByChapterId(7L, 31L)).thenReturn(chapter);
        when(settingService.selectAiNovelSettingList(7L, null)).thenReturn(List.of(setting));
        when(foreshadowService.selectAiNovelForeshadowList(7L, null))
                .thenReturn(List.of(foreshadow));
        ObjectNode agentData = new ObjectMapper().createObjectNode();
        agentData.put("chapterBrief", "江离在井底认出姐姐的断手镯，确认姐姐曾到过此处，并决定继续追查她的去向；本章结束时，他掌握了新的实物线索。");
        agentData.putArray("changes");
        when(agentClient.analyzeNovelContext(any())).thenReturn(agentData);
        when(chapterService.updateChapterBriefIfContentHashMatches(
                eq(7L), eq(31L), any(), any())).thenReturn(1);

        NovelContextAnalyzeRequestDTO request = new NovelContextAnalyzeRequestDTO();
        request.setChapterId(31L);
        ObjectNode result = (ObjectNode) service.analyze(7L, request);

        assertEquals(sha256(chapter.getContent()), result.path("contentHash").asText());
        assertEquals(31L, result.path("chapterId").asLong());
        ArgumentCaptor<NovelContextAgentRequestDTO> captor =
                ArgumentCaptor.forClass(NovelContextAgentRequestDTO.class);
        verify(agentClient).analyzeNovelContext(captor.capture());
        assertEquals(11L, captor.getValue().getSettings().get(0).getSettingId());
        assertEquals(22L, captor.getValue().getForeshadows().get(0).getForeshadowId());
        verify(chapterService).updateChapterBriefIfContentHashMatches(
                eq(7L), eq(31L), eq(sha256(chapter.getContent())),
                eq(agentData.path("chapterBrief").asText()));
        assertTrue(result.path("chapterBriefSaved").asBoolean());
    }

    @Test
    void analyzeDoesNotSaveBriefWhenChapterChangesDuringModelCall()
    {
        AiNovelChapter sourceChapter = chapter("正文版本一");
        when(workService.checkWorkOwner(7L)).thenReturn(work());
        when(chapterService.selectAiNovelChapterByChapterId(7L, 31L))
                .thenReturn(sourceChapter);
        when(settingService.selectAiNovelSettingList(7L, null)).thenReturn(List.of());
        when(foreshadowService.selectAiNovelForeshadowList(7L, null)).thenReturn(List.of());
        ObjectNode agentData = new ObjectMapper().createObjectNode();
        agentData.put("chapterBrief", "这是根据正文版本一生成的旧摘要，正文已经变化，因此这份摘要绝对不能覆盖更新后的章节事实。内容补足到有效长度。");
        agentData.putArray("changes");
        when(agentClient.analyzeNovelContext(any())).thenReturn(agentData);
        when(chapterService.updateChapterBriefIfContentHashMatches(
                eq(7L), eq(31L), any(), any())).thenReturn(0);
        NovelContextAnalyzeRequestDTO request = new NovelContextAnalyzeRequestDTO();
        request.setChapterId(31L);

        ObjectNode result = (ObjectNode) service.analyze(7L, request);

        assertFalse(result.path("chapterBriefSaved").asBoolean());
    }

    @Test
    void applyRejectsSuggestionsWhenChapterChanged()
    {
        AiNovelChapter chapter = chapter("已经修改的新正文");
        when(chapterService.selectAiNovelChapterByChapterId(7L, 31L)).thenReturn(chapter);
        NovelContextApplyRequestDTO request = new NovelContextApplyRequestDTO();
        request.setChapterId(31L);
        request.setContentHash("0".repeat(64));
        request.setChanges(List.of(settingUpdate(11L)));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.apply(7L, request));

        assertTrue(error.getMessage().contains("正文已发生变化"));
    }

    @Test
    void applyWritesOnlyConfirmedAddAndUpdate()
    {
        AiNovelChapter chapter = chapter("正文版本一");
        AiNovelSetting setting = setting(11L);
        when(chapterService.selectAiNovelChapterByChapterId(7L, 31L)).thenReturn(chapter);
        when(settingService.selectAiNovelSettingList(7L, null)).thenReturn(List.of(setting));
        when(foreshadowService.selectAiNovelForeshadowList(7L, null)).thenReturn(List.of());
        when(settingService.updateAiNovelSetting(eq(7L), any())).thenReturn(1);
        when(foreshadowService.insertAiNovelForeshadow(eq(7L), any())).thenReturn(1);
        NovelContextChangeDTO addForeshadow = new NovelContextChangeDTO();
        addForeshadow.setResourceType("foreshadow");
        addForeshadow.setOperation("ADD");
        addForeshadow.setTitle("姐姐的去向");
        addForeshadow.setDescription("断手镯证明姐姐曾到过井底。");
        addForeshadow.setStatus("buried");
        addForeshadow.setPriority("high");
        addForeshadow.setEvidence("断手镯属于失踪的姐姐");
        addForeshadow.setReason("形成后续可回收的新线索");
        NovelContextApplyRequestDTO request = new NovelContextApplyRequestDTO();
        request.setChapterId(31L);
        request.setContentHash(sha256(chapter.getContent()));
        request.setChanges(List.of(settingUpdate(11L), addForeshadow));

        NovelContextApplyResultDTO result = service.apply(7L, request);

        assertEquals(2, result.getAffected());
        assertEquals(1, result.getSettings());
        assertEquals(1, result.getForeshadows());
        verify(settingService).updateAiNovelSetting(eq(7L), any(AiNovelSetting.class));
        verify(foreshadowService).insertAiNovelForeshadow(eq(7L), any(AiNovelForeshadow.class));
    }

    @Test
    void applyRejectsDeleteEvenIfClientCraftsIt()
    {
        AiNovelChapter chapter = chapter("正文版本一");
        when(chapterService.selectAiNovelChapterByChapterId(7L, 31L)).thenReturn(chapter);
        when(settingService.selectAiNovelSettingList(7L, null)).thenReturn(List.of(setting(11L)));
        when(foreshadowService.selectAiNovelForeshadowList(7L, null)).thenReturn(List.of());
        NovelContextChangeDTO change = settingUpdate(11L);
        change.setOperation("DELETE");
        NovelContextApplyRequestDTO request = new NovelContextApplyRequestDTO();
        request.setChapterId(31L);
        request.setContentHash(sha256(chapter.getContent()));
        request.setChanges(List.of(change));

        ServiceException error = assertThrows(
                ServiceException.class, () -> service.apply(7L, request));

        assertTrue(error.getMessage().contains("操作无效"));
    }

    private static AiNovelWork work()
    {
        AiNovelWork work = new AiNovelWork();
        work.setWorkId(7L);
        work.setWorkName("雾隐城");
        work.setWorkType("novel");
        return work;
    }

    private static AiNovelChapter chapter(String content)
    {
        AiNovelChapter chapter = new AiNovelChapter();
        chapter.setChapterId(31L);
        chapter.setWorkId(7L);
        chapter.setChapterNo(3);
        chapter.setChapterTitle("井底来客");
        chapter.setContent(content);
        return chapter;
    }

    private static AiNovelSetting setting(Long id)
    {
        AiNovelSetting setting = new AiNovelSetting();
        setting.setSettingId(id);
        setting.setWorkId(7L);
        setting.setSettingType("character");
        setting.setTitle("江离");
        setting.setContent("少年剑客。");
        return setting;
    }

    private static AiNovelForeshadow foreshadow(Long id)
    {
        AiNovelForeshadow item = new AiNovelForeshadow();
        item.setForeshadowId(id);
        item.setWorkId(7L);
        item.setTitle("断手镯");
        item.setStatus("pending");
        item.setPriority("high");
        return item;
    }

    private static NovelContextChangeDTO settingUpdate(Long id)
    {
        NovelContextChangeDTO change = new NovelContextChangeDTO();
        change.setResourceType("setting");
        change.setOperation("UPDATE");
        change.setTargetId(id);
        change.setSettingType("character");
        change.setTitle("江离");
        change.setContent("少年剑客，确认断手镯属于失踪的姐姐。");
        change.setEvidence("江离认出断手镯属于失踪的姐姐");
        change.setReason("补充人物已确认的信息");
        return change;
    }

    private static String sha256(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception)
        {
            throw new AssertionError(exception);
        }
    }
}
