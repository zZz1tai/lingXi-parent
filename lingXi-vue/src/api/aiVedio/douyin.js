import request from '@/utils/request'

/** 解析抖音分享口令/链接，返回视频信息（标题、作者、封面、时长）与播放令牌 */
export function resolveDouyinVideo(text) {
  return request({
    url: '/aivideo/tool/douyin/resolve',
    method: 'post',
    data: { text }
  })
}

/** 播放流地址（<video> 直连，令牌即访问凭证，由解析接口发放） */
export function douyinStreamUrl(token) {
  return import.meta.env.VITE_APP_BASE_API + '/aivideo/tool/douyin/stream/' + token
}
