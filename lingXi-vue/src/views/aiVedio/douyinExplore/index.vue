<template>
  <div class="douyin-explore-page">
    <header class="explore-header">
      <div class="header-left">
        <button class="back-link" type="button" @click="router.push('/aiVedio/project')">
          <el-icon><ArrowLeft /></el-icon>
          返回小说视频工作台
        </button>
        <h1>刷抖音</h1>
      </div>
      <div class="search-box">
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索抖音视频（需登录）"
          clearable
          @keyup.enter="doSearch"
        />
        <el-button type="primary" :icon="Search" :loading="listLoading" @click="doSearch">搜索</el-button>
      </div>
      <div class="login-area">
        <el-tag v-if="loggedIn" type="success" effect="plain">已登录抖音</el-tag>
        <el-button v-else :icon="User" :loading="loginOpening" @click="openLogin">浏览器登录</el-button>
      </div>
    </header>

    <div class="channel-bar">
      <el-radio-group :model-value="channel" @change="switchChannel">
        <el-radio-button v-for="item in channels" :key="item.value" :value="item.value">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <div class="explore-body">
      <aside class="list-panel">
        <div v-if="mode === 'user' && userInfo" class="user-toolbar">
          <button class="back-link" type="button" @click="backToBrowse">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </button>
          <div class="user-card">
            <el-avatar :size="42" :src="userInfo.avatar" />
            <div>
              <strong>{{ userInfo.nickname }}</strong>
              <p>粉丝 {{ formatCount(userInfo.followerCount) }} · 作品 {{ formatCount(userInfo.awemeCount) }}</p>
            </div>
          </div>
        </div>
        <div v-else class="list-toolbar">{{ listTitle }}</div>

        <div ref="listRef" class="video-list" @scroll="onListScroll">
          <div
            v-for="card in listItems"
            :key="card.awemeId"
            class="video-card"
            :class="{ active: current && current.awemeId === card.awemeId }"
            @click="playCard(card)"
          >
            <div class="cover-wrap">
              <el-image class="cover" fit="cover" :src="card.coverUrl" :alt="card.title" />
              <span class="duration">{{ formatDurationMs(card.durationMs) }}</span>
            </div>
            <div class="card-body">
              <p class="card-title">{{ card.title || '（无标题）' }}</p>
              <p class="card-meta">
                <span class="card-author">{{ card.author || '未知作者' }}</span>
                <span>♥ {{ formatCount(card.likeCount) }}</span>
              </p>
            </div>
          </div>
          <div v-if="listLoading" class="list-hint">加载中…</div>
          <div v-else-if="listItems.length && !listHasMore" class="list-hint">没有更多了</div>
          <el-empty v-if="!listItems.length && !listLoading" description="暂无视频" />
        </div>
      </aside>

      <main class="player-panel">
        <template v-if="current">
          <div class="player-stage">
            <video
              :key="playerKey"
              ref="videoRef"
              class="player-video"
              controls
              autoplay
              :poster="current.coverUrl || undefined"
              :src="streamUrl"
              @timeupdate="onTimeUpdate"
              @error="onVideoError"
            />
            <danmaku-layer :danmaku="danmaku" :current-time="currentTime" :enabled="danmakuEnabled" />
            <div class="danmaku-switch">
              <el-switch v-model="danmakuEnabled" active-text="弹幕" />
            </div>
          </div>

          <div class="video-info">
            <div class="author-line" @click="openAuthor(current)">
              <el-avatar :size="32" :src="current.authorAvatar" />
              <span class="author-name">{{ current.author || '未知作者' }}</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
            <p class="video-title">{{ current.title || '（无标题）' }}</p>
            <p class="stat-line">
              ♥ {{ formatCount(current.likeCount) }}
              · 💬 {{ formatCount(current.commentCount) }}
              · {{ formatDurationMs(current.durationMs) }}
            </p>
          </div>

          <section class="comments-panel">
            <h3>评论</h3>
            <div class="comment-list">
              <div v-for="comment in comments" :key="comment.cid" class="comment-item">
                <el-avatar :size="28" :src="comment.avatar" />
                <div class="comment-body">
                  <p class="comment-nick">
                    {{ comment.nickname }}
                    <span class="comment-time">{{ formatRelativeTime(comment.createTime) }}</span>
                  </p>
                  <p class="comment-text">{{ comment.text }}</p>
                </div>
              </div>
              <div v-if="commentsLoading" class="list-hint">加载中…</div>
              <el-button
                v-else-if="commentsHasMore"
                class="load-more"
                text
                type="primary"
                @click="loadMoreComments"
              >加载更多评论</el-button>
              <div v-else-if="comments.length" class="list-hint">没有更多评论了</div>
              <div v-if="pageLoading" class="list-hint">正在加载弹幕与评论…</div>
              <el-empty
                v-else-if="pageFailed"
                :image-size="60"
                description="评论加载失败"
              >
                <el-button type="primary" size="small" @click="retryVideoPage">重新加载</el-button>
              </el-empty>
              <el-empty v-else-if="!comments.length && !commentsLoading" :image-size="60" description="暂无评论" />
            </div>
          </section>

          <section v-if="related.length" class="related-panel">
            <h3>相关推荐</h3>
            <div class="related-grid">
              <div
                v-for="item in related"
                :key="item.awemeId"
                class="related-card"
                @click="playCard(item)"
              >
                <el-image class="related-cover" fit="cover" :src="item.coverUrl" :alt="item.title" />
                <p class="related-title">{{ item.title || '（无标题）' }}</p>
              </div>
            </div>
          </section>
        </template>
        <el-empty v-else description="从左侧选择视频开始播放" />
      </main>
    </div>
  </div>
