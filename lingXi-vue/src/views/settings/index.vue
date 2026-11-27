<template>
  <main class="settings-center">
    <header class="settings-header"><div><p class="settings-product">灵犀 · 工作空间</p><h1>设置</h1><p>让应用按你的习惯工作。</p></div><span class="environment-label">{{ desktop ? '桌面版 · 本机运行' : '浏览器版' }}</span></header>
    <div class="settings-workspace">
      <nav class="settings-nav" aria-label="设置分类">
        <button v-for="item in sections" :key="item.id" type="button" :class="{ active: section === item.id }"
          :aria-current="section === item.id ? 'page' : undefined" @click="navigate(item.id)">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
          <span v-if="item.id === 'updates' && update.phase === 'available'" class="update-dot" aria-label="有新版本" />
        </button>
      </nav>
      <article class="settings-content">
        <header class="section-heading"><h2>{{ selected.label }}</h2><p>{{ selected.description }}</p></header>
        <AppearanceSettings v-if="section === 'appearance'" />
        <StorageSettings v-else-if="section === 'storage'" />
        <UpdateSettings v-else-if="section === 'updates'" />
        <ModelSettings v-else-if="section === 'models'" embedded />
        <ServiceSettings v-else-if="section === 'services'" embedded />
        <ParameterSettings v-else-if="section === 'parameters'" embedded />
        <AccountSettings v-else-if="section === 'account'" />
        <AssistantSettings v-else-if="section === 'assistant'" />
      </article>
    </div>
  </main>
</template>

<script setup name="SettingsCenter">
import { Brush, FolderOpened, Connection, Key, Operation, Refresh, User } from '@element-plus/icons-vue'
import { defineAsyncComponent } from 'vue'
import auth from '@/plugins/auth'
import { isDesktop as desktop, desktopUpdate as update } from '@/store/modules/desktop'
import { resolveSettingsSection } from '@/utils/settingsNavigation'
import AppearanceSettings from './AppearanceSettings.vue'
const StorageSettings = defineAsyncComponent(() => import('./StorageSettings.vue'))
const UpdateSettings = defineAsyncComponent(() => import('./UpdateSettings.vue'))
const ModelSettings = defineAsyncComponent(() => import('@/views/aiVedio/modelConfig/index.vue'))
const ServiceSettings = defineAsyncComponent(() => import('@/views/system/securityConfig/index.vue'))
const ParameterSettings = defineAsyncComponent(() => import('@/views/system/config/index.vue'))
const AccountSettings = defineAsyncComponent(() => import('@/views/system/user/profile/index.vue'))
const AssistantSettings = defineAsyncComponent(() => import('./AssistantSettings.vue'))
const router = useRouter()
const route = useRoute()
const sections = computed(() => [
  { id: 'appearance', label: '外观与布局', description: '调整导航、界面密度和显示习惯。', icon: Brush },
  { id: 'assistant', label: '助手偏好', description: '设置跨对话生效的回答篇幅、结构和数字格式。', icon: Connection },
  { id: 'storage', label: '本地存储', description: '管理这台电脑上的文件保存位置。', icon: FolderOpened, show: desktop && auth.hasPermi('system:config:edit') },
  { id: 'models', label: '模型配置', description: '配置文本、图片和视频创作所使用的模型。', icon: Connection, show: auth.hasPermi('aivideo:project:edit') },
  { id: 'services', label: desktop ? '联网搜索' : '服务与密钥', description: desktop ? '配置联网搜索所需的服务密钥。未配置时仍可使用其他功能。' : '管理文件存储、Agent 与联网搜索服务。', icon: Key, show: auth.hasPermi('system:config:edit') },
  { id: 'parameters', label: '系统参数', description: '管理业务运行使用的参数，仅对有权限的账户开放。', icon: Operation, show: auth.hasPermi('system:config:list') },
  { id: 'account', label: '账户与密码', description: '维护个人资料、头像和登录密码。', icon: User },
  { id: 'updates', label: '版本更新', description: '查看当前版本、检查更新和管理自动检查。', icon: Refresh, show: desktop }
].filter(item => item.show !== false))
const section = computed(() => resolveSettingsSection(route.query.section, sections.value.map(item => item.id)))
const selected = computed(() => sections.value.find(item => item.id === section.value))
function navigate(value) { router.replace({ path: '/settings', query: { section: value } }) }
</script>

