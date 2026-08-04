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
            <el-button
              class="memory-btn"
              text
              circle
              aria-label="管理回答偏好"
              title="管理回答偏好"
              @click="openMemoryDialog"
            >
              <el-icon><Collection /></el-icon>
            </el-button>
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
                    <div v-if="item.activities?.length" class="agent-work-trace">
                      <div
                        v-for="activity in item.activities"
                        :key="activity.tool"
                        class="work-trace-row"
                        :class="`is-${activity.status}`"
                      >
                        <span class="status-lamp" aria-hidden="true"></span>
                        <span class="work-label">{{ activity.label }}</span>
                        <span v-if="activity.resultCount !== null" class="work-count">
                          {{ activity.resultCount }} 项
                        </span>
                        <span class="work-status">{{ activityStatusText(activity.status) }}</span>
                      </div>
                    </div>
                    <section
                      v-if="item.pendingAction"
                      class="approval-card"
                      :class="`is-${item.pendingAction.decision}`"
                    >
                      <div class="approval-card-header">
                        <div>
                          <span class="approval-eyebrow">需要人工确认</span>
                          <h4>创建维修工单</h4>
                        </div>
                        <span class="approval-state">
                          {{ actionDecisionText(item.pendingAction) }}
                        </span>
                      </div>
                      <dl class="approval-facts">
                        <div>
                          <dt>目标设备</dt>
                          <dd>{{ item.pendingAction.target?.inner_code || '未知设备' }}</dd>
                        </div>
                        <div>
                          <dt>工单状态</dt>
                          <dd>创建后为待处理</dd>
                        </div>
                      </dl>
                      <label class="approval-description-label">工单描述</label>
                      <el-input
                        v-model="item.pendingAction.description"
                        type="textarea"
                        :rows="3"
                        maxlength="500"
                        show-word-limit
                        :disabled="item.pendingAction.decision !== 'pending' || item.pendingAction.submitting"
                      />
                      <p class="approval-impact">
                        <strong>影响范围：</strong>{{ item.pendingAction.impact }}
                      </p>
                      <p v-if="item.pendingAction.error" class="approval-error">
                        {{ item.pendingAction.error }}
                      </p>
                      <p
                        v-if="item.pendingAction.decision === 'approved' && item.pendingAction.result?.task_code"
                        class="approval-result"
                      >
                        已创建工单 {{ item.pendingAction.result.task_code }}
                      </p>
                      <p v-else-if="item.pendingAction.decision === 'rejected'" class="approval-result is-rejected">
                        已拒绝，未执行任何写操作
                      </p>
                      <div
                        v-if="item.pendingAction.decision === 'pending'"
                        class="approval-actions"
                      >
                        <el-button
                          :disabled="item.pendingAction.submitting"
                          :loading="item.pendingAction.submitting && item.pendingAction.submittingDecision === 'reject'"
                          @click="handleActionDecision(item, 'reject')"
                        >
                          拒绝
                        </el-button>
                        <el-button
                          type="primary"
                          :disabled="item.pendingAction.submitting || !item.pendingAction.description?.trim()"
                          :loading="item.pendingAction.submitting && item.pendingAction.submittingDecision === 'approve'"
                          @click="handleActionDecision(item, 'approve')"
                        >
                          批准并创建
                        </el-button>
                      </div>
                    </section>
                    <div v-if="item.citations?.length" class="citation-strip">
                      <div class="citation-heading">参考资料</div>
                      <div class="citation-list">
                        <span
                          v-for="citation in item.citations"
                          :key="citation.source_id"
                          class="citation-chip"
                          :title="citation.source_id"
                        >
                          {{ citation.title || '内部资料' }}
                          <small v-if="citation.section">{{ citation.section }}</small>
                        </span>
                      </div>
                    </div>
                    <div v-if="item.memorySaved?.length" class="memory-saved-note">
                      <el-icon><CircleCheck /></el-icon>
                      已记住：{{ item.memorySaved.map(memoryPreferenceText).join('、') }}
                    </div>
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
                    <div v-if="item.attachments?.length" class="message-attachments">
                      <a
                        v-for="attachment in item.attachments"
                        :key="attachment.attachmentId"
                        class="message-attachment"
                        :class="{ 'is-image': attachment.kind === 'image' }"
                        :href="attachment.previewUrl || undefined"
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        <img
                          v-if="attachment.kind === 'image' && attachment.previewUrl"
                          :src="attachment.previewUrl"
                          :alt="attachment.name"
                        />
                        <span v-else class="attachment-file-icon">
                          <el-icon><Document /></el-icon>
                        </span>
                        <span class="attachment-meta">
                          <strong>{{ attachment.name }}</strong>
                          <small>{{ formatFileSize(attachment.size) }}</small>
                        </span>
                      </a>
                    </div>
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
              <span class="typing-text">
                {{ currentActivityLabel }}
              </span>
            </div>
          </div>
        </div>
        
        <!-- 输入区域 -->
        <div class="chat-input-container">
          <div class="input-wrapper">
            <!-- 主输入框 -->
            <div class="input-main">
              <div v-if="pendingAttachments.length" class="pending-attachments">
                <div
                  v-for="attachment in pendingAttachments"
                  :key="attachment.attachmentId"
                  class="pending-attachment"
                >
                  <img
                    v-if="attachment.kind === 'image' && attachment.previewUrl"
                    :src="attachment.previewUrl"
                    :alt="attachment.name"
                  />
                  <span v-else class="pending-file-icon">
                    <el-icon><Document /></el-icon>
                  </span>
                  <span class="pending-file-meta">
                    <strong :title="attachment.name">{{ attachment.name }}</strong>
                    <small>
                      {{ formatFileSize(attachment.size) }}
                      <template v-if="attachment.truncated"> · 内容已截断</template>
                    </small>
                  </span>
                  <el-button
                    text
                    circle
                    size="small"
                    :loading="attachment.removing"
                    aria-label="移除附件"
                    @click="removePendingAttachment(attachment)"
                  >
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </div>
              <el-input
                v-model="message"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                :placeholder="enableDataAnalysis ? '请输入要分析的问题，将基于数据看板进行智能分析...' : '向灵犀助手提问...'"
                @keydown.enter="handleEnter"
                class="message-input"
                resize="none"
              />
              <input
                ref="attachmentInput"
                class="attachment-input"
                type="file"
                multiple
                :accept="acceptedAttachmentTypes"
                @change="handleAttachmentFiles"
              />
              <div class="input-actions">
                <!-- 模式切换按钮 -->
                <el-button 
                  type="default" 
                  class="mode-btn"
                  @click="toggleDataAnalysis"
                  :class="{ 'active-mode': enableDataAnalysis }"
                >
                  <el-icon v-if="!enableDataAnalysis"><ChatDotSquare /></el-icon>
                  <el-icon v-else><DataAnalysis /></el-icon>
                  <span>{{ enableDataAnalysis ? '数据分析' : '普通对话' }}</span>
                </el-button>

                <el-button
                  type="default"
                  class="attachment-btn"
                  :loading="attachmentUploading"
                  :disabled="loading || enableDataAnalysis || pendingAttachments.length >= maxAttachments"
                  :title="enableDataAnalysis ? '附件仅支持普通对话模式' : '上传图片或文档'"
                  @click="openAttachmentPicker"
                >
                  <el-icon><Paperclip /></el-icon>
                  <span>附件</span>
                  <small v-if="pendingAttachments.length">{{ pendingAttachments.length }}/{{ maxAttachments }}</small>
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
                  :disabled="loading || attachmentUploading || (!message.trim() && !pendingAttachments.length)"
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

    <el-dialog
      v-model="memoryDialogVisible"
      title="回答偏好"
      width="460px"
      align-center
    >
      <div class="memory-dialog" v-loading="memoryLoading">
        <p class="memory-intro">
          这些偏好会跨对话生效。这里只保存选项值，不保存聊天原文、权限或实时业务数据。
        </p>
        <el-alert
          v-if="!memoryEnabled && !memoryLoading"
          title="长期偏好当前未启用"
          description="管理员启用长期 Store 后即可使用。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-form v-else label-position="top" class="memory-form">
          <el-form-item label="回答篇幅">
            <el-select v-model="memoryForm.answer_length" placeholder="保持默认">
              <el-option label="简短" value="short" />
              <el-option label="均衡" value="balanced" />
              <el-option label="详细" value="detailed" />
            </el-select>
          </el-form-item>
          <el-form-item label="回答结构">
            <el-select v-model="memoryForm.answer_structure" placeholder="保持默认">
              <el-option label="结论优先" value="conclusion_first" />
              <el-option label="自然组织" value="natural" />
            </el-select>
          </el-form-item>
          <el-form-item label="数字格式">
            <el-select v-model="memoryForm.number_format" placeholder="保持默认">
              <el-option label="保留两位小数" value="two_decimals" />
              <el-option label="按内容自适应" value="adaptive" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <span class="dialog-footer memory-footer">
          <el-button
            v-if="memoryEnabled"
            type="danger"
            plain
            :disabled="memorySaving"
            @click="clearMemories"
          >
            清空偏好
          </el-button>
          <span class="footer-spacer"></span>
          <el-button @click="memoryDialogVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="memorySaving"
            :disabled="!memoryEnabled"
            @click="saveMemories"
          >
            保存偏好
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue';
import dayjs from 'dayjs';
import { marked } from 'marked';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect
} from 'element-plus';
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
  DataAnalysis,
  Collection,
  CircleCheck,
  Close,
  Document,
  Paperclip
} from '@element-plus/icons-vue';
import {
  clearLongTermMemories,
  createSession,
  deleteAiAttachment,
  deleteSessionById,
  generateSmartQuestions,
  getChatHistory,
  getLongTermMemories,
  getSessions,
  updateLongTermPreference,
  updateSession,
  uploadAiAttachment
} from '@/api/ai';
import useAiChatStore from '@/store/modules/aiChat';
import useUserStore from '@/store/modules/user';
import {
  createLatestSingleFlight,
  smartQuestionRequestKey
} from '@/utils/latestSingleFlight';

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
const error = ref('');
const enableDataAnalysis = ref(false);
const chatContainer = ref(null);
const attachmentInput = ref(null);
const pendingAttachments = ref([]);
const attachmentUploading = ref(false);
const maxAttachments = 5;
const maxAttachmentBytes = 10 * 1024 * 1024;
const acceptedAttachmentTypes = [
  '.png', '.jpg', '.jpeg', '.webp', '.gif', '.pdf', '.docx', '.txt', '.md',
  '.json', '.csv', '.log', '.java', '.py', '.js', '.ts', '.tsx', '.jsx',
  '.vue', '.xml', '.yml', '.yaml', '.sql', '.properties', '.sh', '.ps1'
].join(',');
const smartQuestions = ref([]);
const smartQuestionsLoader = createLatestSingleFlight();
const validQuestions = ref([]);
const memoryDialogVisible = ref(false);
const memoryLoading = ref(false);
const memorySaving = ref(false);
const memoryEnabled = ref(false);
const memoryForm = ref({
  answer_length: '',
  answer_structure: '',
  number_format: ''
});

