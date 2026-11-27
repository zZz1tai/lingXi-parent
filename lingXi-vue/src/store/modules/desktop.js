import { reactive } from 'vue'
import { createDesktopUpdater } from '@/utils/desktopUpdates'

export const isDesktop = typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__)
export const desktopUpdate = reactive({ phase: 'idle', version: '', notes: '', checkedAt: '', progress: 0, error: '' })
export const desktopPreferences = reactive({ autoCheck: localStorage.getItem('desktop-auto-update') !== 'false' })

const updater = createDesktopUpdater({
  check: async options => (await import('@tauri-apps/plugin-updater')).check(options),
  stopServices: async () => (await import('@tauri-apps/api/core')).invoke('stop_desktop_services'),
  onChange: state => Object.assign(desktopUpdate, state)
})

export const checkDesktopUpdate = () => isDesktop ? updater.checkForUpdates() : Promise.resolve(false)
export const installDesktopUpdate = () => isDesktop ? updater.installUpdate() : Promise.resolve()
export function setAutoCheck(value) {
  desktopPreferences.autoCheck = value
  localStorage.setItem('desktop-auto-update', String(value))
}
