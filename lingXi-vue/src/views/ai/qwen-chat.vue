<template>
  <div class="ai-chat-page">
    <!-- 主容器 -->
    <div class="chat-container">
      <!-- 左侧会话栏 -->
      <div class="session-sidebar">
        <div class="sidebar-header">
          <div class="logo-area">
            <img src="/favicon.ico" alt="灵犀助手" class="logo-icon" />
            <h2 class="logo-text">灵犀助手</h2>
          </div>
        </div>
        
        <!-- 新建会话按钮 -->
        <el-button 
          type="primary" 
          class="new-chat-btn" 
          @click="createNewSession"
        >
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </el-button>
        
        <!-- 会话列表 -->
        <div class="session-list-container">
          <div class="list-header">
            <span class="list-title">历史对话</span>
            <span class="list-count">{{ sessions.length }}</span>
          </div>
          
          <div class="session-scroll-area">
            <div 
              v-for="session in sessions" 
              :key="session.id" 
              class="session-item" 
              :class="{ active: session.sessionId === currentSessionId }"
              @click="switchSession(session)"
            >
              <div class="session-icon">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="session-content">
                <div class="session-name">
                  {{ session.sessionName }}
                </div>
                <div class="session-time">{{ formatTime(session.createTime) }}</div>
              </div>
              <div class="session-actions">
                <el-dropdown 
                  @command="(command) => handleSessionAction(command, session)" 
                  trigger="click" 
                  placement="bottom-end"
                >
                  <el-button 
                    type="text" 
                    size="small" 
                    class="more-btn"
                    @click.stop
                  >
                    <el-icon><More /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">
                        <el-icon><EditPen /></el-icon>
                        <span>重命名</span>
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" class="danger-item">
                        <el-icon><Delete /></el-icon>
                        <span>删除</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 右侧聊天主区域 -->
      <div class="chat-main">
        <!-- 聊天内容区域 -->
        <div class="chat-messages-container" ref="chatContainer">
          <!-- 空状态 -->
          <div v-if="!history.length && !loading" class="welcome-screen">
            <div class="welcome-content">
              <div class="welcome-icon">
                <img src="/favicon.ico" alt="灵犀" class="welcome-logo" />
              </div>
              <h1 class="welcome-title">你好，我是灵犀OwO</h1>
              <p class="welcome-subtitle">你的智能AI助手，随时为你提供帮助</p>
            </div>
          </div>
          
          <!-- 消息列表 -->
          <div v-else class="messages-list">
            <div
              v-for="item in history"
              :key="item.id"
              class="message-item"
              :class="item.isUser ? 'user-message' : 'assistant-message'"
            >
              <!-- 助手消息 -->
              <div v-if="!item.isUser" class="assistant-message-wrapper">
                <div class="message-avatar">
                  <div class="avatar assistant-avatar">
                    <img src="/favicon.ico" alt="灵犀" class="avatar-img" />
                  </div>
                </div>
                <div class="message-content-wrapper">
                  <div class="message-header">
                    <span class="message-sender">灵犀</span>
                    <span class="message-time">{{ item.time || formatTime(item.createTime) }}</span>
                  </div>
                  <div class="message-bubble assistant-bubble">
                    <div class="message-text markdown-content" v-html="renderMarkdown(item.content)"></div>
                  </div>
                </div>
              </div>
              
              <!-- 用户消息 -->
              <div v-else class="user-message-wrapper">
                <div class="message-content-wrapper user-wrapper">
                  <div class="message-header user-header">
                    <span class="message-time">{{ item.time }}</span>
                    <span class="message-sender">{{ userStore.name }}</span>
                  </div>
                  <div class="message-bubble user-bubble">
                    <div class="message-text markdown-content" v-html="renderMarkdown(item.content)"></div>
                  </div>
                </div>
                <div class="message-avatar user-avatar">
                  <div class="avatar user-avatar-icon">👤</div>
                </div>
              </div>
            </div>
            
            <!-- 加载指示器 -->
            <div v-if="loading" class="loading-indicator">
              <div class="typing-indicator">
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
              </div>
              <span class="typing-text">灵犀正在思考...</span>
            </div>
          </div>
        </div>
        
        <!-- 输入区域 -->
        <div class="chat-input-container">
          <div class="input-wrapper">
            <!-- 主输入框 -->
            <div class="input-main">
              <el-input
                v-model="message"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                :placeholder="enableDataAnalysis ? '请输入要分析的问题，将基于数据看板进行智能分析...' : '向灵犀助手提问...'"
                @keydown.enter="handleEnter"
                class="message-input"
                resize="none"
              />
              <div class="input-actions">
                <!-- 模式切换按钮 -->
                <el-button 
                  type="default" 
                  class="mode-btn"
                  @click="enableDataAnalysis = !enableDataAnalysis"
                  :class="{ 'active-mode': enableDataAnalysis }"
                >
                  <el-icon v-if="!enableDataAnalysis"><ChatDotSquare /></el-icon>
                  <el-icon v-else><DataAnalysis /></el-icon>
                  <span>{{ enableDataAnalysis ? '数据分析' : '普通对话' }}</span>
                </el-button>
                
                <el-dropdown @command="usePreset" trigger="click" placement="top-start" :disabled="!hasValidQuestions">
                  <el-button type="default" class="preset-btn" :disabled="!hasValidQuestions">
                    <el-icon><MagicStick /></el-icon>
                    <span>快捷提问</span>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <!-- 智能生成的快捷提问 -->
                      <el-dropdown-item
                        v-for="(item, index) in validQuestions"
                        :key="index"
                        :command="item"
                        class="preset-item"
                      >
                        <el-icon><Lightning /></el-icon>
                        <span>{{ item }}</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                
                <el-button 
                  type="primary" 
                  :loading="loading" 
                  :disabled="loading || !message.trim()" 
                  @click="sendMessage" 
                  class="send-btn"
                >
                  <template #icon>
                    <el-icon><Promotion /></el-icon>
                  </template>
                  <span>发送</span>
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 重命名对话框 -->
    <el-dialog
      v-model="renameDialogVisible"
      title="重命名会话"
      width="400px"
      align-center
    >
      <div class="rename-dialog">
        <el-input 
          v-model="newSessionName" 
          placeholder="请输入新的会话名称" 
          size="large"
          maxlength="50"
          show-word-limit
        />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="renameDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmRename" :disabled="!newSessionName.trim()">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue';
