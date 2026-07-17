<template>
  <div class="app-container manage-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title">
        <i class="el-icon-location" />
        <span>点位管理中心</span>
      </div>
      <div class="sub-title">高效管理系统点位配置</div>
    </div>
    <!-- 筛选条件 -->
    <div class="card search-card" v-show="showSearch">
      <div class="card-title">
        <i class="el-icon-filter" />
        <span>筛选条件</span>
      </div>

      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="search-form">
        <el-form-item label="点位搜索" prop="nodeName">
          <el-input v-model="queryParams.nodeName" placeholder="请输入点位名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="区域名称" prop="regionId">
          <!-- <el-input v-model="queryParams.regionId" placeholder="请输入区域ID" clearable @keyup.enter="handleQuery" /> -->
          <el-select v-model="queryParams.regionId" placeholder="请选择区域" clearable filterable>
            <el-option v-for="item in regionList" :key="item.id" :label="item.regionName" :value="item.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格列表 -->
    <div class="card table-card">
      <div class="table-header">
        <div class="left">
          <i class="el-icon-document" />
          <span class="title">点位列表</span>
          <span class="count">共 {{ total }} 条记录</span>
        </div>

        <div class="right">
          <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['manage:node:add']">新增</el-button>
          <el-button type="success" icon="Edit" :disabled="single" @click="handleUpdate"
            v-hasPermi="['manage:node:edit']">修改</el-button>
          <el-button type="danger" icon="Delete" :disabled="multiple" @click="handleDelete"
            v-hasPermi="['manage:node:remove']">删除</el-button>
          <el-button type="warning" icon="Download" @click="handleExport"
            v-hasPermi="['manage:node:export']">导出</el-button>
          <el-button text icon="Refresh" @click="getList">
            刷新
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="nodeList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="序号" type="index" align="center" width="50" prop="id" />
        <el-table-column label="点位名称" align="center" prop="nodeName" />
        <el-table-column label="区域名称" align="center" prop="region.regionName" />
        <el-table-column label="商圈类型" align="center" prop="businessType">
          <template #default="scope">
            <dict-tag :options="business_type" :value="scope.row.businessType" />
          </template>
        </el-table-column>
        <el-table-column label="合作商名称" align="center" prop="partner.partnerName" />
        <el-table-column label="详细地址" align="left" prop="address" show-overflow-tooltip />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="getNodeInfo(scope.row)"
              v-hasPermi="['manage:vm:list']">查看详情</el-button>
            <el-button link type="primary" @click="handleUpdate(scope.row)"
              v-hasPermi="['manage:node:edit']">修改</el-button>
            <el-button link type="primary" @click="handleDelete(scope.row)"
              v-hasPermi="['manage:node:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize" @pagination="getList" />
    </div>

    <!-- 添加或修改点位管理对话框 -->
    <el-dialog :title="title" v-model="open" class="dialog-lg" append-to-body>
      <el-form ref="nodeRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="点位名称" prop="nodeName">
          <el-input v-model="form.nodeName" placeholder="请输入点位名称" />
        </el-form-item>
        <el-form-item label="所属区域" prop="regionId">
          <!-- <el-input v-model="form.regionId" placeholder="请输入区域ID" /> -->
          <el-select v-model="form.regionId" placeholder="请选择区域" clearable filterable>
            <el-option v-for="item in regionList" :key="item.id" :label="item.regionName" :value="item.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="商圈类型" prop="businessType">
          <el-select v-model="form.businessType" placeholder="请选择商圈类型">
            <el-option v-for="dict in business_type" :key="dict.value" :label="dict.label"
              :value="parseInt(dict.value)"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="归属合作商" prop="partnerId">
          <!-- <el-input v-model="form.partnerId" placeholder="请输入合作商ID" /> -->
          <el-select v-model="form.partnerId" placeholder="请选择合作商" clearable filterable>
            <el-option v-for="item in partnerList" :key="item.id" :label="item.partnerName"
              :value="item.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入详细地址" type="textarea" :rows="3"
            :autosize="{ minRows: 3, maxRows: 6 }" resize="vertical" show-word-limit maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog title="点位管理" v-model="nodeOpen" class="dialog-lg" append-to-body>
      <el-table v-loading="loading" :data="vmList" @selection-change="handleSelectionChange">
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="设备编号" align="center" prop="innerCode" />
        <el-table-column label="设备状态" align="center" prop="vmStatus">
          <template #default="scope">
            <dict-tag :options="vm_status" :value="scope.row.vmStatus" />
          </template>
        </el-table-column>
        <el-table-column label="最后一次供货时间" align="center" prop="vmStatus">
          <template #default="scope">
            {{ parseTime(scope.row.lastSupplyTime, "{y}-{m}-{d} {h}:{i}:{s}") }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="Node">
import { listNode, getNode, delNode, addNode, updateNode } from "@/api/manage/node";
import { listRegion } from '@/api/manage/region';
import { listPartner } from '@/api/manage/partner';
import { listVm } from "@/api/manage/vm";
import { loadAllParams } from "../../../api/page";
import { parse } from "@vue/compiler-sfc";
const { proxy } = getCurrentInstance();
const { business_type } = proxy.useDict('business_type');
const { vm_status } = proxy.useDict('vm_status');
const nodeList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    nodeName: null,
    regionId: null,
    partnerId: null,
  },
  rules: {
    nodeName: [
      { required: true, message: "点位名称不能为空", trigger: "blur" }
    ],
    address: [
      { required: true, message: "详细地址不能为空", trigger: "blur" }
    ],
    businessType: [
      { required: true, message: "商圈类型不能为空", trigger: "change" }
    ],
    regionId: [
      { required: true, message: "请选择区域", trigger: "change" }
    ],
    partnerId: [
      { required: true, message: "请选择合作商", trigger: "change" }
    ],
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询点位管理列表 */
function getList() {
  loading.value = true;
  listNode(queryParams.value).then(response => {
    nodeList.value = response.rows || [];
    total.value = Number(response.total) || 0;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    nodeName: null,
    address: null,
    businessType: null,
    regionId: null,
    partnerId: null,
    createTime: null,
    updateTime: null,
    createBy: null,
    updateBy: null,
    remark: null
  };
  proxy.resetForm("nodeRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加点位管理";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getNode(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改点位管理";
  });
}
//查看详情
const nodeOpen = ref(false);
const vmList = ref([]);
function getNodeInfo(row) {
  //根据点位，查询设备
  loadAllParams.nodeId = row.id;
  listVm(loadAllParams).then(response => {
    vmList.value = response.rows;
    nodeOpen.value = true;
  });
}


/** 提交按钮 */
function submitForm() {
  proxy.$refs["nodeRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateNode(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addNode(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  const _names = row ? row.nodeName : '选中的';
  proxy.$modal.confirm('是否确认删除点位"' + _names + '"？').then(function () {
    return delNode(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('manage/node/export', {
    ...queryParams.value
  }, `node_${new Date().getTime()}.xlsx`)
}
//查询所有条件对象
// const loadAllParams = reactive({
//   pageNum: 1,
//   pageSize: 10000
// })
//新增区域列表
const regionList = ref([]);
function getRegionList() {
  listRegion({
    pageNum: 1,
    pageSize: 1000
  }).then(response => {
    console.log('区域API响应:', response);

    // 处理RuoYi-Vue标准分页响应
    if (response && response.rows) {
      regionList.value = response.rows;
    }
    // 处理直接返回数组的情况
    else if (response && Array.isArray(response)) {
      regionList.value = response;
    }
    // 处理AjaxResult包装的情况
    else if (response && response.data) {
      if (Array.isArray(response.data)) {
        regionList.value = response.data;
      } else if (response.data.rows) {
        regionList.value = response.data.rows;
      }
    }

    console.log('处理后的区域列表:', regionList.value);
  }).catch(error => {
    console.error('获取区域列表失败:', error);
    proxy.$modal.msgError("获取区域列表失败");
  });
}
const partnerList = ref([]);
//获取合作商列表
function getPartnerList() {
  listPartner({
    pageNum: 1,
    pageSize: 1000
  }).then(response => {
    console.log('合作商API响应:', response);

    // 处理响应数据的方式与区域列表相同
    if (response && response.rows) {
      partnerList.value = response.rows;
    }
    else if (response && Array.isArray(response)) {
      partnerList.value = response;
    }
    else if (response && response.data) {
      if (Array.isArray(response.data)) {
        partnerList.value = response.data;
      } else if (response.data.rows) {
        partnerList.value = response.data.rows;
      }
    }

    console.log('处理后的合作商列表:', partnerList.value);
  }).catch(error => {
    console.error('获取合作商列表失败:', error);
    proxy.$modal.msgError("获取合作商列表失败");
  });
}

getRegionList();
getPartnerList();
getList();

</script>

<style scoped lang="scss" src="../index.scss"></style>
