<template>
  <div v-loading="loading" class="model-config-page">
    <div class="config-shell">
      <header class="config-header">
        <div>
          <button class="back-link" type="button" @click="router.push('/aiVedio/project')">
            <el-icon><ArrowLeft /></el-icon>
            返回小说视频工作台
          </button>
          <p class="eyebrow">PRODUCTION ROUTING</p>
          <h1>AI 模型配置</h1>
          <p class="lede">控制章节分析、视觉素材和成片生成所使用的模型。保存后的配置从下一次新任务开始生效。</p>
        </div>
        <div class="effective-badge">
          <span class="pulse" />
          运行时读取
        </div>
      </header>

      <section class="pipeline" aria-label="AI 视频生产管线">
        <article class="pipeline-node text-node">
          <span class="node-index">01</span>
          <el-icon><Document /></el-icon>
          <div><small>章节分析</small><strong>{{ form.textModel || '未配置' }}</strong></div>
        </article>
        <span class="pipeline-link" />
        <article class="pipeline-node image-node">
          <span class="node-index">02</span>
          <el-icon><Picture /></el-icon>
          <div><small>图片生成</small><strong>{{ form.imageModel || '未配置' }}</strong></div>
        </article>
        <span class="pipeline-link" />
        <article class="pipeline-node video-node">
          <span class="node-index">03</span>
          <el-icon><VideoCamera /></el-icon>
          <div><small>视频生成</small><strong>{{ form.videoModel || '未配置' }}</strong></div>
        </article>
      </section>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <section class="workspace-panel">
          <div class="panel-heading">
            <div class="heading-icon"><Connection /></div>
            <div>
              <p class="section-kicker">共享入口</p>
              <h2>阿里云百炼业务空间</h2>
              <p>文本请求保留 compatible-mode 路径，图片和视频请求会自动转换为原生 API 路径。</p>
            </div>
          </div>
          <el-form-item label="Workspace Base URL" prop="workspaceBaseUrl">
            <el-input v-model="form.workspaceBaseUrl" size="large" placeholder="https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1" />
          </el-form-item>
          <el-form-item label="API Key" prop="apiKey">
            <div class="secret-field">
              <el-input
                v-if="apiKeyEditing"
                ref="apiKeyInputRef"
                v-model="form.apiKey"
                type="password"
                show-password
                clearable
                autocomplete="new-password"
                maxlength="256"
                size="large"
                placeholder="粘贴新的 API Key"
              />
              <el-input
                v-else
                :model-value="form.apiKeyMasked"
                readonly
                size="large"
                aria-label="已保存的 API Key 掩码"
              />
              <el-button v-if="form.apiKeyConfigured && !apiKeyEditing" size="large" @click="startApiKeyEdit">
                更换密钥
              </el-button>
              <el-button v-if="form.apiKeyConfigured && apiKeyEditing" size="large" @click="cancelApiKeyEdit">
                取消更换
              </el-button>
            </div>
          </el-form-item>
          <div class="security-note">
            <el-icon><Lock /></el-icon>
            API Key 保存后只显示首尾字符，中间以 ** 隐藏，接口不会回传完整内容。
          </div>
        </section>

        <div class="model-grid">
          <section class="model-card text-card">
            <div class="card-accent" />
            <div class="card-heading">
              <span>TEXT</span>
              <el-icon><Document /></el-icon>
            </div>
            <h2>章节分析模型</h2>
            <p>拆解人物、场景和分镜，并生成后续模型可消费的结构化结果。</p>
            <el-form-item label="模型名称" prop="textModel">
              <el-input v-model="form.textModel" placeholder="deepseek-v4-flash" />
            </el-form-item>
            <el-form-item label="场景并发数" prop="chapterSceneConcurrency">
              <el-input-number
                v-model="form.chapterSceneConcurrency"
                :min="1"
                :max="8"
                :step="1"
                step-strictly
                controls-position="right"
              />
            </el-form-item>
            <p class="field-note">同一章节最多同时生成的场景数。默认 2；调高可能触发模型限流。</p>
          </section>

          <section class="model-card image-card">
            <div class="card-accent" />
            <div class="card-heading">
              <span>IMAGE</span>
              <el-icon><Picture /></el-icon>
            </div>
            <h2>图片生成模型</h2>
            <p>生成人物三视图、场景参考图和分镜关键帧，并保留已绑定的参考图顺序。</p>
            <el-form-item label="模型名称" prop="imageModel">
              <el-input v-model="form.imageModel" placeholder="qwen-image-2.0-pro-2026-06-22" />
            </el-form-item>
          </section>

          <section class="model-card video-card">
            <div class="card-accent" />
            <div class="card-heading">
              <span>VIDEO</span>
              <el-icon><VideoCamera /></el-icon>
            </div>
            <h2>视频生成模型</h2>
            <p>使用关键帧、人物三视图和场景图生成成片；适配器决定请求协议。</p>
            <el-form-item label="已安装适配器">
              <el-input v-model="form.videoProvider" disabled>
                <template #suffix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="模型名称" prop="videoModel">
              <el-input v-model="form.videoModel" placeholder="happyhorse-1.1-r2v" />
            </el-form-item>
            <p class="adapter-note">当前适配器接受 <code>happyhorse-*</code>。切换 Seedance 等协议时，需要先安装对应适配器。</p>
            <div class="video-options">
              <el-form-item label="分辨率" prop="videoResolution">
                <el-select v-model="form.videoResolution">
                  <el-option label="720P" value="720P" />
                  <el-option label="1080P" value="1080P" />
                </el-select>
              </el-form-item>
              <el-form-item label="成片画幅" prop="videoRatio">
                <el-select v-model="form.videoRatio">
                  <el-option v-for="ratio in ratios" :key="ratio" :label="ratio" :value="ratio" />
                </el-select>
              </el-form-item>
            </div>
            <p class="adapter-note">场景图和关键帧会跟随成片方向生成；人物三视图为保证正面、侧面、背面并排展示，始终使用 16:9。</p>
            <div class="switch-row">
              <div><strong>供应商水印</strong><span>在视频右下角添加 Happy Horse 标识</span></div>
              <el-switch v-model="form.videoWatermark" />
            </div>
          </section>
        </div>
      </el-form>

      <footer class="action-bar">
        <div>
          <strong>只影响新任务</strong>
          <span>已经提交给供应商的任务会继续使用提交时的参数。</span>
        </div>
        <div class="action-buttons">
          <el-button :icon="Refresh" :disabled="saving" @click="loadConfig">重新加载</el-button>
          <el-button type="primary" :icon="Check" :loading="saving" @click="saveConfig">保存模型配置</el-button>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup name="AiVedioModelConfig">
