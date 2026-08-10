package com.lingXi.ai.service;

import java.util.List;
import com.lingXi.manage.domain.ChatSession;

/**
 * 聊天会话Service接口
 * 
 * @author system
 * @date 2025-12-04
 */
public interface IChatSessionService 
{
    /**
     * 查询聊天会话
     * 
     * @param id 聊天会话主键
     * @return 聊天会话
     */
    public ChatSession selectChatSessionById(Long id);

    /**
     * 查询聊天会话列表
     * 
     * @param chatSession 聊天会话
     * @return 聊天会话集合
     */
    public List<ChatSession> selectChatSessionList(ChatSession chatSession);

    /**
     * 查询指定用户的聊天会话列表
     * 
     * @param userId 用户唯一标识
     * @return 聊天会话集合
     */
    public List<ChatSession> selectChatSessionByUserId(String userId);

    /**
     * 根据会话ID查询聊天会话
     * 
     * @param sessionId 会话唯一标识
     * @return 聊天会话
     */
    public ChatSession selectChatSessionBySessionId(String sessionId);

    /**
     * 新增聊天会话
     * 
     * @param chatSession 聊天会话
     * @return 结果
     */
    public int insertChatSession(ChatSession chatSession);
    
    /**
     * 新增聊天会话
     * 
     * @param userId 用户ID
     * @return 聊天会话对象
     */
    public ChatSession insertChatSession(String userId);

    /**
     * 修改聊天会话
     * 
     * @param chatSession 聊天会话
     * @return 结果
     */
    public int updateChatSession(ChatSession chatSession);

    /**
     * 删除聊天会话
     * 
     * @param id 聊天会话主键
     * @return 结果
     */
    public int deleteChatSessionById(Long id);

    /**
     * 根据会话ID删除聊天会话
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    public int deleteChatSessionBySessionId(String sessionId);

    /**
     * 批量删除聊天会话
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteChatSessionByIds(Long[] ids);

    /**
     * 批量删除聊天会话及关联的历史记录
     * 
     * @param sessionIds 需要删除的会话ID集合
     * @return 结果
     */
    public int deleteChatSessionAndHistoryBySessionIds(String[] sessionIds);

    /**
     * 删除指定会话的聊天会话及历史记录
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    public int deleteChatSessionAndHistoryBySessionId(String sessionId);

    /**
     * 将会话标记为删除中，拒绝新消息（仅 ACTIVE 可流转）。
     * 
     * @param sessionId 会话唯一标识
     * @return 是否成功标记
     */
    public boolean markChatSessionDeleting(String sessionId);

    /**
     * 删除失败时将会话恢复为正常状态（仅 DELETING 可恢复）。
     * 
     * @param sessionId 会话唯一标识
     * @return 是否成功恢复
     */
    public boolean restoreChatSessionActive(String sessionId);
}