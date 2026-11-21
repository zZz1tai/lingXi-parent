package com.lingXi.aiNovel.service;

import com.lingXi.aiNovel.mapper.NovelSessionMapper;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.ModelHistory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NovelSessionService {
    private final NovelSessionMapper mapper;

    public NovelSessionService(NovelSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public long startTurn(String sessionId, String userId, String title, String username, String message) {
        mapper.ensureSession(sessionId, userId, title == null || title.isBlank() ? "小说创作" : title);
        long id = requireSession(sessionId, userId);
        if (mapper.append(id, username, message, "user") != 1) {
            throw new ServiceException("用户消息持久化失败");
        }
        return id;
    }

    public long requireSession(String sessionId, String userId) {
        Long id = mapper.findSession(sessionId, userId);
        if (id == null) {
            throw new ServiceException("小说会话不存在或无权访问");
        }
        return id;
    }

    public List<ModelHistory> history(String sessionId, String userId) {
        return mapper.history(userId, sessionId);
    }

    public void saveReply(long id, String username, String reply) {
        // 原会话被删除后，迟到的回复不得写入复用外部 ID 的新会话。
        mapper.append(id, username, reply, "assistant");
    }

    @Transactional
    public boolean delete(long id, String sessionId, String userId) {
        if (mapper.deleteSession(userId, id) == 0) {
            return false;
        }
        mapper.deleteHistory(userId, sessionId);
        return true;
    }
}
