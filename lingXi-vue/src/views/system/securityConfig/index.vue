<template>
  <div v-loading="loading" class="security-config-page">
    <div class="config-shell">
      <header class="config-header">
        <div>
          <button class="back-link" type="button" @click="router.push('/system/config')">
            <el-icon><ArrowLeft /></el-icon>
            返回参数管理
          </button>
          <p class="eyebrow">SECURITY ROUTING</p>
          <h1>系统安全配置</h1>
          <p class="lede">管理阿里云 OSS、Redis、Token 等敏感配置项。保存后的配置立即生效。</p>
        </div>
        <div class="effective-badge">
          <span class="pulse" />
          运行时读取
        </div>
      </header>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>

        <!-- 阿里云 OSS -->
        <section class="workspace-panel">
          <div class="panel-heading">
            <div class="heading-icon oss-icon"><Upload /></div>
            <div>
              <p class="section-kicker">OBJECT STORAGE</p>
              <h2>阿里云 OSS</h2>
              <p>文件上传服务使用的对象存储配置。AccessKey 和 SecretKey 保存后只显示首尾字符。</p>
            </div>
          </div>

          <div class="form-grid">
            <el-form-item label="Access Key" prop="ossAccessKey">
              <div class="secret-field">
                <el-input
                  v-if="ossAccessKeyEditing"
                  ref="ossAccessKeyInputRef"
                  v-model="form.ossAccessKey"
                  type="password"
                  show-password
                  clearable
                  autocomplete="new-password"
                  maxlength="128"
                  size="large"
                  placeholder="粘贴新的 Access Key"
                />
                <el-input
                  v-else
                  :model-value="form.ossAccessKeyMasked"
                  readonly
                  size="large"
                  aria-label="已保存的 Access Key 掩码"
                />
                <el-button v-if="form.ossAccessKeyConfigured && !ossAccessKeyEditing" size="large" @click="startOssAccessKeyEdit">
                  更换
                </el-button>
                <el-button v-if="form.ossAccessKeyConfigured && ossAccessKeyEditing" size="large" @click="cancelOssAccessKeyEdit">
                  取消
                </el-button>
              </div>
            </el-form-item>

            <el-form-item label="Secret Key" prop="ossSecretKey">
              <div class="secret-field">
                <el-input
                  v-if="ossSecretKeyEditing"
                  ref="ossSecretKeyInputRef"
                  v-model="form.ossSecretKey"
                  type="password"
                  show-password
                  clearable
                  autocomplete="new-password"
                  maxlength="128"
                  size="large"
                  placeholder="粘贴新的 Secret Key"
                />
                <el-input
                  v-else
                  :model-value="form.ossSecretKeyMasked"
                  readonly
                  size="large"
                  aria-label="已保存的 Secret Key 掩码"
                />
                <el-button v-if="form.ossSecretKeyConfigured && !ossSecretKeyEditing" size="large" @click="startOssSecretKeyEdit">
                  更换
                </el-button>
                <el-button v-if="form.ossSecretKeyConfigured && ossSecretKeyEditing" size="large" @click="cancelOssSecretKeyEdit">
                  取消
                </el-button>
              </div>
            </el-form-item>

            <el-form-item label="Endpoint" prop="ossEndpoint">
              <el-input v-model="form.ossEndpoint" size="large" placeholder="oss-cn-beijing.aliyuncs.com" />
            </el-form-item>

            <el-form-item label="Bucket Name" prop="ossBucketName">
              <el-input v-model="form.ossBucketName" size="large" placeholder="my-bucket" />
            </el-form-item>
          </div>

          <el-form-item label="访问域名" prop="ossDomain">
            <el-input v-model="form.ossDomain" size="large" placeholder="https://my-bucket.oss-cn-beijing.aliyuncs.com/" />
          </el-form-item>

          <el-form-item label="基础路径" prop="ossBasePath">
            <el-input v-model="form.ossBasePath" size="large" placeholder="dkd-images/" />
          </el-form-item>

          <div class="security-note">
            <el-icon><Lock /></el-icon>
            Access Key 和 Secret Key 保存后只显示首尾字符，接口不会回传完整内容。
          </div>
        </section>

        <!-- Agent -->
        <section class="workspace-panel">
          <div class="panel-heading">
            <div class="heading-icon agent-icon"><Connection /></div>
            <div>
              <p class="section-kicker">AGENT</p>
              <h2>Agent 服务 API Key</h2>
              <p>Java 后端调用 Python Agent 的认证密钥。此项为可选配置。</p>
            </div>
          </div>

          <el-form-item label="API Key" prop="agentServiceApiKey">
            <div class="secret-field">
              <el-input
                v-if="agentApiKeyEditing"
                ref="agentApiKeyInputRef"
                v-model="form.agentServiceApiKey"
                type="password"
                show-password
                clearable
                autocomplete="new-password"
                maxlength="256"
                size="large"
                placeholder="粘贴新的 Agent API Key（可选）"
              />
              <el-input
                v-else
                :model-value="form.agentServiceApiKeyMasked"
                readonly
                size="large"
                aria-label="已保存的 Agent API Key 掩码"
              />
              <el-button v-if="form.agentServiceApiKeyConfigured && !agentApiKeyEditing" size="large" @click="startAgentApiKeyEdit">
                更换
              </el-button>
              <el-button v-if="form.agentServiceApiKeyConfigured && agentApiKeyEditing" size="large" @click="cancelAgentApiKeyEdit">
                取消
              </el-button>
            </div>
          </el-form-item>
        </section>

        <!-- 联网搜索 -->
        <section class="workspace-panel">
          <div class="panel-heading">
            <div class="heading-icon search-icon"><Search /></div>
            <div>
              <p class="section-kicker">WEB SEARCH</p>
              <h2>联网搜索 API Key</h2>
              <p>Agent 联网搜索工具使用的 Tavily API Key。每次对话请求时由后端注入，未配置时联网搜索不可用。</p>
            </div>
          </div>

          <el-form-item label="Tavily API Key" prop="searchTavilyApiKey">
            <div class="secret-field">
              <el-input
                v-if="tavilyApiKeyEditing"
                ref="tavilyApiKeyInputRef"
                v-model="form.searchTavilyApiKey"
                type="password"
                show-password
                clearable
                autocomplete="new-password"
                maxlength="256"
                size="large"
                placeholder="粘贴新的 Tavily API Key"
              />
              <el-input
                v-else
                :model-value="form.searchTavilyApiKeyMasked"
                readonly
                size="large"
                aria-label="已保存的 Tavily API Key 掩码"
              />
              <el-button v-if="form.searchTavilyApiKeyConfigured && !tavilyApiKeyEditing" size="large" @click="startTavilyApiKeyEdit">
                更换
              </el-button>
              <el-button v-if="form.searchTavilyApiKeyConfigured && tavilyApiKeyEditing" size="large" @click="cancelTavilyApiKeyEdit">
                取消
              </el-button>
            </div>
          </el-form-item>
        </section>
      </el-form>

      <footer class="action-bar">
        <div>
          <strong>立即生效</strong>
          <span>保存后配置立即生效，无需重启服务。</span>
        </div>
        <div class="action-buttons">
          <el-button :icon="Refresh" :disabled="saving" @click="loadConfig">重新加载</el-button>
          <el-button type="primary" :icon="Check" :loading="saving" @click="saveConfig">保存安全配置</el-button>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup name="SystemSecurityConfig">
