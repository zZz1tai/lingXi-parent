package com.lingXi.app.service;

import com.lingXi.app.domain.AppChannel;

import java.util.List;


public interface ChannelService {

    //按照售货机编号查询货道列表
    List<AppChannel> getChannelesByInnerCode(String innerCode);
}