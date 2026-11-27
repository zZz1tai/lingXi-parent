const sections = {
  system: { label: '系统管理', description: '维护组织、权限和系统基础信息。' },
  monitor: { label: '运行监控', description: '查看服务运行情况，及时发现并处理异常。' },
  tool: { label: '开发工具', description: '管理开发任务与代码生成配置。' },
  manage: { label: '业务管理', description: '查询和维护业务记录，使用上方条件快速缩小范围。' }
}
const descriptions = {
  region: '维护业务区域，查看各区域关联的点位数量。',
  node: '管理设备投放点位及其区域归属。',
  order: '查询订单、支付状态及销售明细。',
  sku: '维护商品资料、分类与销售配置。',
  emp: '维护员工资料、角色与负责区域。',
  vm: '查看设备状态，管理投放位置与运营配置。',
  user: '管理账户、登录状态和角色分配。',
  role: '维护角色及其功能和数据访问权限。',
  operlog: '按操作人、业务类型和时间追踪系统操作。'
}

export function workspacePage(route) {
  const segments = route.path.split('/').filter(Boolean)
  const section = sections[segments[0]]
  if (!section || route.meta?.link || route.meta?.hidePageHeading || !route.meta?.title) return null
  // 个人中心、设置重定向及嵌入式详情由自己的页面负责标题。
  if (segments.includes('profile') || segments[0] === 'app' && segments.includes('detail')) return null
  return { category: section.label, title: route.meta.title, description: descriptions[segments[1]] || section.description }
}
