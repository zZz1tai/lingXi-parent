package com.lingXi.aiNovel.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 章节资料建议事务写回结果。 */
@Data
@AllArgsConstructor
public class NovelContextApplyResultDTO
{
    private int affected;
    private int settings;
    private int foreshadows;
}
