package com.lingXi.manage.mapper;

import java.util.List;
import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.domain.vo.ChannelVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 售货机货道Mapper接口
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Mapper
public interface ChannelMapper 
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
     * 删除售货机货道
     * 
     * @param id 售货机货道主键
     * @return 结果
     */
    public int deleteChannelById(Long id);

    /**
     * 批量删除售货机货道
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteChannelByIds(Long[] ids);

    /**
     * 批量新增售货机货道
     *
     * @param channelList 售货机货道
     * @return 结果
     */
    public int selectChannelListByVmId(List<Channel> channelList);

    /**
     * 根据skuIds查询售货机货道数量
     *
     * @param
     * @return 售货机货道信息
     */
    int selectChannelCountBySkuIds(Long[] skuIds);

    /**
     * 根据商品Id查询售货机货道
     * @param innerCode
     * @return 售货机货道信息
     */
    List<ChannelVo> selectChannelVoListByInnerCode(String innerCode );

    /**
     * 根据售货机软编号和货道编号查询售货机货道信息
     * @param innerCode
     * @param channelCode
     * @return 售货机货道信息
     */
    @Select("select * from tb_channel where inner_code=#{innerCode} and channel_code=#{channelCode}")
    Channel getChannelInfo(@Param("innerCode") String innerCode,@Param("channelCode") String channelCode);

    /**
     * 批量更新售货机货道信息
     * @param channelList
     * @return
     */
    int updateChannelBatch(List<Channel> channelList);



}
