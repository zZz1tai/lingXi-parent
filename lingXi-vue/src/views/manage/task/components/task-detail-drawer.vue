<template>
  <el-drawer
    v-model="visible"
    title="工单详情"
    direction="rtl"
    size="480px"
    :close-on-click-modal="true"
    @close="handleClose"
  >
    <template #header>
      <div class="drawer-header">
        <span class="drawer-title">工单详情</span>
        <el-tag :type="statusTagType" effect="light" round>
          {{ statusLabel }}
        </el-tag>
      </div>
    </template>

    <div class="task-detail-body">
      <!-- 基本信息 -->
      <div class="detail-section">
        <div class="section-title">基本信息</div>
        <div class="detail-rows">
          <div class="detail-row">
            <span class="label">工单编号</span>
            <span class="value mono">{{ taskData.taskCode || '--' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">工单类型</span>
            <span class="value">{{ taskTypeLabel }}</span>
          </div>
          <div class="detail-row">
            <span class="label">工单方式</span>
            <span class="value">{{ taskData.createType === 0 ? '自动' : '手动' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">设备编号</span>
            <span class="value mono">{{ taskData.innerCode || '--' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">完成人员</span>
            <span class="value">{{ taskData.userName || '待分配' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间</span>
            <span class="value">{{ taskData.createTime || '--' }}</span>
          </div>
          <div class="detail-row" v-if="taskData.addr">
            <span class="label">定位</span>
            <span class="value">
              <el-icon><Location /></el-icon>
              {{ taskData.addr }}
            </span>
          </div>
          <div class="detail-row" v-if="taskData.desc">
            <span class="label">备注</span>
            <span class="value desc">{{ taskData.desc }}</span>
          </div>
        </div>
      </div>

      <!-- 处理进度 -->
      <div class="detail-section">
        <div class="section-title">处理进度</div>
        <div class="timeline">
          <div
            v-for="(step, index) in timelineSteps"
            :key="index"
            class="timeline-item"
            :class="{ done: step.done, current: step.current }"
          >
            <div class="timeline-dot"></div>
            <div class="timeline-content">
              <div class="timeline-title">{{ step.title }}</div>
              <div class="timeline-meta">{{ step.meta }}</div>
              <div v-if="step.desc" class="timeline-desc">{{ step.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <template #footer>
      <div class="drawer-footer">
        <el-button
          v-if="taskData.taskStatus === 1"
          type="primary"
          @click="handleStartTask"
        >
          开始处理
        </el-button>
        <el-button
          v-if="taskData.taskStatus === 1 || taskData.taskStatus === 2"
          @click="handleCancelTask"
        >
          取消工单
        </el-button>
        <el-button
          v-if="taskData.taskStatus === 3"
          type="primary"
          @click="handleRecreate"
        >
          重新创建
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup name="TaskDetailDrawer">
import { ref, computed, watch } from 'vue';
import { ElMessageBox, ElMessage } from 'element-plus';
import { Location } from '@element-plus/icons-vue';
import { cancelTaskType } from '@/api/manage/taskType';

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  taskData: {
    type: Object,
    default: () => ({}),
  },
  taskId: {
    type: [Number, String],
    default: '',
  },
});

const emit = defineEmits(['update:modelValue', 'refresh', 'recreate']);

const visible = ref(false);

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val;
  }
);

watch(visible, (val) => {
  emit('update:modelValue', val);
});

// 状态映射
const statusMap = {
  1: { label: '待处理', type: 'warning' },
  2: { label: '进行中', type: '' },
  3: { label: '已取消', type: 'info' },
  4: { label: '已完成', type: 'success' },
};

const statusLabel = computed(() => statusMap[props.taskData.taskStatus]?.label || '未知');
const statusTagType = computed(() => statusMap[props.taskData.taskStatus]?.type || 'info');

const taskTypeLabel = computed(() => {
  const map = { 1: '投放工单', 2: '补货工单', 3: '维修工单', 4: '撤机工单' };
  return map[props.taskData.productTypeId] || '未知类型';
});

// 时间线步骤
const timelineSteps = computed(() => {
  const status = props.taskData.taskStatus;
  const steps = [
    {
      title: '工单创建',
      meta: `${props.taskData.createType === 0 ? '系统自动' : '手动创建'} · ${props.taskData.createTime || ''}`,
      desc: props.taskData.createType === 0 ? '检测到设备异常，自动生成工单' : '由管理人员手动创建',
      done: true,
      current: false,
    },
    {
      title: '工单派发',
      meta: props.taskData.userName ? `指派给 ${props.taskData.userName}` : '待派发',
      desc: props.taskData.userName ? `由管理员指派给 ${props.taskData.userName} 处理` : '',
      done: status >= 2,
      current: status === 1,
    },
    {
      title: '现场处理',
      meta: status >= 4 ? props.taskData.updateTime || '已完成' : '待执行',
      desc: '',
      done: status >= 4,
      current: status === 2,
    },
    {
      title: status === 3 ? '工单取消' : '验收关闭',
      meta: status === 3 ? props.taskData.updateTime || '' : status >= 4 ? props.taskData.updateTime || '' : '待执行',
      desc: status === 3 ? props.taskData.desc || '工单已取消' : '',
      done: status >= 3,
      current: false,
    },
  ];
  return steps;
});

// 操作
const handleStartTask = () => {
  emit('refresh', 'start');
  visible.value = false;
};

const handleCancelTask = () => {
  ElMessageBox.confirm('取消工单后将不能恢复，是否确认取消？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    cancelTaskType({ taskId: props.taskId, desc: '后台工作人员取消' }).then((res) => {
      if (res.code === 200) {
        ElMessage.success('工单已取消');
        emit('refresh');
        visible.value = false;
      }
    });
  }).catch(() => {});
};

const handleRecreate = () => {
  emit('recreate');
  visible.value = false;
};

const handleClose = () => {
  visible.value = false;
};
</script>

<style lang="scss" scoped>
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--lx-navy);
}

.task-detail-body {
  padding: 0;
}

.detail-section {
  margin-bottom: var(--lx-space-lg);

  .section-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--lx-text);
    margin-bottom: var(--lx-space-md);
    padding-bottom: var(--lx-space-sm);
    border-bottom: 1px solid var(--lx-border-soft);
  }
}

