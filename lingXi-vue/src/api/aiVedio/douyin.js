import request from '@/utils/request'

// 浏览器抓取包含页面加载、翻页和一次后端重试。
const captureTimeout = 120_000
const loginTimeout = 45_000

/** 解析抖音分享口令/链接，返回视频信息（标题、作者、封面、时长）与播放令牌 */
export function resolveDouyinVideo(text) {
  return request({
    url: '/aivideo/tool/douyin/resolve',
    method: 'post',
    data: { text }
  })
}

/** 播放附加信息：详情、弹幕与相关推荐，评论单独加载。 */
export function getDouyinVideoPage(awemeId) {
  return request({
    url: '/aivideo/tool/douyin/page/' + awemeId,
    method: 'get',
    timeout: captureTimeout,
    params: { includeComments: false }
  })
}

/** 评论翻页（page 从 0 开始） */
export function getDouyinComments(awemeId, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/comments/' + awemeId,
    method: 'get',
    timeout: captureTimeout,
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
    timeout: captureTimeout,
    params: { channel, page }
  })
}

/** 作者主页：资料 + 作品分页 */
export function getDouyinUser(secUid, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/user/' + secUid,
    method: 'get',
    timeout: captureTimeout,
    params: { page }
  })
}

/** 搜索视频（需登录抖音） */
export function searchDouyin(keyword, page = 0) {
  return request({
    url: '/aivideo/tool/douyin/search',
    method: 'get',
    timeout: captureTimeout,
    params: { keyword, page }
  })
}

/** 打开抖音官方搜索页，等待用户手动验证后返回结果。 */
export function verifyDouyinSearch(keyword) {
  return request({
    url: '/aivideo/tool/douyin/search/verify',
    method: 'post',
    timeout: 150_000,
    params: { keyword }
  })
}

/** 打开可见浏览器窗口，一次性登录抖音 */
export function startDouyinBrowserLogin() {
  return request({
    url: '/aivideo/tool/douyin/browser-login',
    method: 'post',
    timeout: loginTimeout
  })
}

/** 抖音登录状态：{loggedIn, windowOpen} */
export function getDouyinLoginStatus() {
  return request({
    url: '/aivideo/tool/douyin/browser-login/status',
    method: 'get',
    timeout: loginTimeout
  })
}

/** 取消登录并关闭可见窗口 */
export function closeDouyinBrowserLogin() {
  return request({
    url: '/aivideo/tool/douyin/browser-login/close',
    method: 'post',
    timeout: loginTimeout
  })
}

/** 播放流地址（<video> 直连，令牌即访问凭证，由解析/列表接口发放） */
export function douyinStreamUrl(token) {
  return import.meta.env.VITE_APP_BASE_API + '/aivideo/tool/douyin/stream/' + token
}