<style lang="scss">
.settings-center {
  min-height: calc(100vh - 6rem); padding: 2.25rem clamp(1.25rem, 4vw, 4rem) 4rem; background: var(--lx-canvas); color: var(--lx-text);
  .settings-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; max-width: 76rem; margin: 0 auto 2rem; }
  .settings-product { color: var(--lx-primary); font-size: .8rem; font-weight: 650; margin: 0 0 .5rem; }
  h1 { font-size: 2rem; letter-spacing: -.04em; margin: 0; font-weight: 650; }
  .settings-header > div > p:last-child { margin: .75rem 0 0; color: var(--lx-muted); }
  .environment-label { color: var(--lx-muted); font-size: .8rem; white-space: nowrap; }
  .settings-workspace { display: grid; grid-template-columns: 11rem minmax(0, 1fr); gap: 2rem; max-width: 76rem; margin: auto; align-items: start; }
  .settings-nav { display: flex; flex-direction: column; gap: .35rem; position: sticky; top: 7rem; }
  .settings-nav button { display: flex; align-items: center; gap: .75rem; text-align: left; padding: .85rem 1rem; border: 0; border-radius: .5rem; background: transparent; color: var(--lx-muted); cursor: pointer; font: inherit; font-size: .9rem; }
  .settings-nav button:hover { background: var(--lx-border-soft); color: var(--lx-text); }
  .settings-nav button.active { color: var(--lx-primary); background: var(--lx-primary-soft); font-weight: 650; }
  button:focus-visible { outline: 2px solid var(--lx-primary); outline-offset: 3px; }
  .settings-nav .el-icon { font-size: 1.1rem; }.update-dot { width: .4rem; height: .4rem; border-radius: 50%; background: var(--lx-primary); margin-left: auto; }
  .settings-content { min-width: 0; padding: clamp(1.25rem, 3vw, 2.5rem); border-radius: 1rem; background: var(--lx-surface); min-height: 35rem; }
  .section-heading { border-bottom: 1px solid var(--lx-border-soft); padding-bottom: 1.5rem; margin-bottom: 1rem; }
  .section-heading h2 { font-size: 1.35rem; font-weight: 650; margin: 0; }.section-heading p { margin: .6rem 0 0; color: var(--lx-muted); font-size: .85rem; line-height: 1.7; }
  .settings-section h3 { font-size: .95rem; margin: 0 0 .5rem; font-weight: 650; }
  .settings-row { display: flex; align-items: center; justify-content: space-between; gap: 2rem; padding: 1.4rem 0; border-bottom: 1px solid var(--lx-border-soft); }
  .settings-row p, .settings-hint { margin: 0; color: var(--lx-muted); font-size: .8rem; line-height: 1.7; }.settings-hint { margin: -1rem 0 1.5rem; }
  .settings-actions { display: flex; align-items: center; flex-wrap: wrap; gap: .75rem; padding-top: 1.5rem; }.settings-actions .el-button + .el-button { margin-left: 0; }
  .path-value { font: .8rem/1.8 Consolas, "Microsoft YaHei UI", monospace; overflow-wrap: anywhere; color: var(--lx-muted); }
  .settings-content > .app-container { padding: 0; }
  .settings-content .is-embedded { min-height: 0; padding: 0; color: var(--lx-text); background: transparent; }
  .settings-content .is-embedded .workspace-panel, .is-embedded .model-card { padding: 1.5rem 0; border: 0; border-bottom: 1px solid var(--lx-border-soft); border-radius: 0; background: transparent; min-height: 0; }
  .settings-content .is-embedded .model-grid { grid-template-columns: 1fr; gap: 0; }
  .settings-content .is-embedded .model-card > p { min-height: 0; }
  .settings-content .is-embedded .panel-heading h2, .is-embedded .model-card h2 { font-size: 1rem; }
  .settings-content .is-embedded .heading-icon, .is-embedded .card-accent, .is-embedded .section-kicker, .is-embedded .card-heading { display: none; }
  .settings-content .is-embedded .panel-heading p:last-child, .is-embedded .model-card > p, .is-embedded .action-bar span { color: var(--lx-muted); }
  .settings-content .is-embedded .action-bar { position: static; padding: 1.5rem 0 0; border: 0; border-radius: 0; box-shadow: none; background: transparent; backdrop-filter: none; }
  .settings-content .is-embedded .el-form-item__label { color: var(--lx-text); }
  .settings-content .is-embedded .el-input__wrapper, .is-embedded .el-select__wrapper { background: var(--el-fill-color-blank); box-shadow: 0 0 0 1px var(--el-border-color) inset; }
  .settings-content .is-embedded .el-input__inner { color: var(--lx-text); }
  @media(max-width: 800px) {
    .settings-workspace { grid-template-columns: minmax(0, 1fr); gap: 1rem; }.settings-nav { position: static; flex-direction: row; overflow-x: auto; padding: .25rem; }.settings-nav button { white-space: nowrap; }.environment-label { display: none; }.settings-row { gap: 1rem; }
  }
}
</style>