</template>

<script setup name="AiVedioDouyinExplore">
import { ArrowLeft, ArrowRight, Search, User } from '@element-plus/icons-vue'
import {
  closeDouyinBrowserLogin,
  douyinStreamUrl,
  getDouyinComments,
  getDouyinFeed,
  getDouyinLoginStatus,
  getDouyinUser,
  getDouyinVideoPage,
  searchDouyin,
  startDouyinBrowserLogin
} from '@/api/aiVedio/douyin'
import { formatCount, formatDurationMs, formatRelativeTime } from '@/utils/douyinVideo'
import DanmakuLayer from './components/DanmakuLayer.vue'

const { proxy } = getCurrentInstance()
const router = useRouter()

const channels = [
  { value: 'recommend', label: '推荐' },
  { value: 'sports', label: '体育' },
  { value: 'music', label: '音乐' },
  { value: 'game', label: '游戏' },
  { value: 'anime', label: '二次元' },
  { value: 'food', label: '美食' },
  { value: 'knowledge', label: '知识' }
]

const keyword = ref('')
const channel = ref('sports')
const mode = ref('feed')
const listItems = ref([])
const listHasMore = ref(false)
const listPage = ref(0)
const listLoading = ref(false)
const listRef = ref()
const userInfo = ref(null)

const current = ref(null)
const streamUrl = ref('')
const playerKey = ref(0)
const currentTime = ref(0)
const danmaku = ref([])
const danmakuEnabled = ref(true)
const comments = ref([])
const commentsHasMore = ref(false)
const commentsPage = ref(0)
const commentsLoading = ref(false)
const related = ref([])
const pageLoading = ref(false)
const pageFailed = ref(false)

const loggedIn = ref(false)
const loginOpening = ref(false)
let loginPollTimer = null

const listTitle = computed(() => {
  if (mode.value === 'search') return `搜索：${keyword.value}`
  if (mode.value === 'user') return '作者作品'
  const found = channels.find((item) => item.value === channel.value)
  return `${found ? found.label : channel.value}频道`
})

onMounted(() => {
  loadList(true)
  refreshLoginStatus()
})

onBeforeUnmount(() => {
  if (loginPollTimer) clearInterval(loginPollTimer)
})

let listRequestId = 0

async function loadList(reset) {
  // 仅防翻页重入；切换频道/搜索属于 reset，立即生效不受加载中限制
  if (!reset && listLoading.value) return
  const requestId = ++listRequestId
  const requestMode = mode.value
  listLoading.value = true
  if (reset) {
    listPage.value = 0
    listItems.value = []
    listHasMore.value = true
  } else {
    listPage.value += 1
  }
  try {
    let response
    if (requestMode === 'search') {
      response = await searchDouyin(keyword.value, listPage.value)
    } else if (requestMode === 'user') {
      response = await getDouyinUser(userInfo.value?.secUid, listPage.value)
    } else {
      response = await getDouyinFeed(channel.value, listPage.value)
    }
    if (requestId !== listRequestId) return
    const data = response.data
    const page = requestMode === 'user' ? data.posts : data
    if (requestMode === 'user' && data.profile) {
      userInfo.value = data.profile
    }
    const items = page.items || []
    // 按作品 ID 去重：翻页偶发返回重复页时列表也能继续增长
    const existing = new Set(listItems.value.map((item) => item.awemeId))
    const fresh = items.filter((item) => item.awemeId && !existing.has(item.awemeId))
    listItems.value = reset ? items : [...listItems.value, ...fresh]
    listHasMore.value = Boolean(page.hasMore)
  } catch {
    if (requestId !== listRequestId) return
    if (reset) {
      // 错误提示已由统一请求层展示；清空以便用户重试
      listItems.value = []
      listHasMore.value = false
    } else {
      // 翻页失败回退页码，避免跳过一页内容
      listPage.value -= 1
    }
  } finally {
    if (requestId === listRequestId) {
      listLoading.value = false
    }
  }
}

