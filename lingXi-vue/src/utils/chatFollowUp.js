export const QUOTE_LIMIT = 6000;
export const QUESTION_LIMIT = 2000;

// 组装用户确认过的引用和问题；只追加草稿，不触发模型调用。
export function appendFollowUp(draft, quote, question) {
  const source = String(quote || '').trim();
  const request = String(question || '').trim();
  if (!source || !request) throw new Error('请填写引用原文和追问问题');
  if (source.length > QUOTE_LIMIT || request.length > QUESTION_LIMIT) {
    throw new Error('引用或问题超出长度限制');
  }
  const text = `针对以下助手回答片段继续讨论：\n\n${source.split(/\r?\n/).map(line => `> ${line}`).join('\n')}\n\n我的追问：${request}`;
  return draft ? `${draft}\n\n${text}` : text;
}
