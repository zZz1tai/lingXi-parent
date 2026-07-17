package com.lingXi.manage.mapper;

import java.util.List;
import com.lingXi.manage.domain.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品管理Mapper接口
 * 
 * @author itzhou
 * @date 2025-08-29
 */
@Mapper
public interface SkuMapper 
{
    /**
     * 查询商品管理
     * 
     * @param skuId 商品管理主键
     * @return 商品管理
     */
    public Sku selectSkuBySkuId(Long skuId);

    /**
     * 查询商品管理列表
     * 
     * @param sku 商品管理
     * @return 商品管理集合
     */
    public List<Sku> selectSkuList(Sku sku);

    /**
     * 新增商品管理
     * 
     * @param sku 商品管理
     * @return 结果
     */
    public int insertSku(Sku sku);

    /**
     * 修改商品管理
     * 
     * @param sku 商品管理
     * @return 结果
     */
    public int updateSku(Sku sku);

    /**
     * 删除商品管理
     * 
     * @param skuId 商品管理主键
     * @return 结果
     */
    public int deleteSkuBySkuId(Long skuId);

    /**
     * 批量删除商品管理
     * 
     * @param skuIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSkuBySkuIds(Long[] skuIds);

    /**
     * 批量插入
     */
    public int insertSkuBatch(List<Sku> skuList);

    /**
     * 根据商品类别列表查询商品ID，排除已知skuId，用于类别补充推荐
     *
     * @param classIds   类别ID列表
     * @param excludeIds 需要排除的skuId列表
     * @return skuId列表
     */
    public List<Long> selectSkuIdsByClassIds(@Param("classIds") List<Long> classIds,
                                              @Param("excludeIds") List<Long> excludeIds);
}
