<template>
  <div class="nk-cards">
    <div v-for="(card, index) in cards" :key="card.settingId || index" class="nk-card" @click="handleEdit(card)">
      <div class="nk-card-head">
        <span class="nk-card-tag">{{ typeLabel(card.settingType || type) }}</span>
        <span class="nk-card-title">{{ card.title || '未命名设定' }}</span>
        <el-icon class="nk-card-more" @click.stop="handleDelete(card)"><Delete /></el-icon>
      </div>
      <div class="nk-card-body">{{ card.content || '暂无内容，点击补充' }}</div>
    </div>

    <button class="nk-card nk-card-add" type="button" @click="handleAdd">
      <el-icon><Plus /></el-icon>{{ addLabel }}
    </button>
  </div>
</template>

<script setup>
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const props = defineProps({
  type: { type: String, required: true }, // character | world | outline | style
  cards: { type: Array, default: () => [] }
})

const emit = defineEmits(['add', 'edit', 'delete'])

const typeLabels = {
  character: '人物',
  world: '世界观',
  outline: '大纲',
  style: '文风'
}

const addLabels = {
  character: '新立一位人物',
  world: '补一条设定',
  outline: '记一节大纲',
  style: '存一种文风'
}

function typeLabel(type) {
  return typeLabels[type] || type
}

function handleAdd() {
  emit('add', props.type)
}

function handleEdit(card) {
  emit('edit', props.type, card)
}

function handleDelete(card) {
  ElMessageBox.confirm(`确定删除「${card.title || '这张设定卡'}」吗？`, '撕掉这张卡片', {
    confirmButtonText: '删除',
    cancelButtonText: '留着',
    type: 'warning'
  })
    .then(() => emit('delete', props.type, card))
    .catch(() => {})
}
</script>
