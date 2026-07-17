package com.lingXi.manage.mapper;

import java.util.List;
import com.lingXi.manage.domain.UserTaskRelation;

/**
 * 用户工单关联Mapper接口
 * 
 * @author lingXi
 * @date 2025-12-16
 */
public interface UserTaskRelationMapper 
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
     * 删除用户工单关联
     * 
     * @param id 用户工单关联主键
     * @return 结果
     */
    public int deleteUserTaskRelationById(Long id);

    /**
     * 批量删除用户工单关联
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteUserTaskRelationByIds(Long[] ids);

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
     * @param userTaskRelation 用户工单关联
     * @return 用户工单关联
     */
    public UserTaskRelation selectUserTaskRelationByTaskIdAndType(UserTaskRelation userTaskRelation);

    /**
     * 根据工单ID删除关联记录
     * 
     * @param taskId 工单ID
     * @return 结果
     */
    public int deleteUserTaskRelationByTaskId(Long taskId);
}