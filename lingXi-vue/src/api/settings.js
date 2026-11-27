import request from '@/utils/request'

export const getDesktopSettings = () => request({ url: '/desktop/settings', method: 'get' })
export const updateDesktopSettings = data => request({
  url: '/desktop/settings', method: 'put', data, headers: { repeatSubmit: false }
})
