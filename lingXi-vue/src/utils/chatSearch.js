/** 按字面关键词搜索当前会话正文，每条消息返回首个命中及上下文。 */
export function searchChatMessages(messages, query) {
  const keyword = String(query || '').trim();
  if (!keyword) return [];
  const pattern = new RegExp(keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'iu');
  return messages.flatMap((message, index) => {
    if (typeof message.content !== 'string') return [];
    const match = pattern.exec(message.content);
    if (!match) return [];
    const before = Array.from(message.content.slice(0, match.index));
    const after = Array.from(message.content.slice(match.index + match[0].length));
    return [{
      index,
      role: message.isUser ? '你' : '灵犀助手',
      before: (before.length > 60 ? '…' : '') + before.slice(-60).join(''),
      match: match[0],
      after: after.slice(0, 60).join('') + (after.length > 60 ? '…' : '')
    }];
  });
}
