<template>
  <div class="shopping-page">
    <!-- 顶部导航栏 -->
    <div class="shopping-header">
      <div class="header-left">
        <h1 class="page-title">客户购物</h1>
      </div>
      <div class="header-right">
        <el-dropdown @command="handleUserAction">
          <span class="user-info">
            {{ userStore.name }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="shopping-content">
      <!-- 售货机选择与策略 -->
      <div class="vm-overview">
        <el-form label-position="left" label-width="100px">
          <el-form-item label="选择售货机：">
            <el-select v-model="selectedVmId" @change="handleVmChange" placeholder="请选择售货机" style="width: 300px">
              <el-option
                v-for="vm in vendingMachines"
                :key="vm.id"
                :label="vm.innerCode"
                :value="vm.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="selectedVmStrategy" class="strategy-container">
          <h2 class="strategy-title">售货机策略</h2>
          <div class="strategy-content">
            <div class="strategy-item">
              <span class="strategy-label">策略名称：</span>
              <span class="strategy-value">{{ selectedVmStrategy.policyName || '无' }}</span>
            </div>
            <div class="strategy-item">
              <span class="strategy-label">策略方案：</span>
              <span class="strategy-value">{{ selectedVmStrategy.discount ? (selectedVmStrategy.discount / 10) + '折' : '无' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="store-layout">
      <!-- 商品展示区域 -->
      <div class="products-container">
        <div class="section-header products-heading">
          <div>
            <h2 class="section-title">全部商品</h2>
            <span class="section-subtitle">{{ channels.length }} 件商品</span>
          </div>
          <div class="cart-pill" role="button" tabindex="0" @click="cartVisible = true">
            <el-icon><ShoppingBag /></el-icon>{{ cartItemCount }}
          </div>
        </div>
        <div v-if="loading" class="loading-container">
          <p>加载中...</p>
        </div>
        <div v-else-if="!channels.length" class="empty-container">
          <el-empty description="暂无商品" />
        </div>
        <div v-else class="channels-grid" :style="vmLayout.cols ? { gridTemplateColumns: `repeat(${vmLayout.cols}, minmax(0, 1fr))` } : undefined">
          <div
            v-for="channel in channels"
            :key="channel.id"
            class="channel-item"
            :class="{ 'sold-out': Number(channel.currentCapacity) <= 0 }"
            @click="Number(channel.currentCapacity) > 0 && addToCart(channel)"
          >
            <div class="channel-position">{{ channel.channelCode }}</div>
            <div class="channel-product">
              <span v-if="isRecommendedSku(channel.sku?.skuId)" class="recommended-badge">推荐</span>
              <img
                :src="channel.sku?.skuImage || '/src/assets/vm/default_sku.png'"
                :alt="channel.sku?.skuName"
                class="product-image"
              />
              <div class="product-info">
                <h3 class="product-name">{{ channel.sku?.skuName || '无商品' }}</h3>
                <p class="product-price">¥{{ calculatePrice(channel.sku?.price) || 0 }}</p>
                <p class="product-stock" v-if="channel.sku">库存: {{ channel.currentCapacity || 0 }}</p>
              </div>
              <div class="sold-out-overlay" v-if="Number(channel.currentCapacity) <= 0">
                <span>售罄</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 购物车抽屉 -->
      <el-drawer v-model="cartVisible" title="购物车" direction="rtl" size="420px">
      <aside class="cart-container cart-drawer-content">
        <div class="cart-heading">
          <h2 class="cart-title"><el-icon><ShoppingBag /></el-icon>购物车</h2>
          <span>{{ cartItemCount }} 件</span>
        </div>
        <div v-if="cart.length === 0" class="empty-cart">
          <el-empty description="购物车为空" />
        </div>
        <div v-else class="cart-items">
          <div
            v-for="(item, index) in cart"
            :key="index"
            class="cart-item"
          >
            <img class="cart-item-image" :src="item.sku.skuImage || '/src/assets/vm/default_sku.png'" :alt="item.sku.skuName" />
            <div class="cart-item-info">
              <span class="cart-item-name">{{ item.sku.skuName }}</span>
              <span class="cart-item-price">
                <template v-if="hasDiscount">
                  <del>¥{{ formatMoney(item.sku.price) }}</del>
                  <strong>¥{{ calculatePrice(item.sku.price) }}</strong>
                  <em>{{ discountLabel }}</em>
                </template>
                <template v-else>¥{{ formatMoney(item.sku.price) }}</template>
                / 件
              </span>
            </div>
            <div class="cart-item-quantity">
              <el-button
                size="small"
                @click="decreaseQuantity(index)"
              >
                -</el-button>
              <span class="quantity">{{ item.quantity }}</span>
              <el-button size="small" @click="increaseQuantity(index)">
                +</el-button>
            </div>
            <el-button class="remove-btn" text
              @click="removeFromCart(index)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <div class="cart-summary">
            <div v-if="hasDiscount"><span>折前金额</span><strong>¥{{ originalTotalAmount }}</strong></div>
            <div v-if="hasDiscount"><span>策略折扣</span><strong class="discount-text">{{ discountLabel }}</strong></div>
            <div><span>折后小计</span><strong>¥{{ totalAmount }}</strong></div>
            <div><span>配送费</span><strong class="free">免费</strong></div>
            <div class="cart-total"><span>合计</span><strong>¥{{ totalAmount }}</strong></div>
          </div>
          <el-button
            type="primary"
            class="checkout-btn"
            @click="processPayment"
            :disabled="cart.length === 0"
          >
            去结算
          </el-button>
        </div>
      </aside>
      </el-drawer>
      </div>
    </div>



    <!-- 支付成功提示 -->
    <el-dialog
      v-model="paymentSuccessVisible"
      title="支付成功"
      width="300px"
      align-center
      :close-on-click-modal="false"
    >
      <div class="success-content">
        <el-icon class="success-icon"><CircleCheck /></el-icon>
        <p>支付成功！</p>
        <p>商品即将出货，请稍候...</p>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="resetShopping">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElDialog, ElButton, ElInput, ElIcon, ElDropdown, ElDropdownMenu, ElDropdownItem, ElSelect, ElOption, ElForm, ElFormItem, ElRadio, ElEmpty, ElDrawer } from 'element-plus';
import { ArrowDown, Delete, CircleCheck, ShoppingBag } from '@element-plus/icons-vue';
import useUserStore from '@/store/modules/user';
import { getVendingMachines, getChannelsByVmId } from '@/api/manage/vm';
import { getVmType } from '@/api/manage/vmType';
import { getPolicy } from '@/api/manage/policy';
import { addOrder, getHybridRecommendations } from '@/api/manage/order';

// 状态管理
const userStore = useUserStore();
const vendingMachines = ref([]);
const selectedVmId = ref('');
const channels = ref([]);
const vmLayout = ref({ rows: 0, cols: 0 });
const cart = ref([]);
const cartVisible = ref(false);
const loading = ref(false);
const paymentSuccessVisible = ref(false);
const selectedVmStrategy = ref(null);
const recommendations = ref([]);
const recommendationsLoading = ref(false);
const cartItemCount = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0));

