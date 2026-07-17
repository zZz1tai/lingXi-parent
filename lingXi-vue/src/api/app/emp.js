import request from '@/utils/request'

// 获取员工信息
export function getEmpInfo(id) {
  return request({
    url: `/user-service/user/${id}`,
    method: 'get'
  })
}

// 根据用户ID获取员工信息
export function getEmpByUserId(userId) {
  return request({
    url: `/user-service/user/byUserId/${userId}`,
    method: 'get'
  })
}
