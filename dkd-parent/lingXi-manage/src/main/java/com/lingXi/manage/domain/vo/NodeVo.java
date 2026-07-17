package com.lingXi.manage.domain.vo;

import com.lingXi.manage.domain.Node;
import com.lingXi.manage.domain.Partner;
import com.lingXi.manage.domain.Region;
import lombok.Data;

@Data
public class NodeVo extends Node {
    //设备数量
    private Integer vmCount;
    //区域性息
    private Region region;
    //合作商信息
    private Partner partner;
}
