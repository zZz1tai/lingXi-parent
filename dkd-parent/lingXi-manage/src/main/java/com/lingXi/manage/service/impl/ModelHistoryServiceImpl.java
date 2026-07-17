package com.lingXi.manage.service.impl;

import java.util.List;
import com.lingXi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.ai.mapper.ModelHistoryMapper;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IModelHistoryService;

/**
 * 大模型对话历史记录Service业务层处理
 * 
 * @author system
 * @date 2025-12-01
 */
@Service
public class ModelHistoryServiceImpl implements IModelHistoryService {
    @Autowired
    private ModelHistoryMapper modelHistoryMapper;

    /**
     * 查询对话历史记录
     * 
     * @param id 对话历史记录主键
     * @return 对话历史记录
     */
    @Override
    public ModelHistory selectModelHistoryById(Long id) {
        return modelHistoryMapper.selectModelHistoryById(id);
    }

    /**
     * 查询对话历史记录列表
     * 
     * @param modelHistory 对话历史记录
     * @return 对话历史记录集合
     */
    @Override
    public List<ModelHistory> selectModelHistoryList(ModelHistory modelHistory) {
        return modelHistoryMapper.selectModelHistoryList(modelHistory);
    }

    /**
     * 查询指定会话的对话历史记录
     * 
     * @param sessionId 会话唯一标识
     * @return 对话历史记录集合
     */
    @Override
    public List<ModelHistory> selectModelHistoryBySessionId(String sessionId) {
        return modelHistoryMapper.selectModelHistoryBySessionId(sessionId);
    }

    /**
     * 新增对话历史记录
     * 
     * @param modelHistory 对话历史记录
     * @return 结果
     */
    @Override
    public int insertModelHistory(ModelHistory modelHistory) {
        modelHistory.setCreateTime(DateUtils.getNowDate());
        modelHistory.setUpdateTime(DateUtils.getNowDate());
        return modelHistoryMapper.insertModelHistory(modelHistory);
    }

    /**
     * 批量新增对话历史记录
     * 
     * @param modelHistories 对话历史记录列表
     * @return 结果
     */
    @Override
    public int batchInsertModelHistory(List<ModelHistory> modelHistories) {
        int result = 0;
        for (ModelHistory modelHistory : modelHistories) {
            result += insertModelHistory(modelHistory);
        }
        return result;
    }

    /**
     * 修改对话历史记录
     * 
     * @param modelHistory 对话历史记录
     * @return 结果
     */
    @Override
    public int updateModelHistory(ModelHistory modelHistory) {
        modelHistory.setUpdateTime(DateUtils.getNowDate());
        return modelHistoryMapper.updateModelHistory(modelHistory);
    }

    /**
     * 删除对话历史记录
     * 
     * @param id 对话历史记录主键
     * @return 结果
     */
    @Override
    public int deleteModelHistoryById(Long id) {
        return modelHistoryMapper.deleteModelHistoryById(id);
    }

    /**
     * 批量删除对话历史记录
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    @Override
    public int deleteModelHistoryByIds(Long[] ids) {
        return modelHistoryMapper.deleteModelHistoryByIds(ids);
    }

    /**
     * 删除指定会话的对话历史记录
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    @Override
    public int deleteModelHistoryBySessionId(String sessionId) {
        return modelHistoryMapper.deleteModelHistoryBySessionId(sessionId);
    }
    
    @Override
    public List<ModelHistory> selectRecentModelHistoryBySessionId(String sessionId, Integer limit) {
        return modelHistoryMapper.selectRecentModelHistoryBySessionId(sessionId, limit);
    }
}