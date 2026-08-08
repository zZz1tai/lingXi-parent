package com.lingXi.aiNovel.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.core.page.TableDataInfo;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelSetting;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.service.IAiNovelChapterService;
import com.lingXi.aiNovel.service.IAiNovelSettingService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * AI 小说作品管理控制器
 * <p>提供作品、章节、设定卡的增删改查与短篇正文保存接口，
 * 所有数据按当前登录用户隔离。</p>
 */
@Tag(name = "AI小说作品管理")
@RestController
@RequestMapping("/novel/work")
public class NovelWorkController extends BaseController
{
    @Autowired
    private IAiNovelWorkService workService;

    @Autowired
    private IAiNovelChapterService chapterService;

    @Autowired
    private IAiNovelSettingService settingService;

    // ── 作品 ──────────────────────────────────────────

    /** 获取作品列表。 */
    @Operation(summary = "获取小说作品列表")
    @PreAuthorize("@ss.hasPermi('novel:work:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiNovelWork work)
    {
        startPage();
        List<AiNovelWork> list = workService.selectAiNovelWorkList(work);
        return getDataTable(list);
    }

    /** 获取作品详情。 */
    @Operation(summary = "获取小说作品详情")
    @PreAuthorize("@ss.hasPermi('novel:work:list')")
    @GetMapping("/{workId}")
    public AjaxResult getInfo(@PathVariable Long workId)
    {
        return success(workService.selectAiNovelWorkByWorkId(workId));
    }

    /** 新增作品。 */
    @Operation(summary = "新增小说作品")
    @PreAuthorize("@ss.hasPermi('novel:work:add')")
    @Log(title = "AI小说作品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiNovelWork work)
    {
        return toAjax(workService.insertAiNovelWork(work));
    }

    /** 更新作品。 */
    @Operation(summary = "更新小说作品")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说作品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiNovelWork work)
    {
        return toAjax(workService.updateAiNovelWork(work));
    }

    /** 删除作品（级联删除章节与设定卡）。 */
    @Operation(summary = "删除小说作品")
    @PreAuthorize("@ss.hasPermi('novel:work:remove')")
    @Log(title = "AI小说作品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workIds}")
    public AjaxResult remove(@PathVariable Long[] workIds)
    {
        return toAjax(workService.deleteAiNovelWorkByWorkIds(workIds));
    }

    // ── 章节（长篇） ──────────────────────────────────

    /** 获取作品章节列表。 */
    @Operation(summary = "获取小说章节列表")
    @PreAuthorize("@ss.hasPermi('novel:work:list')")
    @GetMapping("/{workId}/chapter/list")
    public TableDataInfo chapterList(@PathVariable Long workId)
    {
        startPage();
        List<AiNovelChapter> list = chapterService.selectAiNovelChapterList(workId);
        return getDataTable(list);
    }

    /** 获取章节详情。 */
    @Operation(summary = "获取小说章节详情")
    @PreAuthorize("@ss.hasPermi('novel:work:list')")
    @GetMapping("/{workId}/chapter/{chapterId}")
    public AjaxResult chapterInfo(@PathVariable Long workId, @PathVariable Long chapterId)
    {
        return success(chapterService.selectAiNovelChapterByChapterId(workId, chapterId));
    }

    /** 新增章节。 */
    @Operation(summary = "新增小说章节")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说章节", businessType = BusinessType.INSERT)
    @PostMapping("/{workId}/chapter")
    public AjaxResult addChapter(@PathVariable Long workId, @RequestBody AiNovelChapter chapter)
    {
        return toAjax(chapterService.insertAiNovelChapter(workId, chapter));
    }

    /** 更新章节（含正文与梗概）。 */
    @Operation(summary = "更新小说章节")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说章节", businessType = BusinessType.UPDATE)
    @PutMapping("/{workId}/chapter")
    public AjaxResult editChapter(@PathVariable Long workId, @RequestBody AiNovelChapter chapter)
    {
        return toAjax(chapterService.updateAiNovelChapter(workId, chapter));
    }

    /** 删除章节。 */
    @Operation(summary = "删除小说章节")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workId}/chapter/{chapterId}")
    public AjaxResult removeChapter(@PathVariable Long workId, @PathVariable Long chapterId)
    {
        return toAjax(chapterService.deleteAiNovelChapter(workId, chapterId));
    }

    /** 按给定顺序重排章节。 */
    @Operation(summary = "重排小说章节")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说章节", businessType = BusinessType.UPDATE)
    @PutMapping("/{workId}/chapter/sort")
    public AjaxResult sortChapter(
            @PathVariable Long workId, @RequestBody Map<String, List<Long>> body)
    {
        return toAjax(chapterService.sortAiNovelChapter(workId, body.get("chapterIds")));
    }

    // ── 设定集 ────────────────────────────────────────

    /** 获取作品设定卡列表，可按类型过滤。 */
    @Operation(summary = "获取小说设定卡列表")
    @PreAuthorize("@ss.hasPermi('novel:work:list')")
    @GetMapping("/{workId}/setting/list")
    public TableDataInfo settingList(
            @PathVariable Long workId,
            @RequestParam(value = "type", required = false) String type)
    {
        startPage();
        List<AiNovelSetting> list = settingService.selectAiNovelSettingList(workId, type);
        return getDataTable(list);
    }

    /** 新增设定卡。 */
    @Operation(summary = "新增小说设定卡")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说设定卡", businessType = BusinessType.INSERT)
    @PostMapping("/{workId}/setting")
    public AjaxResult addSetting(@PathVariable Long workId, @RequestBody AiNovelSetting setting)
    {
        return toAjax(settingService.insertAiNovelSetting(workId, setting));
    }

    /** 更新设定卡。 */
    @Operation(summary = "更新小说设定卡")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说设定卡", businessType = BusinessType.UPDATE)
    @PutMapping("/{workId}/setting")
    public AjaxResult editSetting(@PathVariable Long workId, @RequestBody AiNovelSetting setting)
    {
        return toAjax(settingService.updateAiNovelSetting(workId, setting));
    }

    /** 删除设定卡。 */
    @Operation(summary = "删除小说设定卡")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说设定卡", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workId}/setting/{settingId}")
    public AjaxResult removeSetting(@PathVariable Long workId, @PathVariable Long settingId)
    {
        return toAjax(settingService.deleteAiNovelSetting(workId, settingId));
    }

    // ── 短篇正文 ──────────────────────────────────────

    /** 保存短篇正文。 */
    @Operation(summary = "保存小说短篇正文")
    @PreAuthorize("@ss.hasPermi('novel:work:edit')")
    @Log(title = "AI小说正文", businessType = BusinessType.UPDATE)
    @PutMapping("/{workId}/manuscript")
    public AjaxResult saveManuscript(
            @PathVariable Long workId, @RequestBody Map<String, String> body)
    {
        return toAjax(workService.updateAiNovelWorkManuscript(workId, body.get("content")));
    }
}
