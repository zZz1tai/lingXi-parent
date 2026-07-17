<template>
  <div class="box home-user-task-stats bgc1">
    <div class="header">
      <div class="title">
        工单统计<span class="sub-title">{{ start }} ~ {{ end }}</span>
      </div>
    </div>
    <div class="body">
      <el-skeleton v-if="loading" :rows="4" animated />
      <div v-else-if="userTaskStats.length" class="stats-container">
        <div class="stats">
          <div class="item">
            <div class="num color1 text-shadow1">
              {{ userTaskStats[0].total + userTaskStats[1].total }}
            </div>
            <div class="text color2">工单总数（个）</div>
          </div>
        </div>
        <div class="stats">
          <div class="item">
            <div class="num color1 text-shadow1">
              {{ 
                userTaskStats[0].completedTotal + userTaskStats[1].completedTotal 
              }}
            </div>
            <div class="text color2">完成工单（个）</div>
          </div>
        </div>
        <div class="stats">
          <div class="item">
            <div class="num color1 text-shadow1">
              {{ 
                userTaskStats[0].progressTotal + userTaskStats[1].progressTotal 
              }}
            </div>
            <div class="text color2">进行工单（个）</div>
          </div>
        </div>
        <div class="stats">
          <div class="item">
            <div class="num color1 text-shadow1">
              {{ userTaskStats[0].cancelTotal + userTaskStats[1].cancelTotal }}
            </div>
            <div class="text color2">取消工单（个）</div>
          </div>
        </div>
      </div>
      <div v-else class="empty">
        <el-empty description="暂无数据" />
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import dayjs from 'dayjs';
import { getTaskStats } from '@/api/manage/dashboard';

// 定义变量
const repair = ref(false);
const start = dayjs().startOf('month').format('YYYY.MM.DD');
const end = dayjs().endOf('day').format('YYYY.MM.DD');
const userTaskStats = ref([]);
const loading = ref(true);

// 获取工单统计数据
const fetchTaskStats = async () => {
  try {
    loading.value = true;
    const month = {
      start: dayjs().startOf('month').format('YYYY-MM-DD HH:mm:ss'),
      end: dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'),
    };
    const response = await getTaskStats(month);
    userTaskStats.value = response.data || [];
  } catch (error) {
    console.error('获取工单统计数据失败:', error);
    userTaskStats.value = [];
  } finally {
    loading.value = false;
  }
};

// 组件挂载时获取数据
onMounted(() => {
  fetchTaskStats();
});
</script>
<style lang="scss" scoped>
// TODO: 首页、人效统计、对账统计样式抽出组件
.home-user-task-stats {
  display: flex;
  flex-direction: column;
  height: calc((100vh - 120px) * 0.2);
  min-height: 166px;
  background: #E9F3FF;
  border-radius: 20px;

  .body {
    flex: 1;
    display: flex;

    .stats-container {
      flex: 1;
      display: flex;
      justify-content: center;
    }

    .stats {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;

      .item {
        display: inline-flex; // 关键点
        flex-direction: column;
        align-items: center;

        .num {
          height: 50px;
          font-size: 36px;
          font-family: PingFangSC-Semibold, PingFang SC;
          font-weight: 600;
          line-height: 50px;
          text-shadow: 2px 4px 7px rgba(85, 132, 255, 0.5);
        }

        .text {
          height: 17px;
          margin-top: 3px;
          font-size: 12px;
          font-family: PingFangSC-Regular, PingFang SC;
          font-weight: 400;
          color: #91a7dc;
          line-height: 17px;
        }

        .color1 {
          color: #072074;
        }

        .color2 {
          color: #91a7dc;
        }

        .color3 {
          color: #ff5757;
        }

        .color4 {
          color: #de9690;
        }

        .text-shadow1 {
          text-shadow: 2px 4px 7px rgba(85, 132, 255, 0.5);
        }

        .text-shadow2 {
          text-shadow: 2px 4px 7px rgba(255, 99, 85, 0.5);
        }
      }
    }
  }
}

.bgc1 {
  background: #E9F3FF;
  background-image: url('@/assets/images/circle.png'), url('@/assets/images/task.png');
  background-repeat: no-repeat, no-repeat;
  background-position: 0 0, calc(100% - 12px) 100%;

}

.bgc2 {
  background: #FBEFE8 url('@/assets/images/sale.png') no-repeat calc(100% - 12px) 100%;
}
</style>