function switchChannel(value) {
  channel.value = value
  mode.value = 'feed'
  userInfo.value = null
  loadList(true)
}

function doSearch() {
  const text = (keyword.value || '').trim()
  if (!text) {
    proxy.$modal.msgWarning('请输入搜索关键词')
    return
  }
  keyword.value = text
  mode.value = 'search'
  userInfo.value = null
  loadList(true)
}

function onListScroll() {
  const el = listRef.value
  if (!el || !listHasMore.value || listLoading.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 120) {
    // 页码在 loadList 内统一维护：失败会回退，避免跳页
    loadList(false)
  }
}

function backToBrowse() {
  userInfo.value = null
  mode.value = 'feed'
  loadList(true)
}

async function playCard(card) {
  if (!card || !card.awemeId) return
  if (!card.playToken) {
    proxy.$modal.msgWarning('该作品暂不支持在线播放')
    return
  }
  current.value = card
  streamUrl.value = douyinStreamUrl(card.playToken)
  playerKey.value += 1
  currentTime.value = 0
  danmaku.value = []
  comments.value = []
  commentsHasMore.value = false
  commentsPage.value = 0
  related.value = []
  pageLoading.value = true
  pageFailed.value = false
  loadVideoPage(card.awemeId)
}

function retryVideoPage() {
  if (!current.value) return
  pageLoading.value = true
  pageFailed.value = false
  loadVideoPage(current.value.awemeId)
}

async function loadVideoPage(awemeId) {
  try {
    const response = await getDouyinVideoPage(awemeId)
    const data = response.data
    if (current.value?.awemeId !== awemeId) return
    if (data.video?.playToken && !current.value.playToken) {
      current.value = { ...current.value, playToken: data.video.playToken }
      streamUrl.value = douyinStreamUrl(data.video.playToken)
    }
    danmaku.value = data.danmaku || []
    comments.value = data.comments || []
    commentsHasMore.value = Boolean(data.commentsHasMore)
    related.value = data.related || []
  } catch {
    // 弹幕/评论缺失不阻断播放，错误提示已由统一请求层展示
    if (current.value?.awemeId === awemeId) {
      pageFailed.value = true
    }
  } finally {
    if (current.value?.awemeId === awemeId) {
      pageLoading.value = false
    }
  }
}

async function loadMoreComments() {
  if (!current.value || commentsLoading.value) return
  commentsLoading.value = true
  try {
    commentsPage.value += 1
    const response = await getDouyinComments(current.value.awemeId, commentsPage.value)
    const data = response.data
    // 按评论 ID 去重：翻页偶发返回重复页时列表也能继续增长
    const existing = new Set(comments.value.map((item) => item.cid))
    const fresh = (data.items || []).filter((item) => item.cid && !existing.has(item.cid))
    comments.value = [...comments.value, ...fresh]
    commentsHasMore.value = Boolean(data.hasMore)
    if (!fresh.length) {
      proxy.$modal.msgWarning('没有加载到新评论，稍后可再试一次')
    }
  } catch {
    commentsPage.value -= 1
  } finally {
    commentsLoading.value = false
  }
}

function openAuthor(card) {
  if (!card?.authorSecUid) {
    proxy.$modal.msgWarning('无法获取作者主页')
    return
  }
  mode.value = 'user'
  userInfo.value = { secUid: card.authorSecUid }
  loadList(true)
}

function onTimeUpdate(event) {
  currentTime.value = event.target.currentTime || 0
}

function onVideoError() {
  proxy.$modal.msgError('视频播放失败，播放地址可能已过期，请点击列表重新播放')
}

async function refreshLoginStatus() {
  try {
    const response = await getDouyinLoginStatus()
    loggedIn.value = Boolean(response.data?.loggedIn)
  } catch {
    // 状态查询失败不影响浏览
  }
}

