import request from '@/utils/request'

/** 解析抖音分享口令/链接，返回视频信息（标题、作者、封面、时长）与播放令牌 */
export function resolveDouyinVideo(text) {
  return request({
    url: '/aivideo/tool/douyin/resolve',
    method: 'post',
    data: { text }
  })
}

/** 视频详情页数据：详情、弹幕、首屏评论与相关推荐（一次抓取） */
export function getDouyinVideoPage(awemeId) {
  return request({
    url: '/aivideo/tool/douyin/page/' + awemeId,
    method: 'get'
  })
}

/** 评论翻页（page 从 0 开始） */
export function getDouyinComments(awemeId, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/comments/' + awemeId,
    method: 'get',
    params: { page }
  })
}

/** 相关推荐翻页 */
export function getDouyinRelated(awemeId, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/related/' + awemeId,
    method: 'get',
    params: { page }
  })
}

/** 刷视频流（channel=recommend 为推荐流需登录；sports/music/game 等频道流游客可用） */
export function getDouyinFeed(channel = 'recommend', page = 0) {
  return request({
    url: '/aivideo/tool/douyin/feed',
    method: 'get',
    params: { channel, page }
  })
}

/** 作者主页：资料 + 作品分页 */
export function getDouyinUser(secUid, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/user/' + secUid,
    method: 'get',
    params: { page }
  })
}

/** 搜索视频（需登录抖音） */
export function searchDouyin(keyword, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/search',
    method: 'get',
    params: { keyword, page }
  })
}

/** 打开可见浏览器窗口，一次性登录抖音 */
export function startDouyinBrowserLogin() {
  return request({
    url: '/aivideo/tool/douyin/browser-login',
    method: 'post'
  })
}

/** 抖音登录状态：{loggedIn, windowOpen} */
export function getDouyinLoginStatus() {
  return request({
    url: '/aivideo/tool/douyin/browser-login/status',
    method: 'get'
  })
}

/** 关闭登录窗口并恢复无头抓取 */
export function closeDouyinBrowserLogin() {
  return request({
    url: '/aivideo/tool/douyin/browser-login/close',
    method: 'post'
  })
}

/** 播放流地址（<video> 直连，令牌即访问凭证，由解析/列表接口发放） */
export function douyinStreamUrl(token) {
  return import.meta.env.VITE_APP_BASE_API + '/aivideo/tool/douyin/stream/' + token
}
