package com.lingXi.ai.service.impl;

import java.util.List;
import com.lingXi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.ai.mapper.ChatSessionMapper;
import com.lingXi.manage.domain.ChatSession;
import com.lingXi.ai.service.IChatSessionService;
import com.lingXi.manage.service.IModelHistoryService;

/**
 * 聊天会话Service业务层处理
 * 
 * @author system
 * @date 2025-12-04
 */
@Service
public class ChatSessionServiceImpl implements IChatSessionService {
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    
    @Autowired
    private IModelHistoryService modelHistoryService;

    /**
     * 查询聊天会话
     * 
     * @param id 聊天会话主键
     * @return 聊天会话
     */
    @Override
    public ChatSession selectChatSessionById(Long id) {
        return chatSessionMapper.selectChatSessionById(id);
    }

    /**
     * 查询聊天会话列表
     * 
     * @param chatSession 聊天会话
     * @return 聊天会话集合
     */
    @Override
    public List<ChatSession> selectChatSessionList(ChatSession chatSession) {
        return chatSessionMapper.selectChatSessionList(chatSession);
    }

    /**
     * 查询指定用户的聊天会话列表
     * 
     * @param userId 用户唯一标识
     * @return 聊天会话集合
     */
    @Override
    public List<ChatSession> selectChatSessionByUserId(String userId) {
        return chatSessionMapper.selectChatSessionByUserId(userId);
    }

    /**
     * 根据会话ID查询聊天会话
     * 
     * @param sessionId 会话唯一标识
     * @return 聊天会话
     */
    @Override
    public ChatSession selectChatSessionBySessionId(String sessionId) {
        return chatSessionMapper.selectChatSessionBySessionId(sessionId);
    }

    /**
     * 新增聊天会话
     * 
     * @param chatSession 聊天会话
     * @return 结果
     */
    @Override
    public int insertChatSession(ChatSession chatSession) {
        chatSession.setCreateTime(DateUtils.getNowDate());
        chatSession.setUpdateTime(DateUtils.getNowDate());
        return chatSessionMapper.insertChatSession(chatSession);
    }

    /**
     * 新增聊天会话
     * 
     * @param userId 用户ID
     * @return 聊天会话对象
     */
    @Override
    public ChatSession insertChatSession(String userId) {
        // 创建新的会话对象
        ChatSession chatSession = new ChatSession();
        chatSession.setUserId(userId);
        chatSession.setSessionName("新会话");
        // 生成唯一会话ID
        String sessionId = "session_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        chatSession.setSessionId(sessionId);
        
        // 保存会话
        this.insertChatSession(chatSession);
        return chatSession;
    }

    /**
     * 修改聊天会话
     * 
     * @param chatSession 聊天会话
     * @return 结果
     */
    @Override
    public int updateChatSession(ChatSession chatSession) {
        // 检查会话名称是否唯一
        int count = chatSessionMapper.checkSessionNameUnique(chatSession);
        if (count > 0) {
            throw new RuntimeException("会话名称已存在");
        }
        chatSession.setUpdateTime(DateUtils.getNowDate());
        return chatSessionMapper.updateChatSession(chatSession);
    }

    /**
     * 删除聊天会话
     * 
     * @param id 聊天会话主键
     * @return 结果
     */
    @Override
    public int deleteChatSessionById(Long id) {
        return chatSessionMapper.deleteChatSessionById(id);
    }

    /**
     * 根据会话ID删除聊天会话
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    @Override
    public int deleteChatSessionBySessionId(String sessionId) {
        return chatSessionMapper.deleteChatSessionBySessionId(sessionId);
    }

    /**
     * 批量删除聊天会话
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    @Override
    public int deleteChatSessionByIds(Long[] ids) {
        return chatSessionMapper.deleteChatSessionByIds(ids);
    }

    /**
     * 批量删除聊天会话及关联的历史记录
     * 
     * @param sessionIds 需要删除的会话ID集合
     * @return 结果
     */
    @Override
    public int deleteChatSessionAndHistoryBySessionIds(String[] sessionIds) {
        int result = 0;
        for (String sessionId : sessionIds) {
            result += deleteChatSessionAndHistoryBySessionId(sessionId);
        }
        return result;
    }

    /**
     * 删除指定会话的聊天会话及历史记录
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    @Override
    public int deleteChatSessionAndHistoryBySessionId(String sessionId) {
        // 删除关联的历史记录
        int historyResult = modelHistoryService.deleteModelHistoryBySessionId(sessionId);
        // 删除会话
        int sessionResult = chatSessionMapper.deleteChatSessionBySessionId(sessionId);
        return historyResult + sessionResult;
    }
}