package com.lingXi.manage.service;

import java.util.List;
import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.domain.dto.ChannelConfigDto;
import com.lingXi.manage.domain.vo.ChannelVo;

/**
 * 售货机货道Service接口
 * 
 * @author itzhou
 * @date 2025-08-26
 */
public interface IChannelService 
{
    /**
     * 查询售货机货道
     * 
     * @param id 售货机货道主键
     * @return 售货机货道
     */
    public Channel selectChannelById(Long id);

    /**
     * 查询售货机货道列表
     * 
     * @param channel 售货机货道
     * @return 售货机货道集合
     */
    public List<Channel> selectChannelList(Channel channel);

    /**
     * 新增售货机货道
     * 
     * @param channel 售货机货道
     * @return 结果
     */
    public int insertChannel(Channel channel);

    /**
     * 修改售货机货道
     * 
     * @param channel 售货机货道
     * @return 结果
     */
    public int updateChannel(Channel channel);

    /**
     * 批量删除售货机货道
     * 
     * @param ids 需要删除的售货机货道主键集合
     * @return 结果
     */
    public int deleteChannelByIds(Long[] ids);

    /**
     * 批量新增售货机货道信息
     * 
     * @param channelList 售货机货道列表
     * @return 结果
     */
    public int batchInsertChannel(List<Channel> channelList);

    /**
     * 根据skuIds查询售货机货道数量
     *
     * @param skuIds 商品SKU ID数组
     * @return 售货机货道信息
     */
    int selectChannelCountBySkuIds(Long[] skuIds);

    /**
     * 根据售货机编号查询售货机货道信息
     *
     * @param innerCode 售货机编号
     * @return 售货机货道信息
     */
    List<ChannelVo> selectChannelVoListByInnerCode(String innerCode);

    /**
     * 货道关联商品
     *
     * @param channelConfigDto
     * @return 结果
     */
    int setChannel(ChannelConfigDto channelConfigDto);
    
    /**
     * 根据设备ID查询货道信息
     *
     * @param vmId 设备ID
     * @return 货道信息列表
     */
    List<ChannelVo> selectChannelsByVmId(Long vmId);
}
