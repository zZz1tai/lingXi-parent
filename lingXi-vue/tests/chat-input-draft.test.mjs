import test from 'node:test';
import assert from 'node:assert/strict';
import { effectScope, ref } from 'vue';
import { clearChatInputDrafts, useChatInputDraft } from '../src/utils/chatInputDraft.js';

function memoryStorage() {
  const values = new Map();
  return {
    get length() { return values.size; }, key: index => [...values.keys()][index],
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key)
  };
}

test('草稿按用户、会话、模式隔离，刷新恢复，清空后不再恢复', () => {
  const storage = memoryStorage();
  const scope = ref(['1', 'a', 'chat']);
  let text = ref('');
  let effects = effectScope();
  effects.run(() => useChatInputDraft(text, () => scope.value, () => storage));
  text.value = '长问题\n  保留空白 😀';
  scope.value = ['1', 'b', 'chat'];
  assert.equal(text.value, '');
  text.value = '另一个会话';
  scope.value = ['1', 'a', 'video'];
  assert.equal(text.value, '');
  text.value = '视频描述';
  scope.value = ['2', 'a', 'chat'];
  assert.equal(text.value, '');
  scope.value = ['1', 'a', 'chat'];
  assert.equal(text.value, '长问题\n  保留空白 😀');
  effects.stop();
  text = ref('');
  effects = effectScope();
  effects.run(() => useChatInputDraft(text, () => scope.value, () => storage));
  assert.equal(text.value, '长问题\n  保留空白 😀');
  text.value = '';
  scope.value = ['1', 'a', 'video'];
  assert.equal(text.value, '视频描述');
  scope.value = ['1', 'a', 'chat'];
  assert.equal(text.value, '');
  effects.stop();
});

test('存储失败明确提示且不打断输入，删除仅清理目标，退出保留其它存储', () => {
  const storage = memoryStorage();
  const scope = ref(['1', 'a', 'chat']);
  const text = ref('');
  const effects = effectScope();
  const status = effects.run(() => useChatInputDraft(text, () => scope.value, () => storage));
  text.value = '旧草稿';
  storage.setItem = () => { throw new Error('quota'); };
  text.value = '新草稿';
  assert.equal(text.value, '新草稿');
  assert.match(status.value, /未暂存/);
  assert.equal(storage.length, 0);
  effects.stop();
  const other = memoryStorage();
  other.setItem('other-feature', 'untouched');
  for (const id of ['a', 'b']) other.setItem('lingxi:chat-input:' + JSON.stringify(['1', id, 'chat']), '草稿');
  clearChatInputDrafts('1', 'a', () => other);
  assert.equal(other.length, 2);
  other.setItem('lingxi:chat-input:broken', '损坏的旧键');
  clearChatInputDrafts(undefined, undefined, () => other);
  assert.equal(other.length, 1);
  assert.equal(other.getItem('other-feature'), 'untouched');
  assert.doesNotThrow(() => clearChatInputDrafts(undefined, undefined, () => { throw new Error('disabled'); }));
});
