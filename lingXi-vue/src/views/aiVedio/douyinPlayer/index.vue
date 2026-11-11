<template>
  <div v-loading="resolving" class="douyin-player-page">
    <div class="player-shell">
      <header class="player-header">
        <div>
          <button class="back-link" type="button" @click="router.push('/aiVedio/project')">
            <el-icon><ArrowLeft /></el-icon>
            返回小说视频工作台
          </button>
          <p class="eyebrow">DOUYIN PLAYER</p>
          <h1>抖音视频解析</h1>
          <p class="lede">粘贴抖音分享口令或链接，解析后直接在线观看，无需跳转抖音。</p>
        </div>
      </header>

      <section class="share-panel">
        <el-input
          v-model="shareText"
          class="share-input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="粘贴抖音分享口令，例如：8.88 复制打开抖音，看看【作者】的作品 https://v.douyin.com/xxxxxx/ 复制此链接，打开抖音搜索，直接观看视频！"
        />
        <div class="share-actions">
          <el-button type="primary" :icon="VideoPlay" :loading="resolving" @click="resolveVideo">
            解析并播放
          </el-button>
          <el-button :icon="Delete" :disabled="resolving" @click="clearAll">清空</el-button>
        </div>
      </section>

      <section v-if="video" class="result-panel">
        <div class="player-stage">
          <video
            :key="playToken"
            class="player-video"
            controls
            autoplay
            :poster="video.coverUrl || undefined"
            :src="streamUrl"
            @error="onVideoError"
          />
        </div>

        <div class="meta-card">
          <el-image
            v-if="video.coverUrl"
            class="meta-cover"
            fit="cover"
            :src="video.coverUrl"
            :preview-src-list="[video.coverUrl]"
            preview-teleported
          />
          <div class="meta-body">
            <h2 class="meta-title">{{ video.title || '（无标题）' }}</h2>
            <div class="meta-line">
              <el-avatar v-if="video.authorAvatar" :size="28" :src="video.authorAvatar" />
              <span class="meta-author">{{ video.author || '未知作者' }}</span>
              <el-tag v-if="video.durationMs" size="small" type="info">
                {{ formatDurationMs(video.durationMs) }}
              </el-tag>
            </div>
            <div v-if="video.sourceUrl" class="meta-line">
              <el-link :href="video.sourceUrl" target="_blank" type="primary" :icon="Link">
                打开抖音原页面
              </el-link>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup name="AiVedioDouyinPlayer">
import { ArrowLeft, Delete, Link, VideoPlay } from '@element-plus/icons-vue'
import { douyinStreamUrl, resolveDouyinVideo } from '@/api/aiVedio/douyin'
import { formatDurationMs } from '@/utils/douyinVideo'

const { proxy } = getCurrentInstance()
const router = useRouter()
const shareText = ref('')
const resolving = ref(false)
const video = ref(null)
const playToken = ref('')
const streamUrl = ref('')

async function resolveVideo() {
  const text = (shareText.value || '').trim()
  if (!text) {
    proxy.$modal.msgWarning('请先粘贴抖音分享口令')
    return
  }
  resolving.value = true
  try {
    const response = await resolveDouyinVideo(text)
    video.value = response.data
    playToken.value = response.data.playToken
    streamUrl.value = douyinStreamUrl(response.data.playToken)
  } catch {
    // 具体错误提示已由统一请求层展示
  } finally {
    resolving.value = false
  }
}

function clearAll() {
  shareText.value = ''
  video.value = null
  playToken.value = ''
  streamUrl.value = ''
}

function onVideoError() {
  proxy.$modal.msgError('视频播放失败，播放地址可能已过期，请重新解析')
}
</script>

<style scoped>
.douyin-player-page {
  min-height: calc(100vh - 84px);
  padding: 24px;
  background: #f5f7fb;
}

.player-shell {
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.player-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
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
  margin-bottom: 10px;
  font-size: 14px;
}

.back-link:hover {
  color: #409eff;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  letter-spacing: 2px;
  color: #409eff;
}

.player-header h1 {
  margin: 0;
  font-size: 26px;
  color: #1f2d3d;
}

.lede {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.share-panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
}

.share-input :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
}

.share-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
}

.result-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.player-stage {
  background: #0f1115;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  justify-content: center;
}

.player-video {
  width: 100%;
  max-height: 560px;
  background: #0f1115;
}

.meta-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  gap: 16px;
  box-shadow: 0 2px 12px rgba(31, 45, 61, 0.06);
}

.meta-cover {
  width: 120px;
  height: 160px;
  border-radius: 8px;
  flex-shrink: 0;
}

.meta-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.meta-title {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
  color: #1f2d3d;
  word-break: break-word;
}

.meta-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-author {
  color: #4b5563;
  font-size: 14px;
}
</style>
