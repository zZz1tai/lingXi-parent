package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoCharacter;

/**
 * AI视频角色数据访问接口
 * <p>
 * 提供AI视频角色数据的数据库操作方法，包括查询、新增/更新和别名管理等操作。
 * 角色属于特定的项目，用于管理视频中的人物形象信息。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoCharacterMapper
{
    /**
     * 根据项目ID和角色编码查询角色（悲观锁）
     *
     * @param character 角色查询条件（包含projectId和characterCode）
     * @return 锁定的角色信息，不存在时返回null
     */
    AiVideoCharacter selectAiVideoCharacterByProjectAndCodeForUpdate(AiVideoCharacter character);

    /**
     * 根据项目唯一标识查询角色列表（悲观锁）
     *
     * @param character 角色查询条件（包含项目唯一标识信息）
     * @return 锁定的角色列表
     */
    List<AiVideoCharacter> selectAiVideoCharacterByProjectIdentityForUpdate(AiVideoCharacter character);

    /**
     * 根据项目ID查询所有角色列表
     *
     * @param projectId 项目ID
     * @return 角色列表
     */
    List<AiVideoCharacter> selectAiVideoCharactersByProjectId(Long projectId);

    /**
     * 新增或更新AI视频角色（Upsert操作）
     *
     * @param character 角色信息对象
     * @return 影响的行数
     */
    int upsertAiVideoCharacter(AiVideoCharacter character);

    /**
     * 更新AI视频角色的别名列表
     *
     * @param characterId 角色ID
     * @param aliasesJson 别名JSON字符串
     * @param updateBy    操作人
     * @return 影响的行数
     */
    int updateAiVideoCharacterAliases(@Param("characterId") Long characterId,
            @Param("aliasesJson") String aliasesJson, @Param("updateBy") String updateBy);
}
