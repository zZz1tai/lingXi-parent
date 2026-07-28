<template>
  <div class="ai-chat-panel" :class="{ collapsed: collapsed }">
    <!-- 头部 -->
    <div class="panel-header">
      <div class="header-left">
        <div class="header-icon">
          <img src="/favicon.ico" alt="灵犀助手" />
        </div>
        <div class="header-info">
          <span class="header-title">灵犀智能助手</span>
          <span class="header-sub">设备诊断 · 数据分析 · 工单处理</span>
        </div>
      </div>
      <div class="header-actions">
        <el-tooltip content="新对话" placement="bottom">
          <el-button text circle size="small" @click="resetChat">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="收起" placement="bottom">
          <el-button text circle size="small" @click="collapsed = true">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 消息区 -->
    <div class="panel-messages" ref="messagesRef">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="chat-msg"
        :class="msg.role"
      >
        <div class="msg-avatar">
          <img v-if="msg.role === 'assistant'" src="/favicon.ico" alt="AI" />
          <span v-else class="avatar-text">{{ userInitial }}</span>
        </div>
        <div class="msg-bubble" v-html="msg.role === 'assistant' ? renderMd(msg.content) : msg.content"></div>
      </div>

      <!-- 加载态 -->
      <div v-if="streaming" class="chat-msg assistant">
        <div class="msg-avatar"><img src="/favicon.ico" alt="AI" /></div>
        <div class="msg-bubble typing">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </div>
      </div>
    </div>

    <!-- 快捷建议 -->
    <div v-if="showSuggestions" class="panel-suggestions">
      <span
        v-for="s in suggestions"
        :key="s"
        class="suggestion-chip"
        @click="sendSuggestion(s)"
      >{{ s }}</span>
    </div>

    <!-- 输入区 -->
    <div class="panel-input">
      <el-input
        v-model="inputText"
        placeholder="输入消息，按 Enter 发送…"
        :disabled="streaming"
        @keyup.enter="handleSend"
        size="large"
      >
        <template #suffix>
          <el-button
            class="send-btn"
            :disabled="!inputText.trim() || streaming"
            @click="handleSend"
            circle
            size="small"
          >
            <el-icon><Promotion /></el-icon>
          </el-button>
        </template>
      </el-input>
    </div>

    <!-- 收起态的展开按钮 -->
    <div v-if="collapsed" class="collapsed-bar" @click="collapsed = false">
      <el-icon><ChatDotRound /></el-icon>
      <span>AI</span>
    </div>
  </div>
</template>

<script setup name="AiChatPanel">
import { ref, nextTick, onMounted, watch } from 'vue';
import { RefreshRight, ArrowRight, Promotion, ChatDotRound } from '@element-plus/icons-vue';
import { marked } from 'marked';
import { streamChatWithQwen } from '@/api/ai';

const props = defineProps({
  // 外部传入的上下文（如当前设备、点位信息）
  context: {
    type: Object,
    default: () => ({}),
  },
  defaultCollapsed: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['action', 'collapse-change']);

const collapsed = ref(props.defaultCollapsed);

watch(collapsed, (val) => {
  emit('collapse-change', val);
});

const inputText = ref('');
const streaming = ref(false);
const messagesRef = ref(null);
const sessionId = ref('');
const userInitial = ref('管');

const suggestions = ref([
  '查看今日设备告警',
  '本周运营数据汇总',
  '帮我创建一个维修工单',
]);

const showSuggestions = ref(true);

const messages = ref([
  {
    id: 'welcome',
    role: 'assistant',
    content: '你好，我是灵犀智能助手。我可以帮你查询设备状态、分析运营数据、创建工单。有什么可以帮你的？',
  },
]);

// Markdown 渲染
const renderMd = (text) => {
  if (!text) return '';
  try {
    return marked.parse(text, { breaks: true });
  } catch {
    return text;
  }
};

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
    }
  });
};

// 发送消息
const handleSend = async () => {
  const text = inputText.value.trim();
  if (!text || streaming.value) return;

  inputText.value = '';
  showSuggestions.value = false;

  messages.value.push({
    id: Date.now().toString(),
    role: 'user',
    content: text,
  });
  scrollToBottom();

  streaming.value = true;
  const assistantMsg = {
    id: (Date.now() + 1).toString(),
    role: 'assistant',
    content: '',
  };
  messages.value.push(assistantMsg);

  try {
    await streamChatWithQwen(
      text,
      sessionId.value || undefined,
      undefined,
      undefined,
      {
        onEvent(evt) {
          if (evt.type === 'token' && evt.content) {
            assistantMsg.content += evt.content;
            scrollToBottom();
          } else if (evt.type === 'session' && evt.sessionId) {
            sessionId.value = evt.sessionId;
          } else if (evt.type === 'action' && evt.action) {
            emit('action', evt.action);
          }
        },
      }
    );
  } catch (err) {
    if (!assistantMsg.content) {
      assistantMsg.content = '抱歉，请求出现异常，请稍后重试。';
    }
  } finally {
    streaming.value = false;
    scrollToBottom();
  }
};

