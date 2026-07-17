<template>
  <!-- 货道弹层 -->
  <el-dialog
    title="货道设置"
    v-model="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @open="handleGoodOpen"
    @close="handleGoodcClose"
    width="95%"
    max-width="1400px"
    class="channel-dialog"
  >
    <div class="vm-config-channel-dialog-wrapper">
      <!-- 货道展示组件 -->
        <ChannelDisplay
          :device-info="goodData"
          :channel-config="{
            rows: vmType.vmRow || 0,
            cols: vmType.vmCol || 0
          }"
          :channels="channels"
          :loading="listLoading"
          @addOrUpdateProduct="handleAddOrUpdateProduct"
          @deleteProduct="handleDeleteProduct"
        />
      
      <div class="empty-tip" v-if="!listLoading && channels.length === 0">
        <el-empty description="暂无货道数据，请检查设备是否正确配置货道" />
      </div>

      <div class="dialog-footer">
        <el-button
          type="primary"
          class="confirm-button"
          @click="handleClick"
        >
          <el-icon><Check /></el-icon>
          保存配置
        </el-button>
      </div>
    </div>

    <!-- 商品选择 -->
    <el-dialog
      title="选择商品"
      v-model="skuDialogVisible"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
      @open="handleListOpen"
      @close="handleListClose"
      width="90%"
      max-width="1000px"
      class="sku-dialog"
    >
      <div class="vm-select-sku-dialog-wrapper">
        <div class="search-section">
          <el-form
            ref="form"
            class="search-form"
            :model="listQuery"
            :label-width="formLabelWidth"
          >
            <el-form-item label="商品名称：">
              <el-input
                v-model="listQuery.skuName"
                placeholder="请输入商品名称"
                clearable
                class="sku-name-input"
                @keyup.enter="resetPageIndex"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-button
                type="primary"
                class="search-button"
                @click="resetPageIndex"
              >
                查询
              </el-button>
            </el-form-item>
          </el-form>
        </div>
        
        <div class="sku-list-container">
          <el-scrollbar
            ref="scroll2"
            v-loading="listSkuLoading"
            class="scrollbar"
          >
            <div v-loading="listSkuLoading" class="sku-grid">
              <div
                v-for="(item, index) in listSkuData.rows"
                :key="index"
                class="sku-item"
                :class="{ 'selected': currentRow.skuId === item.skuId }"
                @click="handleCurrentChange(index)"
              >
                <div class="sku-image-wrapper">
                  <img 
                    class="sku-image" 
                    :src="item.skuImage || defaultSkuImage" 
                    :alt="item.skuName"
                    @error="handleImageError($event)"
                  />
                  <div class="selected-badge" v-if="currentRow.skuId === item.skuId">
                    <el-icon><Check /></el-icon>
                  </div>
                </div>
                <div class="sku-details">
                  <div class="sku-name" :title="item.skuName">
                    {{ item.skuName }}
                  </div>
                  <div class="sku-price" v-if="item.price">
                    ¥{{ (item.price / 100).toFixed(2) }}
                  </div>
                  <div class="sku-unit" v-if="item.unit">
                    {{ item.unit }}
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>
          
          <div class="pagination-controls" v-if="pageCount > 1">
            <el-button
              type="primary"
              :disabled="listQuery.pageIndex === 1"
              @click="handleClickPrev"
              size="small"
            >
              <el-icon><ArrowLeft /></el-icon>
              上一页
            </el-button>
            <span class="page-info">
              {{ listQuery.pageIndex }} / {{ pageCount }}
            </span>
            <el-button
              type="primary"
              :disabled="listQuery.pageIndex === pageCount"
              @click="handleClickNext"
              size="small"
            >
              下一页
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          
          <div class="empty-tip" v-if="!listSkuLoading && (!listSkuData.rows || listSkuData.rows.length === 0)">
            <el-empty description="暂无可选商品，请先添加商品" />
          </div>
        </div>
      </div>
      <div class="dialog-footer">
        <el-button
          type="primary"
          class="confirm-button"
          @click="handleSelectClick"
        >
          确认选择
        </el-button>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { 
  InfoFilled, 
  Grid, 
  Search, 
  Check, 
  ArrowLeft, 
  ArrowRight 
} from '@element-plus/icons-vue';

