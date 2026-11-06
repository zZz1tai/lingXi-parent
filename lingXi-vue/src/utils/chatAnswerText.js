import { citationText } from './citationText.js';

// 仅导出回答正文与公开展示的引用字段，不携带工具轨迹、审批数据或附件地址。
export function answerDownloadText(message) {
  const content = typeof message.content === 'string' ? message.content : '';
  if (!content.trim()) throw new Error('这条回答没有可保存的正文');
  const parts = [];
  if (message.error || ['FAILED', 'CANCELLED', 'CANCELED'].includes(message.status)) {
    parts.push('注意：这条回答未正常完成，以下可能仅包含部分内容。');
  }
  parts.push(content);
  if (message.citations?.length) {
    parts.push('参考资料（回答生成时的证据快照）', ...message.citations.map(citationText));
  }
  return parts.join('\n\n');
}
