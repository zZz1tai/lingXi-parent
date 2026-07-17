<template>
  <div class="box bgc3 sku-sale-rank">
    <!-- TODO: 分辨大怎么展示问UI -->
    <div class="header">
      <div class="title">
        商品热榜<span class="sub-title">{{ start }} ~ {{ end }}</span>
      </div>
    </div>
    <div class="body">
      <el-skeleton v-if="loading" :rows="10" animated />
      <div v-else-if="skuSaleRank.length">
        <el-row v-for="(item, index) in skuSaleRank" :key="item.skuId || index">
          <el-col :span="5">
            <div :class="'top top' + (index + 1)">
              {{ index + 1 }}
            </div>
          </el-col>
          <el-col :span="13">
            <div class="sku-name" :title="item.skuName">
              {{ item.skuName }}
            </div>
          </el-col>
          <el-col :span="6">
            <div class="count">{{ item.count }}单</div>
          </el-col>
        </el-row>
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
import { getSkuSaleRank } from '@/api/manage/dashboard';

// 定义变量
const skuSaleRank = ref([]);
const start = dayjs().startOf('month').format('YYYY.MM.DD');
const end = dayjs().endOf('day').format('YYYY.MM.DD');
const loading = ref(true);

// 获取SKU销售排名数据
const fetchSkuSaleRank = async () => {
  try {
    loading.value = true;
    const month = {
      start: dayjs().startOf('month').format('YYYY-MM-DD HH:mm:ss'),
      end: dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'),
    };
    const response = await getSkuSaleRank(month);
    skuSaleRank.value = response.data || [];
  } catch (error) {
    console.error('获取SKU销售排名数据失败:', error);
    skuSaleRank.value = [];
  } finally {
    loading.value = false;
  }
};

// 组件挂载时获取数据
onMounted(() => {
  fetchSkuSaleRank();
});
</script>
<style lang="scss" scoped>
@import '@/assets/styles/variables.module.scss';

.sku-sale-rank {
  display: flex;
  flex-direction: column;
  height: calc((100vh - 120px) * 0.6);
  min-height: 538px;
  background: #FFFFFF;
  border-radius: 20px;

  .body {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
    margin-top: 20px;
    padding: 0 10px;

    > div:not(.empty) {
      display: flex;
      flex-direction: column;
      gap: 15px;
    }

    .top {
      display: inline-block;
      width: 16px;
      height: 20px;
      margin-left: 10px;
      background: url('@/assets/user-task-stats/top.png');
      text-align: center;
      font-size: 12px;
      font-weight: normal;
      color: #E9B499;
      line-height: 14px;
    }

    .top1 {
      width: 21px;
      height: 20px;
      background: url('@/assets/user-task-stats/top1.png');
      color: #8E5900;
    }

    .top2 {
      width: 21px;
      height: 20px;
      background: url('@/assets/user-task-stats/top2.png');
      color: #494949;
    }

    .top3 {
      width: 21px;
      height: 20px;
      background: url('@/assets/user-task-stats/top3.png');
      color: #CF6D3D;
    }

    .sku-name {
      height: 24px;
      font-size: 16px;
      font-weight: 500;
      color: $--color-text-primary;
      line-height: 24px;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }

    .count {
      height: 24px;
      font-size: 16px;
      font-family: PingFangSC-Regular, PingFang SC;
      font-weight: 400;
      color: #737589;
      line-height: 24px;
      text-align: right;
    }
  }
}
</style>
