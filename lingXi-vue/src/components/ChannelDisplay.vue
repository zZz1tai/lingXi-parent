<template>
  <div class="channel-display-container">
    <!-- 货道配置信息 -->
    <div class="channel-config-info">
      <h3 class="config-title">
        <el-icon><Grid /></el-icon>
        货道配置信息
      </h3>
      <div class="config-details">
        <div class="config-item">
          <span class="config-label">设备编号：</span>
          <span class="config-value">{{ deviceInfo.innerCode || '-' }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">货道行数：</span>
          <span class="config-value highlight">{{ channelConfig.rows || 0 }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">货道列数：</span>
          <span class="config-value highlight">{{ channelConfig.cols || 0 }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">货道总数：</span>
          <span class="config-value highlight">{{ totalChannels }}</span>
        </div>
      </div>
    </div>

    <!-- 货道网格展示 -->
    <div class="channels-grid-container">
      <div class="grid-header">
        <h3 class="grid-title">
          <el-icon><Box /></el-icon>
          货道商品列表
        </h3>
        <div class="grid-stats">
          <span class="stat-item">
            <span class="stat-label">已配置商品：</span>
            <span class="stat-value available">{{ configuredChannels }}</span>
          </span>
          <span class="stat-item">
            <span class="stat-label">未配置商品：</span>
            <span class="stat-value empty">{{ unconfiguredChannels }}</span>
          </span>
        </div>
      </div>

      <!-- 货道网格 -->
      <div 
        class="channels-grid" 
        :style="gridStyle"
        v-loading="loading"
      >
        <div 
          v-for="(channel, index) in channels" 
          :key="channel.channelCode || index"
          class="channel-cell"
          :class="{
            'has-product': channel.sku,
            'low-stock': channel.sku && isLowStock(channel)
          }"
        >
          <!-- 货道编号 -->
          <div class="channel-code">
            <el-icon><Ticket /></el-icon>
            {{ channel.channelCode || `通道 ${index + 1}` }}
          </div>

          <!-- 商品图片 -->
          <div class="product-image">
            <img 
              v-if="channel.sku && channel.sku.skuImage"
              :src="channel.sku.skuImage"
              :alt="channel.sku.skuName"
              class="product-img"
              @error="handleImageError"
              loading="lazy"
            />
            <div v-else class="no-image">
              <el-icon><Goods /></el-icon>
              <span>暂无商品</span>
            </div>
          </div>

          <!-- 商品信息 -->
          <div class="product-info" v-if="channel.sku">
            <div class="product-name" :title="channel.sku.skuName">
              {{ channel.sku.skuName }}
            </div>
            <div class="product-price">
              <span class="info-label">单价：</span>
              <span class="info-value price">¥{{ formatPrice(channel.sku.price) }}</span>
            </div>
            <div class="product-stock">
              <span class="info-label">库存：</span>
              <span class="info-value stock" :class="{ 'low': isLowStock(channel) }">
                {{ channel.currentCapacity || 0 }}
              </span>
            </div>
            <div class="product-quantity">
              <span class="info-label">数量：</span>
              <span class="info-value">
                {{ channel.currentCapacity || 0 }}
              </span>
            </div>
          </div>

          <!-- 无商品提示 -->
          <div class="no-product" v-else>
            <el-icon><Warning /></el-icon>
            <span>未配置商品</span>
          </div>

          <!-- 操作按钮 -->
          <div class="channel-actions">
            <el-button
              type="primary"
              size="small"
              class="action-btn"
              @click="handleAddOrUpdateProduct(channel)"
            >
              <el-icon><Plus /></el-icon>
              {{ channel.sku ? '修改' : '添加' }}
            </el-button>
            <el-button
              type="danger"
              size="small"
              plain
              class="action-btn"
              :disabled="!channel.sku"
              @click="handleDeleteProduct(channel)"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div class="empty-state" v-if="!loading && channels.length === 0">
        <el-empty 
          description="暂无货道数据，请检查设备配置" 
          :image-size="200"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, defineProps, defineEmits } from 'vue';
import { 
  Grid, 
  Box, 
  Ticket, 
  Goods, 
  Warning, 
  Plus, 
  Delete 
} from '@element-plus/icons-vue';

// 定义组件属性
const props = defineProps({
  // 设备信息
  deviceInfo: {
    type: Object,
    default: () => ({})
  },
  // 货道配置
  channelConfig: {
    type: Object,
    default: () => ({
      rows: 0,
      cols: 0
    })
  },
  // 货道数据
  channels: {
    type: Array,
    default: () => []
  },
  // 加载状态
  loading: {
    type: Boolean,
    default: false
  }
});

// 定义组件事件
const emit = defineEmits([
  'addOrUpdateProduct',
  'deleteProduct'
]);

// 计算货道总数
const totalChannels = computed(() => {
  return (props.channelConfig.rows || 0) * (props.channelConfig.cols || 0);
});

// 计算已配置商品的货道数量
const configuredChannels = computed(() => {
  return props.channels.filter(channel => channel.sku).length;
});

// 计算未配置商品的货道数量
const unconfiguredChannels = computed(() => {
  return props.channels.length - configuredChannels.value;
});

// 计算网格样式
const gridStyle = computed(() => {
  const cols = props.channelConfig.cols || 6;
  return {
    gridTemplateColumns: `repeat(${cols}, minmax(200px, 1fr))`,
    gap: '16px'
  };
});

// 检查是否低库存
const isLowStock = (channel) => {
  if (!channel.maxCapacity) return false;
  const current = channel.currentCapacity || 0;
  const max = channel.maxCapacity;
  return (current / max) < 0.2; // 低于20%视为低库存
};

// 格式化价格
const formatPrice = (price) => {
  if (!price) return '0.00';
  return (price / 100).toFixed(2);
};

// 处理图片加载错误
const handleImageError = (event) => {
  event.target.style.display = 'none';
};

// 处理添加或修改商品
const handleAddOrUpdateProduct = (channel) => {
  emit('addOrUpdateProduct', channel);
};

// 处理删除商品
const handleDeleteProduct = (channel) => {
  emit('deleteProduct', channel);
};
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.module.scss';

.channel-display-container {
  width: 100%;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

/* 货道配置信息 */
.channel-config-info {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
  color: #fff;
  
  .config-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 18px;
    font-weight: 600;
    margin-bottom: 16px;
    
    .el-icon {
      font-size: 20px;
    }
  }
  
  .config-details {
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    
    .config-item {
      display: flex;
      align-items: baseline;
      gap: 8px;
      
      .config-label {
        font-size: 14px;
        opacity: 0.8;
      }
      
      .config-value {
        font-size: 18px;
        font-weight: 600;
        
        &.highlight {
          color: #ffd700;
        }
      }
    }
  }
}

/* 货道网格展示 */
.channels-grid-container {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  
  .grid-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #ebeef5;
    
    .grid-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 18px;
      font-weight: 600;
      color: #303133;
      
      .el-icon {
        color: #409eff;
      }
    }
    
    .grid-stats {
      display: flex;
      gap: 20px;
      
      .stat-item {
        display: flex;
        align-items: center;
        gap: 6px;
        
        .stat-label {
          font-size: 14px;
          color: #909399;
        }
        
        .stat-value {
          font-size: 16px;
          font-weight: 600;
          
          &.available {
            color: #67c23a;
          }
          
          &.empty {
            color: #f56c6c;
          }
        }
      }
    }
  }
  
  .channels-grid {
    display: grid;
    min-height: 400px;
    padding: 8px;
  }
  
  .empty-state {
    padding: 60px 0;
    text-align: center;
  }
}

