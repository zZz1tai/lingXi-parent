package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppVendingMachine;
import com.lingXi.app.domain.AppTaskDetails;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppVendingMachineMapper {
    /**
     * 更新设备状态
     * @param innerCode 设备编码
     * @param status 设备状态
     * @return 更新结果
     */
    int updateStatus(@Param("innerCode") String innerCode, @Param("status") Integer status);

    /**
     * 补货操作
     * @param innerCode 设备编码
     * @param details 补货详情
     * @return 补货结果
     */
    int supply(@Param("innerCode") String innerCode, @Param("details") List<AppTaskDetails> details);
}