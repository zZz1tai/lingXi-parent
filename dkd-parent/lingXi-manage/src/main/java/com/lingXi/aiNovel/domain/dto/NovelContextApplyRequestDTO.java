package com.lingXi.aiNovel.domain.dto;

import java.util.List;
import lombok.Data;

/** 用户确认并申请写回的章节资料变更。 */
@Data
public class NovelContextApplyRequestDTO
{
    private Long chapterId;
    /** 分析时正文的 SHA-256，用于拒绝过期建议。 */
    private String contentHash;
    /** 仅包含用户在确认框中勾选的建议。 */
    private List<NovelContextChangeDTO> changes;
}
