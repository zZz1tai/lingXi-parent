package com.lingXi.aiNovel.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelOutline;
import com.lingXi.aiNovel.domain.dto.NovelOutlineContextItemDTO;

/**
 * 从完整三层大纲中挑选与当前创作位置相关的有界上下文。
 */
public final class NovelOutlineContextSelector
{
    private static final String LEVEL_BOOK = "BOOK";
    private static final String LEVEL_VOLUME = "VOLUME";
    private static final String LEVEL_CHAPTER = "CHAPTER";
    private static final int CHAPTER_RADIUS = 2;
    private static final int INITIAL_CHAPTER_COUNT = 3;
    private static final int MAX_ITEMS = 10;
    private static final int MAX_CONTENT_CHARS = 2_000;

    private static final Comparator<AiNovelOutline> SEQUENCE_COMPARATOR = Comparator
            .comparing(AiNovelOutline::getSeqNo,
                    Comparator.nullsLast(Integer::compareTo))
            .thenComparing(AiNovelOutline::getOutlineId,
                    Comparator.nullsLast(Long::compareTo));

    private static final Comparator<AiNovelOutline> CHAPTER_COMPARATOR = Comparator
            .comparing(AiNovelOutline::getChapterNo,
                    Comparator.nullsLast(Integer::compareTo))
            .thenComparing(AiNovelOutline::getParentId,
                    Comparator.nullsLast(Long::compareTo))
            .thenComparing(SEQUENCE_COMPARATOR);

    private NovelOutlineContextSelector()
    {
    }

