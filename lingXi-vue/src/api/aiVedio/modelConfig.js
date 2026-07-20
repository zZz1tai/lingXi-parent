import request from '@/utils/request'

export function getAiVideoModelConfig() {
  return request({
    url: '/aivideo/model-config',
    method: 'get'
  })
}

export function updateAiVideoModelConfig(data) {
  return request({
    url: '/aivideo/model-config',
    method: 'put',
    data
  })
}
