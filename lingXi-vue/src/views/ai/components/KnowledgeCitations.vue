<template>
  <section v-if="citations.length" class="knowledge-citations" aria-label="参考资料">
    <h4>参考资料</h4>
    <p class="citation-hint">展开查看本次检索到的证据片段</p>
    <details v-for="citation in citations" :key="citation.source_id" class="citation-card">
      <summary>
        <span class="citation-title">{{ citation.title || '内部资料' }}</span>
        <span v-if="citation.section" class="citation-section">{{ citation.section }}</span>
      </summary>
      <div class="citation-body">
        <dl>
          <template v-if="citation.version">
            <dt>版本</dt><dd>{{ citation.version }}</dd>
          </template>
          <template v-if="citation.document_type">
            <dt>资料类型</dt><dd>{{ citation.document_type }}</dd>
          </template>
          <dt>来源标识</dt><dd>{{ citation.source_id }}</dd>
        </dl>
        <blockquote v-if="citation.excerpt" class="citation-excerpt">{{ citation.excerpt }}</blockquote>
        <p v-else class="citation-hint">该引用未包含原文片段。</p>
        <p v-if="citation.excerpt_truncated" class="citation-truncated">片段较长，仅保存部分内容。</p>
        <p class="citation-hint">这是回答生成时的检索快照，不代表资料当前版本。</p>
        <el-button size="small" @click="copyCitation(citation)">复制证据与来源</el-button>
        <el-button size="small" @click="downloadCitation(citation)">下载证据</el-button>
      </div>
    </details>
  </section>
</template>

<script setup>
import { ElButton, ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import { citationText } from '@/utils/citationText'

async function copyCitation(citation) {
  try {
    await navigator.clipboard.writeText(citationText(citation))
    ElMessage.success('证据与来源已复制')
  } catch {
    ElMessage.warning('剪贴板不可用，请点击“下载证据”保存文本。')
  }
}

function downloadCitation(citation) {
  saveAs(new Blob([citationText(citation)], { type: 'text/plain;charset=utf-8' }), '知识引用证据.txt')
}

defineProps({
  citations: { type: Array, default: () => [] }
})
</script>

<style scoped>
.knowledge-citations {
  margin: 12px 0 14px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.knowledge-citations h4 { margin: 0; font-size: 12px; color: var(--el-text-color-regular); }
.citation-hint { margin: 6px 0; font-size: 12px; color: var(--el-text-color-secondary); }
.citation-card {
  margin-top: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow-wrap: anywhere;
}
.citation-card summary {
  padding: 9px 12px;
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 12px;
  line-height: 1.6;
}
.citation-card summary:hover { background: var(--el-fill-color-light); }
.citation-card summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.citation-title { font-weight: 600; }
.citation-section { margin-left: 8px; color: var(--el-text-color-secondary); }
.citation-body { padding: 0 12px 10px; }
.citation-body dl { display: grid; grid-template-columns: auto 1fr; gap: 4px 12px; font-size: 12px; }
.citation-body dt { color: var(--el-text-color-secondary); }
.citation-body dd { margin: 0; }
.citation-excerpt {
  margin: 12px 0;
  padding: 10px 12px;
  border-left: 3px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 13px;
  max-height: 320px;
  overflow-y: auto;
}
.citation-truncated { color: var(--el-color-warning-dark-2); font-size: 12px; }
</style>
