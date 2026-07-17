<template>
  <div class="app-container manage-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title">
        <i class="el-icon-s-custom" />
        <span>员工管理中心</span>
      </div>
      <div class="sub-title">统一管理系统员工数据</div>
    </div>

    <!-- 筛选条件 -->
    <div class="card search-card" v-show="showSearch">
      <div class="card-title">
        <i class="el-icon-filter" />
        <span>筛选条件</span>
      </div>

      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px" class="search-form">
        <el-form-item label="员工名称" prop="userName">
          <el-input v-model="queryParams.userName" placeholder="请输入员工名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="角色类型" prop="roleId">
          <el-select v-model="queryParams.roleId" placeholder="请选择角色类型" clearable>
            <el-option v-for="item in roleList" :key="item.id" :label="item.roleName" :value="Number(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否启用" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择是否启用" clearable>
            <el-option label="是" value="1" />
            <el-option label="否" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否绑定" prop="hasUserId">
          <el-select v-model="queryParams.hasUserId" placeholder="请选择是否绑定账号" clearable>
            <el-option label="是" value="1" />
            <el-option label="否" value="0" />
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
          <span class="title">员工列表</span>
          <span class="count">共 {{ total }} 条记录</span>
        </div>

        <div class="right">
          <el-button type="primary" icon="Plus" @click="handleAdd" v-hasPermi="['manage:emp:add']">新增</el-button>
          <el-button type="success" icon="Edit" :disabled="single" @click="handleUpdate"
            v-hasPermi="['manage:emp:edit']">修改</el-button>
          <el-button type="danger" icon="Delete" :disabled="multiple" @click="handleDelete"
            v-hasPermi="['manage:emp:remove']">删除</el-button>
          <el-button type="warning" icon="Download" @click="handleExport"
            v-hasPermi="['manage:emp:export']">导出</el-button>
          <el-button text icon="Refresh" @click="getList">
            刷新
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="empList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="序号" type="index" align="center" width="50" />
        <el-table-column label="员工名称" align="center" prop="userName" />
        <el-table-column label="归属区域" align="center" prop="regionName" />
        <el-table-column label="角色" align="center" prop="roleName" />
        <el-table-column label="联系电话" align="center" prop="mobile" />
        <el-table-column label="是否启用" align="center">
          <template #default="scope">
            <el-tag type="success" v-if="scope.row.status === 1 || scope.row.status === '1'">是</el-tag>
            <el-tag type="danger" v-else>否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否绑定账号" align="center">
          <template #default="scope">
            <el-tag type="success" v-if="scope.row.userId">是</el-tag>
            <el-tag type="danger" v-else>否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdate(scope.row)"
              v-hasPermi="['manage:emp:edit']">修改</el-button>
            <el-button link type="primary" @click="handleDelete(scope.row)"
              v-hasPermi="['manage:emp:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件：移到表格下方右侧 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageIndex"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total || 0"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 添加或修改员工对话框 -->
    <el-dialog :title="title" v-model="open" class="dialog-lg" append-to-body>
      <el-form ref="empRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="人员名称" prop="userName">
          <el-input v-model="form.userName" placeholder="请输入人员名称" />
        </el-form-item>
        <el-form-item label="登录密码" prop="password" v-if="!form.id">
          <el-input v-model="form.password" type="password" placeholder="请输入登录密码" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="请选择角色" clearable>
            <el-option v-for="item in roleList" :key="item.id" :label="item.roleName" :value="Number(item.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话" prop="mobile">
          <el-input v-model="form.mobile" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="负责区域" prop="regionId">
          <el-select v-model="form.regionId" placeholder="请选择负责区域" clearable>
            <el-option v-for="item in regionList" :key="item.id" :label="item.regionName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="头像" prop="image">
          <image-upload v-model="form.image" />
        </el-form-item>
        <el-form-item label="是否启用" prop="status">
          <el-checkbox v-model="form.status" true-label="1" false-label="0">启用</el-checkbox>
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

<script setup name="Emp">
import { ref, reactive, toRefs, getCurrentInstance } from "vue";
import { listEmp, getEmp, delEmp, addEmp, updateEmp } from "@/api/manage/emp";
import { listRole } from "@/api/manage/role";
import { loadAllParams } from "@/api/page";
import { listRegion } from "@/api/manage/region";

const { proxy } = getCurrentInstance();
const { emp_status } = proxy.useDict('emp_status');

const empList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const title = ref("");

// 分页信息
const pagination = reactive({
  pageIndex: 1,
  pageSize: 10,
});

const data = reactive({
  form: {},
  queryParams: {
    userName: null,
    roleId: null,
    roleCode: null,
    status: null,
  },
  rules: {
    userName: [{ required: true, message: "员工名称不能为空", trigger: "blur" }],
    password: [{ required: true, message: "登录密码不能为空", trigger: "blur" }],
    regionId: [{ required: true, message: "所属区域Id不能为空", trigger: "blur" }],
    roleId: [{ required: true, message: "角色id不能为空", trigger: "change" }],
    mobile: [{ required: true, message: "联系电话不能为空", trigger: "blur" }],
    image: [{ required: true, message: "员工头像不能为空", trigger: "blur" }],
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询员工列表 */
function getList() {
  loading.value = true;
  const params = {
    ...queryParams.value,
    pageNum: pagination.pageIndex,
    pageSize: pagination.pageSize
  };
  // 清除空参数
  Object.keys(params).forEach(key => {
    if (params[key] === '' || params[key] === null || params[key] === undefined) {
      delete params[key];
    }
  });

  listEmp(params).then(response => {
    empList.value = response.rows || [];
    total.value = Number(response.total) || 0;  // 强制转为数字

    loading.value = false;
  }).catch(err => {
    console.error('获取员工列表失败:', err);
    empList.value = [];
    total.value = 0;
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
    userName: null,
    password: null,
    regionId: null,
    regionName: null,
    roleId: null,
    roleCode: null,
    roleName: null,
    mobile: null,
    image: null,
    status: "1", // 默认启用
    createTime: null,
    updateTime: null
  };
  proxy.resetForm("empRef");
}

/** 搜索按钮操作 */
function handleQuery() {
  pagination.pageIndex = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  pagination.pageIndex = 1;
  getList();
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.pageSize = size;
  getList();
};

// 当前页变化
const handleCurrentChange = (current) => {
  pagination.pageIndex = current;
  getList();
};

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id);
  single.value = selection.length !== 1;
  multiple.value = !selection.length;
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加员工";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value[0];
  getEmp(_id).then(response => {
    form.value = response.data;
    form.value.roleId = Number(form.value.roleId);
    open.value = true;
    title.value = "修改员工";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs.empRef.validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateEmp(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        });
      } else {
        addEmp(form.value).then(() => {
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
  const _ids = row?.id || ids.value.join(',');
  const _names = row ? row.userName : '选中的员工';
  proxy.$modal.confirm(`是否确认删除员工"${_names}"？`).then(() => {
    return delEmp(_ids);
  }).then(() => {
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('manage/emp/export', { ...queryParams.value }, `emp_${new Date().getTime()}.xlsx`);
}

// 角色列表
const roleList = ref([]);
function getRoleList() {
  listRole(loadAllParams).then(response => {
    roleList.value = response.rows || [];
  });
}

// 区域列表
const regionList = ref([]);
function getRegionList() {
  listRegion(loadAllParams).then(response => {
    regionList.value = response.rows || [];
  });
}

// 初始化数据
getRegionList();
getRoleList();
getList();
</script>

<style scoped lang="scss" src="../index.scss"></style>