const discountRate = computed(() => {
  const discount = selectedVmStrategy.value?.discount;
  return discount && discount > 0 ? discount / 100 : 1;
});
const hasDiscount = computed(() => discountRate.value < 1);
const discountLabel = computed(() => `${(discountRate.value * 10).toFixed(1)}折`);
const formatMoney = (price) => ((Number(price || 0)) / 100).toFixed(2);
const originalTotalAmount = computed(() => (cart.value.reduce((total, item) => total + Number(item.sku.price || 0) * item.quantity, 0) / 100).toFixed(2));

// 计算属性
const totalAmount = computed(() => {
  return (cart.value.reduce((total, item) => {
    return total + (Number(item.sku.price || 0) * item.quantity * discountRate.value);
  }, 0) / 100).toFixed(2);
});

// 生命周期
onMounted(async () => {
  // 检查登录状态
  if (!userStore.token) {
    // 重定向到登录页面
    window.location.href = '/login';
    return;
  }
  
  // 加载售货机列表
  await loadVendingMachines();
});

// 加载售货机列表
const loadVendingMachines = async () => {
  try {
    const res = await getVendingMachines();
    vendingMachines.value = res.data || [];
    if (vendingMachines.value.length > 0) {
      selectedVmId.value = vendingMachines.value[0].id;
      await loadVmLayout(vendingMachines.value[0]);
      await loadChannels();
      await loadVmStrategy(selectedVmId.value);
      await loadRecommendations();
    }
  } catch (error) {
    ElMessage.error('加载售货机列表失败');
    console.error('加载售货机列表失败:', error);
  }
};