import {
  getGoodsList,
  getGoodsType,
  channelConfig,
} from '@/api/manage/channel';
import { listSku } from '@/api/manage/sku';
import ChannelDialogItem from './ChannelDialogItem.vue';
import ChannelDisplay from '@/components/ChannelDisplay.vue';

const props = defineProps({
  goodVisible: {
    type: Boolean,
    default: false,
  },
  goodData: {
    type: Object,
    default: () => {},
  },
});

const emit = defineEmits(['handleCloseGood']);

const visible = ref(false);
const listLoading = ref(false);
const vmType = ref({});
const channels = ref([]);
const scroll = ref(null);

const defaultSkuImage = ref('');

const formLabelWidth = '100px';

const currentIndex = ref(0);
const channelCode = ref('');
const skuDialogVisible = ref(false);
const listQuery = ref({
  pageIndex: 1,
  pageSize: 12,
});
const listSkuLoading = ref(false);
const listSkuData = ref({});
const currentRow = ref({});
const pageCount = ref(0);
const channelModelView = ref({});

const configuredCount = computed(() => {
  return channels.value.filter(c => c.sku && c.sku.skuId).length;
});

const unconfiguredCount = computed(() => {
  return channels.value.length - configuredCount.value;
});

watch(
  () => props.goodVisible,
  (val) => {
    visible.value = val;
  }
);

const handleGoodOpen = () => {
  if (!props.goodData.vmTypeId || !props.goodData.innerCode) {
    ElMessage.error('设备信息不完整，无法加载货道数据');
    return;
  }
  getVmType();
  channelList();
};

const getVmType = async () => {
  try {
    if (!props.goodData.vmTypeId) {
      throw new Error('设备类型ID不存在');
    }
    const { data } = await getGoodsType(props.goodData.vmTypeId);
    vmType.value = data || {};
  } catch (error) {
    console.error('获取设备类型信息失败:', error);
    vmType.value = {};
    ElMessage.error('获取设备类型信息失败: ' + (error.message || '请检查权限'));
  }
};

const channelList = async () => {
  listLoading.value = true;
  try {
    if (!props.goodData.innerCode) {
      throw new Error('设备编号不存在');
    }
    const { data } = await getGoodsList(props.goodData.innerCode);
    channels.value = data || [];
  } catch (error) {
    console.error('获取货道列表失败:', error);
    channels.value = [];
    ElMessage.error('获取货道列表失败: ' + (error.message || '请检查权限'));
  } finally {
    listLoading.value = false;
  }
};

const handleGoodcClose = () => {
  visible.value = false;
  emit('handleCloseGood');
};

const openRemoveSkuDialog = (index, code) => {
  currentIndex.value = index;
  channelCode.value = code;
  channels.value[currentIndex.value].skuId = '0';
  channels.value[currentIndex.value].sku = undefined;
};

const handleListOpen = async () => {
  listSkuLoading.value = true;
  try {
    const queryParams = {
      pageNum: listQuery.value.pageIndex,
      pageSize: listQuery.value.pageSize,
    };
    if (listQuery.value.skuName) {
      queryParams.skuName = listQuery.value.skuName;
    }
    const data = await listSku(queryParams);
    listSkuData.value = data;
    pageCount.value = Math.ceil((data.total || 0) / listQuery.value.pageSize);
  } catch (error) {
    console.error('获取商品列表失败:', error);
    ElMessage.error('获取商品列表失败');
    listSkuData.value = {};
  } finally {
    listSkuLoading.value = false;
  }
};

const openSetSkuDialog = (index, code) => {
  currentIndex.value = index;
  channelCode.value = code;
  currentRow.value = {};
  listQuery.value = {
    pageIndex: 1,
    pageSize: 12,
  };
  skuDialogVisible.value = true;
};

const handleListClose = () => {
  skuDialogVisible.value = false;
};

const handleClickPrev = () => {
  if (listQuery.value.pageIndex === 1) {
    return;
  }
  listQuery.value.pageIndex--;
  handleListOpen();
};

