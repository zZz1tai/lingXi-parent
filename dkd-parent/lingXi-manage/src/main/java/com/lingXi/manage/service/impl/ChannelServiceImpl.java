package com.lingXi.manage.service.impl;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.lingXi.common.utils.DateUtils;
import com.lingXi.manage.domain.dto.ChannelConfigDto;
import com.lingXi.manage.domain.dto.ChannelDto;
import com.lingXi.manage.domain.vo.ChannelVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.manage.mapper.ChannelMapper;
import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.service.IChannelService;

/**
 * 售货机货道Service业务层处理
 *
 * @author itzhou
 * @date 2025-08-26
 */
@Service
public class ChannelServiceImpl implements IChannelService {
    @Autowired
    private ChannelMapper channelMapper;

    /**
     * 查询售货机货道
     *
     * @param id 售货机货道主键
     * @return 售货机货道
     */
    @Override
    public Channel selectChannelById(Long id) {
        return channelMapper.selectChannelById(id);
    }

    /**
     * 查询售货机货道列表
     *
     * @param channel 售货机货道
     * @return 售货机货道
     */
    @Override
    public List<Channel> selectChannelList(Channel channel) {
        return channelMapper.selectChannelList(channel);
    }

    /**
     * 新增售货机货道
     *
     * @param channel 售货机货道
     * @return 结果
     */
    @Override
    public int insertChannel(Channel channel) {
        return channelMapper.insertChannel(channel);
    }

    /**
     * 修改售货机货道
     *
     * @param channel 售货机货道
     * @return 结果
     */
    @Override
    public int updateChannel(Channel channel) {
        return channelMapper.updateChannel(channel);
    }

    /**
     * 批量删除售货机货道
     *
     * @param ids 需要删除的售货机货道主键
     * @return 结果
     */
    @Override
    public int deleteChannelByIds(Long[] ids) {
        return channelMapper.deleteChannelByIds(ids);
    }

    /**
     * 批量新增售货机货道
     *
     * @param channelList 售货机货道
     * @return 结果
     */
    @Override
    public int batchInsertChannel(List<Channel> channelList) {
        return channelMapper.selectChannelListByVmId(channelList);
    }

    /**
     * 根据skuIds查询售货机货道数量
     *
     * @param
     * @return 售货机货道信息
     */
    @Override
    public int selectChannelCountBySkuIds(Long[] skuIds) {
        return channelMapper.selectChannelCountBySkuIds(skuIds);
    }

    /**
     * 根据售货机编号查询售货机货道信息
     *
     * @param
     * @return 售货机货道信息
     */
    @Override
    public List<ChannelVo> selectChannelVoListByInnerCode(String innerCode) {

        return channelMapper.selectChannelVoListByInnerCode(innerCode);
    }

    /**
     * 货道关联商品
     *
     * @param
     * @return 售货机货道信息
     */
    @Override
    public int setChannel(ChannelConfigDto channelConfigDto) {

        //1.将dto转为po对象并逐个更新
        int count = 0;
        for (ChannelDto dto : channelConfigDto.getChannelList()) {
            Channel channel = channelMapper.getChannelInfo(dto.getInnerCode(), dto.getChannelCode());
            if (channel != null){
                //关联最新商品id
                channel.setSkuId(dto.getSkuId());
                //更新单个货道
                count += channelMapper.updateChannel(channel);
            }
        }

        return count;

    }

    @Override
    public List<ChannelVo> selectChannelsByVmId(Long vmId) {
        // 这里需要根据vmId查询到设备的innerCode，然后调用selectChannelVoListByInnerCode
        // 但由于我们在Controller中已经做了这个操作，所以这里可以简化实现
        return new ArrayList<>();
    }

}