import dayjs from 'dayjs';
import { marked } from 'marked';
import { ElMessage, ElDialog, ElButton, ElInput, ElIcon, ElDropdown, ElDropdownMenu, ElDropdownItem, ElMessageBox } from 'element-plus';
import { 
  Plus, 
  ChatDotRound, 
  More, 
  EditPen, 
  Delete, 
  MagicStick, 
  Lightning, 
  Promotion,
  ChatDotSquare,
  DataAnalysis
} from '@element-plus/icons-vue';
import { chatWithQwen, analyzeDashboard, getChatHistory, saveChatHistory, getSessions, createSession, updateSession, deleteSessionById, generateSmartQuestions } from '@/api/ai';
import useUserStore from '@/store/modules/user';

// 配置marked
marked.setOptions({
  breaks: true, // 启用空行转换为<br>标签
  gfm: true,
  headerIds: false
});

// 转换markdown为html
const renderMarkdown = (content) => {
  if (!content) return '';
  // 清理多余的空格和换行
  const cleanedContent = content
    .replace(/\n{3,}/g, '\n\n') // 将3个以上连续换行替换为2个
    .trim();
  return marked.parse(cleanedContent);
};

// 聊天相关状态
const message = ref('');
const history = ref([]);
const loading = ref(false);
const error = ref('');
const enableDataAnalysis = ref(false);
const chatContainer = ref(null);
const smartQuestions = ref([]);
const validQuestions = ref([]);

// 计算是否有有效的快捷提问
const hasValidQuestions = computed(() => {
  validQuestions.value = smartQuestions.value.filter(question => {
    return question && question.trim() !== '';
  });
  return validQuestions.value.length > 0;
});

const presets = [];

