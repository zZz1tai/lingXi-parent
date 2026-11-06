import { ref, watch } from 'vue';

const PREFIX = 'lingxi:chat-input:';
const browserStorage = () => window.sessionStorage;

// 仅清理本功能的文字草稿，退出登录时清空，删除会话时按范围清理。
export function clearChatInputDrafts(userId, sessionId, storage = browserStorage) {
  try {
    const target = storage();
    for (let index = target.length - 1; index >= 0; index--) {
      const key = target.key(index);
      if (!key?.startsWith(PREFIX)) continue;
      if (userId === undefined && sessionId === undefined) {
        target.removeItem(key);
        continue;
      }
      const [user, session] = JSON.parse(key.slice(PREFIX.length));
      if ((userId === undefined || user === String(userId)) && (sessionId === undefined || session === String(sessionId))) target.removeItem(key);
    }
  } catch {
    // 浏览器禁用存储时，不阻断退出登录或删除会话。
  }
}

export function useChatInputDraft(message, scope, storage = browserStorage) {
  const status = ref('');
  let key = '';
  let restoring = false;
  watch(scope, ([user, session, mode]) => {
    key = user !== '' && user != null && session ? PREFIX + JSON.stringify([String(user), String(session), mode]) : '';
    restoring = true;
    try {
      message.value = key ? storage().getItem(key) || '' : '';
      status.value = message.value ? '已恢复当前模式的文字草稿' : '';
    } catch {
      message.value = '';
      status.value = '浏览器无法读取草稿，请勿依赖刷新恢复';
    } finally {
      restoring = false;
    }
  }, { immediate: true, flush: 'sync' });
  watch(message, text => {
    if (restoring || !key) return;
    try {
      const target = storage();
      if (text) target.setItem(key, text);
      else target.removeItem(key);
      status.value = text ? '文字草稿已暂存到当前标签页' : '';
    } catch {
      // 避免下次恢复成旧版本，却误以为是刚输入的内容。
      try { storage().removeItem(key); } catch { /* 存储可能完全不可用 */ }
      status.value = '草稿未暂存，切换或刷新前请复制文字';
    }
  }, { flush: 'sync' });
  return status;
}
