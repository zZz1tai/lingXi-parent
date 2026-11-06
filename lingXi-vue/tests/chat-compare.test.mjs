import test from 'node:test';
import assert from 'node:assert/strict';
import { comparisonAnswers } from '../src/utils/chatCompare.js';

test('对比保留完整原文和对应问题，跳过空回答并区分重复回答', () => {
  const source = [
    {isUser: true, content: '问题一'}, {content: '回答\n 一'}, {content: '  '},
    {isUser: true, content: '问题二'}, {content: '回答\n 一', status: 'FAILED'},
    {content: '补充', error: '中断'}
  ];
  const snapshot = JSON.stringify(source);
  const result = comparisonAnswers(source);
  assert.deepEqual(result.map(item => [item.index, item.question, item.incomplete]), [[1,'问题一',false],[4,'问题二',true],[5,'问题二',true]]);
  assert.equal(result[0].content, '回答\n 一');
  assert.notEqual(result[0].label, result[1].label);
  assert.equal(JSON.stringify(source), snapshot);
});

test('空会话、缺失问题、附件问题和超长 Unicode 标题', () => {
  assert.deepEqual(comparisonAnswers([]), []);
  const result = comparisonAnswers([{content:'😀'.repeat(100)}, {isUser:true, attachments:[{name:'图.png'}]}, {content:'<script>文本</script>'}]);
  assert.equal(result[0].question, '（未找到对应问题）');
  assert.equal(result[0].label, '消息 1 · ' + '😀'.repeat(45));
  assert.equal(result[1].question, '（仅附件或空白问题）');
  assert.equal(result[1].content, '<script>文本</script>');
});
