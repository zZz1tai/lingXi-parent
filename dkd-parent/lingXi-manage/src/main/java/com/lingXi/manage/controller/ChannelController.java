package com.lingXi.manage.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.lingXi.manage.domain.dto.ChannelConfigDto;
import com.lingXi.manage.domain.vo.ChannelVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.service.IChannelService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 售货机货道Controller
 *
 * @author itzhou
 * @date 2025-08-26
 */
@Api(tags = "售货机货道管理")
@RestController
@RequestMapping("/manage/channel")
public class ChannelController extends BaseController {
    @Autowired
    private IChannelService channelService;

    /**
     * 查询售货机货道列表
     */
    @ApiOperation("查询售货机货道列表")
    @PreAuthorize("@ss.hasPermi('manage:channel:list')")
    @GetMapping("/list")
    public TableDataInfo list(Channel channel) {
        startPage();
        List<Channel> list = channelService.selectChannelList(channel);
        return getDataTable(list);
    }

    /**
     * 导出售货机货道列表
     */
    @ApiOperation("导出售货机货道列表")
    @PreAuthorize("@ss.hasPermi('manage:channel:export')")
    @Log(title = "售货机货道", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Channel channel) {
        List<Channel> list = channelService.selectChannelList(channel);
        ExcelUtil<Channel> util = new ExcelUtil<Channel>(Channel.class);
        util.exportExcel(response, list, "售货机货道数据");
    }

    /**
     * 获取售货机货道详细信息
     */
    @ApiOperation("获取售货机货道详细信息")
    @PreAuthorize("@ss.hasPermi('manage:channel:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return success(channelService.selectChannelById(id));
    }

    /**
     * 新增售货机货道
     */
    @ApiOperation("新增售货机货道")
    @PreAuthorize("@ss.hasPermi('manage:channel:add')")
    @Log(title = "售货机货道", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Channel channel) {
        return toAjax(channelService.insertChannel(channel));
    }

    /**
     * 修改售货机货道
     */
    @ApiOperation("修改售货机货道")
    @PreAuthorize("@ss.hasPermi('manage:channel:edit')")
    @Log(title = "售货机货道", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Channel channel) {
        return toAjax(channelService.updateChannel(channel));
    }

    /**
     * 删除售货机货道
     */
    @ApiOperation("删除售货机货道")
    @PreAuthorize("@ss.hasPermi('manage:channel:remove')")
    @Log(title = "售货机货道", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(channelService.deleteChannelByIds(ids));
    }

    /**
     * 根据售货机编号查询售货机货道列表
     */
    @ApiOperation("根据售货机编号查询售货机货道列表")
    @GetMapping("/list/{innerCode}")
    public AjaxResult list(@PathVariable("innerCode") String innerCode) {

        List<ChannelVo> voList = channelService.selectChannelVoListByInnerCode(innerCode);
        return success(voList);
    }

    @ApiOperation("配置售货机货道")
    @PreAuthorize("@ss.hasPermi('manage:channel:edit')")
    @Log(title = "售货机货道", businessType = BusinessType.UPDATE)
    @PutMapping("/config")
    public AjaxResult config(@RequestBody ChannelConfigDto channelConfigDto) {
        return  toAjax(channelService.setChannel(channelConfigDto));
    }
}
