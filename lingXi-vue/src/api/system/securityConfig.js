import request from '@/utils/request'

export function getSystemSecurityConfig() {
  return request({
    url: '/system/security-config',
    method: 'get'
  })
}

export function updateSystemSecurityConfig(data) {
  return request({
    url: '/system/security-config',
    method: 'put',
    headers: { repeatSubmit: false },
    data
  })
}
