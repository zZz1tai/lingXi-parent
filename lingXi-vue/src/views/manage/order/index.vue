<template>
  <div class="app-container manage-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="title">
        <i class="el-icon-s-order" />
        <span>订单管理中心</span>
      </div>
      <div class="sub-title">统一管理系统订单数据</div>
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
        <el-form-item label="订单编号" prop="orderNo">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入订单编号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>

        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 280px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">
            搜索
          </el-button>
          <el-button icon="Refresh" @click="resetQuery">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格区 -->
    <div class="card table-card">
      <div class="table-header">
        <div class="left">
          <i class="el-icon-document" />
          <span class="title">订单列表</span>
          <span class="count">共 {{ total }} 条记录</span>
        </div>

        <div class="right">
          <el-button text icon="Refresh" @click="getList">
            刷新
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="orderList"
        @selection-change="handleSelectionChange"
      >
        <el-table-column
          label="序号"
          type="index"
          width="80"
          align="center"
        />

        <el-table-column
          label="订单编号"
          prop="orderNo"
          align="center"
          show-overflow-tooltip
        />

        <el-table-column
          label="商品明细"
          prop="skuName"
          align="center"
          min-width="220"
          show-overflow-tooltip
        />

        <el-table-column label="订单状态" prop="status" align="center">
          <template #default="scope">
            <dict-tag
              :options="order_status"
              :value="scope.row.status"
            />
          </template>
        </el-table-column>

        <el-table-column
          label="订单金额"
          align="center"
        >
          <template #default="scope">
            {{ (scope.row.amount / 100).toFixed(2) }}
          </template>
        </el-table-column>

        <el-table-column
          label="订单时间"
          prop="createTime"
          width="180"
          align="center"
        >
          <template #default="scope">
            {{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
          </template>
        </el-table-column>

        <el-table-column
          label="操作"
          align="center"
          width="140"
          fixed="right"
        >
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="handleUpdate(scope.row)"
              v-hasPermi="['manage:order:query']"
            >
              查看详情
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

    <!-- 订单详情 -->
    <el-dialog
      :title="title"
      v-model="open"
      class="dialog-lg"
      append-to-body
    >
      <el-form
        ref="orderRef"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="订单编号">
          {{ form.orderNo }}
        </el-form-item>
        <el-form-item label="商品明细">
          {{ form.skuName }}
        </el-form-item>
        <el-form-item label="订单金额">
          {{ (form.amount / 100).toFixed(2) }}
        </el-form-item>
        <el-form-item label="订单状态">
          <dict-tag :options="order_status" :value="form.status" />
        </el-form-item>
        <el-form-item label="支付类型">
          {{ form.payType === '1' ? '支付宝' : '微信支付' }}
        </el-form-item>
        <el-form-item label="订单时间">
          {{ parseTime(form.createTime, '{y}-{m}-{d} {h}:{i}:{s}') }}
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="cancel">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Order">
import { listOrder, getOrder } from "@/api/manage/order";

const { proxy } = getCurrentInstance();
const { order_status } = proxy.useDict("order_status");

const orderList = ref([]);
const open = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const total = ref(0);
const title = ref("");
const dateRange = ref([]);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    orderNo: null,
    createTime: null
  },
  rules: {}
});

const { queryParams, form, rules } = toRefs(data);

/** 查询订单 */
function getList() {
  loading.value = true;
  listOrder(proxy.addDateRange(queryParams.value, dateRange.value))
    .then(res => {
      orderList.value = res.rows || [];
      total.value = Number(res.total) || 0;
      loading.value = false;
    });
}

function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

function resetQuery() {
  dateRange.value = [];
  proxy.resetForm("queryRef");
  handleQuery();
}

function handleUpdate(row) {
  getOrder(row.id).then(res => {
    form.value = res.data;
    open.value = true;
    title.value = "订单详情";
  });
}

function cancel() {
  open.value = false;
}

getList();
</script>

<style scoped lang="scss" src="../index.scss"></style>
