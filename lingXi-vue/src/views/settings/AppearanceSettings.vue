<template>
  <section class="settings-section">
    <div class="settings-row">
      <div><h3>侧栏风格</h3><p>选择适合工作环境的导航颜色。</p></div>
      <el-radio-group v-model="settings.sideTheme" aria-label="侧栏风格">
        <el-radio-button value="theme-dark">深色</el-radio-button>
        <el-radio-button value="theme-light">浅色</el-radio-button>
      </el-radio-group>
    </div>
    <div class="settings-row">
      <div><h3>主题颜色</h3><p>用于按钮、选中项和交互提示。</p></div>
      <el-color-picker v-model="settings.theme" :predefine="colors" @change="handleThemeStyle" aria-label="主题颜色" />
    </div>
    <div class="settings-row">
      <div><h3>界面密度</h3><p>调整表格、表单和按钮的大小。</p></div>
      <el-select :model-value="app.size" class="density-select" aria-label="界面密度" @change="app.setSize">
        <el-option label="宽松" value="large" /><el-option label="标准" value="default" /><el-option label="紧凑" value="small" />
      </el-select>
    </div>
    <div v-for="item in toggles" :key="item.key" class="settings-row">
      <div><h3>{{ item.label }}</h3><p>{{ item.description }}</p></div>
      <el-switch v-model="settings[item.key]" :aria-label="item.label" @change="item.key === 'topNav' && updateNavigation()" />
    </div>
    <footer class="settings-actions">
      <el-button type="primary" @click="save">保存外观设置</el-button>
      <el-button @click="reset">恢复默认</el-button>
    </footer>
  </section>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import useSettingsStore from '@/store/modules/settings'
import useAppStore from '@/store/modules/app'
import usePermissionStore from '@/store/modules/permission'
import defaults from '@/settings'
import { handleThemeStyle } from '@/utils/theme'
import { useDynamicTitle } from '@/utils/dynamicTitle'

const settings = useSettingsStore()
const app = useAppStore()
const permissions = usePermissionStore()
const colors = ['#0f766e', '#409EFF', '#5865a8', '#b7791f', '#c2414f']
const toggles = [
  { key: 'topNav', label: '顶部导航', description: '将一级菜单放在窗口顶部。' },
  { key: 'tagsView', label: '多页签', description: '保留打开的页面，方便来回切换。' },
  { key: 'fixedHeader', label: '固定导航栏', description: '滚动页面时保留顶部导航。' },
  { key: 'sidebarLogo', label: '侧栏标识', description: '在导航侧栏中显示应用名称。' },
  { key: 'dynamicTitle', label: '跟随页面标题', description: '窗口标题显示当前页面名称。' }
]
function updateNavigation() {
  if (!settings.topNav) {
    app.toggleSideBarHide(false)
    permissions.setSidebarRouters(permissions.defaultRoutes)
  }
}
function save() {
  const keys = ['theme', 'sideTheme', ...toggles.map(item => item.key)]
  localStorage.setItem('layout-setting', JSON.stringify(Object.fromEntries(keys.map(key => [key, settings[key]]))))
  useDynamicTitle()
  ElMessage.success('外观设置已保存')
}
function reset() {
  for (const key of ['sideTheme', ...toggles.map(item => item.key)]) settings[key] = defaults[key]
  settings.theme = '#0f766e'
  app.setSize('default')
  handleThemeStyle(settings.theme)
  updateNavigation()
  save()
}
</script>

<style scoped>.density-select { width: 8rem; }</style>