// 加载货道信息
const loadChannels = async () => {
  if (!selectedVmId.value) return;
  
  loading.value = true;
  try {
    const res = await getChannelsByVmId(selectedVmId.value);
    channels.value = res.data || [];
  } catch (error) {
    ElMessage.error('加载货道信息失败');
    console.error('加载货道信息失败:', error);
  } finally {
    loading.value = false;
  }
};

// 处理售货机切换
const handleVmChange = async () => {
  await loadVmLayout(vendingMachines.value.find(vm => vm.id === selectedVmId.value));
  await loadChannels();
  await loadVmStrategy(selectedVmId.value);
  await loadRecommendations();
  // 切换售货机时清空购物车
  cart.value = [];
};

// 加载推荐商品
const loadRecommendations = async () => {
  if (!selectedVmId.value) return;
  
  recommendationsLoading.value = true;
  try {
    // 获取当前选中的售货机信息
    const selectedVm = vendingMachines.value.find(vm => vm.id === selectedVmId.value);
    const innerCode = selectedVm?.innerCode || '';
    const regionId = selectedVm?.regionId || selectedVmStrategy.value?.regionId;
    
    const res = await getHybridRecommendations({
      userName: userStore.name || 'guest',
      innerCode: innerCode,
      regionId: regionId,
      limit: 6
    });
    // 推荐结果必须在当前售货机存在有库存货道，否则不展示为可购买推荐
    recommendations.value = (res.data || []).filter(item => isRecommendedAvailable(item));
  } catch (error) {
    console.error('加载推荐商品失败:', error);
    recommendations.value = [];
  } finally {
    recommendationsLoading.value = false;
  }
};

// 从推荐商品加入购物车
const addRecommendationToCart = (item) => {
  // 查找对应的货道
  // 同一商品可能占用多个货道；优先选择仍有库存的货道，不能被已售罄货道“遮蔽”
  const channel = channels.value.find(c => c.sku && c.sku.skuId === item.skuId && Number(c.currentCapacity) > 0);
  
  if (channel) {
    // 如果找到对应货道，使用货道的addToCart逻辑
    addToCart(channel);
  } else {
    ElMessage.warning(`${item.skuName} 当前售货机已售罄`);
  }
};

const loadVmLayout = async (vm) => {
  try {
    const res = await getVmType(vm?.vmTypeId);
    vmLayout.value = { rows: Number(res.data?.vmRow) || 0, cols: Number(res.data?.vmCol) || 0 };
  } catch (error) {
    vmLayout.value = { rows: 0, cols: 0 };
  }
};

const isRecommendedAvailable = (item) => channels.value.some(c =>
  c.sku && c.sku.skuId === item.skuId && Number(c.currentCapacity) > 0
);
const isRecommendedSku = (skuId) => recommendations.value.some(item => item.skuId === skuId);

// 添加商品到购物车
const addToCart = (channel) => {
  if (!channel.sku) {
    ElMessage.warning('该货道暂无商品');
    return;
  }
  
  if (Number(channel.currentCapacity) <= 0) {
    ElMessage.warning('该商品已售罄');
    return;
  }
  
  const existingItem = cart.value.find(item => item.channelId === channel.id);
  if (existingItem) {
    if (existingItem.quantity >= channel.currentCapacity) {
      ElMessage.warning('已达到库存上限');
      return;
    }
    existingItem.quantity++;
  } else {
    cart.value.push({
      channelId: channel.id,
      sku: channel.sku,
      quantity: 1
    });
  }
  ElMessage.success('商品已添加到购物车');
};

// 减少商品数量
const decreaseQuantity = (index) => {
  if (cart.value[index].quantity > 1) {
    cart.value[index].quantity--;
  } else {
    removeFromCart(index);
  }
};

