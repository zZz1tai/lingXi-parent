package com.lingXi.manage.domain.dto;

import lombok.Data;

/**
 * 多参数混合推荐查询条件
 */
@Data
public class RecommendQuery
{
    /** 用户标识（与订单 open_id 对应，一般为登录用户名） */
    private String userName;

    /** 区域ID */
    private Long regionId;

    /** 商圈类型 */
    private Long businessType;

    /** 合作商ID */
    private Long partnerId;

    /** 点位ID */
    private Long nodeId;

    /** 设备编号（传入后会自动补全区域/商圈/点位等上下文，并优先推荐本机有货商品） */
    private String innerCode;

    /** 返回数量，默认 10 */
    private Integer limit;

    public int resolveLimit()
    {
        if (limit == null || limit <= 0)
        {
            return 10;
        }
        return Math.min(limit, 50);
    }

    /**
     * JavaBean getter used by MyBatis OGNL expressions such as
     * {@code #{query.resolveLimit}}.
     */
    public int getResolveLimit()
    {
        return resolveLimit();
    }
}
