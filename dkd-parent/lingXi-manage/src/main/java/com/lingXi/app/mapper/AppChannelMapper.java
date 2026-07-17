package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * <p>
 * 售货机货道表 Mapper 接口
 * </p>
 *
 * @author LKD
 */
@Mapper
public interface AppChannelMapper {
    /**
     * 按照售货机编号查询货道列表
     * @param innerCode 售货机编号
     * @return 货道列表
     */
    List<AppChannel> getChannelesByInnerCode(@Param("innerCode") String innerCode);
}