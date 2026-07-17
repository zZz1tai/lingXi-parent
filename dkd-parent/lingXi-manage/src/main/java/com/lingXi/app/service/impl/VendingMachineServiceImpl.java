package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppChannel;
import com.lingXi.app.domain.AppTaskDetails;
import com.lingXi.app.mapper.AppVendingMachineMapper;
import com.lingXi.app.service.ChannelService;
import com.lingXi.app.service.VendingMachineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service("appVendingMachineServiceImpl")
@Slf4j
public class VendingMachineServiceImpl implements VendingMachineService {

    @Autowired
    private AppVendingMachineMapper vendingMachineMapper;
    @Autowired
    private ChannelService channelService;

    // 更改售货机状态
    public boolean updateStatus(String innerCode, Integer status) {
        int result = vendingMachineMapper.updateStatus(innerCode, status);
        return result > 0;
    }

    // 更新货道库存
    @Override
    public boolean supply(String innerCode, List<AppTaskDetails> details) {
        // 更新货道库存
        int result = vendingMachineMapper.supply(innerCode, details);
        return result > 0;
    }
}