// 增加商品数量
const increaseQuantity = (index) => {
  const item = cart.value[index];
  const totalCapacity = channels.value
    .filter(c => c.sku && c.sku.skuId === item.sku.skuId)
    .reduce((sum, c) => sum + Math.max(0, Number(c.currentCapacity) || 0), 0);
  if (item.quantity >= totalCapacity) {
    ElMessage.warning('已达到库存上限');
    return;
  }
  item.quantity++;
};

// 从购物车移除商品
const removeFromCart = (index) => {
  cart.value.splice(index, 1);
  ElMessage.success('商品已从购物车移除');
};

// 处理支付
const processPayment = async () => {
  if (cart.value.length === 0) {
    ElMessage.warning('购物车为空');
    return;
  }
  
  try {
    // 模拟支付过程
    await new Promise(resolve => setTimeout(resolve, 1000));
    const selectedVm = vendingMachines.value.find(vm => vm.id === selectedVmId.value);
    
    const first = cart.value[0];
    const details = cart.value.flatMap(item => {
      let remaining = item.quantity;
      return channels.value
        .filter(c => c.sku && c.sku.skuId === item.sku.skuId && Number(c.currentCapacity) > 0)
        .map(c => {
          const quantity = Math.min(remaining, Number(c.currentCapacity));
          remaining -= quantity;
          return quantity > 0 ? {
            channelId: c.id, skuId: item.sku.skuId, skuName: item.sku.skuName,
            quantity, price: Math.round(item.sku.price * discountRate.value),
            amount: Math.round(item.sku.price * quantity * discountRate.value)
          } : null;
        }).filter(Boolean);
    });
    const orderData = {
        channelId: first.channelId,
        skuId: first.sku.skuId,
        skuName: first.sku.skuName,
        quantity: cart.value.reduce((sum, item) => sum + item.quantity, 0),
        amount: Math.round(Number(totalAmount.value) * 100),
        price: Math.round(Number(totalAmount.value) * 100),
        payType: '2', // 固定使用微信支付
        status: 1, // 支付完成
        payStatus: 1, // 支付完成
        innerCode: selectedVm?.innerCode || '', // 售货机编号
        regionId: selectedVmStrategy.value?.regionId, // 区域ID
        regionName: selectedVmStrategy.value?.regionName, // 区域名称
        details
    };
    await addOrder(orderData);
    
    cartVisible.value = false;
    paymentSuccessVisible.value = true;
  } catch (error) {
    ElMessage.error('支付失败，请重试');
    console.error('支付失败:', error);
  }
};

// 重置购物状态
const resetShopping = async () => {
  paymentSuccessVisible.value = false;
  cart.value = [];
  // 重新加载货道信息，更新库存状态
  await loadChannels();
  ElMessage.success('购物已完成');
};

// 计算价格
const calculatePrice = (price) => {
  return (Number(price || 0) * discountRate.value / 100).toFixed(2);
};

// 加载售货机策略
const loadVmStrategy = async (vmId) => {
  try {
    // 首先获取售货机详情，获取policyId
    const vm = vendingMachines.value.find(item => item.id === vmId);
    if (!vm || !vm.policyId || vm.policyId === 'all') {
      selectedVmStrategy.value = {
        policyName: '无',
        discount: null,
        promotionRules: '无',
        discountActivities: '无',
        purchaseLimits: '无',
        priceMultiplier: 1
      };
      return;
    }
    
    // 确保policyId是有效的数字
    const policyId = Number(vm.policyId);
    if (isNaN(policyId)) {
      selectedVmStrategy.value = {
        policyName: '无',
        discount: null,
        promotionRules: '无',
        discountActivities: '无',
        purchaseLimits: '无',
        priceMultiplier: 1
      };
      return;
    }
    
    // 根据policyId获取策略详情
    const res = await getPolicy(policyId);
    const policy = res.data;
    
    // 转换策略信息
    selectedVmStrategy.value = {
      policyName: policy.policyName || '无',
      discount: policy.discount || null,
      promotionRules: policy.promotionRules || '无',
      discountActivities: policy.discountActivities || '无',
      purchaseLimits: policy.purchaseLimits || '无',
      priceMultiplier: policy.priceMultiplier || 1
    };
  } catch (error) {
    ElMessage.error('加载策略信息失败');
    console.error('加载策略信息失败:', error);
    // 加载失败时使用默认策略
    selectedVmStrategy.value = {
      policyName: '无',
      discount: null,
      promotionRules: '无',
      discountActivities: '无',
      purchaseLimits: '无',
      priceMultiplier: 1
    };
  }
};

