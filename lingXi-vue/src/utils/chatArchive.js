import { citationText } from './citationText.js';

// 使用比正文更长的围栏，保留 Markdown/HTML 原文且不触发外部图片加载。
function textBlock(value) {
  const text = String(value || '');
  let longest = 0;
  for (const match of text.matchAll(/`+/g)) longest = Math.max(longest, match[0].length);
  const fence = '`'.repeat(Math.max(3, longest + 1));
  return `${fence}text\n${text}\n${fence}`;
}

export function createChatArchive(messages, { title = '新的对话', assistantOnly = false, exportedAt = '' } = {}) {
  const selected = messages.filter(item => (!assistantOnly || !item.isUser) && (
    (typeof item.content === 'string' && item.content.trim()) || item.attachments?.length || item.citations?.length
  ));
  const sections = ['# AI 对话归档', textBlock(title), `导出时间：${exportedAt}`, `消息数量：${selected.length}`,
    '范围：当前会话已加载的' + (assistantOnly ? '助手回答' : '用户与助手消息') + '。',
    '正文以文本代码块保留原始格式。附件仅列名称，不含文件内容或临时地址；不包含工具执行轨迹、审批操作及可视化卡片。'];
  selected.forEach((item, index) => {
    sections.push(`## ${index + 1}. ${item.isUser ? '用户' : '灵犀助手'}`);
    if (item.error || ['FAILED', 'CANCELLED', 'CANCELED'].includes(item.status)) {
      sections.push('注意：这条消息未正常完成，以下可能仅包含部分内容。');
    }
    if (item.content) sections.push(textBlock(item.content));
    if (item.attachments?.length) {
      sections.push('### 附件名称', textBlock(item.attachments.map(file => file.name || '未命名附件').join('\n')));
    }
    if (item.citations?.length) {
      sections.push('### 参考资料（回答生成时的证据快照）');
      item.citations.forEach(citation => sections.push(textBlock(citationText(citation))));
    }
  });
  const name = Array.from(String(title).replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').trim()).slice(0, 60).join('').replace(/[. ]+$/, '') || '新的对话';
  return { count: selected.length, markdown: sections.join('\n\n') + '\n', filename: `AI对话-${name}.md` };
}
