package com.lingXi.manage.service.impl;

import java.util.ArrayList;
import java.util.List;

import com.lingXi.common.constant.DkdContants;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.uuid.UUIDUtils;
import com.lingXi.manage.domain.Channel;
import com.lingXi.manage.domain.Node;
import com.lingXi.manage.domain.VmType;
import com.lingXi.manage.service.IChannelService;
import com.lingXi.manage.service.INodeService;
import com.lingXi.manage.service.IVmTypeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.manage.mapper.VendingMachineMapper;
import com.lingXi.manage.domain.VendingMachine;
import com.lingXi.manage.service.IVendingMachineService;

import static com.lingXi.common.utils.PageUtils.startPage;

/**
 * 设备管理Service业务层处理
 *
 * @author itzhou
 * @date 2025-08-26
 */
@Service
public class VendingMachineServiceImpl implements IVendingMachineService {
    @Autowired
    private VendingMachineMapper vendingMachineMapper;

    @Autowired
    private IVmTypeService vmTypeService;
    @Autowired
    private INodeService nodeService;
    @Autowired
    private IChannelService channelService;

    /**
     * 查询设备管理
     *
     * @param id 设备管理主键
     * @return 设备管理
     */
    @Override
    public VendingMachine selectVendingMachineById(Long id) {
        return vendingMachineMapper.selectVendingMachineById(id);
    }

    /**
     * 查询设备管理列表
     *
     * @param vendingMachine 设备管理
     * @return 设备管理
     */
    @Override
    public List<VendingMachine> selectVendingMachineList(VendingMachine vendingMachine) {
        startPage();
        return vendingMachineMapper.selectVendingMachineList(vendingMachine);
    }

    /**
     * 新增设备管理
     *
     * @param vendingMachine 设备管理
     * @return 结果
     */
    @Override
    public int insertVendingMachine(VendingMachine vendingMachine) {
        //1.新增设备
        //生成设备编号
        String innerCode = UUIDUtils.getUUID();
        vendingMachine.setInnerCode(innerCode);
        //查询设备类型
        VmType vmType = vmTypeService.selectVmTypeById(vendingMachine.getVmTypeId());
        vendingMachine.setChannelMaxCapacity(vmType.getChannelMaxCapacity());
        //查询点位表
        Node node = nodeService.selectNodeById(vendingMachine.getNodeId());
        BeanUtils.copyProperties(node,vendingMachine,"id");
        //新增详细地址
        vendingMachine.setAddr(node.getAddress() + " " + node.getNodeName());
        //设备状态
        vendingMachine.setVmStatus(DkdContants.VM_STATUS_NODEPLOY);
        vendingMachine.setCreateTime(DateUtils.getNowDate());
        vendingMachine.setUpdateTime(DateUtils.getNowDate());
        int result = vendingMachineMapper.insertVendingMachine(vendingMachine);
        //2.新增货道
        List<Channel> channelList = new ArrayList<>();
        for (int i = 1; i <= vmType.getVmCol(); i++) {
            for (int j = 1; j <= vmType.getVmRow(); j++) {
                //封装channel对象
                Channel channel = new Channel();
                channel.setChannelCode(i + "-" + j);
                channel.setVmId(vendingMachine.getId());
                channel.setInnerCode(vendingMachine.getInnerCode());
                channel.setMaxCapacity(vmType.getChannelMaxCapacity());
                channel.setCreateTime(DateUtils.getNowDate());
                channel.setUpdateTime(DateUtils.getNowDate());
                channelList.add( channel);
            }
        }
        channelService.batchInsertChannel(channelList);


        return result;
    }

    /**
     * 修改设备管理
     *
     * @param vendingMachine 设备管理
     * @return 结果
     */
    @Override
    public int updateVendingMachine(VendingMachine vendingMachine) {
        //查询点位表
        if(vendingMachine.getNodeId() != null){
            Node node = nodeService.selectNodeById(vendingMachine.getNodeId());
            BeanUtils.copyProperties(node, vendingMachine, "id");
            //新增详细地址
            vendingMachine.setAddr(node.getAddress() + " " + node.getNodeName());
        }
        vendingMachine.setUpdateTime(DateUtils.getNowDate());
        return vendingMachineMapper.updateVendingMachine(vendingMachine);
    }

    /**
     * 批量删除设备管理
     *
     * @param ids 需要删除的设备管理主键
     * @return 结果
     */
    @Override
    public int deleteVendingMachineByIds(Long[] ids) {
        return vendingMachineMapper.deleteVendingMachineByIds(ids);
    }

    /**
     * 删除设备管理信息
     *
     * @param id 设备管理主键
     * @return 结果
     */
    @Override
    public int deleteVendingMachineById(Long id) {
        return vendingMachineMapper.deleteVendingMachineById(id);
    }

    /**
     * 根据设备编号查询设备信息
     * @param innerCode 设备编号
     * @return
     */
    @Override
    public VendingMachine selectVendingMachineByInnerCode(String innerCode) {

        return vendingMachineMapper.selectVendingMachineByInnerCode(innerCode);
    }

    /** 在调用方事务中锁定设备行，防止受控工单并发创建穿透状态复核。 */
    @Override
    public VendingMachine selectVendingMachineByInnerCodeForUpdate(String innerCode) {
        return vendingMachineMapper.selectVendingMachineByInnerCodeForUpdate(innerCode);
    }

}