.detail-rows {
  .detail-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 8px 0;
    border-bottom: 1px solid var(--lx-border-soft);
    font-size: 13px;

    &:last-child {
      border-bottom: none;
    }

    .label {
      color: var(--lx-muted);
      flex-shrink: 0;
      min-width: 72px;
    }

    .value {
      font-weight: 500;
      color: var(--lx-text);
      text-align: right;
      display: flex;
      align-items: center;
      gap: 4px;

      &.mono {
        font-family: var(--font-mono, 'SF Mono', 'Cascadia Code', monospace);
        font-size: 12px;
      }

      &.desc {
        font-weight: 400;
        color: var(--lx-muted);
        max-width: 280px;
        text-align: right;
      }
    }
  }
}

// 时间线
.timeline {
  position: relative;
  padding-left: 24px;

  &::before {
    content: '';
    position: absolute;
    left: 7px;
    top: 6px;
    bottom: 6px;
    width: 2px;
    background: var(--lx-border-soft);
  }

  .timeline-item {
    position: relative;
    padding-bottom: var(--lx-space-lg);

    &:last-child {
      padding-bottom: 0;
    }

    .timeline-dot {
      position: absolute;
      left: -21px;
      top: 4px;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      border: 2px solid var(--lx-border);
      background: var(--lx-surface);
      transition: background 0.2s, border-color 0.2s;
    }

    &.done .timeline-dot {
      background: var(--lx-primary);
      border-color: var(--lx-primary);
    }

    &.current .timeline-dot {
      border-color: var(--lx-primary);
      box-shadow: 0 0 0 3px var(--lx-primary-glow);
    }

    .timeline-title {
      font-size: 13px;
      font-weight: 500;
      color: var(--lx-text);
    }

    .timeline-meta {
      font-size: 12px;
      color: var(--lx-muted);
      margin-top: 2px;
    }

    .timeline-desc {
      font-size: 13px;
      color: var(--lx-muted);
      margin-top: var(--lx-space-xs);
    }
  }
}

.drawer-footer {
  display: flex;
  gap: var(--lx-space-sm);
  justify-content: flex-end;
}
</style>
