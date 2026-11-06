// 按消息顺序保留每条回答最近的用户问题，用原始位置区分重复内容和缺失 ID。
export function comparisonAnswers(messages) {
  let question = '';
  return messages.flatMap((message, index) => {
    if (message.isUser) {
      question = typeof message.content === 'string' && message.content.trim()
        ? message.content : '（仅附件或空白问题）';
      return [];
    }
    if (typeof message.content !== 'string' || !message.content.trim()) return [];
    const preview = Array.from(message.content.replace(/\s+/g, ' ').trim()).slice(0, 45).join('');
    return [{
      index, question: question || '（未找到对应问题）', content: message.content,
      label: `消息 ${index + 1} · ${preview}`,
      incomplete: Boolean(message.error) || ['FAILED', 'CANCELLED', 'CANCELED'].includes(message.status)
    }];
  });
}
