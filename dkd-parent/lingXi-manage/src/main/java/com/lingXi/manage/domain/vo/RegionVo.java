package com.lingXi.manage.domain.vo;

import com.lingXi.manage.domain.Region;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegionVo extends Region {

    //点位数量
    private Integer nodeCount;

}
