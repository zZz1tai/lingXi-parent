package com.lingXi.manage.domain.dto;

import lombok.Data;


import java.util.List;

@Data
public class ChannelConfigDto {
    private String innerCode;
    private List<ChannelDto> channelList;
}
