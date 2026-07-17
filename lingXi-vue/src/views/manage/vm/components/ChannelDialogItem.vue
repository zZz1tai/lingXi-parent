<template>
  <div v-if="channel" class="channel-item" :class="{ 'has-product': channel.sku && channel.sku.skuId }">
    <div class="item-header">
      <div class="channel-code">
        <el-icon><Box /></el-icon>
        <span>{{ channel.channelCode || '-' }}</span>
      </div>
      <div class="channel-status" :class="{ 'empty': !channel.sku || !channel.sku.skuId }">
        {{ (channel.sku && channel.sku.skuId) ? '已配置' : '未配置' }}
      </div>
    </div>
    
    <div class="item-body">
      <div class="product-image">
        <img
          v-if="channel.sku && channel.sku.skuImage"
          class="img"
          :src="channel.sku.skuImage"
          :alt="channel.sku.skuName"
          @error="handleImageError"
        />
        <div v-else class="no-product">
          <el-icon><Goods /></el-icon>
          <span>暂无商品</span>
        </div>
      </div>
      
      <div class="product-info">
        <div class="product-name" :title="channel.sku ? channel.sku.skuName : '暂无商品'">
          {{ channel.sku ? channel.sku.skuName : '暂无商品' }}
        </div>
        <div class="product-price" v-if="channel.sku && channel.sku.price">
          <span class="price-label">单价</span>
          <span class="price-value">¥{{ (channel.sku.price / 100).toFixed(2) }}</span>
        </div>
        <div class="product-unit" v-if="channel.sku && channel.sku.unit">
          <span class="unit-label">规格</span>
          <span class="unit-value">{{ channel.sku.unit }}</span>
        </div>
      </div>
    </div>
    
    <div class="item-stock">
      <div class="stock-info">
        <div class="stock-item">
          <span class="stock-label">当前库存</span>
          <span class="stock-value" :class="{ 'low': isLowStock }">
            {{ channel.currentCapacity || 0 }}
          </span>
        </div>
        <div class="stock-item">
          <span class="stock-label">最大容量</span>
          <span class="stock-value">
            {{ channel.maxCapacity || 0 }}
          </span>
        </div>
      </div>
      <div class="stock-bar">
        <div class="stock-progress" :style="{ width: stockPercentage + '%' }" :class="{ 'low': isLowStock }"></div>
      </div>
    </div>
    
    <div class="item-actions">
      <el-button
        type="primary"
        size="small"
        class="action-btn"
        @click="handleSetClick"
      >
        <el-icon><Plus /></el-icon>
        {{ (channel.sku && channel.sku.skuId) ? '更换' : '添加' }}
      </el-button>
      <el-button
        type="danger"
        size="small"
        plain
        class="action-btn"
        :disabled="!channel.sku || !channel.sku.skuId"
        @click="handleRemoveClick"
      >
        <el-icon><Delete /></el-icon>
        删除
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { 
  Box, 
  Goods, 
  Plus, 
  Delete 
} from '@element-plus/icons-vue';

const props = defineProps({
  currentIndex: {
    type: Number,
    default: 0,
  },
  channel: {
    type: Object,
    default: () => {},
  },
});

const emit = defineEmits(['openSetSkuDialog', 'openRemoveSkuDialog']);

const isLowStock = computed(() => {
  if (!props.channel || !props.channel.maxCapacity) return false;
  const current = props.channel.currentCapacity || 0;
  const max = props.channel.maxCapacity || 1;
  const percentage = (current / max) * 100;
  return percentage < 20;
});

const stockPercentage = computed(() => {
  if (!props.channel || !props.channel.maxCapacity) return 0;
  const current = props.channel.currentCapacity || 0;
  const max = props.channel.maxCapacity || 1;
  return Math.min(100, (current / max) * 100);
});

const handleSetClick = () => {
  emit('openSetSkuDialog', props.currentIndex, props.channel.channelCode);
};

const handleRemoveClick = () => {
  emit('openRemoveSkuDialog', props.currentIndex, props.channel.channelCode);
};

const handleImageError = (event) => {
  event.target.style.display = 'none';
};
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.module.scss';

.channel-item {
  background: #fff;
  border: 2px solid #ebeef5;
  border-radius: 12px;
  padding: 16px;
  transition: all 0.3s ease;
  min-width: 220px;
  
  &:hover {
    border-color: #409eff;
    box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
    transform: translateY(-2px);
  }
  
  &.has-product {
    border-color: #67c23a;
    background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  }
  
  .item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    padding-bottom: 10px;
    border-bottom: 1px dashed #ebeef5;
    
    .channel-code {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      
      .el-icon {
        color: #409eff;
        font-size: 16px;
      }
    }
    
    .channel-status {
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 500;
      background: #67c23a;
      color: #fff;
      
      &.empty {
        background: #909399;
      }
    }
  }
  
  .item-body {
    margin-bottom: 12px;
    
    .product-image {
      width: 100%;
      height: 100px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f7fa;
      border-radius: 8px;
      margin-bottom: 12px;
      overflow: hidden;
      
      .img {
        max-width: 90%;
        max-height: 90%;
        object-fit: contain;
        transition: transform 0.3s ease;
        
        &:hover {
          transform: scale(1.05);
        }
      }
      
      .no-product {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        color: #c0c4cc;
        
        .el-icon {
          font-size: 32px;
        }
        
        span {
          font-size: 12px;
        }
      }
    }
    
    .product-info {
      .product-name {
        font-size: 14px;
        font-weight: 500;
        color: #303133;
        margin-bottom: 8px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        text-align: center;
      }
      
      .product-price {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 4px;
        
        .price-label {
          font-size: 12px;
          color: #909399;
        }
        
        .price-value {
          font-size: 16px;
          font-weight: 700;
          color: #f56c6c;
        }
      }
      
      .product-unit {
        display: flex;
        justify-content: space-between;
        align-items: center;
        
        .unit-label {
          font-size: 12px;
          color: #909399;
        }
        
        .unit-value {
          font-size: 12px;
          color: #606266;
        }
      }
    }
  }
  
  .item-stock {
    margin-bottom: 12px;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 8px;
    
    .stock-info {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      
      .stock-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        
        .stock-label {
          font-size: 11px;
          color: #909399;
          margin-bottom: 2px;
        }
        
        .stock-value {
          font-size: 16px;
          font-weight: 600;
          color: #67c23a;
          
          &.low {
            color: #f56c6c;
          }
        }
      }
    }
    
    .stock-bar {
      height: 6px;
      background: #e4e7ed;
      border-radius: 3px;
      overflow: hidden;
      
      .stock-progress {
        height: 100%;
        background: linear-gradient(90deg, #67c23a 0%, #85ce61 100%);
        border-radius: 3px;
        transition: width 0.3s ease;
        
        &.low {
          background: linear-gradient(90deg, #f56c6c 0%, #f78989 100%);
        }
      }
    }
  }
  
  .item-actions {
    display: flex;
    gap: 8px;
    justify-content: center;
    
    .action-btn {
      flex: 1;
      padding: 6px 8px;
      font-size: 12px;
      
      .el-icon {
        margin-right: 4px;
      }
    }
  }
}

@media (max-width: 768px) {
  .channel-item {
    padding: 12px;
    
    .item-body {
      .product-image {
        height: 80px;
      }
    }
    
    .item-actions {
      flex-direction: column;
      
      .action-btn {
        width: 100%;
      }
    }
  }
}
</style>
