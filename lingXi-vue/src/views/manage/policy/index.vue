<template>
  <div class="app-container manage-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title">
        <i class="icon el-icon-s-operation" />
        <span>策略管理中心</span>
      </div>
      <div class="sub-title">高效管理系统策略配置</div>
    </div>

    <!-- 筛选条件 -->
    <div class="card search-card" v-show="showSearch">
      <div class="card-title">
        <i class="el-icon-filter" />
        <span>筛选条件</span>
      </div>

      <el-form
        ref="queryRef"
        :model="queryParams"
        :inline="true"
        label-width="68px"
        class="search-form"
      >
        <el-form-item label="策略名称" prop="policyName">
          <el-input
            v-model="queryParams.policyName"
            placeholder="请输入策略名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            icon="Search"
            class="primary-btn"
            @click="handleQuery"
          >
            搜索
          </el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格列表 -->
    <div class="card table-card">
      <div class="table-header">
        <div class="left">
          <i class="el-icon-document" />
          <span class="title">策略列表</span>
          <span class="count">共 {{ total }} 条记录</span>
        </div>

        <div class="right">
          <el-button type="primary" icon="Plus" @click="handleAdd">
            新增
          </el-button>
          <el-button
            type="success"
            icon="Edit"
            :disabled="single"
            @click="handleUpdate"
          >
            修改
          </el-button>
          <el-button
            type="danger"
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
          >
            删除
          </el-button>
          <el-button icon="Download" @click="handleExport">
            导出
          </el-button>
          <el-button text icon="Refresh" @click="getList">
            刷新
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="policyList"
        table-layout="fixed"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column label="序号" type="index" width="60" align="center" />

        <el-table-column
          label="策略名称"
          prop="policyName"
          min-width="220"
        />

        <el-table-column label="策略方案" width="140" align="center">
          <template #default="{ row }">
            <el-tag type="success" effect="light">
              {{ row.discount }} 折
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          label="创建时间"
          prop="createTime"
          width="180"
          align="center"
        >
          <template #default="scope">
            {{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}') }}
          </template>
        </el-table-column>

        <el-table-column
          label="操作"
          width="200"
          fixed="right"
          align="center"
        >
          <template #default="scope">
            <el-button link type="primary" @click="getPolicyInfo(scope.row)">
              详情
            </el-button>
            <el-button link type="primary" @click="handleUpdate(scope.row)">
              修改
            </el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog :title="title" v-model="open" class="dialog-md">
      <el-form ref="policyRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="策略名称" prop="policyName">
          <el-input v-model="form.policyName" />
        </el-form-item>
        <el-form-item label="策略方案" prop="discount">
          <el-input-number v-model="form.discount" :min="1" :max="100" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button type="primary" @click="submitForm">确定</el-button>
        <el-button @click="cancel">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Policy">
import {
  listPolicy,
  getPolicy,
  delPolicy,
  addPolicy,
  updatePolicy
} from "@/api/manage/policy";

const { proxy } = getCurrentInstance();

const policyList = ref([]);
const loading = ref(false);
const showSearch = ref(true);
const open = ref(false);
const title = ref("");
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    policyName: null
  },
  form: {},
  rules: {
    policyName: [{ required: true, message: "请输入策略名称", trigger: "blur" }],
    discount: [{ required: true, message: "请输入折扣方案", trigger: "blur" }]
  }
});

const { queryParams, form, rules } = toRefs(data);

function getList() {
  loading.value = true;
  listPolicy(queryParams.value).then(res => {
    policyList.value = res.rows || [];
    total.value = Number(res.total) || 0;
    loading.value = false;
  });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.policyId);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

function handleAdd() {
  form.value = {};
  open.value = true;
  title.value = "新增策略";
}

function handleUpdate(row) {
  if (!row || !row.policyId) {
    // 如果没有传递row参数，或者row.policyId不存在，直接返回
    return;
  }
  getPolicy(row.policyId).then(res => {
    form.value = res.data;
    open.value = true;
    title.value = "修改策略";
  });
}

function submitForm() {
  proxy.$refs.policyRef.validate(valid => {
    if (!valid) return;
    const api = form.value.policyId ? updatePolicy : addPolicy;
    api(form.value).then(() => {
      proxy.$modal.msgSuccess("操作成功");
      open.value = false;
      getList();
    });
  });
}

function handleDelete(row) {
  const _ids = row?.policyId || ids.value;
  proxy.$modal.confirm("确认删除选中的策略？").then(() => {
    delPolicy(_ids).then(() => {
      proxy.$modal.msgSuccess("删除成功");
      getList();
    });
  });
}

function handleExport() {
  proxy.download("manage/policy/export", { ...queryParams.value });
}

getList();
</script>

<style scoped lang="scss" src="../index.scss"></style>
