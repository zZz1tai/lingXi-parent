/** 旧入口只负责兼容书签，设置的唯一展示位置是设置中心。 */
export const settingsComponents = {
  'system/config/index': 'parameters',
  'system/securityConfig/index': 'services',
  'aiVedio/modelConfig/index': 'models'
}

export function settingsTarget(component) {
  const section = settingsComponents[component]
  return section ? { path: '/settings', query: { section } } : null
}

export function resolveSettingsSection(requested, available) {
  return available.includes(requested) ? requested : available[0] || 'appearance'
}