const handleClickNext = () => {
  if (listQuery.value.pageIndex >= pageCount.value) {
    return;
  }
  listQuery.value.pageIndex++;
  handleListOpen();
};

const resetPageIndex = () => {
  listQuery.value.pageIndex = 1;
  handleListOpen();
};

const handleCurrentChange = (i) => {
  currentRow.value = listSkuData.value.rows[i];
};

const handleSelectClick = () => {
  if (!currentRow.value || !currentRow.value.skuId) {
    ElMessage.warning('请先选择商品');
    return;
  }
  handleListClose();
  channels.value[currentIndex.value].skuId = currentRow.value.skuId;
  channels.value[currentIndex.value].sku = {
    skuId: currentRow.value.skuId,
    skuName: currentRow.value.skuName,
    skuImage: currentRow.value.skuImage,
    price: currentRow.value.price,
    unit: currentRow.value.unit,
  };
};

// 处理添加或修改商品
const handleAddOrUpdateProduct = (channel) => {
  const index = channels.value.findIndex(c => c.channelCode === channel.channelCode);
  if (index !== -1) {
    currentIndex.value = index;
    channelCode.value = channel.channelCode;
    currentRow.value = {};
    listQuery.value = {
      pageIndex: 1,
      pageSize: 12,
    };
    skuDialogVisible.value = true;
  }
};

// 处理删除商品
const handleDeleteProduct = (channel) => {
  const index = channels.value.findIndex(c => c.channelCode === channel.channelCode);
  if (index !== -1) {
    channels.value[index].skuId = '0';
    channels.value[index].sku = undefined;
  }
};

const handleClick = async () => {
  if (!channels.value || channels.value.length === 0) {
    ElMessage.warning('没有可保存的货道数据');
    return;
  }
  
  channelModelView.value.innerCode = props.goodData.innerCode;
  channelModelView.value.channelList = channels.value.map((item) => {
    return {
      innerCode: props.goodData.innerCode,
      channelCode: item.channelCode,
      skuId: item.skuId,
    };
  });
  
  try {
    const res = await channelConfig(channelModelView.value);
    if (res.code === 200) {
      ElMessage.success('货道配置保存成功');
      visible.value = false;
      emit('handleCloseGood');
    } else {
      ElMessage.error(res.msg || '保存失败');
    }
  } catch (error) {
    console.error('保存货道配置失败:', error);
    ElMessage.error('保存货道配置失败');
  }
};

const handleImageError = (event) => {
  event.target.src = defaultSkuImage.value || '/src/assets/vm/default_sku.png';
};
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.module.scss';

.channel-dialog {
  :deep(.el-dialog__body) {
    padding: 20px;
  }
}

.vm-config-channel-dialog-wrapper {
  .channel-info-panel {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 12px;
    padding: 20px;
    margin-bottom: 20px;
    color: #fff;
    
    .info-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 16px;
      
      .el-icon {
        font-size: 20px;
      }
    }
    
    .info-content {
      display: flex;
      flex-wrap: wrap;
      gap: 24px;
      
      .info-item {
        .info-label {
          font-size: 12px;
          opacity: 0.8;
          margin-bottom: 4px;
        }
        
        .info-value {
          font-size: 18px;
          font-weight: 600;
          
          &.highlight {
            color: #ffd700;
          }
        }
      }
    }
  }
  
  .channels-container {
      background: #fff;
      border-radius: 12px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
      padding: 20px;
      
      .channels-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 20px;
        padding-bottom: 16px;
        border-bottom: 1px solid #ebeef5;
        
        .header-title {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 16px;
          font-weight: 600;
          color: #303133;
          
          .el-icon {
            color: #409eff;
          }
        }
        
        .header-stats {
          display: flex;
          gap: 20px;
          
          .stat-item {
            .stat-label {
              color: #909399;
              font-size: 14px;
            }
            
            .stat-value {
              color: #67c23a;
              font-weight: 600;
              font-size: 14px;
              
              &.warning {
                color: #f56c6c;
              }
            }
          }
        }
      }
      
      .scrollbar {
        min-height: 400px;
        width: 100%;
      }
      
      .channels-grid {
        display: grid;
        gap: 16px;
        width: 100%;
      }
      
      .empty-tip {
        padding: 40px 0;
      }
    }
  
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
    
    .confirm-button {
      padding: 12px 32px;
      font-size: 15px;
      font-weight: 500;
      border-radius: 8px;
      
      .el-icon {
        margin-right: 6px;
      }
    }
  }
}

