<template>
  <el-button :disabled="disabled || answers.length < 2" title="至少需要两条文字回答" @click="open">回答对比</el-button>
  <el-dialog v-model="visible" title="AI 回答对比" width="min(1100px, 96vw)" append-to-body destroy-on-close>
    <p>比较当前会话已加载的两条回答。按原文显示 Markdown，不包含附件、引用证据和可视化卡片。</p>
    <p role="status">{{ left?.content === right?.content ? '两条回答的文字完全相同。' : '已选择两条不同的回答，可分别滚动阅读。' }}</p>
    <div class="compare-columns">
      <section v-for="(answer, side) in [left, right]" :key="side" :aria-label="side === 0 ? '回答 A' : '回答 B'">
        <label :for="`compare-answer-${side}`">{{ side === 0 ? '回答 A' : '回答 B' }}</label>
        <select :id="`compare-answer-${side}`" :value="side === 0 ? leftIndex : rightIndex" @change="selectAnswer(side, $event)">
          <option v-for="option in answers" :key="option.index" :value="option.index" :disabled="option.index === (side === 0 ? rightIndex : leftIndex)">{{ option.label }}</option>
        </select>
        <div v-if="answer" class="compare-scroll" tabindex="0" :aria-label="side === 0 ? '回答 A 原文' : '回答 B 原文'">
          <details :key="answer.index">
            <summary>对应问题（展开查看）</summary>
            <p class="compare-question">{{ answer.question }}</p>
          </details>
          <h3>助手回答</h3>
          <p v-if="answer.incomplete" class="compare-warning">这条回答未正常完成，可能只包含部分内容。</p>
          <pre>{{ answer.content }}</pre>
        </div>
      </section>
    </div>
    <template #footer>
      <el-button @click="swap">交换左右</el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElButton, ElDialog } from 'element-plus';
import { comparisonAnswers } from '@/utils/chatCompare';

const props = defineProps({ messages: { type: Array, default: () => [] }, disabled: Boolean });
const answers = computed(() => comparisonAnswers(props.messages));
const visible = ref(false);
const leftIndex = ref(null);
const rightIndex = ref(null);
const left = computed(() => answers.value.find(item => item.index === leftIndex.value));
const right = computed(() => answers.value.find(item => item.index === rightIndex.value));
watch(() => props.disabled, value => { if (value) visible.value = false; });
function open() {
  if (props.disabled || answers.value.length < 2) return;
  leftIndex.value = answers.value.at(-2).index;
  rightIndex.value = answers.value.at(-1).index;
  visible.value = true;
}
function selectAnswer(side, event) {
  const index = Number(event.target.value);
  if (!answers.value.some(item => item.index === index) || index === (side === 0 ? rightIndex.value : leftIndex.value)) return;
  if (side === 0) leftIndex.value = index;
  else rightIndex.value = index;
}
function swap() {
  [leftIndex.value, rightIndex.value] = [rightIndex.value, leftIndex.value];
}
</script>

<style scoped>
.compare-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
label { display: block; font-weight: 600; margin-bottom: 8px; }
select { width: 100%; min-width: 0; padding: 8px; color: var(--el-text-color-primary); background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: 4px; }
.compare-scroll { margin-top: 12px; padding: 12px; height: 48vh; overflow: auto; background: var(--el-fill-color-light); border-radius: 8px; }
.compare-question, pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; line-height: 1.7; }
h3 { margin: 0 0 8px; font-size: 14px; }
details { margin-bottom: 16px; }
summary { cursor: pointer; color: var(--el-color-primary); }
.compare-warning { color: var(--el-color-warning-dark-2); }
@media (max-width: 640px) { .compare-columns { grid-template-columns: 1fr; } .compare-scroll { height: 32vh; } }
</style>
