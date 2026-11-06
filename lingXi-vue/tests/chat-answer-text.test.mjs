import test from 'node:test';
import assert from 'node:assert/strict';
import { answerDownloadText } from '../src/utils/chatAnswerText.js';

test('单条回答下载保留正文空白、代码和 Unicode，不混入内部字段', () => {
  const content = '  # 标题\n\n```js\n  const name = "灵犀😀";\n\n\n```\n';
  assert.equal(answerDownloadText({content, activities:[{secret:'内部轨迹'}], attachments:[{previewUrl:'私有地址'}]}), content);
  assert.equal(answerDownloadText({content:'<script>原文</script>'}), '<script>原文</script>');
});

test('引用附带证据快照说明，部分失败回答带警告，空正文不生成文件', () => {
  const result = answerDownloadText({content:'部分回答', status:'FAILED', citations:[{title:'手册', source_id:'guide#1', excerpt:'证据', source_uri:'不可导出地址'}]});
  for (const value of ['未正常完成', '部分回答', '手册', 'guide#1', '证据', '检索快照']) assert.ok(result.includes(value));
  assert.ok(!result.includes('不可导出地址'));
  for (const content of [undefined, '', '  \n', 123]) assert.throws(() => answerDownloadText({content}));
});