async function openLogin() {
  loginOpening.value = true
  try {
    await startDouyinBrowserLogin()
    proxy.$modal.msgSuccess('登录窗口已打开，请在弹出的浏览器中扫码/验证码登录抖音')
    if (loginPollTimer) clearInterval(loginPollTimer)
    let polls = 0
    loginPollTimer = setInterval(async () => {
      polls += 1
      await refreshLoginStatus()
      if (loggedIn.value || polls > 40) {
        clearInterval(loginPollTimer)
        loginPollTimer = null
        if (loggedIn.value) {
          proxy.$modal.msgSuccess('抖音登录成功，现在可以使用搜索与推荐流')
          try {
            await closeDouyinBrowserLogin()
          } catch {
            // 关闭失败不影响后续使用
          }
        }
      }
    }, 3000)
  } catch {
    // 错误提示已由统一请求层展示
  } finally {
    loginOpening.value = false
  }
}
</script>

<style scoped>
.douyin-explore-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  background: #f5f7fb;
}

.explore-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 20px 10px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-left h1 {
  margin: 0;
  font-size: 22px;
  color: #1f2d3d;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  color: #6b7280;
  cursor: pointer;
  padding: 0;
  font-size: 13px;
}

.back-link:hover {
  color: #409eff;
}

.search-box {
  display: flex;
  gap: 8px;
  flex: 1;
  max-width: 520px;
}

.login-area {
  margin-left: auto;
}

.channel-bar {
  padding: 0 20px 10px;
}

.explore-body {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 0 20px 20px;
  min-height: 0;
}

.list-panel {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
}

.list-toolbar {
  padding: 12px 16px;
  font-weight: 600;
  color: #1f2d3d;
  border-bottom: 1px solid #eef1f6;
}

.user-toolbar {
  padding: 10px 16px;
  border-bottom: 1px solid #eef1f6;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-card p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.video-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.video-card {
  display: flex;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid transparent;
}

.video-card:hover {
  background: #f2f6ff;
}

.video-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.cover-wrap {
  position: relative;
  width: 112px;
  height: 84px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 100%;
}

.duration {
  position: absolute;
  right: 4px;
  bottom: 4px;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
}

.card-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.card-title {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: #1f2d3d;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.card-meta {
  margin: 0;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}

.card-author {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-hint {
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  padding: 8px 0;
}

.player-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 纵向 flex 子项默认会被压缩：评论/推荐变多时会把播放器舞台压扁甚至“挤没”，一律禁止压缩、超高走滚动 */
.player-panel > * {
  flex-shrink: 0;
}

.player-stage {
  position: relative;
  background: #0f1115;
  border-radius: 12px;
  overflow: hidden;
  /* 舞台高度固定、宽度随视频比例自适应：竖屏为窄条、横屏铺满，尺寸稳定不塌缩 */
  height: min(62vh, 560px);
  width: fit-content;
  max-width: 100%;
  margin: 0 auto;
  display: flex;
  justify-content: center;
}

.player-video {
  display: block;
  height: 100%;
  width: auto;
  max-width: 100%;
  object-fit: contain;
  background: #0f1115;
}

.danmaku-switch {
  position: absolute;
  right: 14px;
  top: 12px;
  z-index: 6;
  background: rgba(15, 17, 21, 0.55);
  padding: 4px 10px;
  border-radius: 8px;
}

.danmaku-switch :deep(.el-switch__label) {
  color: #fff;
}

.video-info {
  background: #fff;
  border-radius: 12px;
  padding: 14px 18px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
}

.author-line {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  width: fit-content;
}

.author-line:hover .author-name {
  color: #409eff;
}

.author-name {
  font-weight: 600;
  color: #1f2d3d;
}

.video-title {
  margin: 10px 0 6px;
  font-size: 15px;
  line-height: 1.6;
  color: #1f2d3d;
  word-break: break-word;
}

.stat-line {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.comments-panel,
.related-panel {
  background: #fff;
  border-radius: 12px;
  padding: 14px 18px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
}

.comments-panel h3,
.related-panel h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #1f2d3d;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-item {
  display: flex;
  gap: 10px;
}

.comment-body {
  min-width: 0;
}

.comment-nick {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 10px;
}

.comment-time {
  color: #b0b7c3;
}

.comment-text {
  margin: 4px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: #1f2d3d;
  word-break: break-word;
}

.load-more {
  align-self: center;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.related-card {
  cursor: pointer;
}

.related-card:hover .related-title {
  color: #409eff;
}

.related-cover {
  width: 100%;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
}

.related-title {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #4b5563;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
</style>
