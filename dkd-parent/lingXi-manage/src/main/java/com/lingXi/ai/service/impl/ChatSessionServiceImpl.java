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
 * 聊天会话 Service 业务层处理
 *
 * @author system
 * @date 2025-12-04
 */
@Service
public class ChatSessionServiceImpl implements IChatSessionService {
    /** 会话数据访问接口。 */
    @Autowired
    private ChatSessionMapper chatSessionMapper;

    /** 对话历史服务，用于删除会话时同步清理关联消息。 */
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
        // 新会话使用统一默认名称，客户端可在首次对话后再发起改名。
        ChatSession chatSession = new ChatSession();
        chatSession.setUserId(userId);
        chatSession.setSessionName("新会话");
        // 时间戳结合随机尾数，生成便于排查且低碰撞的外部会话ID。
        String sessionId = "session_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        chatSession.setSessionId(sessionId);
        
        // 复用实体新增方法，统一补齐创建和更新时间。
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
        // 同一用户下不允许出现重复会话名称。
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
        // 逐个复用单会话删除流程，确保每个会话都同步清理历史记录。
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
        // 先删除关联历史，再删除会话主体，避免留下无法归属的消息记录。
        int historyResult = modelHistoryService.deleteModelHistoryBySessionId(sessionId);
        int sessionResult = chatSessionMapper.deleteChatSessionBySessionId(sessionId);
        return historyResult + sessionResult;
    }
}