import { ArrowLeft, Check, Connection, Lock, Picture, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { getSystemSecurityConfig, updateSystemSecurityConfig } from '@/api/system/securityConfig'

const { proxy } = getCurrentInstance()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const saving = ref(false)

// Secret field editing states
const ossAccessKeyEditing = ref(true)
const ossAccessKeyInputRef = ref()
const ossSecretKeyEditing = ref(true)
const ossSecretKeyInputRef = ref()
const agentApiKeyEditing = ref(true)
const agentApiKeyInputRef = ref()
const tavilyApiKeyEditing = ref(true)
const tavilyApiKeyInputRef = ref()

const form = reactive({
  ossAccessKey: '',
  ossAccessKeyMasked: '',
  ossAccessKeyConfigured: false,
  ossSecretKey: '',
  ossSecretKeyMasked: '',
  ossSecretKeyConfigured: false,
  ossEndpoint: 'oss-cn-beijing.aliyuncs.com',
  ossBucketName: '',
  ossDomain: '',
  ossBasePath: 'dkd-images/',
  agentServiceApiKey: '',
  agentServiceApiKeyMasked: '',
  agentServiceApiKeyConfigured: false,
  searchTavilyApiKey: '',
  searchTavilyApiKeyMasked: '',
  searchTavilyApiKeyConfigured: false
})

function validateSecret(_rule, value, callback) {
  callback()
}

const rules = {
  ossAccessKey: [{ validator: validateSecret, trigger: ['blur', 'change'] }],
  ossSecretKey: [{ validator: validateSecret, trigger: ['blur', 'change'] }],
  ossEndpoint: [{ required: true, message: '请输入 OSS Endpoint', trigger: 'blur' }],
  ossBucketName: [
    { required: true, message: '请输入 Bucket Name', trigger: 'blur' },
    { min: 3, max: 63, message: 'Bucket Name 长度应在 3 到 63 个字符之间', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9\-]*[a-z0-9]$/, message: 'Bucket Name 仅支持小写字母、数字和短横线', trigger: 'blur' }
  ],
  ossDomain: [],
  ossBasePath: [],
  agentServiceApiKey: [{ validator: validateSecret, trigger: ['blur', 'change'] }],
  searchTavilyApiKey: [{ validator: validateSecret, trigger: ['blur', 'change'] }]
}

