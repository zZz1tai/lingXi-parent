/** 只导出已展示的证据字段，原文空白保持不变。 */
export function citationText(citation) {
  return [citation.title || '内部资料',
    citation.section ? `章节：${citation.section}` : '',
    citation.version ? `版本：${citation.version}` : '',
    `来源：${citation.source_id || '未提供'}`, '', citation.excerpt || '该引用未包含原文片段。', '',
    citation.excerpt_truncated ? '注意：仅包含截断后的部分原文。' : '',
    '这是回答生成时的检索快照，不代表资料当前版本。'
  ].filter(value => value !== '').join('\n')
}
