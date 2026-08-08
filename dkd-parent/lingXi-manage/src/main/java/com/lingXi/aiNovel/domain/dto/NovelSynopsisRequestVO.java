package com.lingXi.aiNovel.domain.dto;

import lombok.Data;

/**
 * 「AI 自动拟写故事梗概」请求视图对象。
 * <p>新建作品表单填好书名后，由 Java 转发给 Python 直接调用 LLM 生成，
 * 不进入创作智能体会话，也不写入任何历史记录。</p>
 */
@Data
public class NovelSynopsisRequestVO {

    /** 书名（必填） */
    private String workName;

    /** 作品类型：short/novel */
    private String workType;

    /** 题材类型，可空 */
    private String genre;
}
