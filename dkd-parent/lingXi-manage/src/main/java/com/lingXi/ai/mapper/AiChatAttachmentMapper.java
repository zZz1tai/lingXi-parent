package com.lingXi.ai.mapper;

import com.lingXi.ai.domain.AiChatAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** AI 聊天附件元数据 Mapper。 */
@Mapper
public interface AiChatAttachmentMapper {
    int insert(AiChatAttachment attachment);

    AiChatAttachment selectOwned(
            @Param("attachmentId") String attachmentId,
            @Param("sessionId") String sessionId,
            @Param("userId") String userId);

    List<AiChatAttachment> selectOwnedByIds(
            @Param("attachmentIds") List<String> attachmentIds,
            @Param("sessionId") String sessionId,
            @Param("userId") String userId);

    List<AiChatAttachment> selectByHistoryIds(
            @Param("historyIds") List<Long> historyIds,
            @Param("userId") String userId);

    List<AiChatAttachment> selectBySession(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId);

    int bindToHistory(
            @Param("attachmentIds") List<String> attachmentIds,
            @Param("sessionId") String sessionId,
            @Param("userId") String userId,
            @Param("historyId") Long historyId);

    int deletePendingOwned(
            @Param("attachmentId") String attachmentId,
            @Param("sessionId") String sessionId,
            @Param("userId") String userId);

    int deleteBySession(
            @Param("sessionId") String sessionId,
            @Param("userId") String userId);
}
