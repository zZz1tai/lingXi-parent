package com.lingXi.aiNovel.mapper;

import com.lingXi.manage.domain.ModelHistory;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 小说业务固定使用 novel 域，用户身份来自登录上下文。 */
public interface NovelSessionMapper {
    @Insert("""
        insert into tb_chat_session (session_id, user_id, session_name, business_type)
        values (#{sessionId}, #{userId}, #{name}, 'novel')
        on duplicate key update id = id
        """)
    void ensureSession(@Param("sessionId") String sessionId, @Param("userId") String userId,
                       @Param("name") String name);

    @Select("""
        select id from tb_chat_session where session_id=#{sessionId} and user_id=#{userId}
        and business_type='novel' and status='ACTIVE'
        """)
    Long findSession(@Param("sessionId") String sessionId, @Param("userId") String userId);

    @Insert("""
        insert into tb_model_history (session_id, user_id, user_name, content, message_type, model_name, business_type)
        select session_id, user_id, #{username}, #{content}, #{role}, 'novel-agent', 'novel'
        from tb_chat_session where id=#{id} and business_type='novel' and status='ACTIVE'
        """)
    int append(@Param("id") long id, @Param("username") String username,
               @Param("content") String content, @Param("role") String role);

    @Select("""
        select id, session_id as sessionId, user_id as userId, user_name as userName, content,
               message_type as messageType, model_name as modelName, created_at as createTime
        from tb_model_history where business_type='novel' and user_id=#{userId} and session_id=#{sessionId}
        order by created_at, id
        """)
    List<ModelHistory> history(@Param("userId") String userId, @Param("sessionId") String sessionId);

    @Delete("""
        delete from tb_chat_session where business_type='novel' and user_id=#{userId} and id=#{id}
        """)
    int deleteSession(@Param("userId") String userId, @Param("id") long id);

    @Delete("""
        delete from tb_model_history where business_type='novel' and user_id=#{userId} and session_id=#{sessionId}
        """)
    int deleteHistory(@Param("userId") String userId, @Param("sessionId") String sessionId);
}
