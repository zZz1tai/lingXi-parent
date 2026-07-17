package com.lingXi.ai.mapper;

import java.util.List;
import com.lingXi.manage.domain.ModelHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 大模型对话历史记录Mapper接口
 * 
 * @author system
 * @date 2025-12-01
 */
@Mapper
public interface ModelHistoryMapper 
{
    /**
     * 查询对话历史记录
     * 
     * @param id 对话历史记录主键
     * @return 对话历史记录
     */
    public ModelHistory selectModelHistoryById(Long id);

    /**
     * 查询对话历史记录列表
     * 
     * @param modelHistory 对话历史记录
     * @return 对话历史记录集合
     */
    public List<ModelHistory> selectModelHistoryList(ModelHistory modelHistory);

    /**
     * 查询指定会话的对话历史记录
     * 
     * @param sessionId 会话唯一标识
     * @return 对话历史记录集合
     */
    public List<ModelHistory> selectModelHistoryBySessionId(String sessionId);

    /**
     * 新增对话历史记录
     * 
     * @param modelHistory 对话历史记录
     * @return 结果
     */
    public int insertModelHistory(ModelHistory modelHistory);

    /**
     * 修改对话历史记录
     * 
     * @param modelHistory 对话历史记录
     * @return 结果
     */
    public int updateModelHistory(ModelHistory modelHistory);

    /**
     * 删除对话历史记录
     * 
     * @param id 对话历史记录主键
     * @return 结果
     */
    public int deleteModelHistoryById(Long id);

    /**
     * 批量删除对话历史记录
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteModelHistoryByIds(Long[] ids);

    /**
     * 删除指定会话的对话历史记录
     * 
     * @param sessionId 会话唯一标识
     * @return 结果
     */
    public int deleteModelHistoryBySessionId(String sessionId);
    
    /**
     * 查询指定会话的最近N条对话历史记录
     * 
     * @param sessionId 会话唯一标识
     * @param limit 限制数量
     * @return 对话历史记录集合
     */
    public List<ModelHistory> selectRecentModelHistoryBySessionId(@Param("sessionId") String sessionId, @Param("limit") Integer limit);
}