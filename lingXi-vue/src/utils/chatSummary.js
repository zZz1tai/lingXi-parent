// 纪要字段作为纯文本导出，避免模型内容变成 HTML、链接或额外的 Markdown 结构。
function plainText(value) {
  return String(value || '')
    .replace(/[\r\n\t]+/g, ' ')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/([\\`*_{}\[\]()#+.!|~-])/g, '\\$1')
    .trim()
}

export function chatSummaryToMarkdown(summary) {
  const lines = [
    `# ${plainText(summary.title) || '对话纪要'}`,
    '',
    `> 基于 ${Number(summary.message_count) || 0} 条消息${summary.truncated ? '（仅最近部分内容，已截断）' : ''}。AI 整理结果请核对；待办尚未执行。`,
    '',
    '## 摘要',
    '',
    plainText(summary.summary),
    '',
    '## 关键结论',
    '',
    ...(summary.decisions.length ? summary.decisions.map(item => `- ${plainText(item)}`) : ['暂无明确结论。']),
    '',
    '## 待办事项',
    '',
    ...(summary.actions.length ? summary.actions.map(item => (
      `- [ ] ${plainText(item.task)}（负责人：${plainText(item.owner) || '未明确'}；期限：${plainText(item.due_date) || '未明确'}）`
    )) : ['暂无明确待办。']),
    '',
    '## 待确认事项',
    '',
    ...(summary.open_questions.length ? summary.open_questions.map(item => `- ${plainText(item)}`) : ['暂无待确认事项。']),
    ''
  ]
  return lines.join('\n')
}