// 会话管理相关状态
const sessions = ref([]);
const currentSessionId = ref('');
const renameDialogVisible = ref(false);
const newSessionName = ref('');
const currentEditingSession = ref(null);

// 获取当前用户信息
const userStore = useUserStore();

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
};

// 添加消息
const appendMessage = (isUser, content) => {
  const newMessage = {
    id: `${isUser ? 'user' : 'assistant'}-${Date.now()}-${Math.random()}`,
    isUser,
    content,
    time: dayjs().format('HH:mm'),
    createTime: new Date()
  };
  history.value.push(newMessage);
  scrollToBottom();
};

// 发送消息
const sendMessage = async () => {
  const content = message.value.trim();
  if (!content) {
    ElMessage.warning('请输入内容');
    return;
  }
  if (loading.value) {
    ElMessage.warning('数据正在处理，请勿重复提交');
    return;
  }
  
  error.value = '';
  loading.value = true;
  appendMessage(true, content);
  message.value = '';
  
  try {
    let reply;
    if (enableDataAnalysis.value) {
      reply = await analyzeDashboard(content, null, null, userStore.id, userStore.name);
    } else {
      reply = await chatWithQwen(content, userStore.id, userStore.name);
    }
    appendMessage(false, reply);
  } catch (err) {
    error.value = err?.msg || err?.message || '发送失败，请稍后重试';
    ElMessage.error('发送失败：' + error.value);
    history.value.pop();
  } finally {
    loading.value = false;
    // 生成智能快捷提问
    if (history.value.length > 0) {
      generateSmartQuestions(history.value, userStore.id, userStore.name)
        .then(res => {
          smartQuestions.value = res.data || [];
        })
        .catch(err => {
          console.error('生成智能快捷提问失败:', err);
        });
    }
  }
};

// 处理回车键
const handleEnter = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    sendMessage();
  }
};

// 使用预设问题
const usePreset = (text) => {
  message.value = text;
  nextTick(() => {
    const textarea = document.querySelector('.message-input textarea');
    textarea && textarea.focus();
  });
};

// 格式化时间
const formatTime = (time) => {
  if (!time) return '';
  const now = dayjs();
  const target = dayjs(time);
  
  if (now.isSame(target, 'day')) {
    return target.format('HH:mm');
  } else if (now.subtract(1, 'day').isSame(target, 'day')) {
    return '昨天 ' + target.format('HH:mm');
  } else if (now.isSame(target, 'year')) {
    return target.format('MM-DD HH:mm');
  } else {
    return target.format('YYYY-MM-DD');
  }
};

// 加载会话列表
const loadSessions = async () => {
  try {
    const res = await getSessions(userStore.id);
    sessions.value = res.data || [];
    if (sessions.value.length === 0) {
      await createNewSession();
    } else if (!currentSessionId.value) {
      switchSession(sessions.value[0]);
    }
  } catch (err) {
    console.error('加载会话列表失败:', err);
    ElMessage.error('加载会话列表失败: ' + (err?.msg || err?.message || '未知错误'));
  }
};

// 加载对话历史记录
const loadChatHistory = async () => {
  if (!currentSessionId.value) return;
  
  try {
    const res = await getChatHistory(currentSessionId.value);
    const data = res.data || [];
    if (data && data.length > 0) {
      const formattedHistory = data.map(item => ({
        ...item,
        isUser: item.messageType === 'user'
      }));
      history.value = formattedHistory;
      scrollToBottom();
      // 生成智能快捷提问
      generateSmartQuestions(history.value, userStore.id, userStore.name)
        .then(res => {
          smartQuestions.value = res.data || [];
        })
        .catch(err => {
          console.error('生成智能快捷提问失败:', err);
        });
    } else {
      history.value = [];
      smartQuestions.value = [];
    }
  } catch (err) {
    console.error('加载对话历史失败:', err);
    ElMessage.error('加载对话历史失败: ' + (err?.msg || err?.message || '未知错误'));
  }
};