// 计算是否有有效的快捷提问
const hasValidQuestions = computed(() => {
  validQuestions.value = smartQuestions.value.filter(question => {
    return question && question.trim() !== '';
  });
  return validQuestions.value.length > 0;
});

const presets = [];

const refreshSmartQuestions = async (
  sessionId = currentSessionId.value,
  chatHistory = history.value
) => {
  if (!sessionId || !chatHistory.length || loading.value) return;
  const requestKey = smartQuestionRequestKey(sessionId, chatHistory);
  try {
    const result = await smartQuestionsLoader.run(requestKey, () => (
      generateSmartQuestions(
        chatHistory,
        userStore.id,
        userStore.name,
        sessionId
      )
    ));
    if (
      result.status === 'applied'
      && currentSessionId.value === sessionId
      && smartQuestionRequestKey(sessionId, history.value) === requestKey
    ) {
      smartQuestions.value = result.value?.data || [];
    }
  } catch (err) {
    if (currentSessionId.value === sessionId) {
      console.error('生成智能快捷提问失败:', err);
    }
  }
};

// 会话管理相关状态
const sessions = ref([]);
const currentSessionId = ref('');
const renameDialogVisible = ref(false);
const newSessionName = ref('');
const currentEditingSession = ref(null);

// 获取当前用户信息
const userStore = useUserStore();
const aiChatStore = useAiChatStore();
const currentDraft = computed(() => aiChatStore.draftFor(currentSessionId.value));
const loading = computed(() => ['streaming', 'resuming'].includes(currentDraft.value?.status));
const currentActivityLabel = computed(() => {
  const running = [...(currentDraft.value?.activities || [])]
    .reverse()
    .find(item => item.status === 'running');
  if (running) return `${running.label}...`;
  return currentDraft.value?.assistantContent ? '灵犀正在输入...' : '灵犀正在思考...';
});