import { ArrowLeft, Check, Connection, Document, Lock, Picture, Refresh, VideoCamera } from '@element-plus/icons-vue'
import { getAiVideoModelConfig, updateAiVideoModelConfig } from '@/api/aiVedio/modelConfig'

const { proxy } = getCurrentInstance()
const router = useRouter()
const formRef = ref()
const apiKeyInputRef = ref()
const loading = ref(false)
const saving = ref(false)
const apiKeyEditing = ref(true)
const ratios = ['16:9', '9:16', '3:4', '4:3', '4:5', '5:4', '1:1', '9:21', '21:9']
const form = reactive({
  workspaceBaseUrl: '',
  apiKey: '',
  apiKeyMasked: '',
  apiKeyConfigured: false,
  textModel: '',
  chapterSceneConcurrency: 2,
  imageModel: '',
  videoProvider: '',
  videoModel: '',
  videoResolution: '720P',
  videoRatio: '16:9',
  videoWatermark: false
})
const modelPattern = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/
function validateWorkspaceUrl(_rule, value, callback) {
  try {
    const url = new URL(value)
    const validHost = url.hostname === 'dashscope.aliyuncs.com' || url.hostname.endsWith('.cn-beijing.maas.aliyuncs.com')
    const validPort = !url.port || url.port === '443'
    if (url.protocol !== 'https:' || !validHost || !validPort || url.username || url.password || url.search || url.hash) {
      callback(new Error('请输入阿里云百炼北京地域的标准 HTTPS 地址'))
      return
    }
    callback()
  } catch {
    callback(new Error('请输入阿里云百炼北京地域的标准 HTTPS 地址'))
  }
}
function validateApiKey(_rule, value, callback) {
  if (form.apiKeyConfigured && !apiKeyEditing.value) {
    callback()
    return
  }
  const apiKey = (value || '').trim()
  if (!apiKey) {
    callback(new Error('请粘贴 API Key'))
    return
  }
  if (apiKey.length < 8 || apiKey.length > 256 || /\s/.test(apiKey)) {
    callback(new Error('API Key 应为 8-256 个不含空格的字符'))
    return
  }
  callback()
}
const rules = {
  workspaceBaseUrl: [
    { required: true, message: '请输入业务空间地址', trigger: 'blur' },
    { validator: validateWorkspaceUrl, trigger: 'blur' }
  ],
  apiKey: [{ validator: validateApiKey, trigger: ['blur', 'change'] }],
  textModel: [{ required: true, pattern: modelPattern, message: '模型名称格式不正确', trigger: 'blur' }],
  chapterSceneConcurrency: [
    { required: true, type: 'number', min: 1, max: 8, message: '场景并发数必须在 1 到 8 之间', trigger: 'change' }
  ],
  imageModel: [{ required: true, pattern: modelPattern, message: '模型名称格式不正确', trigger: 'blur' }],
  videoModel: [{ required: true, pattern: modelPattern, message: '模型名称格式不正确', trigger: 'blur' }],
  videoResolution: [{ required: true, message: '请选择分辨率', trigger: 'change' }],
  videoRatio: [{ required: true, message: '请选择画面比例', trigger: 'change' }]
}

