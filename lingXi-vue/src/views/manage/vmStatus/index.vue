<template>
  <div class="app-container manage-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title">
        <i class="el-icon-cpu" />
        <span>设备状态管理中心</span>
      </div>
      <div class="sub-title">高效管理系统设备状态监控</div>
    </div>
    <!-- 筛选条件 -->
    <div class="card search-card" v-show="showSearch">
      <div class="card-title">
        <i class="el-icon-filter" />
        <span>筛选条件</span>
      </div>

      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="search-form">
        <el-form-item label="设备编号" prop="innerCode">
          <el-input v-model="queryParams.innerCode" placeholder="请输入设备编号" clearable @keyup.enter="handleQuery" />
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
          <span class="title">设备状态列表</span>
          <span class="count">共 {{ total }} 条记录</span>
        </div>

        <div class="right">
          <el-button text icon="Refresh" @click="getList">
            刷新
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="vmList" @selection-change="handleSelectionChange">
        <el-table-column label="序号" type="index" width="80" align="center" />
        <el-table-column label="设备编号" align="center" prop="innerCode" />
        <el-table-column label="设备型号" align="center" prop="vmTypeId">
          <template #default="scope">
            <div v-for="item in vmTypeList" :key="item.id">
              <span v-if="item.id === scope.row.vmTypeId">{{ item.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="详细地址" align="center" prop="addr" show-overflow-tooltip />
        <el-table-column label="合作商" align="center" prop="partnerId">
          <template #default="scope">
            <div v-for="item in partnerList" :key="item.id">
              <span v-if="item.id === scope.row.partnerId">{{ item.partnerName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="运营状态" align="center" prop="vmStatus">
          <template #default="scope">
            <dict-tag :options="vm_status" :value="scope.row.vmStatus" />
          </template>
        </el-table-column>
        <el-table-column label="设备状态" align="center" prop="vmStatus">
          <template #default="scope">
            <span v-if="scope.row.runningStatus != null">
              {{ JSON.parse(scope.row.runningStatus).status == true ? '正常' : '异常' }}
            </span>
            <span v-else>异常</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)"
              v-hasPermi="['manage:vm:edit']">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize" @pagination="getList" />
    </div>

    <!-- 查看设备详情对话框 -->
    <el-dialog :title="title" v-model="open" class="dialog-lg" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="设备编号">
          {{ form.innerCode }}
        </el-form-item>
        <el-form-item label="设备类型">
          <div v-for="item in vmTypeList" :key="item.id">
            <span v-if="item.id === form.vmTypeId">{{ item.name }}</span>
          </div>
        </el-form-item>
        <el-form-item label="详细地址">
          {{ form.addr }}
        </el-form-item>
        <el-form-item label="合作商">
          <div v-for="item in partnerList" :key="item.id">
            <span v-if="item.id === form.partnerId">{{ item.partnerName }}</span>
          </div>
        </el-form-item>
        <el-form-item label="运营状态">
          <dict-tag :options="vm_status" :value="form.vmStatus" />
        </el-form-item>
        <el-form-item label="设备状态">
          <span v-if="form.runningStatus != null">
            {{ JSON.parse(form.runningStatus).status == true ? '正常' : '异常' }}
          </span>
          <span v-else>异常</span>
        </el-form-item>
        <el-form-item label="维修次数">
          {{ form.maintenanceCount || 0 }}
        </el-form-item>
        <el-form-item label="最后补货时间">
          {{ parseTime(form.lastSupplyTime, "{y}-{m}-{d} {h}:{i}:{s}") }}
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="cancel">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Vm">
import { listVm, getVm, delVm, addVm, updateVm } from "@/api/manage/vm";
import { listVmType } from "@/api/manage/vmType";
import { listPartner } from "@/api/manage/partner";
import { loadAllParams } from "@/api/page";
import { listNode } from "@/api/manage/node";
import { listRegion } from "@/api/manage/region";
import { getMaintenanceCount } from "@/api/manage/task";
import { ref, reactive, toRefs } from "vue";
import { parseTime } from "@/utils/ruoyi";

const { proxy } = getCurrentInstance();
const { vm_status } = proxy.useDict('vm_status');

const vmList = ref([]);
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
    innerCode: null,
    nodeId: null,
    businessType: null,
    regionId: null,
    partnerId: null,
    vmTypeId: null,
    vmStatus: null,
    runningStatus: null,
    policyId: null,
  },
  rules: {
    nodeId: [
      { required: true, message: "点位Id不能为空", trigger: "blur" }
    ],
    vmTypeId: [
      { required: true, message: "设备型号不能为空", trigger: "blur" }
    ],
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询设备管理列表 */
function getList() {
  loading.value = true;
  listVm(queryParams.value).then(response => {
    vmList.value = response.rows || [];
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
    innerCode: null,
    channelMaxCapacity: null,
    nodeId: null,
    addr: null,
    lastSupplyTime: null,
    businessType: null,
    regionId: null,
    partnerId: null,
    vmTypeId: null,
    vmStatus: null,
    runningStatus: null,
    longitudes: null,
    latitude: null,
    clientId: null,
    policyId: null,
    createTime: null,
    updateTime: null,
    maintenanceCount: 0
  };
  proxy.resetForm("vmRef");
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
  title.value = "添加设备管理";
}

/** 查看详情按钮操作 */
function handleUpdate(row) {
  reset();
  // 确保使用row.id，而不是ids.value，因为ids.value是为批量操作准备的
  const _id = row.id;
  if (!_id) {
    console.error('设备ID为空，无法查看详情');
    return;
  }
  getVm(_id).then(response => {
    form.value = response.data;
    // 获取设备维修次数
    if (form.value.innerCode) {
      getMaintenanceCount(form.value.innerCode).then(res => {
        form.value.maintenanceCount = res.data;
      }).catch(error => {
        console.error('获取维修次数失败:', error);
        // 即使获取维修次数失败，也要打开对话框
        form.value.maintenanceCount = 0;
      });
    }
    open.value = true;
    title.value = "查看设备详情";
  }).catch(error => {
    console.error('获取设备详情失败:', error);
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('是否确认删除设备管理编号为"' + _ids + '"的数据项？').then(function () {
    return delVm(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('manage/vm/export', {
    ...queryParams.value
  }, `vm_${new Date().getTime()}.xlsx`)
}

//查询设备类型表
const vmTypeList = ref([]);
function getVmTypeList() {
  listVmType(loadAllParams).then(response => {
    vmTypeList.value = response.rows;
  });
}

//查询合作商列表
const partnerList = ref([]);
function getPartnerList() {
  listPartner(loadAllParams).then(response => {
    partnerList.value = response.rows;
  });
}
//查询点位列表
const nodeList = ref([]);
function getNodeList() {
  listNode(loadAllParams).then(response => {
    nodeList.value = response.rows;
  });
}

//查询区域列表
const regionList = ref([]);
function getRegionList() {
  listRegion(loadAllParams).then(response => {
    regionList.value = response.rows;
  });
}



getRegionList();
getNodeList();
getVmTypeList();
getPartnerList();
getList();
</script>

<style scoped lang="scss" src="../index.scss"></style>
