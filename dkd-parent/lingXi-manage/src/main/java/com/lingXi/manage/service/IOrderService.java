package com.lingXi.manage.service;

import java.util.List;
import com.lingXi.manage.domain.Order;
import com.lingXi.manage.domain.Sku;
import com.lingXi.manage.domain.dto.RecommendQuery;

/**
 * 订单管理Service接口
 * 
 * @author itheima
 * @date 2024-07-29
 */
public interface IOrderService 
{
    /**
     * 查询订单管理
     * 
     * @param id 订单管理主键
     * @return 订单管理
     */
    public Order selectOrderById(Long id);

    /**
     * 查询订单管理列表
     * 
     * @param order 订单管理
     * @return 订单管理集合
     */
    public List<Order> selectOrderList(Order order);

    /**
     * 新增订单管理
     * 
     * @param order 订单管理
     * @return 结果
     */
    public int insertOrder(Order order);

    /**
     * 修改订单管理
     * 
     * @param order 订单管理
     * @return 结果
     */
    public int updateOrder(Order order);

    /**
     * 批量删除订单管理
     * 
     * @param ids 需要删除的订单管理主键集合
     * @return 结果
     */
    public int deleteOrderByIds(Long[] ids);

    /**
     * 删除订单管理信息
     * 
     * @param id 订单管理主键
     * @return 结果
     */
    public int deleteOrderById(Long id);

    /**
     * 获取推荐商品skuId列表
     * 基于用户历史订单分析购买偏好，结合历史sku购买频率和类别偏好返回推荐列表
     *
     * @param userName 用户名
     * @return 推荐的skuId列表
     */
    public List<Long> getRecommendSkuIds(String userName);

    /**
     * 混合推荐：综合用户偏好、类别偏好、区域上下文、库存等多维度打分排序
     *
     * @param query 混合推荐查询条件
     * @return 推荐的商品列表（包含商品详情和综合得分）
     */
    public List<Sku> getHybridRecommendations(RecommendQuery query);
}
