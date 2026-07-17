package com.lingXi.manage.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.manage.mapper.OrderMapper;
import com.lingXi.manage.mapper.SkuMapper;
import com.lingXi.manage.domain.Order;
import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.domain.OrderDetail;
import com.lingXi.manage.domain.Sku;
import com.lingXi.manage.domain.dto.RecommendQuery;
import com.lingXi.manage.domain.vo.SkuScoreVo;
import com.lingXi.manage.service.IOrderService;
import com.lingXi.manage.service.IChannelService;
import com.lingXi.common.utils.SecurityUtils;

/**
 * 订单管理Service业务层处理
 *
 * @author itheima
 * @date 2024-07-29
 */
@Service
public class OrderServiceImpl implements IOrderService
{
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private IChannelService channelService;

    @Autowired
    private SkuMapper skuMapper;

    /**
     * 查询订单管理
     *
     * @param id 订单管理主键
     * @return 订单管理
     */
    @Override
    public Order selectOrderById(Long id)
    {
        return orderMapper.selectOrderById(id);
    }

    /**
     * 查询订单管理列表
     *
     * @param order 订单管理
     * @return 订单管理
     */
    @Override
    public List<Order> selectOrderList(Order order)
    {
        return orderMapper.selectOrderList(order);
    }

    /**
     * 新增订单管理
     *
     * @param order 订单管理
     * @return 结果
     */
    @Override
    @Transactional
    public int insertOrder(Order order)
    {
        // 多商品订单：主订单使用第一条明细，所有商品另存明细并扣减各自库存
        if (order.getDetails() != null && !order.getDetails().isEmpty()) {
            OrderDetail first = order.getDetails().get(0);
            order.setChannelId(first.getChannelId());
            order.setSkuId(first.getSkuId());
            order.setSkuName(first.getSkuName());
            order.setQuantity(order.getDetails().stream().mapToInt(d -> d.getQuantity() == null ? 1 : d.getQuantity()).sum());
        }

        // 查询货道信息
        Long channelId = order.getChannelId();
        Channel channel = channelService.selectChannelById(channelId);
        if (channel == null) {
            throw new ServiceException("货道信息不存在");
        }

        // 单商品订单直接校验库存；多商品订单在明细循环中分别校验
        Integer quantity = order.getQuantity() != null ? order.getQuantity() : 1;
        if (order.getDetails() == null || order.getDetails().isEmpty()) {
            if (channel.getCurrentCapacity() < quantity) {
                throw new ServiceException("商品库存不足");
            }
        }

        // 更新库存
        if (order.getDetails() == null || order.getDetails().isEmpty()) {
            channel.setCurrentCapacity(channel.getCurrentCapacity() - quantity);
            channelService.updateChannel(channel);
        }

        // 生成订单编号（使用时间戳+随机数确保唯一性）
        String orderNo = "A" + System.currentTimeMillis();
        order.setOrderNo(orderNo);

        // 生成唯一的订单ID（使用时间戳+随机数）
        long orderId = System.currentTimeMillis();
        order.setId(orderId);

        // 设置货道编号
        order.setChannelCode(channel.getChannelCode());

        // 设置订单信息
        order.setStatus(1L); // 支付完成
        order.setPayStatus(1L); // 支付完成
        order.setOpenId(SecurityUtils.getUsername()); // 记录购买用户，用于推荐算法
        order.setCreateTime(DateUtils.getNowDate());
        order.setUpdateTime(DateUtils.getNowDate());

        int result = orderMapper.insertOrder(order);
        if (order.getDetails() != null && !order.getDetails().isEmpty()) {
            for (OrderDetail detail : order.getDetails()) {
                Channel detailChannel = channelService.selectChannelById(detail.getChannelId());
                if (detailChannel == null || detailChannel.getCurrentCapacity() < detail.getQuantity()) {
                    throw new ServiceException("商品货道不存在或库存不足");
                }
                if (detail.getSkuId() == null) {
                    detail.setSkuId(detailChannel.getSkuId());
                }
                if (detail.getSkuId() == null) {
                    throw new ServiceException("商品信息不存在");
                }
                detailChannel.setCurrentCapacity(detailChannel.getCurrentCapacity() - detail.getQuantity());
                channelService.updateChannel(detailChannel);
                detail.setOrderId(order.getId());
                orderMapper.insertOrderDetail(detail);
            }
        }
        return result;
    }

    /**
     * 修改订单管理
     *
     * @param order 订单管理
     * @return 结果
     */
    @Override
    public int updateOrder(Order order)
    {
        return orderMapper.updateOrder(order);
    }

    /**
     * 批量删除订单管理
     *
     * @param ids 需要删除的订单管理主键
     * @return 结果
     */
    @Override
    public int deleteOrderByIds(Long[] ids)
    {
        return orderMapper.deleteOrderByIds(ids);
    }

    /**
     * 删除订单管理信息
     *
     * @param id 订单管理主键
     * @return 结果
     */
    @Override
    public int deleteOrderById(Long id)
    {
        return orderMapper.deleteOrderById(id);
    }

    /**
     * 获取推荐商品skuId列表
     * 算法：返回当前登录用户最喜欢的两种商品（历史订单中购买频率最高的2个skuId）
     *
     * @param userName 用户名
     * @return 推荐的skuId列表（最多2个）
     */
    @Override
    public List<Long> getRecommendSkuIds(String userName)
    {
        // 获取用户历史购买频率最高的2个 skuId
        List<Long> recommendedSkuIds = orderMapper.selectRecommendSkuIds(userName);

        // 只返回前2个最喜欢的商品
        if (recommendedSkuIds.size() > 2) {
            return recommendedSkuIds.subList(0, 2);
        }

        return recommendedSkuIds;
    }

    /**
     * 混合推荐：综合用户偏好、类别偏好、区域上下文、库存等多维度打分排序
     * <p>
     * 打分维度及权重：
     * - 用户购买频率 (35%)：用户历史购买该商品的次数
     * - 类别偏好 (25%)：用户对所属类别的偏好程度
     * - 区域热度 (20%)：同区域其他用户的购买热度
     * - 库存可用性 (10%)：当前设备是否有货
     * - 折扣促销 (10%)：是否打折促销
     * <p>
     * 兜底策略：当用户无历史订单时，按区域/设备维度推荐热门商品
     *
     * @param query 混合推荐查询条件
     * @return 推荐的商品列表（包含商品详情和综合得分）
     */
    @Override
    public List<Sku> getHybridRecommendations(RecommendQuery query)
    {
        List<Long> skuIds;

        // 优先使用混合打分推荐
        List<SkuScoreVo> scoredList = orderMapper.selectHybridRecommendScores(query);
        if (scoredList != null && !scoredList.isEmpty())
        {
            skuIds = scoredList.stream()
                    .map(SkuScoreVo::getSkuId)
                    .collect(Collectors.toList());
        }
        else
        {
            // 兜底策略：用户无历史订单时，推荐区域/设备下的热门商品
            skuIds = orderMapper.selectHotSkuIds(query);
        }

        if (skuIds == null || skuIds.isEmpty())
        {
            return new ArrayList<>();
        }

        // 根据skuId列表批量查询商品详情
        List<Sku> skuList = new ArrayList<>();
        for (Long skuId : skuIds)
        {
            Sku sku = skuMapper.selectSkuBySkuId(skuId);
            if (sku != null)
            {
                skuList.add(sku);
            }
        }

        return skuList;
    }
}
