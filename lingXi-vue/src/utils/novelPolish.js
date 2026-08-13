/**
 * 精修模板：模板元数据、标记消息解析与精修结果提取的纯逻辑。
 * 与 Python 侧 lingXi-agent/app/agents/novel_prompts.py 的精修模板库保持一致：
 * 前端只展示模板 id/名称/分类/说明，完整指令由 Python 侧展开。
 */

export const POLISH_MARKER_PREFIX = '【精修】'

export const POLISH_CATEGORIES = [
  '结构与节奏',
  '文笔与风格',
  '人物与对话',
  '情节与悬念',
  '细节与氛围'
]

export const POLISH_TEMPLATES = [
  // ── 结构与节奏 ──
  {
    id: 'chapter_open_hook',
    name: '开篇钩子',
    category: '结构与节奏',
    description: '强化章节开头吸引力，第一时间抓住读者'
  },
  {
    id: 'chapter_end_hook',
    name: '章末钩子',
    category: '结构与节奏',
    description: '在章节结尾制造悬念或未竟之事'
  },
  {
    id: 'pace_accelerate',
    name: '节奏加快',
    category: '结构与节奏',
    description: '压缩闲笔与赘述，加快叙事推进'
  },
  {
    id: 'pace_slow_down',
    name: '节奏放缓',
    category: '结构与节奏',
    description: '在关键情节处放慢节奏、增加张力'
  },
  {
    id: 'transition_smooth',
    name: '过渡衔接',
    category: '结构与节奏',
    description: '消除生硬切换，让段落与场景自然衔接'
  },
  // ── 文笔与风格 ──
  {
    id: 'de_ai_flavor',
    name: '去AI味',
    category: '文笔与风格',
    description: '消除模板化、书面腔与 AI 腔表达'
  },
  {
    id: 'unify_style',
    name: '文风统一',
    category: '文笔与风格',
    description: '统一叙事视角、语气与文风'
  },
  {
    id: 'vocabulary_refine',
    name: '词汇升级',
    category: '文笔与风格',
    description: '用更精准生动的词汇替换平淡表达'
  },
  {
    id: 'sentence_power',
    name: '句子力量感',
    category: '文笔与风格',
    description: '把拖沓句子改短改有力'
  },
  {
    id: 'colloquial_dialogue',
    name: '对话口语化',
    category: '文笔与风格',
    description: '让对白更像真人说话，去除书面腔'
  },
  {
    id: 'poetic_imagery',
    name: '画面意境',
    category: '文笔与风格',
    description: '用意象与比喻提升文字质感'
  },
  // ── 人物与对话 ──
  {
    id: 'character_consistency',
    name: '人设一致性',
    category: '人物与对话',
    description: '校准人物言行与设定卡一致'
  },
  {
    id: 'inner_world',
    name: '内心戏',
    category: '人物与对话',
    description: '补充心理活动，让人物有血有肉'
  },
  {
    id: 'action_beat',
    name: '动作细节',
    category: '人物与对话',
    description: '用动作细节代替直接陈述'
  },
  // ── 情节与悬念 ──
  {
    id: 'suspense_add',
    name: '悬念加强',
    category: '情节与悬念',
    description: '埋设或强化悬念点，吊住读者'
  },
  {
    id: 'foreshadow_hint',
    name: '伏笔呼应',
    category: '情节与悬念',
    description: '让已埋伏笔自然露面，为回收铺垫'
  },
  {
    id: 'twist_impact',
    name: '反转冲击力',
    category: '情节与悬念',
    description: '让反转更意外且合理'
  },
  {
    id: 'emotion_buildup',
    name: '情绪铺垫',
    category: '情节与悬念',
    description: '为高潮情绪做足铺垫'
  },
  {
    id: 'satisfaction_point',
    name: '爽点强化',
    category: '情节与悬念',
    description: '强化打脸、翻盘等爽点效果'
  },
  // ── 细节与氛围 ──
  {
    id: 'sensory_detail',
    name: '五感细节',
    category: '细节与氛围',
    description: '用视听闻嗅触让场景活起来'
  },
  {
    id: 'atmosphere_build',
    name: '氛围营造',
    category: '细节与氛围',
    description: '用环境与细节渲染情绪氛围'
  },
  {
    id: 'continuity_check',
    name: '连续性检查',
    category: '细节与氛围',
    description: '检查时间线、地点、物件等细节前后一致'
  }
]

/**
 * 按 id 查找精修模板，找不到时返回 null。
 * @param {string} templateId
 * @returns {{id: string, name: string, category: string, description: string}|null}
 */