// 创建新会话
const createNewSession = async () => {
  try {
    const res = await createSession(userStore.id);
    const newSession = res.data;
    sessions.value.unshift(newSession);
    smartQuestions.value = [];
    switchSession(newSession);
    ElMessage.success('新会话已创建');
  } catch (err) {
    console.error('创建会话失败:', err);
    ElMessage.error('创建会话失败: ' + (err?.msg || err?.message || '未知错误'));
  }
};

// 切换会话
const switchSession = async (session) => {
  // 先清空快捷提问，确保切换过程中不显示残留内容
  smartQuestions.value = [];
  currentSessionId.value = session.sessionId;
  localStorage.setItem('ai_chat_session_id', session.sessionId);
  await loadChatHistory();
};

// 显示重命名对话框
const showRenameDialog = (session) => {
  currentEditingSession.value = session;
  newSessionName.value = session.sessionName;
  renameDialogVisible.value = true;
};

// 确认重命名
const confirmRename = async () => {
  if (!newSessionName.value.trim()) {
    ElMessage.warning('会话名称不能为空');
    return;
  }
  
  try {
    await updateSession({
      id: currentEditingSession.value.id,
      sessionName: newSessionName.value.trim()
    });
    const index = sessions.value.findIndex(s => s.id === currentEditingSession.value.id);
    if (index !== -1) {
      sessions.value[index].sessionName = newSessionName.value.trim();
    }
    renameDialogVisible.value = false;
    ElMessage.success('会话名称已更新');
  } catch (err) {
    console.error('更新会话名称失败:', err);
    ElMessage.error('更新会话名称失败: ' + (err?.msg || err?.message || '未知错误'));
  }
};