const activityStatusText = status => ({
  running: '进行中',
  completed: '已完成',
  error: '未完成'
}[status] || '处理中');

const memoryPreferenceText = item => {
  const labels = {
    answer_length: { short: '简短回答', balanced: '均衡篇幅', detailed: '详细回答' },
    answer_structure: { conclusion_first: '结论优先', natural: '自然组织' },
    number_format: { two_decimals: '数字保留两位小数', adaptive: '数字格式自适应' }
  };
  return labels[item.preference]?.[item.value] || '回答偏好';
};

const actionDecisionText = action => ({
  pending: action.error ? '等待重试' : '等待确认',
  approved: '已批准',
  rejected: '已拒绝',
  failed: '已失效'
}[action.decision] || '处理中');

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
};

// 添加消息
const appendMessage = (isUser, content, id, attachments = []) => {
  const newMessage = {
    id: id || `${isUser ? 'user' : 'assistant'}-${Date.now()}-${Math.random()}`,
    isUser,
    content,
    time: dayjs().format('HH:mm'),
    createTime: new Date(),
    activities: [],
    citations: [],
    memorySaved: [],
    clarification: '',
    pendingAction: null,
    attachments
  };
  history.value.push(newMessage);
  scrollToBottom();
  return history.value[history.value.length - 1];
};

