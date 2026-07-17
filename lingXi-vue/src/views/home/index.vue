<template>
  <div class="app-container home">
    <!-- 第一行：左侧主数据 + 右侧排行榜（同高） -->
    <el-row
      :gutter="24"
      class="home-row home-row-stretch"
    >
      <!-- 左侧主区域 -->
      <el-col :xl="18" :lg="18" :md="24" :sm="24" :xs="24">
        <!-- 统计卡片 -->
        <el-row :gutter="20" class="stats-row">
          <el-col :xl="13" :lg="13" :md="24" :sm="24" :xs="24">
            <home-user-task-stats class="home-card stats-card" />
          </el-col>
          <el-col :xl="11" :lg="11" :md="24" :sm="24" :xs="24">
            <home-sku-sale-stats class="home-card stats-card" />
          </el-col>
        </el-row>

        <!-- 销售汇总图表 -->
        <sku-sale-collect-chart class="home-chart-card" />
      </el-col>

      <!-- 右侧排行榜（强制铺满） -->
      <el-col
        :xl="6"
        :lg="6"
        :md="24"
        :sm="24"
        :xs="24"
        class="rank-col"
      >
        <sku-sale-rank-chart class="home-rank-card rank-full" />
      </el-col>
    </el-row>

    <!-- 第二行 -->
    <el-row :gutter="24" class="home-row">
      <el-col :xl="14" :lg="14" :md="24" :sm="24" :xs="24">
        <partner-node-collect-chart class="home-chart-card" />
      </el-col>
      <el-col :xl="10" :lg="10" :md="24" :sm="24" :xs="24">
        <abnormal-equipment-table class="home-table-card" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Index">
import HomeUserTaskStats from './components/home-user-task-stats.vue'
import HomeSkuSaleStats from './components/home-sku-sale-stats.vue'
import SkuSaleRankChart from './components/sku-sale-rank-chart.vue'
import SkuSaleCollectChart from './components/sku-sale-collect-chart.vue'
import PartnerNodeCollectChart from './components/partner-node-collect-chart.vue'
import AbnormalEquipmentTable from './components/abnormal-equipment-table.vue'
</script>

<style scoped lang="scss">
.home {
  padding: 24px;
  background: var(--lx-canvas);
  min-height: calc(100dvh - 92px);
  color: var(--lx-text);

  /* ================= 行布局 ================= */
  .home-row {
    margin-bottom: 24px;
  }

  /* 第一行强制等高 */
  .home-row-stretch {
    display: flex;
    align-items: stretch;
  }

  .stats-row {
    margin-bottom: 20px;

    .el-col {
      display: flex;
    }
  }

  /* ================= 通用卡片 ================= */
  :deep(.home-card),
  :deep(.home-chart-card),
  :deep(.home-rank-card),
  :deep(.home-table-card) {
    position: relative;
    background: #fff;
    border: 1px solid var(--lx-border-soft);
    border-radius: var(--lx-radius);
    padding: 20px;
    width: 100%;
    box-shadow: var(--lx-shadow-sm);
    transition: all 0.28s ease;

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 16px 36px rgba(16, 42, 67, 0.1);
    }

    /* 顶部渐变强调线 */
    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: linear-gradient(90deg, var(--lx-primary), #38b2ac);
      border-radius: 14px 14px 0 0;
    }

    .title,
    .header {
      font-size: 16px;
      font-weight: 600;
      color: var(--lx-navy);
      margin-bottom: 16px;
      padding-bottom: 10px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid #f0f0f0;

      .sub-title {
        font-size: 12px;
        color: var(--lx-muted);
        margin-left: 8px;
      }
    }
  }

  /* ================= 统计卡片 ================= */
  :deep(.stats-card) {
    min-height: 190px;
    padding: 16px 18px;

    .title {
      font-size: 15px;
    }
  }

  /* ================= 图表卡片 ================= */
  :deep(.home-chart-card) {
    min-height: 340px;
  }

  /* ================= 排行榜：铺满核心 ================= */
  .rank-col {
    display: flex;
  }

  :deep(.rank-full) {
    height: 100%;
  }

  :deep(.home-rank-card) {
    height: 100%;
    min-height: unset;
    background: linear-gradient(180deg, #ffffff 0%, #f2f8f7 100%);
  }

  /* ================= 表格 ================= */
  :deep(.home-table-card) {
    min-height: 340px;

    .el-table {
      border-radius: 12px;
      font-size: 13px;
    }

    .el-table__header th {
      background: #fafafa;
      font-weight: 600;
      height: 44px;
    }
  }

  /* ================= 响应式 ================= */
  @media (max-width: 1200px) {
    padding: 18px;
  }

  @media (max-width: 768px) {
    padding: 12px;

    .home-row-stretch {
      flex-direction: column;
    }
  }
}
</style>
