package com.lingXi.app.service;

import com.lingXi.app.domain.AppTaskDetails;
import com.lingXi.app.domain.AppVendingMachine;

import java.util.List;

public interface VendingMachineService {

    // 更改售货机状态
    boolean updateStatus(String innerCode, Integer vmStatus);

    // 更新货道库存
    boolean supply(String innerCode, List<AppTaskDetails> details);
}