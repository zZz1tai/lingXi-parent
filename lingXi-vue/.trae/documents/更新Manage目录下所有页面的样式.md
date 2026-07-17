# 更新Manage目录下所有页面的样式

## 目标
将manage目录下所有页面的样式更新为与order和policy页面一致的风格。

## 样式特点（参考order和policy页面）
1. **页面结构**：
   - 顶部页面标题（.page-header），包含主标题和副标题
   - 筛选条件区域（.card.search-card），带有卡片样式
   - 表格区域（.card.table-card），带有卡片样式和表格头
   - 表格操作栏（.table-header），包含左侧标题和右侧操作按钮

2. **样式特点**：
   - 整体背景色：#f5f7fa
   - 卡片样式：白色背景，14px圆角，18px 20px内边距，6px 24px阴影
   - 标题样式：20px字号，600权重
   - 副标题样式：13px字号，#909399颜色
   - 表格头样式：浅灰色背景（#fafafa），600权重
   - 操作按钮区域：右侧对齐，按钮间距8px

## 更新计划

### 1. 更新页面列表
需要更新的页面包括：
- emp/index.vue - 员工管理
- node/index.vue - 点位管理
- partner/index.vue - 合作商管理
- region/index.vue - 区域管理
- sku/index.vue - 商品管理
- skuClass/index.vue - 商品分类管理
- task/business.vue - 业务任务管理
- task/operation.vue - 操作任务管理
- vmStatus/index.vue - 设备状态管理
- vmType/index.vue - 设备类型管理

### 2. 每个页面的更新步骤

#### 步骤1：添加页面容器类
将根容器div的class从`app-container`更新为`app-container {页面名称}-page`，例如`emp-page`。

#### 步骤2：添加页面标题区域
```html
<!-- 页面标题 -->
<div class="page-header">
  <div class="title">
    <i class="el-icon-s-order" /> <!-- 根据页面类型选择合适的图标 -->
    <span>{页面标题}</span>
  </div>
  <div class="sub-title">{页面描述}</div>
</div>
```

#### 步骤3：包装筛选条件区域
将原有的el-form包装在卡片样式中：
```html
<!-- 筛选条件 -->
<div class="card search-card" v-show="showSearch">
  <div class="card-title">
    <i class="el-icon-filter" />
    <span>筛选条件</span>
  </div>
  <!-- 原有的el-form内容 -->
</div>
```

#### 步骤4：包装表格区域
将原有的表格和分页组件包装在卡片样式中：
```html
<!-- 表格列表 -->
<div class="card table-card">
  <div class="table-header">
    <div class="left">
      <i class="el-icon-document" />
      <span class="title">{表格标题}</span>
      <span class="count">共 {{ total }} 条记录</span>
    </div>
    <div class="right">
      <!-- 原有的操作按钮 -->
    </div>
  </div>
  <!-- 原有的el-table和pagination内容 -->
</div>
```

#### 步骤5：添加scoped样式
为每个页面添加与order和policy页面一致的scoped样式：
```scss
<style scoped lang="scss">
.{页面名称}-page {
  background: #f5f7fa;
  padding: 20px;

  .page-header {
    margin-bottom: 18px;

    .title {
      font-size: 20px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .sub-title {
      margin-top: 4px;
      font-size: 13px;
      color: #909399;
    }
  }

  .card {
    background: #fff;
    border-radius: 14px;
    padding: 18px 20px;
    margin-bottom: 16px;
    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.06);
  }

  .card-title {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .left {
      display: flex;
      align-items: center;
      gap: 8px;

      .title {
        font-size: 15px;
        font-weight: 600;
      }

      .count {
        font-size: 13px;
        color: #909399;
      }
    }

    .right {
      display: flex;
      gap: 8px;
    }
  }

  .el-table__header th {
    background: #fafafa;
    font-weight: 600;
  }
}
</style>
```

### 3. 执行顺序
按照以下顺序更新页面，确保每个页面更新后都能正常显示：
1. emp/index.vue - 员工管理
2. node/index.vue - 点位管理
3. partner/index.vue - 合作商管理
4. region/index.vue - 区域管理
5. sku/index.vue - 商品管理
6. skuClass/index.vue - 商品分类管理
7. task/business.vue - 业务任务管理
8. task/operation.vue - 操作任务管理
9. vmStatus/index.vue - 设备状态管理
10. vmType/index.vue - 设备类型管理
11. vm/index.vue - 设备管理（已包含部分样式，需要调整）

### 4. 验证
每个页面更新后，需要检查：
- 页面布局是否正确
- 样式是否与order和policy页面一致
- 功能是否正常（搜索、新增、修改、删除等）
- 响应式布局是否正常

## 预期结果
所有manage页面将具有统一的样式风格，提高系统的一致性和用户体验。