function applyConfig(data = {}) {
  Object.assign(form, {
    workspaceBaseUrl: data.workspaceBaseUrl || '',
    apiKey: '',
    apiKeyMasked: data.apiKeyMasked || '',
    apiKeyConfigured: Boolean(data.apiKeyConfigured),
    textModel: data.textModel || '',
    chapterSceneConcurrency: Number.isInteger(Number(data.chapterSceneConcurrency))
      ? Number(data.chapterSceneConcurrency)
      : 2,
    imageModel: data.imageModel || '',
    videoProvider: data.videoProvider || 'happyhorse',
    videoModel: data.videoModel || '',
    videoResolution: data.videoResolution || '720P',
    videoRatio: data.videoRatio || '16:9',
    videoWatermark: Boolean(data.videoWatermark)
  })
  apiKeyEditing.value = !form.apiKeyConfigured
  nextTick(() => formRef.value?.clearValidate('apiKey'))
}

function startApiKeyEdit() {
  form.apiKey = ''
  apiKeyEditing.value = true
  nextTick(() => apiKeyInputRef.value?.focus())
}

function cancelApiKeyEdit() {
  form.apiKey = ''
  apiKeyEditing.value = false
  formRef.value?.clearValidate('apiKey')
}

async function loadConfig() {
  loading.value = true
  try {
    const response = await getAiVideoModelConfig()
    applyConfig(response.data)
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      workspaceBaseUrl: form.workspaceBaseUrl,
      textModel: form.textModel,
      chapterSceneConcurrency: form.chapterSceneConcurrency,
      imageModel: form.imageModel,
      videoProvider: form.videoProvider,
      videoModel: form.videoModel,
      videoResolution: form.videoResolution,
      videoRatio: form.videoRatio,
      videoWatermark: form.videoWatermark
    }
    if (apiKeyEditing.value) payload.apiKey = form.apiKey.trim()
    const response = await updateAiVideoModelConfig(payload)
    applyConfig(response.data)
    proxy.$modal.msgSuccess('模型配置已保存，将从下一次新任务开始生效')
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>

<style scoped>
.model-config-page {
  min-height: calc(100vh - 84px);
  padding: 30px;
  color: #edf1f7;
  background:
    radial-gradient(circle at 86% 4%, rgba(99, 211, 255, 0.09), transparent 24%),
    #101318;
}
.config-shell { max-width: 1380px; margin: 0 auto; }
.config-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 28px; margin-bottom: 28px; }
.back-link { display: inline-flex; align-items: center; gap: 7px; margin: 0 0 24px; padding: 0; color: #98a3b3; border: 0; background: transparent; cursor: pointer; }
.back-link:hover, .back-link:focus-visible { color: #63d3ff; }
.back-link:focus-visible { outline: 2px solid #63d3ff; outline-offset: 5px; }
.eyebrow, .section-kicker { margin: 0 0 8px; color: #63d3ff; font: 700 11px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .2em; }
h1 { margin: 0; font-size: clamp(34px, 5vw, 58px); line-height: .98; letter-spacing: -.045em; }
.lede { max-width: 720px; margin: 16px 0 0; color: #9da8b7; font-size: 16px; line-height: 1.7; }
.effective-badge { display: inline-flex; align-items: center; gap: 10px; padding: 10px 14px; color: #b9f3cf; border: 1px solid rgba(87, 215, 139, .24); border-radius: 999px; background: rgba(87, 215, 139, .08); font-size: 13px; white-space: nowrap; }
.pulse { width: 7px; height: 7px; border-radius: 50%; background: #57d78b; box-shadow: 0 0 0 5px rgba(87, 215, 139, .1); }
.pipeline { display: grid; grid-template-columns: 1fr 42px 1fr 42px 1fr; align-items: center; margin-bottom: 22px; }
.pipeline-node { position: relative; display: flex; align-items: center; gap: 14px; min-width: 0; padding: 17px 18px; border: 1px solid #2a313d; border-radius: 14px; background: #171c24; }
.pipeline-node > .el-icon { flex: 0 0 auto; width: 34px; height: 34px; border-radius: 10px; font-size: 18px; background: rgba(255, 255, 255, .05); }
.pipeline-node div { min-width: 0; }
.pipeline-node small { display: block; margin-bottom: 4px; color: #8390a1; }
.pipeline-node strong { display: block; overflow: hidden; font: 600 13px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.node-index { position: absolute; top: 8px; right: 10px; color: #46505e; font: 700 10px/1 ui-monospace, SFMono-Regular, Consolas, monospace; }
.text-node { border-top-color: #8b7cf6; } .image-node { border-top-color: #63d3ff; } .video-node { border-top-color: #ffb85c; }
.pipeline-link { height: 1px; background: linear-gradient(90deg, #343d49, #566273, #343d49); }
.workspace-panel { margin-bottom: 22px; padding: 25px; border: 1px solid #2a313d; border-radius: 18px; background: #171c24; }
.panel-heading { display: flex; gap: 16px; margin-bottom: 22px; }
.heading-icon { display: grid; flex: 0 0 46px; height: 46px; place-items: center; color: #63d3ff; border: 1px solid rgba(99, 211, 255, .2); border-radius: 13px; background: rgba(99, 211, 255, .06); font-size: 20px; }
.panel-heading h2, .model-card h2 { margin: 0; font-size: 20px; }
.panel-heading p:last-child, .model-card > p { margin: 7px 0 0; color: #8f9aaa; line-height: 1.55; }
.security-note { display: flex; align-items: center; gap: 9px; margin-top: -2px; color: #778496; font-size: 12px; }
.secret-field { display: flex; width: 100%; gap: 10px; }
.secret-field .el-input { flex: 1; }
.model-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; align-items: start; }
.model-card { position: relative; overflow: hidden; min-height: 300px; padding: 24px; border: 1px solid #2a313d; border-radius: 18px; background: #171c24; }
.card-accent { position: absolute; inset: 0 auto 0 0; width: 3px; }
.text-card .card-accent { background: #8b7cf6; } .image-card .card-accent { background: #63d3ff; } .video-card .card-accent { background: #ffb85c; }
.card-heading { display: flex; justify-content: space-between; margin-bottom: 13px; color: #7f8b9c; }
.card-heading span { font: 700 11px/1 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .18em; }
.model-card > p { min-height: 68px; margin-bottom: 22px; font-size: 13px; }
.adapter-note { min-height: 0 !important; margin: -8px 0 17px !important; color: #778496 !important; font-size: 11px !important; }
.adapter-note code { color: #ffca82; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.field-note { min-height: 0 !important; margin: -10px 0 0 !important; color: #778496 !important; font-size: 11px !important; }
.text-card :deep(.el-input-number) { width: 100%; }
.video-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.switch-row { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding-top: 4px; }
.switch-row strong, .switch-row span { display: block; }
.switch-row strong { margin-bottom: 4px; font-size: 13px; }
.switch-row span { color: #778496; font-size: 11px; }
.action-bar { position: sticky; z-index: 3; bottom: 14px; display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-top: 22px; padding: 16px 18px; border: 1px solid rgba(99, 211, 255, .18); border-radius: 16px; background: rgba(20, 25, 32, .94); box-shadow: 0 18px 50px rgba(0, 0, 0, .32); backdrop-filter: blur(14px); }
.action-bar strong, .action-bar span { display: block; } .action-bar strong { margin-bottom: 4px; font-size: 13px; } .action-bar span { color: #7f8b9c; font-size: 12px; }
.action-buttons { display: flex; gap: 10px; }
:deep(.el-form-item__label) { color: #aeb8c5; font-size: 12px; }
:deep(.el-input__wrapper), :deep(.el-select__wrapper) { background: #10151c; box-shadow: 0 0 0 1px #313946 inset; }
:deep(.el-input__inner) { color: #edf1f7; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
:deep(.el-input.is-disabled .el-input__wrapper) { background: #151922; box-shadow: 0 0 0 1px #29303b inset; }
@media (prefers-reduced-motion: no-preference) { .pulse { animation: signal 2.2s ease-out infinite; } @keyframes signal { 0%, 30% { box-shadow: 0 0 0 0 rgba(87, 215, 139, .25); } 70%, 100% { box-shadow: 0 0 0 8px rgba(87, 215, 139, 0); } } }
@media (max-width: 980px) { .pipeline { grid-template-columns: 1fr; gap: 8px; } .pipeline-link { width: 1px; height: 16px; margin-left: 34px; } .model-grid { grid-template-columns: 1fr; } .model-card { min-height: auto; } .model-card > p { min-height: auto; } }
@media (max-width: 640px) { .model-config-page { padding: 20px; } .config-header, .action-bar { align-items: flex-start; flex-direction: column; } .effective-badge { align-self: flex-start; } .action-buttons { width: 100%; } .action-buttons .el-button { flex: 1; } .video-options { grid-template-columns: 1fr; gap: 0; } .secret-field { align-items: stretch; flex-direction: column; } }
</style>