// 处理用户操作
const handleUserAction = (command) => {
  switch (command) {
    case 'profile':
      // 跳转到个人中心
      break;
    case 'logout':
      // 退出登录
      userStore.logout();
      window.location.href = '/login';
      break;
    default:
      break;
  }
};
</script>

<style scoped lang="scss">
  .cart-drawer-content {
    width: 100%;
    height: 100%;
  }
.shopping-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.shopping-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;

  .page-title {
    font-size: 24px;
    font-weight: 600;
    color: #1e293b;
    margin: 0;
  }

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: #475569;
    cursor: pointer;
  }
}

.shopping-content {
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 24px;
}

.store-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  align-items: start;
}

.products-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.cart-pill {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #ecfdf3;
  color: #15803d;
  font-weight: 600;
}

.vm-overview {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 36px;
  flex-wrap: wrap;
}

.strategy-container {
  flex: 1;
  order: -1;

  .strategy-title {
    font-size: 18px;
    font-weight: 600;
    color: #1e293b;
    margin: 0 0 16px 0;
  }

  .strategy-content {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 16px;

    .strategy-item {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .strategy-label {
        font-size: 14px;
        color: #64748b;
        font-weight: 500;
      }

      .strategy-value {
        font-size: 14px;
        color: #1e293b;
      }
    }
  }
}

.products-container {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;

  .loading-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 0;

    p {
      margin-top: 16px;
      color: #64748b;
    }
  }

  .empty-container {
    padding: 60px 0;
  }

  .channels-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(0, 1fr));
    gap: 20px;

    .channel-item {
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 10px;
        cursor: pointer;
        transition: all 0.3s ease;
        position: relative;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
          border-color: #667eea;
        }

        &.sold-out {
          opacity: 0.6;
          cursor: not-allowed;

          &:hover {
            transform: none;
            box-shadow: none;
            border-color: #e2e8f0;
          }
        }

      .channel-position {
        font-size: 12px;
        color: #94a3b8;
        margin-bottom: 8px;
      }

      .channel-product {
        .recommended-badge {
          position: absolute;
          top: 8px;
          right: 8px;
          z-index: 2;
          padding: 3px 7px;
          border-radius: 10px;
          background: #f59e0b;
          color: #fff;
          font-size: 11px;
          font-weight: 600;
        }
        display: flex;
        flex-direction: column;
        align-items: center;

        .product-image {
          width: min(120px, 100%);
          height: auto;
          aspect-ratio: 1;
          object-fit: contain;
          margin-bottom: 12px;
        }

        .product-info {
          text-align: center;

          .product-name {
            font-size: 14px;
            font-weight: 500;
            color: #1e293b;
            margin: 0 0 8px 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .product-price {
            font-size: 16px;
            font-weight: 600;
            color: #ef4444;
            margin: 0 0 4px 0;
          }

          .product-stock {
            font-size: 12px;
            color: #64748b;
            margin: 0;
          }
        }

        .sold-out-overlay {
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: rgba(255, 255, 255, 0.8);
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 8px;
          z-index: 10;

          span {
            font-size: 24px;
            font-weight: 600;
            color: #ef4444;
            transform: rotate(-30deg);
          }
        }
      }
    }
  }
}

