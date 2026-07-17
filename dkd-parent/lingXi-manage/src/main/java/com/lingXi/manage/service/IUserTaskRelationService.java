package com.lingXi.manage.service;

import java.util.List;
import com.lingXi.manage.domain.UserTaskRelation;

/**
 * 用户工单关联Service接口
 * 
 * @author lingXi
 * @date 2025-12-16
 */
public interface IUserTaskRelationService 
{
    /**
     * 查询用户工单关联
     * 
     * @param id 用户工单关联主键
     * @return 用户工单关联
     */
    public UserTaskRelation selectUserTaskRelationById(Long id);

    /**
     * 查询用户工单关联列表
     * 
     * @param userTaskRelation 用户工单关联
     * @return 用户工单关联集合
     */
    public List<UserTaskRelation> selectUserTaskRelationList(UserTaskRelation userTaskRelation);

    /**
     * 新增用户工单关联
     * 
     * @param userTaskRelation 用户工单关联
     * @return 结果
     */
    public int insertUserTaskRelation(UserTaskRelation userTaskRelation);

    /**
     * 修改用户工单关联
     * 
     * @param userTaskRelation 用户工单关联
     * @return 结果
     */
    public int updateUserTaskRelation(UserTaskRelation userTaskRelation);

    /**
     * 批量删除用户工单关联
     * 
     * @param ids 需要删除的用户工单关联主键集合
     * @return 结果
     */
    public int deleteUserTaskRelationByIds(Long[] ids);

    /**
     * 删除用户工单关联信息
     * 
     * @param id 用户工单关联主键
     * @return 结果
     */
    public int deleteUserTaskRelationById(Long id);

    /**
     * 根据工单ID查询关联用户
     * 
     * @param taskId 工单ID
     * @return 用户工单关联集合
     */
    public List<UserTaskRelation> selectUserTaskRelationByTaskId(Long taskId);

    /**
     * 根据用户ID查询关联工单
     * 
     * @param userId 用户ID
     * @return 用户工单关联集合
     */
    public List<UserTaskRelation> selectUserTaskRelationByUserId(Long userId);

    /**
     * 根据工单ID和关联类型查询关联用户
     * 
     * @param taskId 工单ID
     * @param relationType 关联类型
     * @return 用户工单关联
     */
    public UserTaskRelation selectUserTaskRelationByTaskIdAndType(Long taskId, Integer relationType);

    /**
     * 根据工单ID删除关联记录
     * 
     * @param taskId 工单ID
     * @return 结果
     */
    public int deleteUserTaskRelationByTaskId(Long taskId);

    /**
     * 为工单分配用户
     * 
     * @param taskId 工单ID
     * @param userId 用户ID
     * @param relationType 关联类型
     * @return 结果
     */
    public int assignUserToTask(Long taskId, Long userId, Integer relationType);

    /**
     * 检查用户是否有权限处理工单
     * 
     * @param taskId 工单ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    public boolean checkUserPermission(Long taskId, Long userId);
}