    /**
     * 选取一条全书总纲、相关卷纲，以及当前章前后各两条章纲。
     * <p>若当前章节尚未与章纲对齐，则按章节号挑选最近的前后章纲；
     * 新书没有当前章节时，注入开头三条章纲。</p>
     */
    public static List<NovelOutlineContextItemDTO> selectRelevant(
            List<AiNovelOutline> outlines, AiNovelChapter currentChapter)
    {
        if (outlines == null || outlines.isEmpty())
        {
            return List.of();
        }

        List<AiNovelOutline> books = nodesAtLevel(outlines, LEVEL_BOOK);
        List<AiNovelOutline> volumes = nodesAtLevel(outlines, LEVEL_VOLUME);
        List<AiNovelOutline> chapters = nodesAtLevel(outlines, LEVEL_CHAPTER);
        books.sort(SEQUENCE_COMPARATOR);
        volumes.sort(SEQUENCE_COMPARATOR);
        chapters.sort(CHAPTER_COMPARATOR);

        int currentIndex = findCurrentChapterIndex(chapters, currentChapter);
        Set<Integer> selectedChapterIndexes = selectChapterIndexes(
                chapters, currentChapter, currentIndex);
        Set<Long> selectedVolumeIds = new LinkedHashSet<>();
        for (Integer index : selectedChapterIndexes)
        {
            Long parentId = chapters.get(index).getParentId();
            if (parentId != null && parentId > 0)
            {
                selectedVolumeIds.add(parentId);
            }
        }
        if (selectedVolumeIds.isEmpty() && !volumes.isEmpty())
        {
            selectedVolumeIds.add(volumes.get(0).getOutlineId());
        }

        List<NovelOutlineContextItemDTO> result = new ArrayList<>();
        if (!books.isEmpty())
        {
            add(result, books.get(0), "global");
        }

        Long currentVolumeId = currentIndex >= 0
                ? chapters.get(currentIndex).getParentId()
                : null;
        Map<Long, AiNovelOutline> volumeById = new HashMap<>();
        for (AiNovelOutline volume : volumes)
        {
            volumeById.put(volume.getOutlineId(), volume);
        }
        for (Long volumeId : selectedVolumeIds)
        {
            AiNovelOutline volume = volumeById.get(volumeId);
            if (volume != null)
            {
                add(result, volume, volumeId.equals(currentVolumeId)
                        ? "current_volume"
                        : "related_volume");
            }
        }

        for (Integer index : selectedChapterIndexes)
        {
            AiNovelOutline outline = chapters.get(index);
            add(result, outline, chapterRelevance(outline, currentChapter, index, currentIndex));
            if (result.size() >= MAX_ITEMS)
            {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static List<AiNovelOutline> nodesAtLevel(
            List<AiNovelOutline> outlines, String level)
    {
        List<AiNovelOutline> nodes = new ArrayList<>();
        for (AiNovelOutline outline : outlines)
        {
            if (outline != null && level.equals(outline.getOutlineLevel())
                    && !isBlank(outline.getOutlineTitle()))
            {
                nodes.add(outline);
            }
        }
        return nodes;
    }

    private static int findCurrentChapterIndex(
            List<AiNovelOutline> chapters, AiNovelChapter currentChapter)
    {
        if (currentChapter == null)
        {
            return -1;
        }
        if (currentChapter.getChapterId() != null)
        {
            for (int i = 0; i < chapters.size(); i++)
            {
                if (currentChapter.getChapterId().equals(chapters.get(i).getChapterId()))
                {
                    return i;
                }
            }
        }
        if (currentChapter.getChapterNo() != null)
        {
            for (int i = 0; i < chapters.size(); i++)
            {
                if (currentChapter.getChapterNo().equals(chapters.get(i).getChapterNo()))
                {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Set<Integer> selectChapterIndexes(
            List<AiNovelOutline> chapters, AiNovelChapter currentChapter, int currentIndex)
    {
        Set<Integer> indexes = new LinkedHashSet<>();
        if (currentIndex >= 0)
        {
            int start = Math.max(0, currentIndex - CHAPTER_RADIUS);
            int end = Math.min(chapters.size() - 1, currentIndex + CHAPTER_RADIUS);
            for (int i = start; i <= end; i++)
            {
                indexes.add(i);
            }
            return indexes;
        }

        Integer currentNo = currentChapter == null ? null : currentChapter.getChapterNo();
        if (currentNo == null)
        {
            for (int i = 0; i < Math.min(INITIAL_CHAPTER_COUNT, chapters.size()); i++)
            {
                indexes.add(i);
            }
            return indexes;
        }

        List<Integer> previous = new ArrayList<>();
        List<Integer> next = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++)
        {
            Integer chapterNo = chapters.get(i).getChapterNo();
            if (chapterNo == null)
            {
                continue;
            }
            if (chapterNo < currentNo)
            {
                previous.add(i);
            }
            else if (chapterNo > currentNo)
            {
                next.add(i);
            }
        }
        int previousStart = Math.max(0, previous.size() - CHAPTER_RADIUS);
        indexes.addAll(previous.subList(previousStart, previous.size()));
        indexes.addAll(next.subList(0, Math.min(CHAPTER_RADIUS, next.size())));
        return indexes;
    }

    private static String chapterRelevance(
            AiNovelOutline outline, AiNovelChapter currentChapter,
            int index, int currentIndex)
    {
        if (index == currentIndex)
        {
            return "current_chapter";
        }
        Integer currentNo = currentChapter == null ? null : currentChapter.getChapterNo();
        Integer outlineNo = outline.getChapterNo();
        if (currentNo != null && outlineNo != null)
        {
            return outlineNo < currentNo ? "previous_chapter" : "next_chapter";
        }
        return currentIndex >= 0 && index < currentIndex
                ? "previous_chapter"
                : "planned_chapter";
    }

    private static void add(List<NovelOutlineContextItemDTO> target,
            AiNovelOutline source, String relevance)
    {
        if (target.size() >= MAX_ITEMS)
        {
            return;
        }
        NovelOutlineContextItemDTO item = new NovelOutlineContextItemDTO();
        item.setLevel(source.getOutlineLevel());
        item.setRelevance(relevance);
        item.setTitle(truncate(source.getOutlineTitle(), 128));
        item.setContent(truncate(source.getOutlineContent(), MAX_CONTENT_CHARS));
        item.setChapterNo(source.getChapterNo());
        target.add(item);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static String truncate(String value, int maxChars)
    {
        if (isBlank(value))
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxChars
                ? normalized
                : normalized.substring(0, maxChars);
    }
}