.recommendations-container {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  margin-bottom: 24px;

  .section-header {
    margin-bottom: 20px;

    .section-title {
      font-size: 18px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 4px 0;
    }

    .section-subtitle {
      font-size: 14px;
      color: #64748b;
    }
  }

  .recommendations-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 16px;
  }

      .recommendation-item {
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 12px;
        cursor: pointer;

        &.recommendation-disabled {
          opacity: 0.5;
          cursor: not-allowed;
          filter: grayscale(0.8);
        }
    transition: all 0.3s ease;
    background: #fafafa;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      border-color: #667eea;
    }

    .recommendation-image {
      position: relative;
      margin-bottom: 12px;

      img {
        width: 100%;
        height: 120px;
        object-fit: contain;
        border-radius: 4px;
      }

      .recommendation-score {
        position: absolute;
        top: 8px;
        right: 8px;
        background: rgba(102, 126, 234, 0.9);
        color: white;
        font-size: 11px;
        padding: 2px 6px;
        border-radius: 10px;
      }
    }

    .recommendation-info {
      .recommendation-name {
        font-size: 13px;
        font-weight: 500;
        color: #1e293b;
        margin: 0 0 8px 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .recommendation-price {
        font-size: 16px;
        font-weight: 600;
        color: #ef4444;
        margin: 0 0 8px 0;
      }

      .recommendation-reasons {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;

        .el-tag {
          font-size: 11px;
        }
      }
    }
  }
}

.cart-container {
  background: white;
  padding: 22px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
  margin-bottom: 24px;
  position: sticky;
  top: 24px;

  .cart-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 16px;
    border-bottom: 1px solid #e5e7eb;
    color: #64748b;
  }

  .cart-title {
    font-size: 18px;
    font-weight: 600;
    color: #1e293b;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .empty-cart {
    padding: 40px 0;
  }

  .cart-items {
    .cart-item {
      display: flex;
      align-items: center;
      padding: 14px 0;
      gap: 10px;
      border-bottom: 1px solid #edf0f2;

      .cart-item-image {
        width: 52px;
        height: 52px;
        object-fit: cover;
        border-radius: 9px;
        background: #f8fafc;
      }

      &:last-child {
        border-bottom: none;
      }

      .cart-item-info {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 5px;

        .cart-item-name {
          font-size: 14px;
          color: #1e293b;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .cart-item-price {
          font-size: 14px;
          font-weight: 500;
          color: #64748b;

          del {
            margin-right: 6px;
            color: #94a3b8;
            font-size: 12px;
          }

          strong {
            margin-right: 6px;
            color: #ef4444;
          }

          em {
            color: #ef4444;
            font-size: 11px;
            font-style: normal;
          }
        }
      }

      .cart-item-quantity {
        display: flex;
        align-items: center;
        gap: 5px;
        margin: 0;

        :deep(.el-button) {
          width: 26px;
          height: 26px;
          padding: 0;
          border-radius: 7px;
        }

        :deep(.el-button:last-child) {
          color: #fff;
          background: #16a34a;
          border-color: #16a34a;
        }

        .quantity {
          min-width: 30px;
          text-align: center;
        }
      }

      .remove-btn { color: #ef4444; padding: 4px; }
    }

    .cart-summary {
      padding-top: 18px;
      display: grid;
      gap: 11px;

      > div { display: flex; justify-content: space-between; color: #64748b; }
      strong { color: #1e293b; }
      .free { color: #16a34a; }
      .cart-total { padding-top: 14px; border-top: 1px solid #e5e7eb; font-size: 18px; color: #111827; }
      .cart-total strong { font-size: 21px; }
    }

    .checkout-btn {
      width: 100%;
      height: 46px;
      margin-top: 18px;
      border: 0;
      border-radius: 9px;
      background: #16a34a;
      font-weight: 600;

      &:hover { background: #15803d; }
    }
  }
}

.payment-content {
  .payment-amount {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px 0;
    border-bottom: 1px solid #e2e8f0;

    .amount {
      font-size: 24px;
      font-weight: 600;
      color: #ef4444;
    }
  }

  .payment-method {
    padding: 20px 0;

    h3 {
      font-size: 16px;
      font-weight: 600;
      color: #1e293b;
      margin: 0 0 16px 0;
    }

    .pay-method-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .pay-icon {
        width: 24px;
        height: 24px;
      }
    }
  }
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;

  .success-icon {
    font-size: 48px;
    color: #10b981;
    margin-bottom: 16px;
  }

  p {
    margin: 8px 0;
    color: #475569;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .shopping-content {
    padding: 0 12px;
  }

  .recommendations-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 12px;
  }

  .channels-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 12px;
  }

  .channel-item {
    padding: 12px;

    .product-image {
      width: 100px;
      height: 100px;
    }
  }
}

@media (max-width: 1100px) {
  .store-layout { grid-template-columns: 1fr; }
  .cart-container { position: static; }
}
</style>
