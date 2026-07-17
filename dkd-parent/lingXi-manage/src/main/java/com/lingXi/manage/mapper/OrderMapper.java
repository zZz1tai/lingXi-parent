package com.lingXi.manage.mapper;

import java.util.List;
import com.lingXi.manage.domain.Order;
import com.lingXi.manage.domain.Sku;
import com.lingXi.manage.domain.vo.SkuScoreVo;
import com.lingXi.manage.domain.OrderDetail;
import org.apache.ibatis.annotations.Param;

/**
 * 订单管理Mapper接口
 * 
 * @author itheima
 * @date 2024-07-29
 */
public interface OrderMapper 
{
    int insertOrderDetail(OrderDetail detail);
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
     * 删除订单管理
     * 
     * @param id 订单管理主键
     * @return 结果
     */
    public int deleteOrderById(Long id);

    /**
     * 批量删除订单管理
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteOrderByIds(Long[] ids);

    /**
     * 查询用户历史购买商品推荐列表（按购买次数降序）
     *
     * @param userName 用户名
     * @return 推荐skuId列表
     */
    public List<Long> selectRecommendSkuIds(@Param("userName") String userName);

    /**
     * 查询用户历史购买的商品类别列表（按购买次数降序）
     *
     * @param userName 用户名
     * @return 推荐classId列表
     */
    public List<Long> selectRecommendClassIds(@Param("userName") String userName);

    /**
     * 混合推荐：综合打分查询
     * 结合用户购买偏好、类别偏好、区域热度、库存情况等多维度打分
     *
     * @param query 查询条件（userName/regionId/businessType/partnerId/nodeId/innerCode/limit）
     * @return 打分后的商品列表
     */
    public List<SkuScoreVo> selectHybridRecommendScores(@Param("query") com.lingXi.manage.domain.dto.RecommendQuery query);

    /**
     * 兜底推荐：查询指定区域/设备下的热门商品（按销量降序）
     * 用于用户无历史订单时的降级推荐
     *
     * @param query 查询条件
     * @return 热门商品skuId列表
     */
    public List<Long> selectHotSkuIds(@Param("query") com.lingXi.manage.domain.dto.RecommendQuery query);
}
