package com.lingXi.ai.mapper;

import java.util.List;
import com.lingXi.manage.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话Mapper接口
 * 
 * @author system
 * @date 2025-12-04
 */
@Mapper
public interface ChatSessionMapper 
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
     * 将会话标记为删除中（仅 ACTIVE 可流转，防并发重复标记）。
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    public int markChatSessionDeleting(String sessionId);

    /**
     * 删除失败时将会话恢复为正常状态（仅 DELETING 可恢复）。
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    public int restoreChatSessionActive(String sessionId);

    /**
     * 批量删除聊天会话
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteChatSessionByIds(Long[] ids);
    
    /**
     * 检查会话名称是否唯一
     * 
     * @param chatSession 聊天会话
     * @return 结果
     */
    public int checkSessionNameUnique(ChatSession chatSession);
}