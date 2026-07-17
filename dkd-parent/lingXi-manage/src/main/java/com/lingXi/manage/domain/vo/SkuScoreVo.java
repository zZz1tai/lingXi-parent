package com.lingXi.manage.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推荐打分结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuScoreVo
{
    /** 商品ID */
    private Long skuId;

    /** 综合得分 */
    private Double score;
}