// 删除会话
const deleteSession = async (session) => {
  try {
    // 添加确认提示，包含会话名称
    await ElMessageBox.confirm(
      `确定要删除会话【${session.sessionName}】吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    
    await deleteSessionById(session.sessionId);
    const index = sessions.value.findIndex(s => s.id === session.id);
    if (index !== -1) {
      sessions.value.splice(index, 1);
    }
    if (session.sessionId === currentSessionId.value) {
      if (sessions.value.length > 0) {
        switchSession(sessions.value[0]);
      } else {
        await createNewSession();
      }
    }
    ElMessage.success('会话已删除');
  } catch (err) {
    if (err !== 'cancel') {
      console.error('删除会话失败:', err);
      ElMessage.error('删除会话失败: ' + (err?.msg || err?.message || '未知错误'));
    }
  }
};

// 处理会话操作命令
const handleSessionAction = (command, session) => {
  switch (command) {
    case 'rename':
      showRenameDialog(session);
      break;
    case 'delete':
      deleteSession(session);
      break;
    default:
      break;
  }
};

// 监听消息变化，自动滚动
watch(history, () => {
  scrollToBottom();
}, { deep: true });

// 组件挂载
onMounted(async () => {
  const savedSessionId = localStorage.getItem('ai_chat_session_id');
  if (savedSessionId) {
    currentSessionId.value = savedSessionId;
  }
  await loadSessions();
});
</script>

<style scoped lang="scss">
@import '@/assets/styles/dialog-styles.scss';
.ai-chat-page {
  height: 100vh;
  background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-container {
  width: 100%;
  height: 100vh;
  background: white;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* 左侧会话栏 */
.session-sidebar {
  width: 280px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  padding: 20px;
  position: relative;
  z-index: 1;

  .sidebar-header {
    margin-bottom: 24px;

    .logo-area {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      border-radius: 12px;
      background: white;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);

      .logo-icon {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        object-fit: contain;
      }

      .logo-text {
        font-size: 18px;
        font-weight: 600;
        color: #1e293b;
        margin: 0;
      }
    }
  }

  .new-chat-btn {
    height: 48px;
    border-radius: 12px;
    background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
    border: none;
    color: white;
    font-weight: 500;
    margin-bottom: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
    }

    .el-icon {
      font-size: 18px;
    }
  }

  .session-list-container {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding: 0 8px;

      .list-title {
        font-size: 14px;
        font-weight: 600;
        color: #64748b;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }

      .list-count {
        background: #e2e8f0;
        color: #475569;
        font-size: 12px;
        font-weight: 600;
        padding: 2px 8px;
        border-radius: 10px;
      }
    }

    .session-scroll-area {
      flex: 1;
      overflow-y: auto;
      padding-right: 8px;

      &::-webkit-scrollbar {
        width: 4px;
      }

      &::-webkit-scrollbar-track {
        background: #f1f5f9;
        border-radius: 4px;
      }

      &::-webkit-scrollbar-thumb {
        background: #cbd5e1;
        border-radius: 4px;
      }
    }

    .session-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      margin-bottom: 8px;
      border-radius: 12px;
      background: white;
      cursor: pointer;
      transition: all 0.3s ease;
      border: 1px solid #e2e8f0;
      position: relative;

      &:hover {
        background: #f8fafc;
        transform: translateX(4px);
        border-color: #cbd5e1;

        .session-actions {
          opacity: 1;
        }
      }

      &.active {
        background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
        border-color: transparent;
        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);

        .session-icon {
          background: rgba(255, 255, 255, 0.2);
          color: white;
        }

        .session-name {
          color: white;
        }

        .session-time {
          color: rgba(255, 255, 255, 0.8);
        }
      }

      .session-icon {
        width: 36px;
        height: 36px;
        border-radius: 8px;
        background: #f1f5f9;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #64748b;
        flex-shrink: 0;
      }

      .session-content {
        flex: 1;
        min-width: 0;

        .session-name {
          font-size: 14px;
          font-weight: 500;
          color: #1e293b;
          margin-bottom: 2px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .session-time {
          font-size: 12px;
          color: #94a3b8;
        }
      }

      .session-actions {
        opacity: 0;
        transition: opacity 0.3s ease;

        .more-btn {
          color: #94a3b8;
          padding: 4px;

          &:hover {
            background: rgba(0, 0, 0, 0.05);
          }
        }
      }
    }
  }
}

/* 右侧聊天主区域 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  position: relative;
}

.chat-messages-container {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  position: relative;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: #f8fafc;
    border-radius: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 4px;
  }
}

/* 欢迎屏幕 */
.welcome-screen {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;

  .welcome-content {
    text-align: center;
    max-width: 600px;
    padding: 40px;

    .welcome-icon {
      margin-bottom: 24px;
      animation: float 3s ease-in-out infinite;

      .welcome-logo {
        width: 80px;
        height: 80px;
        border-radius: 16px;
        object-fit: contain;
        box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
      }
    }

    .welcome-title {
      font-size: 42px;
      font-weight: 600;
      color: #1e293b;
      margin-bottom: 12px;
      background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .welcome-subtitle {
      font-size: 18px;
      color: #64748b;
      margin-bottom: 40px;
    }

    .quick-actions {
      .quick-title {
        font-size: 16px;
        font-weight: 600;
        color: #475569;
        margin-bottom: 16px;
      }

      .quick-buttons {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 12px;
        max-width: 400px;
        margin: 0 auto;

        .quick-btn {
          height: 44px;
          border-radius: 12px;
          background: #f8fafc;
          border: 1px solid #e2e8f0;
          color: #475569;
          font-size: 14px;
          transition: all 0.3s ease;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;

          &:hover {
            background: white;
            border-color: #cbd5e1;
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
          }
        }
      }
    }
  }
}

/* 消息列表 */
.messages-list {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 消息通用样式 */
.message-item {
  .message-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
    font-size: 13px;

    .message-sender {
      font-weight: 600;
      color: #475569;
    }

    .message-time {
      color: #94a3b8;
    }
  }

  .message-bubble {
    padding: 1px 16px;
    border-radius: 16px;
    max-width: 600px;
    line-height: 1.5;
    font-size: 15px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
    
    .message-text {
      word-break: break-word;
      line-height: 1.5;
      font-size: 15px;
    }
    
    /* Markdown 样式 - 优化版 */
    .markdown-content {
      /* 重置基础样式 */
      * {
        margin: 0;
        padding: 0;
        line-height: 1.5;
      }
      
      /* 基础文本样式 */
      font-size: 15px;
      line-height: 1.5;
      
      /* 段落间距 - 更合理的间距 */
      p {
        margin-bottom: 12px;
        
        &:last-child {
          margin-bottom: 0;
        }
      }
      
      /* 标题样式 - 合理的间距 */
      h1, h2, h3, h4, h5, h6 {
        font-weight: 600;
        line-height: 1.3;
        margin-top: 20px;
        margin-bottom: 12px;
        color: inherit;
      }
      
      h1 {
        font-size: 24px;
        margin-top: 0;
        padding-bottom: 8px;
        border-bottom: 1px solid rgba(0, 0, 0, 0.1);
      }
      
      h2 {
        font-size: 20px;
        margin-top: 24px;
        padding-bottom: 6px;
        border-bottom: 1px solid rgba(0, 0, 0, 0.08);
      }
      
      h3 {
        font-size: 18px;
        margin-top: 20px;
      }
      
      h4, h5, h6 {
        font-size: 16px;
        margin-top: 16px;
      }
      
      /* 列表样式 */
      ul, ol {
        margin: 12px 0;
        padding-left: 24px;
        
        li {
          margin-bottom: 4px;
          line-height: 1.5;
        }
      }
      
      /* 嵌套列表 */
      ul ul, ol ol, ul ol, ol ul {
        margin-top: 4px;
        margin-bottom: 0;
      }
      
      /* 代码块样式 */
      pre {
        background: rgba(0, 0, 0, 0.05);
        padding: 16px;
        border-radius: 8px;
        overflow-x: auto;
        margin: 12px 0;
        font-size: 14px;
        
        code {
          background: transparent;
          padding: 0;
          font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
          line-height: 1.4;
        }
      }
      
      /* 内联代码 */
      code:not(pre code) {
        background: rgba(0, 0, 0, 0.08);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        font-size: 14px;
      }
      
      /* 引用块 */
      blockquote {
        border-left: 4px solid #0f766e;
        padding-left: 16px;
        margin: 16px 0;
        color: inherit;
        opacity: 0.85;
        
        p {
          margin: 8px 0;
        }
      }
      
      /* 表格样式 */
      table {
        border-collapse: collapse;
        width: 100%;
        margin: 16px 0;
        font-size: 14px;
        
        th, td {
          border: 1px solid rgba(0, 0, 0, 0.1);
          padding: 8px 12px;
          text-align: left;
          line-height: 1.4;
        }
        
        th {
          background: rgba(0, 0, 0, 0.05);
          font-weight: 600;
        }
      }
      
      /* 分隔线 */
      hr {
        border: none;
        border-top: 1px solid rgba(0, 0, 0, 0.1);
        margin: 24px 0;
      }
      
      /* 链接 */
      a {
        color: #0f766e;
        text-decoration: none;
        transition: color 0.2s;
        
        &:hover {
          color: #0d9488;
          text-decoration: underline;
        }
      }
      
      /* 强调文本 */
      strong {
        font-weight: 600;
      }
      
      em {
        font-style: italic;
      }
      
      /* 图片 */
      img {
        max-width: 100%;
        height: auto;
        border-radius: 8px;
        margin: 12px 0;
      }
      
      /* 任务列表 */
      input[type="checkbox"] {
        margin-right: 8px;
        vertical-align: middle;
      }
    }
  }
}

/* 助手消息 */
.assistant-message-wrapper {
  display: flex;
  gap: 12px;

  .message-avatar {
    .assistant-avatar {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      border: none;
      box-shadow: none;

      .avatar-img {
        width: 120%;
        height: 120%;
        object-fit: contain;
        padding: 2px;
        border: none;
        box-shadow: none;
        background: transparent;
      }
    }
  }

  .assistant-bubble {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 16px 16px 16px 4px;
    color: #1e293b;
  }
}

/* 用户消息 */
.user-message-wrapper {
  display: flex;
  justify-content: flex-end;
  gap: 12px;

  .user-wrapper {
    text-align: right;
  }

  .user-header {
    justify-content: flex-end;

    .message-sender {
      color: #0f766e;
    }
  }

  .message-avatar {
    .user-avatar-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: #e2e8f0;
      color: #64748b;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
    }
  }

  .user-bubble {
    background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
    color: white;
    border-radius: 16px 16px 4px 16px;
    
    /* 用户消息中的Markdown特殊处理 */
    .markdown-content {
      a {
        color: rgba(255, 255, 255, 0.9);
        text-decoration: underline;
      }
      
      code:not(pre code) {
        background: rgba(255, 255, 255, 0.2);
        color: rgba(255, 255, 255, 0.95);
      }
      
      pre {
        background: rgba(0, 0, 0, 0.2);
        
        code {
          color: rgba(255, 255, 255, 0.95);
        }
      }
    }
  }
}

/* 加载指示器 */
.loading-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 16px;
  max-width: 200px;
  margin: 0 auto;

  .typing-indicator {
    display: flex;
    gap: 4px;

    .typing-dot {
      width: 8px;
      height: 8px;
      background: #0f766e;
      border-radius: 50%;
      animation: typing 1.4s infinite ease-in-out;

      &:nth-child(1) { animation-delay: 0s; }
      &:nth-child(2) { animation-delay: 0.2s; }
      &:nth-child(3) { animation-delay: 0.4s; }
    }
  }

  .typing-text {
    font-size: 14px;
    color: #64748b;
    font-weight: 500;
  }
}

/* 输入区域 */
.chat-input-container {
  border-top: 1px solid #e2e8f0;
  background: white;
  padding: 16px 24px;
  position: relative;

  .input-wrapper {
    max-width: 800px;
    margin: 0 auto;
  }

  .input-main {
    .message-input {
      :deep(.el-textarea__inner) {
        min-height: 56px;
        max-height: 120px;
        border: 2px solid #e2e8f0;
        border-radius: 16px;
        padding: 16px 20px;
        font-size: 15px;
        line-height: 1.5;
        resize: none;
        background: #f8fafc;
        transition: all 0.3s ease;

        &:focus {
          border-color: #0f766e;
          background: white;
          box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
        }

        &::placeholder {
          color: #94a3b8;
        }
      }
    }

    .input-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-top: 12px;

      .mode-btn {
        border-radius: 12px;
        border: 1px solid #e2e8f0;
        background: white;
        color: #475569;
        padding: 10px 16px;
        transition: all 0.3s ease;
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        font-weight: 500;

        &:hover {
          border-color: #cbd5e1;
          background: #f8fafc;
        }

        &.active-mode {
          background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
          color: white;
          border-color: transparent;

          &:hover {
            background: linear-gradient(135deg, #0b5e58 0%, #123f50 100%);
          }
        }

        .el-icon {
          font-size: 16px;
        }
      }

      .preset-btn {
        border-radius: 12px;
        border: 1px solid #e2e8f0;
        background: white;
        color: #475569;
        padding: 10px 16px;
        transition: all 0.3s ease;
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        font-weight: 500;

        &:hover {
          border-color: #cbd5e1;
          background: #f8fafc;
        }

        .el-icon {
          font-size: 16px;
        }
      }

      .send-btn {
        border-radius: 12px;
        background: linear-gradient(135deg, #0f766e 0%, #164e63 100%);
        border: none;
        color: white;
        padding: 10px 24px;
        font-weight: 500;
        margin-left: auto;
        transition: all 0.3s ease;
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;

        &:hover:not(:disabled) {
          transform: translateY(-2px);
          box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
        }

        &:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .el-icon {
          font-size: 16px;
        }
      }
    }
  }
}

.rename-dialog {
  padding: 8px 0;
}

/* 动画 */
@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.6;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .session-sidebar {
    width: 240px;
  }
}

@media (max-width: 768px) {
  .ai-chat-page {
    padding: 0;
  }

  .chat-container {
    height: 100vh;
    border-radius: 0;
    flex-direction: column;
  }

  .session-sidebar {
    width: 100%;
    height: 200px;
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }

  .welcome-content {
    padding: 20px !important;

    .quick-buttons {
      grid-template-columns: 1fr !important;
    }
  }

  .input-actions {
    flex-wrap: wrap;
    justify-content: space-between;

    .mode-btn,
    .preset-btn,
    .send-btn {
      flex: 1;
      min-width: 120px;
      justify-content: center;
    }
  }
}
</style>
