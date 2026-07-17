package com.lingXi.manage.service.impl;

import java.util.List;
import com.lingXi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.manage.mapper.UserTaskRelationMapper;
import com.lingXi.manage.domain.UserTaskRelation;
import com.lingXi.manage.service.IUserTaskRelationService;

/**
 * 用户工单关联Service业务层处理
 * 
 * @author lingXi
 * @date 2025-12-16
 */
@Service
public class UserTaskRelationServiceImpl implements IUserTaskRelationService 
{
    @Autowired
    private UserTaskRelationMapper userTaskRelationMapper;

    /**
     * 查询用户工单关联
     * 
     * @param id 用户工单关联主键
     * @return 用户工单关联
     */
    @Override
    public UserTaskRelation selectUserTaskRelationById(Long id)
    {
        return userTaskRelationMapper.selectUserTaskRelationById(id);
    }

    /**
     * 查询用户工单关联列表
     * 
     * @param userTaskRelation 用户工单关联
     * @return 用户工单关联集合
     */
    @Override
    public List<UserTaskRelation> selectUserTaskRelationList(UserTaskRelation userTaskRelation)
    {
        return userTaskRelationMapper.selectUserTaskRelationList(userTaskRelation);
    }

    /**
     * 新增用户工单关联
     * 
     * @param userTaskRelation 用户工单关联
     * @return 结果
     */
    @Override
    public int insertUserTaskRelation(UserTaskRelation userTaskRelation)
    {
        userTaskRelation.setCreateTime(DateUtils.getNowDate());
        userTaskRelation.setUpdateTime(DateUtils.getNowDate());
        return userTaskRelationMapper.insertUserTaskRelation(userTaskRelation);
    }

    /**
     * 修改用户工单关联
     * 
     * @param userTaskRelation 用户工单关联
     * @return 结果
     */
    @Override
    public int updateUserTaskRelation(UserTaskRelation userTaskRelation)
    {
        userTaskRelation.setUpdateTime(DateUtils.getNowDate());
        return userTaskRelationMapper.updateUserTaskRelation(userTaskRelation);
    }

    /**
     * 批量删除用户工单关联
     * 
     * @param ids 需要删除的用户工单关联主键集合
     * @return 结果
     */
    @Override
    public int deleteUserTaskRelationByIds(Long[] ids)
    {
        return userTaskRelationMapper.deleteUserTaskRelationByIds(ids);
    }

    /**
     * 删除用户工单关联信息
     * 
     * @param id 用户工单关联主键
     * @return 结果
     */
    @Override
    public int deleteUserTaskRelationById(Long id)
    {
        return userTaskRelationMapper.deleteUserTaskRelationById(id);
    }

    /**
     * 根据工单ID查询关联用户
     * 
     * @param taskId 工单ID
     * @return 用户工单关联集合
     */
    @Override
    public List<UserTaskRelation> selectUserTaskRelationByTaskId(Long taskId)
    {
        return userTaskRelationMapper.selectUserTaskRelationByTaskId(taskId);
    }

    /**
     * 根据用户ID查询关联工单
     * 
     * @param userId 用户ID
     * @return 用户工单关联集合
     */
    @Override
    public List<UserTaskRelation> selectUserTaskRelationByUserId(Long userId)
    {
        return userTaskRelationMapper.selectUserTaskRelationByUserId(userId);
    }

    /**
     * 根据工单ID和关联类型查询关联用户
     * 
     * @param taskId 工单ID
     * @param relationType 关联类型
     * @return 用户工单关联
     */
    @Override
    public UserTaskRelation selectUserTaskRelationByTaskIdAndType(Long taskId, Integer relationType)
    {
        UserTaskRelation userTaskRelation = new UserTaskRelation();
        userTaskRelation.setTaskId(taskId);
        userTaskRelation.setRelationType(relationType);
        return userTaskRelationMapper.selectUserTaskRelationByTaskIdAndType(userTaskRelation);
    }

    /**
     * 根据工单ID删除关联记录
     * 
     * @param taskId 工单ID
     * @return 结果
     */
    @Override
    public int deleteUserTaskRelationByTaskId(Long taskId)
    {
        return userTaskRelationMapper.deleteUserTaskRelationByTaskId(taskId);
    }

    /**
     * 为工单分配用户
     * 
     * @param taskId 工单ID
     * @param userId 用户ID
     * @param relationType 关联类型
     * @return 结果
     */
    @Override
    public int assignUserToTask(Long taskId, Long userId, Integer relationType)
    {
        // 先删除已有的同类型关联
        UserTaskRelation existingRelation = selectUserTaskRelationByTaskIdAndType(taskId, relationType);
        if (existingRelation != null)
        {
            deleteUserTaskRelationById(existingRelation.getId());
        }
        
        // 创建新的关联
        UserTaskRelation userTaskRelation = new UserTaskRelation();
        userTaskRelation.setTaskId(taskId);
        userTaskRelation.setUserId(userId);
        userTaskRelation.setRelationType(relationType);
        return insertUserTaskRelation(userTaskRelation);
    }

    /**
     * 检查用户是否有权限处理工单
     * 
     * @param taskId 工单ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    @Override
    public boolean checkUserPermission(Long taskId, Long userId)
    {
        UserTaskRelation userTaskRelation = new UserTaskRelation();
        userTaskRelation.setTaskId(taskId);
        userTaskRelation.setUserId(userId);
        List<UserTaskRelation> relations = userTaskRelationMapper.selectUserTaskRelationList(userTaskRelation);
        return !relations.isEmpty();
    }
}