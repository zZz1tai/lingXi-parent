<template>
  <div class="home-wrapper">
  <div class="app-container home">
    <header class="overview-header"><div><p>业务概览</p><h1>工作台</h1><span>销售、设备与任务进度，集中查看。</span></div><router-link to="/settings">工作空间设置 <span aria-hidden="true">↗</span></router-link></header>
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

  <!-- AI 智能助手悬浮面板 -->
  <div class="ai-panel-dock" :class="{ expanded: !chatCollapsed }">
    <AiChatPanel :default-collapsed="true" @collapse-change="chatCollapsed = $event" />
  </div>
  </div>
</template>

<script setup name="Index">
import HomeUserTaskStats from './components/home-user-task-stats.vue'
import HomeSkuSaleStats from './components/home-sku-sale-stats.vue'
import SkuSaleRankChart from './components/sku-sale-rank-chart.vue'
import SkuSaleCollectChart from './components/sku-sale-collect-chart.vue'
import PartnerNodeCollectChart from './components/partner-node-collect-chart.vue'
import AbnormalEquipmentTable from './components/abnormal-equipment-table.vue'
import AiChatPanel from '@/components/AiChatPanel/index.vue'

const chatCollapsed = ref(true)
</script>

<style scoped lang="scss">
.overview-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin: .25rem 0 2rem; }
.overview-header p { margin: 0 0 .55rem; color: var(--lx-primary); font-size: .75rem; font-weight: 650; }
.overview-header h1 { margin: 0; font-size: 1.85rem; letter-spacing: -.04em; font-weight: 650; }
.overview-header span { display: inline-block; margin-top: .65rem; color: var(--lx-muted); font-size: .8rem; }
.overview-header a { color: var(--lx-muted); font-size: .8rem; white-space: nowrap; }
@media(max-width: 640px) { .overview-header a { display: none; } }

.home-wrapper {
  position: relative;
}

.ai-panel-dock {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 44px;
  height: 44px;
  z-index: 900;
  transition: all 0.25s ease;

  &.expanded {
    width: 440px;
    height: calc(100vh - 140px);
    top: 100px;
    bottom: 24px;
  }
}

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
      box-shadow: var(--lx-shadow-sm);
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
