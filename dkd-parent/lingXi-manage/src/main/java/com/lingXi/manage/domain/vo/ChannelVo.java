package com.lingXi.manage.domain.vo;

import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.domain.Sku;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChannelVo extends Channel {

    //商品对象
    private Sku sku;
}
