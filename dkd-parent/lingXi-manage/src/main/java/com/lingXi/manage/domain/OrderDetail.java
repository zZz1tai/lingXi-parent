package com.lingXi.manage.domain;

import lombok.Data;

@Data
public class OrderDetail {
    private Long orderId;
    private Long channelId;
    private Long skuId;
    private String skuName;
    private Integer quantity;
    private Long price;
    private Long amount;
}