.vm-select-sku-dialog-wrapper {
  .search-section {
    margin-bottom: 20px;
    padding: 20px;
    background: #f5f7fa;
    border-radius: 8px;
    
    .search-form {
      .el-form-item {
        margin-bottom: 0;
      }
      
      .sku-name-input {
        width: 300px;
        margin-right: 12px;
      }
      
      .search-button {
        padding: 8px 20px;
      }
    }
  }
  
  .sku-list-container {
    .scrollbar {
      min-height: 350px;
      max-height: 450px;
    }
    
    .sku-grid {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 12px;
      padding: 8px;
    }
    
    .sku-item {
      background: #fff;
      border: 2px solid #ebeef5;
      border-radius: 12px;
      padding: 10px;
      text-align: center;
      cursor: pointer;
      transition: all 0.3s ease;
      
      &:hover {
        border-color: #409eff;
        transform: translateY(-4px);
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
      }
      
      &.selected {
        border-color: #409eff;
        background: linear-gradient(135deg, rgba(64, 158, 255, 0.05) 0%, rgba(64, 158, 255, 0.1) 100%);
        box-shadow: 0 4px 16px rgba(64, 158, 255, 0.2);
      }
      
      .sku-image-wrapper {
        position: relative;
        width: 100%;
        height: 80px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #f5f7fa;
        border-radius: 8px;
        margin-bottom: 8px;
        overflow: hidden;
        
        .sku-image {
          max-width: 100%;
          max-height: 100%;
          object-fit: contain;
          transition: transform 0.3s ease;
        }
        
        .selected-badge {
          position: absolute;
          top: 8px;
          right: 8px;
          width: 24px;
          height: 24px;
          background: #409eff;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          color: #fff;
          font-size: 14px;
        }
      }
      
      .sku-details {
        .sku-name {
          font-size: 12px;
          font-weight: 500;
          color: #303133;
          margin-bottom: 6px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        
        .sku-price {
          font-size: 14px;
          font-weight: 700;
          color: #f56c6c;
          margin-bottom: 2px;
        }
        
        .sku-unit {
          font-size: 10px;
          color: #909399;
        }
      }
    }
    
    .pagination-controls {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      margin-top: 20px;
      padding-top: 20px;
      border-top: 1px solid #ebeef5;
      
      .page-info {
        font-size: 14px;
        color: #606266;
        font-weight: 500;
      }
    }
    
    .empty-tip {
      padding: 40px 0;
    }
  }
}

@media (max-width: 1200px) {
  .vm-select-sku-dialog-wrapper {
    .sku-list-container {
      .sku-grid {
        grid-template-columns: repeat(5, 1fr);
      }
    }
  }
}

@media (max-width: 992px) {
  .vm-select-sku-dialog-wrapper {
    .sku-list-container {
      .sku-grid {
        grid-template-columns: repeat(4, 1fr);
      }
    }
  }
}

@media (max-width: 768px) {
  .vm-config-channel-dialog-wrapper {
    .channel-info-panel {
      .info-content {
        flex-direction: column;
        gap: 16px;
      }
    }
    
    .channels-container {
      .channels-header {
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
      }
    }
  }
  
  .vm-select-sku-dialog-wrapper {
    .search-section {
      .search-form {
        .sku-name-input {
          width: 100%;
          margin-bottom: 12px;
        }
      }
    }
    
    .sku-list-container {
      .sku-grid {
        grid-template-columns: repeat(3, 1fr);
      }
    }
  }
}

@media (max-width: 480px) {
  .vm-select-sku-dialog-wrapper {
    .sku-list-container {
      .sku-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
  }
}
</style>