export function findPolishTemplate(templateId) {
  return POLISH_TEMPLATES.find(item => item.id === templateId) || null
}

/**
 * 构造发给创作智能体的标记消息：`【精修】<template_id>\n<目标文字>`。
 * @param {string} templateId
 * @param {string} target 待精修的目标文字
 * @returns {string}
 */
export function buildPolishMessage(templateId, target) {
  return `${POLISH_MARKER_PREFIX}${templateId}\n${target || ''}`
}

/**
 * 解析精修标记消息。
 * 消息形如「【精修】de_ai_flavor\n目标文字」；解析成功返回
 * { template, target }，否则返回 null 表示普通创作指令。
 * @param {string} message
 * @returns {{template: object, target: string}|null}
 */
export function parsePolishMessage(message) {
  if (typeof message !== 'string' || !message.trim().startsWith(POLISH_MARKER_PREFIX)) {
    return null
  }
  const rest = message.trim().slice(POLISH_MARKER_PREFIX.length).trimStart()
  const lineEnd = rest.indexOf('\n')
  const templateId = (lineEnd === -1 ? rest : rest.slice(0, lineEnd)).trim()
  if (!templateId) return null
  const template = findPolishTemplate(templateId)
  if (!template) return null
  const target = lineEnd === -1 ? '' : rest.slice(lineEnd + 1).trim()
  return { template, target }
}

/**
 * 从 AI 精修回复中提取「精修后正文」部分。
 * 回复结构为「精修后正文」+「修改点标注」两节；找不到分节标记时
 * 返回全文，保证降级为直接采纳。
 * @param {string} content AI 回复全文
 * @returns {string} 仅精修后正文
 */
export function extractPolishBody(content) {
  if (typeof content !== 'string') return ''
  const text = content.trim()
  const markers = [
    '## 修改点标注',
    '修改点标注：',
    '修改点标注',
    '## 修改说明'
  ]
  for (const marker of markers) {
    const index = text.indexOf(marker)
    if (index >= 0) return text.slice(0, index).trim()
  }
  return text
}

/**
 * 提取「修改点标注」节（无标注时返回空字符串）。
 * @param {string} content AI 回复全文
 * @returns {string}
 */
export function extractPolishChanges(content) {
  if (typeof content !== 'string') return ''
  const body = extractPolishBody(content)
  if (body === content.trim()) return ''
  return content.trim().slice(body.length).trim()
}

// ── 作品文风（风格实验室）──────────────────────────────────

export const STYLE_MARKER_PREFIX = '【文风】'

/**
 * 构造按文风改写消息：`【文风】<标题>\n<内容>\n\n<目标文字>`。
 * @param {{title: string, content: string}} styleCard 文风设定卡
 * @param {string} target 待改写文字
 * @returns {string}
 */
export function buildStyleMessage(styleCard, target) {
  return `${STYLE_MARKER_PREFIX}${styleCard.title}\n${styleCard.content}\n\n${target || ''}`
}

/**
 * 解析文风标记消息。
 * 消息形如「【文风】冷峻硬派\n短句留白。\n\n目标文字」；解析成功返回
 * { styleTitle, styleBody, target }，否则返回 null。
 * @param {string} message
 * @returns {{styleTitle: string, styleBody: string, target: string}|null}
 */
export function parseStyleMessage(message) {
  if (typeof message !== 'string' || !message.trim().startsWith(STYLE_MARKER_PREFIX)) {
    return null
  }
  const rest = message.trim().slice(STYLE_MARKER_PREFIX.length).trimStart()
  const lineEnd = rest.indexOf('\n')
  if (lineEnd === -1) return null
  const styleTitle = rest.slice(0, lineEnd).trim()
  if (!styleTitle) return null
  const body = rest.slice(lineEnd + 1).trim()
  if (!body) return null
  const sepIndex = body.indexOf('\n\n')
  if (sepIndex === -1) return { styleTitle, styleBody: body, target: '' }
  return {
    styleTitle,
    styleBody: body.slice(0, sepIndex).trim(),
    target: body.slice(sepIndex + 2).trim()
  }
}

/**
 * 把用户消息渲染为可读标签：文风/精修标记显示名称，其余原样。
 * @param {string} content
 * @returns {string}
 */
export function polishLabel(content) {
  const style = parseStyleMessage(content)
  if (style) return `【文风】${style.styleTitle}`
  const polish = parsePolishMessage(content)
  if (polish) return `【精修】${polish.template.name}`
  return content
}