function applyConfig(data = {}) {
  Object.assign(form, {
    ossAccessKey: '',
    ossAccessKeyMasked: data.ossAccessKeyMasked || '',
    ossAccessKeyConfigured: Boolean(data.ossAccessKeyConfigured),
    ossSecretKey: '',
    ossSecretKeyMasked: data.ossSecretKeyMasked || '',
    ossSecretKeyConfigured: Boolean(data.ossSecretKeyConfigured),
    ossEndpoint: data.ossEndpoint || 'oss-cn-beijing.aliyuncs.com',
    ossBucketName: data.ossBucketName || '',
    ossDomain: data.ossDomain || '',
    ossBasePath: data.ossBasePath || 'dkd-images/',
    agentServiceApiKey: '',
    agentServiceApiKeyMasked: data.agentServiceApiKeyMasked || '',
    agentServiceApiKeyConfigured: Boolean(data.agentServiceApiKeyConfigured),
    searchTavilyApiKey: '',
    searchTavilyApiKeyMasked: data.searchTavilyApiKeyMasked || '',
    searchTavilyApiKeyConfigured: Boolean(data.searchTavilyApiKeyConfigured)
  })
  ossAccessKeyEditing.value = !form.ossAccessKeyConfigured
  ossSecretKeyEditing.value = !form.ossSecretKeyConfigured
  agentApiKeyEditing.value = !form.agentServiceApiKeyConfigured
  tavilyApiKeyEditing.value = !form.searchTavilyApiKeyConfigured
}

// OSS AccessKey edit handlers
function startOssAccessKeyEdit() {
  form.ossAccessKey = ''
  ossAccessKeyEditing.value = true
  nextTick(() => ossAccessKeyInputRef.value?.focus())
}
function cancelOssAccessKeyEdit() {
  form.ossAccessKey = ''
  ossAccessKeyEditing.value = false
}

// OSS SecretKey edit handlers
function startOssSecretKeyEdit() {
  form.ossSecretKey = ''
  ossSecretKeyEditing.value = true
  nextTick(() => ossSecretKeyInputRef.value?.focus())
}
function cancelOssSecretKeyEdit() {
  form.ossSecretKey = ''
  ossSecretKeyEditing.value = false
}

// Agent API Key edit handlers
function startAgentApiKeyEdit() {
  form.agentServiceApiKey = ''
  agentApiKeyEditing.value = true
  nextTick(() => agentApiKeyInputRef.value?.focus())
}
function cancelAgentApiKeyEdit() {
  form.agentServiceApiKey = ''
  agentApiKeyEditing.value = false
}

// Tavily API Key edit handlers
function startTavilyApiKeyEdit() {
  form.searchTavilyApiKey = ''
  tavilyApiKeyEditing.value = true
  nextTick(() => tavilyApiKeyInputRef.value?.focus())
}
function cancelTavilyApiKeyEdit() {
  form.searchTavilyApiKey = ''
  tavilyApiKeyEditing.value = false
}

