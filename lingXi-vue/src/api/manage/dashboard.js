import request from '@/utils/request'

// 查询工单统计
export function getTaskStats(query) {
  return request({
    url: '/manage/dashboard/taskStats',
    method: 'get',
    params: query
  })
}

// 查询销售统计
export function getSaleStats(query) {
  return request({
    url: '/manage/dashboard/saleStats',
    method: 'get',
    params: query
  })
}

// 查询SKU销售排名
export function getSkuSaleRank(query) {
  return request({
    url: '/manage/dashboard/skuSaleRank',
    method: 'get',
    params: query
  })
}

// 查询SKU销售汇总
export function getSkuSaleCollect(query) {
  return request({
    url: '/manage/dashboard/skuSaleCollect',
    method: 'get',
    params: query
  })
}

// 查询合作商节点汇总
export function getPartnerNodeCollect(query) {
  return request({
    url: '/manage/dashboard/partnerNodeCollect',
    method: 'get',
    params: query
  })
}

// 查询异常设备列表
export function getAbnormalEquipment(query) {
  return request({
    url: '/manage/dashboard/abnormalEquipment',
    method: 'get',
    params: query
  })
}