// 页面重新激活或切回会话时，把仍在后台生成的草稿接回当前消息列表。
const syncStreamDraft = () => {
  const draft = currentDraft.value;
  if (!draft) return;

  const hasEquivalentUserMessage = history.value.some(item => (
    item.isUser && item.content === draft.userContent
  ));
  if (!hasEquivalentUserMessage) {
    appendMessage(true, draft.userContent, draft.userMessageId, draft.attachments || []);
  }

  let assistantMessage = history.value.find(item => item.id === draft.assistantMessageId);
  if (!assistantMessage) {
    assistantMessage = appendMessage(false, draft.assistantContent, draft.assistantMessageId);
  } else {
    assistantMessage.content = draft.assistantContent;
  }
  assistantMessage.activities = draft.activities;
  assistantMessage.citations = draft.citations;
  assistantMessage.memorySaved = draft.memorySaved;
  assistantMessage.clarification = draft.clarification;
  assistantMessage.pendingAction = draft.pendingAction;
};

const formatFileSize = size => {
  const bytes = Number(size) || 0;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const openAttachmentPicker = () => {
  if (enableDataAnalysis.value) {
    ElMessage.warning('附件仅支持普通对话模式');
    return;
  }
  attachmentInput.value?.click();
};

const handleAttachmentFiles = async event => {
  const selected = Array.from(event.target?.files || []);
  if (attachmentInput.value) attachmentInput.value.value = '';
  if (!selected.length) return;
  if (!currentSessionId.value) {
    ElMessage.warning('请先创建或选择一个会话');
    return;
  }
  if (enableDataAnalysis.value) {
    ElMessage.warning('附件仅支持普通对话模式');
    return;
  }
  const available = maxAttachments - pendingAttachments.value.length;
  if (selected.length > available) {
    ElMessage.warning(`每条消息最多上传${maxAttachments}个附件`);
  }
  attachmentUploading.value = true;
  try {
    for (const file of selected.slice(0, available)) {
      if (file.size <= 0 || file.size > maxAttachmentBytes) {
        ElMessage.error(`${file.name}：文件需小于10MB`);
        continue;
      }
      try {
        const response = await uploadAiAttachment(file, currentSessionId.value);
        pendingAttachments.value.push({ ...response.data, removing: false });
      } catch (err) {
        ElMessage.error(`${file.name}：${err?.msg || err?.message || '上传失败'}`);
      }
    }
  } finally {
    attachmentUploading.value = false;
  }
};

const removePendingAttachment = async attachment => {
  if (!attachment || attachment.removing) return;
  attachment.removing = true;
  try {
    await deleteAiAttachment(attachment.attachmentId, currentSessionId.value);
    pendingAttachments.value = pendingAttachments.value.filter(
      item => item.attachmentId !== attachment.attachmentId
    );
  } catch (err) {
    attachment.removing = false;
    ElMessage.error('移除附件失败：' + (err?.msg || err?.message || '未知错误'));
  }
};

const discardPendingAttachments = async () => {
  const sessionId = currentSessionId.value;
  const attachments = [...pendingAttachments.value];
  pendingAttachments.value = [];
  await Promise.allSettled(
    attachments.map(item => deleteAiAttachment(item.attachmentId, sessionId))
  );
};

const toggleDataAnalysis = () => {
  if (!enableDataAnalysis.value && pendingAttachments.value.length) {
    ElMessage.warning('请先发送或移除附件，再切换数据分析模式');
    return;
  }
  enableDataAnalysis.value = !enableDataAnalysis.value;
};

const handleActionDecision = async (item, decision) => {
  const action = item.pendingAction;
  const sessionId = currentSessionId.value;
  if (!action || !sessionId || action.decision !== 'pending' || action.submitting) return;
  if (decision === 'approve' && !action.description?.trim()) {
    ElMessage.warning('请填写工单描述');
    return;
  }
  try {
    await aiChatStore.decideAction({
      sessionId,
      actionId: action.action_id,
      decision,
      description: action.description?.trim()
    });
    syncStreamDraft();
    await nextTick();
    ElMessage.success(decision === 'approve' ? '维修工单已创建' : '已拒绝创建维修工单');
    aiChatStore.clearDraft(sessionId);
  } catch (err) {
    syncStreamDraft();
    if (aiChatStore.draftFor(sessionId)?.status === 'completed') {
      await nextTick();
      aiChatStore.clearDraft(sessionId);
    }
    ElMessage.error('操作失败：' + (err?.msg || err?.message || '请稍后重试'));
  }
};

const loadMemories = async () => {
  memoryLoading.value = true;
  try {
    const response = await getLongTermMemories();
    const data = response.data || {};
    memoryEnabled.value = Boolean(data.enabled);
    memoryForm.value = {
      answer_length: '',
      answer_structure: '',
      number_format: ''
    };
    for (const item of data.items || []) {
      if (Object.prototype.hasOwnProperty.call(memoryForm.value, item.preference)) {
        memoryForm.value[item.preference] = item.value;
      }
    }
  } catch (err) {
    memoryEnabled.value = false;
    ElMessage.error('获取回答偏好失败：' + (err?.msg || err?.message || '未知错误'));
  } finally {
    memoryLoading.value = false;
  }
};

const openMemoryDialog = async () => {
  memoryDialogVisible.value = true;
  await loadMemories();
};

const saveMemories = async () => {
  const entries = Object.entries(memoryForm.value).filter(([, value]) => value);
  if (!entries.length) {
    ElMessage.info('请选择至少一项偏好');
    return;
  }
  memorySaving.value = true;
  try {
    await Promise.all(
      entries.map(([preference, value]) => updateLongTermPreference(preference, value))
    );
    ElMessage.success('回答偏好已保存');
    await loadMemories();
  } catch (err) {
    ElMessage.error('保存回答偏好失败：' + (err?.msg || err?.message || '未知错误'));
  } finally {
    memorySaving.value = false;
  }
};

const clearMemories = async () => {
  try {
    await ElMessageBox.confirm(
      '清空后，新对话将不再应用这些回答偏好。',
      '清空回答偏好',
      {
        confirmButtonText: '清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    memorySaving.value = true;
    await clearLongTermMemories();
    memoryForm.value = {
      answer_length: '',
      answer_structure: '',
      number_format: ''
    };
    ElMessage.success('回答偏好已清空');
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error('清空回答偏好失败：' + (err?.msg || err?.message || '未知错误'));
    }
  } finally {
    memorySaving.value = false;
  }
};

// 发送消息
const sendMessage = async () => {
  const content = message.value.trim();
  const attachments = pendingAttachments.value.map(item => ({ ...item, removing: false }));
  if (!content && !attachments.length) {
    ElMessage.warning('请输入内容或上传附件');
    return;
  }
  if (enableDataAnalysis.value && attachments.length) {
    ElMessage.warning('附件仅支持普通对话模式');
    return;
  }
  if (loading.value) {
    ElMessage.warning('数据正在处理，请勿重复提交');
    return;
  }
  if (currentDraft.value?.status === 'awaiting_approval') {
    ElMessage.warning('请先处理当前消息中的待确认操作');
    return;
  }
  
  const sessionId = currentSessionId.value;
  if (!sessionId) {
    ElMessage.warning('请先创建或选择一个会话');
    return;
  }

  const messageKey = `${Date.now()}-${Math.random()}`;
  const userMessageId = `user-stream-${messageKey}`;
  const assistantMessageId = `assistant-stream-${messageKey}`;
  const submittedContent = content || '请分析我上传的附件。';

  error.value = '';
  appendMessage(true, submittedContent, userMessageId, attachments);
  appendMessage(false, '', assistantMessageId);
  message.value = '';
  pendingAttachments.value = [];

  let completed = false;
  try {
    await aiChatStore.streamMessage({
      sessionId,
      message: submittedContent,
      userId: userStore.id,
      userName: userStore.name,
      dataAnalysis: enableDataAnalysis.value,
      attachments,
      userMessageId,
      assistantMessageId
    });
    completed = aiChatStore.draftFor(sessionId)?.status === 'completed';
  } catch (err) {
    error.value = err?.msg || err?.message || '发送失败，请稍后重试';
    ElMessage.error('发送失败：' + error.value);
    const assistantIndex = history.value.findIndex(item => item.id === assistantMessageId);
    if (assistantIndex !== -1 && !history.value[assistantIndex].content) {
      history.value.splice(assistantIndex, 1);
    }
  } finally {
    // 先让订阅草稿的页面实例完成最后一次渲染，再释放全局流状态。
    await nextTick();
    if (aiChatStore.draftFor(sessionId)?.status !== 'awaiting_approval') {
      aiChatStore.clearDraft(sessionId);
    }
    // 生成智能快捷提问
    if (completed && currentSessionId.value === sessionId && history.value.length > 0) {
      void refreshSmartQuestions(sessionId, history.value);
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
    } else {
      const selectedSession = sessions.value.find(
        session => session.sessionId === currentSessionId.value
      ) || sessions.value[0];
      await switchSession(selectedSession);
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
      syncStreamDraft();
      scrollToBottom();
      // 生成智能快捷提问
      if (!loading.value) {
        void refreshSmartQuestions(currentSessionId.value, history.value);
      }
    } else {
      history.value = [];
      smartQuestions.value = [];
      syncStreamDraft();
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
    await switchSession(newSession);
    ElMessage.success('新会话已创建');
  } catch (err) {
    console.error('创建会话失败:', err);
    ElMessage.error('创建会话失败: ' + (err?.msg || err?.message || '未知错误'));
  }
};

// 切换会话
const switchSession = async (session) => {
  if (
    pendingAttachments.value.length
    && currentSessionId.value
    && currentSessionId.value !== session.sessionId
  ) {
    await discardPendingAttachments();
  }
  // 先清空快捷提问，确保切换过程中不显示残留内容
  smartQuestionsLoader.invalidate();
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
  if (['streaming', 'resuming', 'awaiting_approval'].includes(
    aiChatStore.draftFor(session.sessionId)?.status
  )) {
    ElMessage.warning('该会话正在生成回答，请等待完成后再删除');
    return;
  }

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

watch(currentDraft, () => {
  syncStreamDraft();
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

onBeforeUnmount(() => {
  if (pendingAttachments.value.length) {
    void discardPendingAttachments();
  }
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

      .memory-btn {
        margin-left: auto;
        color: #0f766e;
        background: #ecfdf5;
        border: 1px solid #ccfbf1;

        &:hover,
        &:focus-visible {
          color: #164e63;
          background: #dff7f1;
        }
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

    .agent-work-trace {
      margin: 12px 0 14px;
      padding: 9px 10px;
      border: 1px solid #dce8e7;
      border-left: 3px solid #0f766e;
      border-radius: 8px;
      background: #f3faf8;
    }

    .work-trace-row {
      display: grid;
      grid-template-columns: 10px minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 8px;
      min-height: 26px;
      color: #365b5a;
      font-size: 12px;

      & + .work-trace-row {
        border-top: 1px solid #deebe9;
      }

      .status-lamp {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: #0d9488;
        box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.12);
      }

      &.is-running .status-lamp {
        animation: status-pulse 1.4s ease-in-out infinite;
      }

      &.is-completed .status-lamp {
        background: #22c55e;
        box-shadow: none;
      }

      &.is-error .status-lamp {
        background: #ef4444;
        box-shadow: none;
      }

      .work-label {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-weight: 600;
      }

      .work-count,
      .work-status {
        color: #68817f;
        font-variant-numeric: tabular-nums;
      }
    }

    .approval-card {
      margin: 14px 0;
      padding: 16px;
      border: 1px solid #f0c36a;
      border-left: 4px solid #d97706;
      border-radius: 12px;
      background: #fffbeb;

      &.is-approved {
        border-color: #86cbb4;
        border-left-color: #059669;
        background: #f0fdf4;
      }

      &.is-rejected {
        border-color: #cbd5e1;
        border-left-color: #64748b;
        background: #f8fafc;
      }

      .approval-card-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 16px;
        margin-bottom: 14px;

        h4 {
          margin: 2px 0 0;
          color: #1e293b;
          font-size: 15px;
        }
      }

      .approval-eyebrow {
        color: #92400e;
        font-size: 11px;
        font-weight: 700;
        letter-spacing: 0.08em;
      }

      .approval-state {
        flex: none;
        padding: 4px 8px;
        color: #92400e;
        background: rgba(245, 158, 11, 0.12);
        border-radius: 999px;
        font-size: 11px;
        font-weight: 700;
      }

      .approval-facts {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 10px;
        margin: 0 0 14px;

        div {
          padding: 9px 10px;
          background: rgba(255, 255, 255, 0.68);
          border: 1px solid rgba(148, 163, 184, 0.22);
          border-radius: 8px;
        }

        dt {
          color: #64748b;
          font-size: 11px;
        }

        dd {
          margin: 3px 0 0;
          color: #0f172a;
          font-size: 13px;
          font-weight: 650;
        }
      }

      .approval-description-label {
        display: block;
        margin-bottom: 6px;
        color: #475569;
        font-size: 12px;
        font-weight: 650;
      }

      .approval-impact {
        margin: 10px 0 0;
        color: #64748b;
        font-size: 12px;
        line-height: 1.55;
      }

      .approval-error,
      .approval-result {
        margin: 10px 0 0;
        color: #b91c1c;
        font-size: 12px;
        font-weight: 650;
      }

      .approval-result {
        color: #047857;

        &.is-rejected {
          color: #475569;
        }
      }

      .approval-actions {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin-top: 14px;
      }
    }

    .citation-strip {
      margin: 12px 0 14px;
      padding-top: 10px;
      border-top: 1px solid #e2e8f0;

      .citation-heading {
        margin-bottom: 7px;
        color: #64748b;
        font-size: 11px;
        font-weight: 700;
        letter-spacing: 0.08em;
      }

      .citation-list {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }

      .citation-chip {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        max-width: 100%;
        padding: 5px 8px;
        color: #0f5f5a;
        background: #ecfdf5;
        border: 1px solid #ccfbf1;
        border-radius: 6px;
        font-size: 12px;

        small {
          overflow: hidden;
          max-width: 180px;
          color: #64748b;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    .memory-saved-note {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 10px 0 12px;
      color: #0f766e;
      font-size: 12px;
      font-weight: 600;
    }
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

    .message-attachments {
      display: grid;
      gap: 8px;
      margin-bottom: 10px;
    }

    .message-attachment {
      display: flex;
      align-items: center;
      gap: 9px;
      min-width: 220px;
      max-width: 360px;
      padding: 8px;
      color: #f8fafc;
      background: rgba(255, 255, 255, 0.12);
      border: 1px solid rgba(255, 255, 255, 0.18);
      border-radius: 10px;
      text-align: left;
      text-decoration: none;

      &:hover {
        color: white;
        background: rgba(255, 255, 255, 0.18);
        text-decoration: none;
      }

      img {
        width: 52px;
        height: 52px;
        margin: 0;
        border-radius: 8px;
        object-fit: cover;
      }

      .attachment-file-icon {
        display: grid;
        flex: none;
        width: 38px;
        height: 38px;
        place-items: center;
        background: rgba(255, 255, 255, 0.14);
        border-radius: 9px;
        font-size: 20px;
      }

      .attachment-meta {
        display: flex;
        min-width: 0;
        flex: 1;
        flex-direction: column;

        strong {
          overflow: hidden;
          font-size: 12px;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        small {
          margin-top: 2px;
          color: rgba(255, 255, 255, 0.7);
          font-size: 10px;
        }
      }
    }
    
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
  max-width: 320px;
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
    .attachment-input {
      display: none;
    }

    .pending-attachments {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
      margin-bottom: 10px;
    }

    .pending-attachment {
      display: flex;
      align-items: center;
      gap: 9px;
      min-width: 0;
      padding: 8px 9px;
      background: #f0fdfa;
      border: 1px solid #ccfbf1;
      border-radius: 10px;

      img,
      .pending-file-icon {
        width: 38px;
        height: 38px;
        flex: none;
        border-radius: 8px;
      }

      img {
        object-fit: cover;
      }

      .pending-file-icon {
        display: grid;
        color: #0f766e;
        background: #ccfbf1;
        place-items: center;
        font-size: 19px;
      }

      .pending-file-meta {
        display: flex;
        min-width: 0;
        flex: 1;
        flex-direction: column;

        strong {
          overflow: hidden;
          color: #134e4a;
          font-size: 12px;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        small {
          margin-top: 2px;
          color: #64748b;
          font-size: 10px;
        }
      }
    }

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

      .attachment-btn {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 10px 14px;
        color: #475569;
        background: white;
        border: 1px solid #e2e8f0;
        border-radius: 12px;

        &:hover:not(:disabled) {
          color: #0f766e;
          background: #f0fdfa;
          border-color: #99f6e4;
        }

        small {
          color: #0f766e;
          font-size: 10px;
          font-weight: 700;
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

.memory-dialog {
  min-height: 220px;

  .memory-intro {
    margin: 0 0 18px;
    color: #64748b;
    font-size: 13px;
    line-height: 1.65;
  }

  .memory-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 2px 14px;

    :deep(.el-form-item:last-child) {
      grid-column: 1 / -1;
    }

    :deep(.el-select) {
      width: 100%;
    }
  }
}

.memory-footer {
  display: flex;
  width: 100%;
  align-items: center;

  .footer-spacer {
    flex: 1;
  }
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

@keyframes status-pulse {
  0%, 100% { transform: scale(0.82); opacity: 0.65; }
  50% { transform: scale(1); opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .welcome-icon,
  .typing-dot,
  .status-lamp {
    animation: none !important;
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
    .attachment-btn,
    .send-btn {
      flex: 1;
      min-width: 120px;
      justify-content: center;
    }
  }

  .pending-attachments {
    grid-template-columns: 1fr !important;
  }

  .memory-dialog .memory-form {
    grid-template-columns: 1fr;

    :deep(.el-form-item:last-child) {
      grid-column: auto;
    }
  }
}
</style>