async function loadConfig() {
  loading.value = true
  try {
    const response = await getSystemSecurityConfig()
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
      ossEndpoint: form.ossEndpoint,
      ossBucketName: form.ossBucketName,
      ossDomain: form.ossDomain,
      ossBasePath: form.ossBasePath
    }
    // Only include secrets that are being edited
    if (ossAccessKeyEditing.value) payload.ossAccessKey = form.ossAccessKey.trim()
    if (ossSecretKeyEditing.value) payload.ossSecretKey = form.ossSecretKey.trim()
    if (agentApiKeyEditing.value) payload.agentServiceApiKey = form.agentServiceApiKey.trim()
    if (tavilyApiKeyEditing.value) payload.searchTavilyApiKey = form.searchTavilyApiKey.trim()
    const response = await updateSystemSecurityConfig(payload)
    applyConfig(response.data)
    proxy.$modal.msgSuccess('安全配置已保存')
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>

<style scoped>
.security-config-page {
  min-height: calc(100vh - 84px);
  padding: 30px;
  color: #edf1f7;
  background:
    radial-gradient(circle at 14% 4%, rgba(255, 107, 107, 0.09), transparent 24%),
    #101318;
}
.config-shell { max-width: 960px; margin: 0 auto; }
.config-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 28px; margin-bottom: 28px; }
.back-link { display: inline-flex; align-items: center; gap: 7px; margin: 0 0 24px; padding: 0; color: #98a3b3; border: 0; background: transparent; cursor: pointer; }
.back-link:hover, .back-link:focus-visible { color: #63d3ff; }
.back-link:focus-visible { outline: 2px solid #63d3ff; outline-offset: 5px; }
.eyebrow, .section-kicker { margin: 0 0 8px; color: #ff6b6b; font: 700 11px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .2em; }
h1 { margin: 0; font-size: clamp(34px, 5vw, 58px); line-height: .98; letter-spacing: -.045em; }
.lede { max-width: 720px; margin: 16px 0 0; color: #9da8b7; font-size: 16px; line-height: 1.7; }
.effective-badge { display: inline-flex; align-items: center; gap: 10px; padding: 10px 14px; color: #b9f3cf; border: 1px solid rgba(87, 215, 139, .24); border-radius: 999px; background: rgba(87, 215, 139, .08); font-size: 13px; white-space: nowrap; }
.pulse { width: 7px; height: 7px; border-radius: 50%; background: #57d78b; box-shadow: 0 0 0 5px rgba(87, 215, 139, .1); }
.workspace-panel { margin-bottom: 22px; padding: 25px; border: 1px solid #2a313d; border-radius: 18px; background: #171c24; }
.panel-heading { display: flex; gap: 16px; margin-bottom: 22px; }
.heading-icon { display: grid; flex: 0 0 46px; height: 46px; place-items: center; border: 1px solid rgba(99, 211, 255, .2); border-radius: 13px; background: rgba(99, 211, 255, .06); font-size: 20px; }
.oss-icon { color: #ffb85c; border-color: rgba(255, 184, 92, .2); background: rgba(255, 184, 92, .06); }
.agent-icon { color: #63d3ff; border-color: rgba(99, 211, 255, .2); background: rgba(99, 211, 255, .06); }
.search-icon { color: #7dd3a8; border-color: rgba(125, 211, 168, .2); background: rgba(125, 211, 168, .06); }
.panel-heading h2 { margin: 0; font-size: 20px; }
.panel-heading p:last-child { margin: 7px 0 0; color: #8f9aaa; line-height: 1.55; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 20px; }
.security-note { display: flex; align-items: center; gap: 9px; margin-top: -2px; color: #778496; font-size: 12px; }
.secret-field { display: flex; width: 100%; gap: 10px; }
.secret-field .el-input { flex: 1; }
.action-bar { position: sticky; z-index: 3; bottom: 14px; display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-top: 22px; padding: 16px 18px; border: 1px solid rgba(255, 107, 107, .18); border-radius: 16px; background: rgba(20, 25, 32, .94); box-shadow: 0 18px 50px rgba(0, 0, 0, .32); backdrop-filter: blur(14px); }
.action-bar strong, .action-bar span { display: block; } .action-bar strong { margin-bottom: 4px; font-size: 13px; } .action-bar span { color: #7f8b9c; font-size: 12px; }
.action-buttons { display: flex; gap: 10px; }
:deep(.el-form-item__label) { color: #aeb8c5; font-size: 12px; }
:deep(.el-input__wrapper), :deep(.el-select__wrapper) { background: #10151c; box-shadow: 0 0 0 1px #313946 inset; }
:deep(.el-input__inner) { color: #edf1f7; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
@media (prefers-reduced-motion: no-preference) { .pulse { animation: signal 2.2s ease-out infinite; } @keyframes signal { 0%, 30% { box-shadow: 0 0 0 0 rgba(87, 215, 139, .25); } 70%, 100% { box-shadow: 0 0 0 8px rgba(87, 215, 139, 0); } } }
@media (max-width: 640px) { .security-config-page { padding: 20px; } .config-header, .action-bar { align-items: flex-start; flex-direction: column; } .effective-badge { align-self: flex-start; } .action-buttons { width: 100%; } .action-buttons .el-button { flex: 1; } .form-grid { grid-template-columns: 1fr; } .secret-field { align-items: stretch; flex-direction: column; } }
</style>