/* 货道单元格 */
.channel-cell {
    background: #fff;
    border: 2px solid #ebeef5;
    border-radius: 12px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    transition: all 0.3s ease;
    min-height: 340px;
    
    &:hover {
      border-color: #409eff;
      box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
      transform: translateY(-2px) scale(1.02);
    }
    
    &.has-product {
      border-color: #67c23a;
      background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
    }
    
    &.low-stock {
      border-color: #e6a23c;
    }
    
    .channel-code {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 12px;
      align-self: flex-start;
      
      .el-icon {
        color: #409eff;
        font-size: 16px;
      }
    }
    
    .product-image {
      width: 140px;
      height: 120px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f5f7fa;
      border-radius: 8px;
      margin-bottom: 16px;
      overflow: hidden;
      
      .product-img {
        max-width: 100%;
        max-height: 100%;
        object-fit: contain;
        transition: transform 0.3s ease;
        
        &:hover {
          transform: scale(1.05);
        }
      }
      
      .no-image {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 8px;
        color: #c0c4cc;
        
        .el-icon {
          font-size: 32px;
        }
        
        span {
          font-size: 14px;
        }
      }
    }
    
    .product-info {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin-bottom: 16px;
      
      .product-name {
        font-size: 14px;
        font-weight: 500;
        color: #303133;
        text-align: center;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        margin-bottom: 4px;
      }
      
      .product-price,
      .product-stock,
      .product-quantity {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 14px;
        
        .info-label {
          color: #909399;
        }
        
        .info-value {
          font-weight: 600;
          color: #606266;
          
          &.price {
            color: #f56c6c;
            font-size: 16px;
          }
          
          &.stock {
            color: #67c23a;
            
            &.low {
              color: #f56c6c;
            }
          }
        }
      }
    }
    
    .no-product {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      color: #909399;
      margin-top: 20px;
      margin-bottom: 20px;
      
      .el-icon {
        font-size: 24px;
      }
      
      span {
        font-size: 14px;
      }
    }
    
    .channel-actions {
      width: 100%;
      display: flex;
      gap: 8px;
      margin-top: auto;
      
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

/* 响应式设计 */
@media (max-width: 1200px) {
  .channels-grid-container {
    .channels-grid {
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)) !important;
    }
  }
  
  .channel-cell {
    min-height: 260px;
    
    .product-image {
      width: 120px;
      height: 100px;
    }
  }
}

@media (max-width: 768px) {
  .channel-display-container {
    padding: 16px;
  }
  
  .channel-config-info {
    .config-details {
      flex-direction: column;
      gap: 12px;
    }
  }
  
  .channels-grid-container {
    padding: 16px;
    
    .grid-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;
    }
    
    .channels-grid {
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)) !important;
      gap: 12px !important;
    }
  }
  
  .channel-cell {
    padding: 12px;
    min-height: 240px;
    
    .product-image {
      width: 100px;
      height: 80px;
    }
    
    .product-info {
      .product-price,
      .product-stock,
      .product-quantity {
        font-size: 12px;
      }
    }
  }
}

@media (max-width: 480px) {
  .channels-grid-container {
    .channels-grid {
      grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)) !important;
    }
  }
  
  .channel-cell {
    min-height: 220px;
  }
}
</style>