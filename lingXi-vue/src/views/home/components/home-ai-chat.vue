<template>
  <div class="box ai-chat">
    <div class="header">
      <div class="title">
        灵犀智能助手
        <span class="sub-title"></span>
      </div>
      <el-button type="primary" :loading="loading" @click="sendMessage">发送</el-button>
    </div>
    <el-form @submit.prevent>
      <el-form-item label="输入消息">
        <el-input
          v-model="message"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
          placeholder="请输入要发送给灵犀助手的内容"
        />
      </el-form-item>
    </el-form>
    <el-alert v-if="error" type="error" :closable="false" show-icon class="mt-10">{{ error }}</el-alert>
    <div class="history" v-loading="loading && !history.length">
      <div v-if="!history.length && !loading" class="empty">暂时没有对话，试着发送第一条消息吧。</div>
      <div v-for="(item, index) in history" :key="index" class="message" :class="item.role">
        <div class="role">{{ item.role === 'user' ? '我' : '灵犀' }}</div>
        <div class="content">{{ item.content }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { chatWithQwen } from '@/api/ai';
import useUserStore from '@/store/modules/user';

const message = ref('');
const history = ref([]);
const loading = ref(false);
const error = ref('');
const userStore = useUserStore();

const sendMessage = async () => {
  const content = message.value.trim();
  if (!content || loading.value) {
    return;
  }
  error.value = '';
  loading.value = true;
  history.value.push({ role: 'user', content });
  try {
    const reply = await chatWithQwen(content, userStore.id, userStore.name);
    history.value.push({ role: 'assistant', content: reply });
    message.value = '';
  } catch (err) {
    error.value = err?.msg || err?.message || '发送失败，请稍后重试';
    history.value.pop();
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped lang="scss">
.ai-chat {
  display: flex;
  flex-direction: column;
  .header {
    margin-bottom: 0;
  }
  .mt-10 {
    margin-top: 10px;
  }
  .history {
    flex: 1;
    margin-top: 20px;
    max-height: 360px;
    overflow-y: auto;
    border: 1px dashed #ebeef5;
    border-radius: 12px;
    padding: 16px;
    background: #fafafa;
  }
  .empty {
    text-align: center;
    color: #999;
  }
  .message {
    margin-bottom: 12px;
    padding: 12px;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
    &:last-child {
      margin-bottom: 0;
    }
    .role {
      font-weight: 600;
      margin-bottom: 6px;
    }
    &.user {
      border-left: 4px solid #409eff;
    }
    &.assistant {
      border-left: 4px solid #67c23a;
    }
  }
}
</style>

