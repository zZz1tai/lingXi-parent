<template>
  <div class="app-container manage-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title">
        <i class="el-icon-eleme" />
        <span>设备类型管理中心</span>
      </div>
      <div class="sub-title">高效管理系统设备类型配置</div>
    </div>
    <!-- 筛选条件 -->
    <div class="card search-card" v-show="showSearch">
      <div class="card-title">
        <i class="el-icon-filter" />
        <span>筛选条件</span>
      </div>

      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="search-form">
        <el-form-item label="型号名称" prop="name">
          <el-input v-model="queryParams.name" placeholder="请输入型号名称" clearable @keyup.enter="handleQuery" />
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
          <span class="title">设备类型列表</span>
          <span class="count">共 {{ total }} 条记录</span>
        </div>

        <div class="right">
          <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['manage:vmType:add']">新增</el-button>
          <el-button type="success" icon="Edit" :disabled="single" @click="handleUpdate"
            v-hasPermi="['manage:vmType:edit']">修改</el-button>
          <el-button type="danger" icon="Delete" :disabled="multiple" @click="handleDelete"
            v-hasPermi="['manage:vmType:remove']">删除</el-button>
          <el-button type="warning" icon="Download" @click="handleExport"
            v-hasPermi="['manage:vmType:export']">导出</el-button>
          <el-button text icon="Refresh" @click="getList">
            刷新
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="vmTypeList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="主键" align="center" prop="id" />
        <el-table-column label="型号名称" align="center" prop="name" />
        <el-table-column label="型号编码" align="center" prop="model" />
        <el-table-column label="设备图片" align="center" prop="image" width="100">
          <template #default="scope">
            <image-preview :src="scope.row.image" :width="50" :height="50" />
          </template>
        </el-table-column>
        <el-table-column label="货道行" align="center" prop="vmRow" />
        <el-table-column label="货道列" align="center" prop="vmCol" />
        <el-table-column label="设备容量" align="center" prop="channelMaxCapacity" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)"
              v-hasPermi="['manage:vmType:edit']">修改</el-button>
            <el-button link type="primary" @click="handleDelete(scope.row)"
              v-hasPermi="['manage:vmType:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize" @pagination="getList" />
    </div>

    <!-- 添加或修改设备类型管理对话框 -->
    <el-dialog :title="title" v-model="open" class="dialog-lg" append-to-body>
      <el-form ref="vmTypeRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="型号名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入型号名称" />
        </el-form-item>
        <el-form-item label="型号编码" prop="model">
          <el-input v-model="form.model" placeholder="请输入型号编码" />
        </el-form-item>
        <el-form-item label="货道数" prop="vmRow">
          <el-input-number v-model="form.vmRow" placeholder="请输入货道行" :min="1" :max="20" controls-position="right"
            style="width: 40%" />行 &nbsp;&nbsp;
          <el-input-number v-model="form.vmCol" placeholder="请输入货道列" :min="1" :max="20" controls-position="right"
            style="width: 40%" />列

        </el-form-item>
        <el-form-item label="货道容量" prop="channelMaxCapacity">
          <el-input-number v-model="form.channelMaxCapacity" placeholder="请输入设备容量" :min="1" :max="10"
            controls-position="right" style="width: 50%" />
        </el-form-item>
        <el-form-item label="设备图片" prop="image">
          <image-upload v-model="form.image" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="VmType">
import { listVmType, getVmType, delVmType, addVmType, updateVmType } from "@/api/manage/vmType";

const { proxy } = getCurrentInstance();

const vmTypeList = ref([]);
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
    name: null,
    model: null,
  },
  rules: {
    name: [
      { required: true, message: "型号名称不能为空", trigger: "blur" }
    ],
    model: [
      { required: true, message: "型号编码不能为空", trigger: "blur" }
    ],
    image: [
      { required: true, message: "设备图片不能为空", trigger: "blur" }
    ],
    vmRow: [
      { required: true, message: "货道行不能为空", trigger: "blur" }
    ],
    vmCol: [
      { required: true, message: "货道列不能为空", trigger: "blur" }
    ],
    channelMaxCapacity: [
      { required: true, message: "设备容量不能为空", trigger: "blur" }
    ]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询设备类型管理列表 */
function getList() {
  loading.value = true;
  listVmType(queryParams.value).then(response => {
    vmTypeList.value = response.rows || [];
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
    name: null,
    model: null,
    image: null,
    vmRow: null,
    vmCol: null,
    channelMaxCapacity: null
  };
  proxy.resetForm("vmTypeRef");
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
  title.value = "添加设备类型管理";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getVmType(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改设备类型管理";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["vmTypeRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateVmType(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addVmType(form.value).then(response => {
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
  proxy.$modal.confirm('是否确认删除设备类型管理编号为"' + _ids + '"的数据项？').then(function () {
    return delVmType(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => { });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('manage/vmType/export', {
    ...queryParams.value
  }, `vmType_${new Date().getTime()}.xlsx`)
}

getList();
</script>

<style scoped lang="scss" src="../index.scss"></style>
