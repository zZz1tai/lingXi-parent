package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppChannel;
import com.lingXi.app.mapper.AppChannelMapper;
import com.lingXi.app.service.ChannelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("appChannelServiceImpl")
public class ChannelServiceImpl implements ChannelService {

    @Autowired
    private AppChannelMapper channelMapper;

    @Override
    public List<AppChannel> getChannelesByInnerCode(String innerCode) {
        return channelMapper.getChannelesByInnerCode(innerCode);
    }
}