// 快捷建议
const sendSuggestion = (text) => {
  inputText.value = text;
  handleSend();
};

// 重置对话
const resetChat = () => {
  messages.value = [
    {
      id: 'welcome-' + Date.now(),
      role: 'assistant',
      content: '好的，已开启新对话。有什么可以帮你的？',
    },
  ];
  sessionId.value = '';
  showSuggestions.value = true;
};

onMounted(() => {
  scrollToBottom();
});
</script>

<style lang="scss" scoped>
.ai-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--lx-surface);
  border: 1px solid var(--lx-border-soft);
  border-radius: var(--lx-radius);
  overflow: hidden;
  position: relative;
  box-shadow: var(--lx-shadow-md);

  &.collapsed {
    width: 44px;
    height: 44px;
    min-width: 44px;
    min-height: 44px;
    border-radius: 50%;
    box-shadow: var(--lx-shadow-lg);
    cursor: pointer;

    .panel-header,
    .panel-messages,
    .panel-suggestions,
    .panel-input {
      display: none;
    }
  }
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lx-space-md) var(--lx-space-lg);
  border-bottom: 1px solid var(--lx-border-soft);
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: var(--lx-space-sm);
  }

  .header-icon {
    width: 32px;
    height: 32px;
    border-radius: var(--lx-radius-sm);
    background: linear-gradient(135deg, var(--lx-primary), var(--lx-primary-hover));
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;

    img {
      width: 20px;
      height: 20px;
    }
  }

  .header-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--lx-text);
    display: block;
  }

  .header-sub {
    font-size: 11px;
    color: var(--lx-muted);
  }

  .header-actions {
    display: flex;
    gap: 2px;
  }
}

.panel-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--lx-space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--lx-space-md);
}

.chat-msg {
  display: flex;
  gap: var(--lx-space-sm);
  max-width: 85%;

  &.user {
    align-self: flex-end;
    flex-direction: row-reverse;
  }

  .msg-avatar {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .avatar-text {
      font-size: 11px;
      font-weight: 600;
      color: var(--lx-surface);
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, var(--lx-chart-blue), var(--lx-chart-slate));
      border-radius: 50%;
    }
  }

  .msg-bubble {
    padding: 10px 14px;
    border-radius: var(--lx-radius);
    font-size: 13px;
    line-height: 1.6;
    word-break: break-word;

    :deep(p) {
      margin: 0 0 8px;
      &:last-child { margin-bottom: 0; }
    }
    :deep(code) {
      background: var(--lx-canvas);
      padding: 2px 5px;
      border-radius: 4px;
      font-size: 12px;
    }
  }

  &.assistant .msg-bubble {
    background: var(--lx-canvas);
    color: var(--lx-text);
    border-bottom-left-radius: 4px;
  }

  &.user .msg-bubble {
    background: var(--lx-primary);
    color: #fff;
    border-bottom-right-radius: 4px;
  }
}

// 打字动画
.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 14px 18px;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--lx-muted);
    animation: typing-bounce 1.2s infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-4px); opacity: 1; }
}

.panel-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lx-space-sm);
  padding: 0 var(--lx-space-lg) var(--lx-space-md);
  flex-shrink: 0;

  .suggestion-chip {
    padding: 6px 12px;
    font-size: 12px;
    color: var(--lx-muted);
    background: var(--lx-canvas);
    border: 1px solid var(--lx-border-soft);
    border-radius: 20px;
    cursor: pointer;
    transition: border-color 0.15s, color 0.15s;

    &:hover {
      border-color: var(--lx-primary);
      color: var(--lx-primary);
    }
  }
}

.panel-input {
  padding: var(--lx-space-md) var(--lx-space-lg);
  border-top: 1px solid var(--lx-border-soft);
  flex-shrink: 0;

  .send-btn {
    background: var(--lx-primary);
    color: #fff;
    border: none;

    &:hover {
      background: var(--lx-primary-hover);
    }

    &.is-disabled {
      background: var(--lx-border);
    }
  }
}

.collapsed-bar {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(135deg, var(--lx-primary), var(--lx-primary-hover));
  border-radius: 50%;
  transition: transform 0.15s, box-shadow 0.15s;

  &:hover {
    transform: scale(1.08);
    box-shadow: 0 4px 16px rgba(15, 118, 110, 0.3);
  }

  span {
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.06em;
  }
}

@media (prefers-reduced-motion: reduce) {
  .typing .dot {
    animation: none;
    opacity: 0.6;
  }
}
</style>
