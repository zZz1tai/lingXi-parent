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
        <!-- 会话头部 -->
        <div class="chat-header">
          <div class="chat-header-info">
            <span class="chat-header-icon">
              <el-icon><ChatDotRound /></el-icon>
            </span>
            <span class="chat-header-copy">
              <span class="chat-header-title">{{ currentSessionName }}</span>
              <span class="chat-header-meta">灵犀助手 · 对话内容自动保存</span>
            </span>
          </div>
        </div>
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
                    <div v-if="item.error" class="message-error">
                      <el-icon><WarningFilled /></el-icon>
                      <span>{{ item.error }}</span>
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

                <el-button
                  v-hasPermi="['aivideo:project:edit']"
                  type="default"
                  class="video-btn"
                  :class="{ 'active-mode': quickVideoDialogVisible }"
                  title="使用画面描述和多张参考图生成视频"
                  @click="openQuickVideoDialog"
                >
                  <el-icon><VideoCamera /></el-icon>
                  <span>AI 视频</span>
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

                <span class="input-shortcut">Enter 发送 · Shift + Enter 换行</span>
                
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
          已保存的偏好如需恢复默认，可将该项选为"保持默认"；全部重置请使用"清空偏好"。
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
            <el-select v-model="memoryForm.answer_length" placeholder="保持默认" clearable>
              <el-option label="保持默认" value="" />
              <el-option label="简短" value="short" />
              <el-option label="均衡" value="balanced" />
              <el-option label="详细" value="detailed" />
            </el-select>
          </el-form-item>
          <el-form-item label="回答结构">
            <el-select v-model="memoryForm.answer_structure" placeholder="保持默认" clearable>
              <el-option label="保持默认" value="" />
              <el-option label="结论优先" value="conclusion_first" />
              <el-option label="自然组织" value="natural" />
            </el-select>
          </el-form-item>
          <el-form-item label="数字格式">
            <el-select v-model="memoryForm.number_format" placeholder="保持默认" clearable>
              <el-option label="保持默认" value="" />
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

    <el-dialog
      v-model="quickVideoDialogVisible"
      width="min(680px, calc(100vw - 28px))"
      class="quick-video-dialog"
      :close-on-click-modal="!quickVideoSubmitting"
      :close-on-press-escape="!quickVideoSubmitting"
      @closed="handleQuickVideoDialogClosed"
    >
      <template #header>
        <div class="quick-video-dialog-heading">
          <span class="quick-video-dialog-icon">
            <el-icon><VideoCamera /></el-icon>
          </span>
          <span>
            <strong>AI 视频快创</strong>
            <small>用一段描述和参考画面锁定镜头</small>
          </span>
        </div>
      </template>

      <div v-if="!quickVideoTask" class="quick-video-composer">
        <section class="quick-video-section">
          <div class="quick-video-label-row">
            <label for="quick-video-prompt">画面描述</label>
            <span>{{ quickVideoPrompt.length }}/2500</span>
          </div>
          <el-input
            id="quick-video-prompt"
            v-model="quickVideoPrompt"
            type="textarea"
            :rows="4"
            maxlength="2500"
            resize="none"
            placeholder="例如：雨后的城市天台，女孩回头望向镜头，风吹动衣角，镜头缓慢推进，电影感光影"
          />
        </section>

        <section class="quick-video-section">
          <div class="quick-video-label-row">
            <label>视频时长</label>
            <span>生成模型会自动匹配最接近的可用档位</span>
          </div>
          <div class="duration-strip" role="radiogroup" aria-label="视频时长">
            <button
              v-for="duration in quickVideoDurations"
              :key="duration.value"
              type="button"
              :class="{ active: quickVideoDurationMs === duration.value }"
              :aria-pressed="quickVideoDurationMs === duration.value"
              @click="quickVideoDurationMs = duration.value"
            >
              <strong>{{ duration.seconds }}</strong>
              <small>秒</small>
              <span>{{ duration.hint }}</span>
            </button>
          </div>
        </section>

        <section class="quick-video-section">
          <div class="quick-video-label-row">
            <label>参考画面</label>
            <span>第 1 张作为起始帧 · 最多 5 张</span>
          </div>
          <input
            ref="quickVideoImageInput"
            class="quick-video-file-input"
            type="file"
            multiple
            accept=".png,.jpg,.jpeg,image/png,image/jpeg"
            @change="handleQuickVideoImages"
          />
          <div class="reference-filmstrip">
            <article
              v-for="(image, index) in quickVideoImages"
              :key="image.id"
              class="reference-frame"
            >
              <img :src="image.previewUrl" :alt="image.name" />
              <span class="frame-index">{{ index === 0 ? '首帧' : `参考 ${index}` }}</span>
              <button
                type="button"
                class="remove-reference"
                :aria-label="`移除 ${image.name}`"
                @click="removeQuickVideoImage(image.id)"
              >
                <el-icon><Close /></el-icon>
              </button>
              <strong :title="image.name">{{ image.name }}</strong>
            </article>
            <button
              v-if="quickVideoImages.length < maxQuickVideoImages"
              type="button"
              class="add-reference-frame"
              @click="openQuickVideoImagePicker"
            >
              <span class="add-reference-icon"><el-icon><Picture /></el-icon></span>
              <strong>{{ quickVideoImages.length ? '继续添加' : '添加参考图' }}</strong>
              <small>PNG / JPG · 单张 10MB</small>
            </button>
          </div>
          <p class="quick-video-reference-note">
            可分多次添加。建议首图使用清晰的主体构图，其余图片补充人物、服装、场景或美术风格。
          </p>
        </section>

        <div class="quick-video-cost-note">
          <el-icon><WarningFilled /></el-icon>
          <span>点击生成后会提交模型任务并产生费用；任务提交期间请勿重复操作。</span>
        </div>
      </div>

      <div v-else class="quick-video-task-panel" :class="`is-${quickVideoTaskTone}`">
        <div
          class="quick-video-progress"
          :style="{ '--quick-video-progress': `${quickVideoProgress * 3.6}deg` }"
          aria-hidden="true"
        >
          <span><strong>{{ quickVideoProgress }}</strong>%</span>
        </div>
        <div class="quick-video-task-copy">
          <span class="quick-video-task-eyebrow">{{ quickVideoTaskEyebrow }}</span>
          <h3>{{ quickVideoTaskTitle }}</h3>
          <p>{{ quickVideoTaskDescription }}</p>
          <small>任务 {{ quickVideoTask.taskId }} · {{ formatQuickVideoDuration(quickVideoTask.durationMs || quickVideoDurationMs) }}</small>
        </div>
        <video
          v-if="quickVideoTask.videoUrl"
          class="quick-video-result"
          :src="quickVideoTask.videoUrl"
          controls
          playsinline
          preload="metadata"
        />
        <a
          v-if="quickVideoTask.videoUrl"
          class="quick-video-open-result"
          :href="quickVideoTask.videoUrl"
          target="_blank"
          rel="noopener noreferrer"
        >
          在新窗口查看视频
        </a>
      </div>

      <template #footer>
        <div class="quick-video-footer">
          <el-button @click="quickVideoDialogVisible = false">关闭</el-button>
          <el-button
            v-if="quickVideoTask && quickVideoCanStartAnother"
            @click="resetQuickVideoComposer"
          >
            再生成一个
          </el-button>
          <el-button
            v-if="!quickVideoTask"
            type="primary"
            class="quick-video-submit"
            :loading="quickVideoSubmitting"
            :disabled="!quickVideoReady"
            @click="submitQuickVideoTask"
          >
            <el-icon v-if="!quickVideoSubmitting"><VideoCamera /></el-icon>
            生成视频
          </el-button>
        </div>
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
  Paperclip,
  WarningFilled,
  VideoCamera,
  Picture
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
import {
  getQuickAiVideoStatus,
  submitQuickAiVideo
} from '@/api/aiVedio/project';
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

// 转换markdown为html（带内存缓存，切会话/重复渲染时避免反复解析）
const markdownCache = new Map();
const MARKDOWN_CACHE_LIMIT = 120;
const renderMarkdown = (content) => {
  if (!content) return '';
  if (markdownCache.has(content)) return markdownCache.get(content);
  if (markdownCache.size >= MARKDOWN_CACHE_LIMIT) markdownCache.clear();
  // 清理多余的空格和换行
  const cleanedContent = content
    .replace(/\n{3,}/g, '\n\n') // 将3个以上连续换行替换为2个
    .trim();
  const html = marked.parse(cleanedContent);
  markdownCache.set(content, html);
  return html;
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
const quickVideoDialogVisible = ref(false);
const quickVideoPrompt = ref('');
const quickVideoDurationMs = ref(5000);
const quickVideoImages = ref([]);
const quickVideoImageInput = ref(null);
const quickVideoSubmitting = ref(false);
const quickVideoTask = ref(null);
const maxQuickVideoImages = 5;
const maxQuickVideoImageBytes = 10 * 1024 * 1024;
const quickVideoDurations = [
  { value: 5000, seconds: 5, hint: '片刻' },
  { value: 10000, seconds: 10, hint: '标准' },
  { value: 15000, seconds: 15, hint: '完整' }
];
const quickVideoTerminalStatuses = new Set(['SUCCEEDED', 'FAILED', 'CANCELED', 'NEEDS_REVIEW']);
let quickVideoPollTimer = null;
let quickVideoPollGeneration = 0;
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
const currentSessionName = computed(() => {
  const session = sessions.value.find(item => item.sessionId === currentSessionId.value);
  return session?.sessionName || '新的对话';
});
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

const quickVideoReady = computed(() => (
  !quickVideoSubmitting.value
  && quickVideoPrompt.value.trim().length > 0
  && quickVideoImages.value.length > 0
));

const quickVideoProgress = computed(() => {
  const value = Number(quickVideoTask.value?.progress);
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(100, Math.round(value)));
});

const quickVideoTaskMeta = computed(() => ({
  QUEUED: {
    tone: 'working',
    eyebrow: '任务已排队',
    title: '正在等待生成资源',
    description: '参考画面已安全转存，后台即将开始生成。'
  },
  WAITING_CALLBACK: {
    tone: 'working',
    eyebrow: '模型已受理',
    title: '视频正在生成',
    description: '可以关闭窗口继续对话，重新打开后仍可查看本次进度。'
  },
  RUNNING: {
    tone: 'working',
    eyebrow: '生成进行中',
    title: '正在合成画面与运动',
    description: '后台正在检查供应商结果并转存视频文件。'
  },
  SUCCEEDED: {
    tone: 'success',
    eyebrow: '生成完成',
    title: '你的视频已经准备好',
    description: '结果已转存，可直接播放或在新窗口查看。'
  },
  FAILED: {
    tone: 'danger',
    eyebrow: '生成未完成',
    title: '这次视频生成失败',
    description: cleanQuickVideoError(quickVideoTask.value?.errorMessage) || '请检查参考图和画面描述后再试一次。'
  },
  CANCELED: {
    tone: 'muted',
    eyebrow: '任务已取消',
    title: '未生成视频',
    description: '可以返回编辑画面描述并重新提交。'
  },
  NEEDS_REVIEW: {
    tone: 'warning',
    eyebrow: '等待人工核对',
    title: '供应商提交结果暂不确定',
    description: '请勿重复提交本次任务，管理员核对供应商任务后会更新状态。'
  }
}[quickVideoTask.value?.status] || {
  tone: 'working',
  eyebrow: '任务处理中',
  title: '正在准备视频',
  description: '参考画面和生成参数正在交给后台处理。'
}));

const quickVideoTaskTone = computed(() => quickVideoTaskMeta.value.tone);
const quickVideoTaskEyebrow = computed(() => quickVideoTaskMeta.value.eyebrow);
const quickVideoTaskTitle = computed(() => quickVideoTaskMeta.value.title);
const quickVideoTaskDescription = computed(() => quickVideoTaskMeta.value.description);
const quickVideoCanStartAnother = computed(() => (
  ['SUCCEEDED', 'FAILED', 'CANCELED'].includes(quickVideoTask.value?.status)
));

// 判断是否贴近底部（用于流式输出时避免打断用户上翻）
const SCROLL_NEAR_BOTTOM_OFFSET = 80;
const isNearBottom = () => {
  const el = chatContainer.value;
  if (!el) return true;
  return el.scrollHeight - el.scrollTop - el.clientHeight < SCROLL_NEAR_BOTTOM_OFFSET;
};

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight;
    }
  });
};

// 仅在贴近底部时才跟随滚动（流式输出期间若用户上翻则不去打扰）
const smartScrollToBottom = () => {
  nextTick(() => {
    const el = chatContainer.value;
    if (el && isNearBottom()) {
      el.scrollTop = el.scrollHeight;
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
    error: '',
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
  assistantMessage.error = draft.error;
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

function openQuickVideoDialog() {
  if (!quickVideoTask.value && !quickVideoPrompt.value.trim() && message.value.trim()) {
    quickVideoPrompt.value = message.value.trim();
  }
  quickVideoDialogVisible.value = true;
  if (quickVideoTask.value && !quickVideoTerminalStatuses.has(quickVideoTask.value.status)) {
    startQuickVideoPolling();
  }
}

function openQuickVideoImagePicker() {
  quickVideoImageInput.value?.click();
}

function handleQuickVideoImages(event) {
  const selected = Array.from(event.target?.files || []);
  if (quickVideoImageInput.value) quickVideoImageInput.value.value = '';
  if (!selected.length) return;
  const available = maxQuickVideoImages - quickVideoImages.value.length;
  if (selected.length > available) {
    ElMessage.warning(`最多添加${maxQuickVideoImages}张参考图片`);
  }

  const existingSignatures = new Set(quickVideoImages.value.map(item => item.signature));
  const additions = [];
  selected.slice(0, available).forEach(file => {
    const supported = ['image/png', 'image/jpeg'].includes(file.type)
      || /\.(png|jpe?g)$/i.test(file.name || '');
    if (!supported) {
      ElMessage.error(`${file.name || '图片'}：仅支持 PNG 或 JPG`);
      return;
    }
    if (file.size <= 0 || file.size > maxQuickVideoImageBytes) {
      ElMessage.error(`${file.name || '图片'}：单张图片需小于10MB`);
      return;
    }
    const signature = `${file.name}:${file.size}:${file.lastModified}`;
    if (existingSignatures.has(signature)) {
      ElMessage.info(`${file.name} 已添加`);
      return;
    }
    existingSignatures.add(signature);
    additions.push({
      id: `quick-video-image-${Date.now()}-${Math.random()}`,
      file,
      name: file.name,
      size: file.size,
      signature,
      previewUrl: URL.createObjectURL(file)
    });
  });
  quickVideoImages.value = [...quickVideoImages.value, ...additions];
}

function removeQuickVideoImage(imageId) {
  const target = quickVideoImages.value.find(item => item.id === imageId);
  if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl);
  quickVideoImages.value = quickVideoImages.value.filter(item => item.id !== imageId);
}

function clearQuickVideoImages() {
  quickVideoImages.value.forEach(item => {
    if (item.previewUrl) URL.revokeObjectURL(item.previewUrl);
  });
  quickVideoImages.value = [];
  if (quickVideoImageInput.value) quickVideoImageInput.value.value = '';
}

function resetQuickVideoComposer() {
  stopQuickVideoPolling();
  quickVideoTask.value = null;
  quickVideoSubmitting.value = false;
  clearQuickVideoImages();
}

function handleQuickVideoDialogClosed() {
  stopQuickVideoPolling();
}

async function submitQuickVideoTask() {
  if (!quickVideoReady.value) return;
  quickVideoSubmitting.value = true;
  try {
    const response = await submitQuickAiVideo({
      prompt: quickVideoPrompt.value.trim(),
      durationMs: quickVideoDurationMs.value,
      images: quickVideoImages.value.map(item => item.file)
    });
    const data = response.data || {};
    if (!data.projectId || !data.videoAssetId || !data.taskId) {
      throw new Error('后端未返回完整的视频任务信息');
    }
    quickVideoTask.value = {
      ...data,
      status: data.status || 'WAITING_CALLBACK',
      progress: Number.isFinite(Number(data.progress)) ? Number(data.progress) : 20,
      durationMs: quickVideoDurationMs.value,
      videoUrl: ''
    };
    if (quickVideoTask.value.status === 'NEEDS_REVIEW') {
      ElMessage.warning('供应商提交结果待核对，请勿重复生成');
    } else {
      ElMessage.success('视频任务已提交，可以继续对话');
    }
    startQuickVideoPolling();
  } catch (err) {
    ElMessage.error('视频任务提交失败：' + (err?.msg || err?.message || '请稍后重试'));
  } finally {
    quickVideoSubmitting.value = false;
  }
}

function startQuickVideoPolling() {
  stopQuickVideoPolling();
  const generation = quickVideoPollGeneration;
  void pollQuickVideoTask(generation);
}

function stopQuickVideoPolling() {
  quickVideoPollGeneration += 1;
  if (quickVideoPollTimer) {
    clearTimeout(quickVideoPollTimer);
    quickVideoPollTimer = null;
  }
}

async function pollQuickVideoTask(generation) {
  if (
    generation !== quickVideoPollGeneration
    || !quickVideoDialogVisible.value
    || !quickVideoTask.value?.projectId
    || !quickVideoTask.value?.taskId
  ) return;

  let shouldContinue = true;
  try {
    const previousStatus = quickVideoTask.value.status;
    const response = await getQuickAiVideoStatus(
      quickVideoTask.value.projectId,
      quickVideoTask.value.taskId
    );
    if (generation !== quickVideoPollGeneration) return;
    const status = response.data || {};
    quickVideoTask.value = { ...quickVideoTask.value, ...status };
    shouldContinue = !quickVideoTerminalStatuses.has(status.status);
    if (status.status === 'SUCCEEDED' && previousStatus !== 'SUCCEEDED') {
      ElMessage.success('AI 视频生成完成');
    } else if (status.status === 'FAILED' && previousStatus !== 'FAILED') {
      ElMessage.error(cleanQuickVideoError(status.errorMessage) || 'AI 视频生成失败');
    } else if (status.status === 'NEEDS_REVIEW' && previousStatus !== 'NEEDS_REVIEW') {
      ElMessage.warning('供应商提交结果待核对，请勿重复生成');
    }
  } catch (err) {
    console.warn('查询快速视频任务失败:', err);
  }

  if (
    shouldContinue
    && generation === quickVideoPollGeneration
    && quickVideoDialogVisible.value
  ) {
    quickVideoPollTimer = setTimeout(() => pollQuickVideoTask(generation), 4000);
  }
}

function cleanQuickVideoError(errorMessage) {
  return String(errorMessage || '').replace(/^retryable=(true|false)\s*\|\s*/i, '').trim();
}

function formatQuickVideoDuration(durationMs) {
  const seconds = Math.max(1, Math.round((Number(durationMs) || 0) / 1000));
  return `${seconds} 秒`;
}

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
    // 审批续流完成后刷新快捷提问
    if (currentSessionId.value === sessionId && history.value.length > 0) {
      void refreshSmartQuestions(sessionId, history.value);
    }
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
    if (assistantIndex === -1) return;
    if (!history.value[assistantIndex].content) {
      history.value.splice(assistantIndex, 1);
    } else {
      // 保留半截内容，但在气泡内标注失败原因
      history.value[assistantIndex].error = error.value;
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
  // 中文输入法组词确认的回车不应触发发送
  if (event.isComposing || event.keyCode === 229) return;
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
      await createNewSession(true);
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

// 历史请求序号：快速切换会话时丢弃过期响应，避免乱序覆盖
let historyRequestSeq = 0;

// 加载对话历史记录
const loadChatHistory = async () => {
  if (!currentSessionId.value) return;
  const sessionId = currentSessionId.value;
  const seq = ++historyRequestSeq;

  try {
    const res = await getChatHistory(sessionId, userStore.id);
    // 已切换到其它会话，丢弃过期结果
    if (seq !== historyRequestSeq || currentSessionId.value !== sessionId) return;
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
        void refreshSmartQuestions(sessionId, history.value);
      }
    } else {
      history.value = [];
      smartQuestions.value = [];
      syncStreamDraft();
    }
  } catch (err) {
    if (seq !== historyRequestSeq || currentSessionId.value !== sessionId) return;
    console.error('加载对话历史失败:', err);
    ElMessage.error('加载对话历史失败: ' + (err?.msg || err?.message || '未知错误'));
  }
};

// 创建新会话
const createNewSession = async (silent = false) => {
  try {
    const res = await createSession(userStore.id);
    const newSession = res.data;
    sessions.value.unshift(newSession);
    smartQuestions.value = [];
    await switchSession(newSession);
    if (!silent) ElMessage.success('新会话已创建');
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
      sessionId: currentEditingSession.value.sessionId,
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

// 监听消息变化，仅在贴近底部时自动滚动
watch(history, () => {
  smartScrollToBottom();
}, { deep: true });

watch(currentDraft, () => {
  syncStreamDraft();
  smartScrollToBottom();
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
  stopQuickVideoPolling();
  clearQuickVideoImages();
  if (pendingAttachments.value.length) {
    void discardPendingAttachments();
  }
});
</script>

<style scoped lang="scss">
@import '@/assets/styles/dialog-styles.scss';
.ai-chat-page {
  height: 100dvh;
  background:
    radial-gradient(900px 480px at 88% -12%, rgba(15, 118, 110, 0.12), transparent 62%),
    radial-gradient(760px 420px at -8% 112%, rgba(13, 148, 136, 0.09), transparent 60%),
    var(--lx-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-container {
  width: 100%;
  height: 100%;
  background: var(--lx-canvas);
  display: flex;
  overflow: hidden;
  position: relative;
}

/* 左侧会话栏 */
.session-sidebar {
  width: clamp(236px, 15vw, 260px);
  flex: 0 0 clamp(236px, 15vw, 260px);
  background: var(--lx-surface);
  border-right: 1px solid var(--lx-border-soft);
  display: flex;
  flex-direction: column;
  padding: 18px 12px 14px;
  position: relative;
  z-index: 1;

  .sidebar-header {
    margin-bottom: 18px;
    padding: 0 6px;

    .logo-area {
      display: flex;
      align-items: center;
      gap: 12px;

      .logo-icon {
        width: 38px;
        height: 38px;
        padding: 7px;
        border-radius: 12px;
        object-fit: contain;
        background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
        box-shadow: 0 6px 14px rgba(15, 118, 110, 0.22);
      }

      .logo-text {
        font-size: 17px;
        font-weight: 700;
        color: var(--lx-navy);
        margin: 0;
        letter-spacing: 0.01em;
      }

      .memory-btn {
        margin-left: auto;
        color: var(--lx-muted);
        transition: color 0.2s ease, background-color 0.2s ease;

        &:hover,
        &:focus-visible {
          color: var(--lx-primary);
          background: var(--lx-primary-soft);
        }
      }
    }
  }

  .new-chat-btn {
    height: 44px;
    margin: 0 6px 20px;
    border-radius: 12px;
    background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
    border: none;
    color: white;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    box-shadow: 0 6px 16px rgba(15, 118, 110, 0.28);
    transition: transform 0.25s ease, box-shadow 0.25s ease;

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 10px 22px rgba(15, 118, 110, 0.34);
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
      margin-bottom: 12px;
      padding: 0 10px;

      .list-title {
        font-size: 12px;
        font-weight: 700;
        color: var(--lx-muted);
        text-transform: uppercase;
        letter-spacing: 0.08em;
      }

      .list-count {
        background: var(--lx-canvas);
        color: var(--lx-muted);
        font-size: 11px;
        font-weight: 700;
        padding: 2px 8px;
        border-radius: 999px;
        font-variant-numeric: tabular-nums;
      }
    }

    .session-scroll-area {
      flex: 1;
      overflow-y: auto;
      padding: 2px 6px 6px;

      &::-webkit-scrollbar {
        width: 4px;
      }

      &::-webkit-scrollbar-track {
        background: transparent;
        border-radius: 4px;
      }

      &::-webkit-scrollbar-thumb {
        background: var(--lx-border);
        border-radius: 4px;
      }
    }

    .session-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 11px 12px;
      margin-bottom: 6px;
      border-radius: 12px;
      background: transparent;
      cursor: pointer;
      transition: background-color 0.2s ease, border-color 0.2s ease;
      border: 1px solid transparent;
      position: relative;

      &:hover {
        background: var(--lx-canvas);
        border-color: var(--lx-border-soft);

        .session-actions {
          opacity: 1;
        }
      }

      &.active {
        background: linear-gradient(135deg, rgba(15, 118, 110, 0.10), rgba(13, 148, 136, 0.06));
        border-color: color-mix(in srgb, var(--seed-primary) 22%, var(--seed-surface));

        .session-icon {
          background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
          color: white;
          box-shadow: 0 4px 10px rgba(15, 118, 110, 0.25);
        }

        .session-name {
          color: var(--lx-navy);
          font-weight: 700;
        }

        .session-time {
          color: var(--lx-muted);
        }
      }

      .session-icon {
        width: 34px;
        height: 34px;
        border-radius: 10px;
        background: var(--lx-canvas);
        display: flex;
        align-items: center;
        justify-content: center;
        color: var(--lx-muted);
        flex-shrink: 0;
        transition: all 0.2s ease;
      }

      .session-content {
        flex: 1;
        min-width: 0;

        .session-name {
          font-size: 13.5px;
          font-weight: 600;
          color: var(--lx-text);
          margin-bottom: 2px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .session-time {
          font-size: 11.5px;
          color: var(--lx-muted);
        }
      }

      .session-actions {
        opacity: 0;
        transition: opacity 0.2s ease;

        .more-btn {
          color: var(--lx-muted);
          padding: 4px;
          border-radius: 8px;

          &:hover {
            background: var(--lx-primary-soft);
            color: var(--lx-primary);
          }
        }
      }
    }

    @media (hover: none) {
      .session-actions {
        opacity: 1;
      }
    }
  }
}

/* 右侧聊天主区域 */
.chat-main {
  --chat-column-width: 880px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(680px 360px at 50% 0%, rgba(15, 118, 110, 0.045), transparent 72%),
    var(--lx-canvas);
  position: relative;
  min-width: 0;
}

.chat-header {
  flex: none;
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 0 clamp(24px, 3.2vw, 48px);
  border-bottom: 1px solid var(--lx-border-soft);
  background: color-mix(in srgb, var(--lx-surface) 88%, transparent);
  backdrop-filter: blur(12px);

  .chat-header-info {
    display: flex;
    align-items: center;
    gap: 10px;
    width: min(100%, var(--chat-column-width));
    margin: 0 auto;
    min-width: 0;
  }

  .chat-header-icon {
    display: grid;
    width: 28px;
    height: 28px;
    place-items: center;
    color: var(--lx-primary);
    background: var(--lx-primary-soft);
    border-radius: 9px;
    font-size: 15px;
  }

  .chat-header-copy {
    display: flex;
    min-width: 0;
    flex-direction: column;
    gap: 2px;
  }

  .chat-header-title {
    overflow: hidden;
    font-size: 14.5px;
    font-weight: 700;
    color: var(--lx-navy);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chat-header-meta {
    overflow: hidden;
    color: var(--lx-muted);
    font-size: 11.5px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.chat-messages-container {
  flex: 1;
  min-height: 0;
  padding: 32px clamp(24px, 3.2vw, 48px) 18px;
  overflow-y: auto;
  overflow-x: hidden;
  position: relative;
  scroll-padding-bottom: 32px;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
    border-radius: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--lx-border);
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
      position: relative;
      display: grid;
      width: 108px;
      height: 108px;
      margin: 0 auto 28px;
      place-items: center;
      border-radius: 32px;
      background: linear-gradient(135deg, var(--lx-primary-soft), rgba(13, 148, 136, 0.18));
      animation: float 3.2s ease-in-out infinite;

      &::after {
        content: '';
        position: absolute;
        inset: -16px;
        border-radius: 40px;
        background: radial-gradient(closest-side, rgba(15, 118, 110, 0.14), transparent 70%);
        z-index: -1;
      }

      .welcome-logo {
        width: 64px;
        height: 64px;
        object-fit: contain;
      }
    }

    .welcome-title {
      font-size: 34px;
      font-weight: 800;
      color: var(--lx-navy);
      margin: 0 0 12px;
      letter-spacing: -0.01em;
    }

    .welcome-subtitle {
      font-size: 15px;
      color: var(--lx-muted);
      margin: 0;
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
  width: min(100%, var(--chat-column-width));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 30px;
  padding-bottom: 24px;
}

/* 消息通用样式 */
.message-item {
  .message-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
    font-size: 13px;

    .message-sender {
      font-weight: 700;
      color: var(--lx-text);
    }

    .message-time {
      color: var(--lx-muted);
      font-size: 12px;
    }
  }

  .message-bubble {
    padding: 10px 14px;
    border-radius: 16px;
    max-width: min(680px, 100%);
    line-height: 1.5;
    font-size: 15px;

    .message-text {
      word-break: break-word;
      line-height: 1.6;
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
        background: #102a43;
        color: #e6edf3;
        padding: 16px 18px;
        border-radius: 12px;
        overflow-x: auto;
        margin: 14px 0;
        font-size: 13.5px;
        line-height: 1.55;
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);

        code {
          background: transparent;
          color: inherit;
          padding: 0;
          font-family: 'JetBrains Mono', 'Fira Code', 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
          line-height: 1.55;
        }
      }
      
      /* 内联代码 */
      code:not(pre code) {
        background: rgba(15, 118, 110, 0.10);
        color: #0f5f5a;
        padding: 2px 6px;
        border-radius: 6px;
        font-family: 'JetBrains Mono', 'Fira Code', 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        font-size: 13px;
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
          background: var(--lx-canvas);
          color: var(--lx-text);
          font-weight: 700;
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
        display: block;
        max-width: min(100%, 640px);
        height: auto;
        border-radius: 12px;
        margin: 12px 0;
      }
      
      /* 任务列表 */
      input[type="checkbox"] {
        margin-right: 8px;
        vertical-align: middle;
      }
    }

    :deep(.markdown-content img) {
      display: block;
      width: auto;
      max-width: min(100%, 520px);
      max-height: min(52vh, 520px);
      margin: 12px 0 2px;
      border: 1px solid var(--lx-border-soft);
      border-radius: 14px;
      background: var(--lx-canvas);
      box-shadow: 0 10px 28px rgba(18, 52, 59, 0.10);
      object-fit: contain;
    }
  }
}

/* 助手消息 */
.assistant-message-wrapper {
  display: flex;
  gap: 12px;

  > .message-content-wrapper {
    min-width: 0;
    max-width: calc(100% - 48px);
  }

  .message-avatar {
    .assistant-avatar {
      width: 36px;
      height: 36px;
      border-radius: 12px;
      background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
      border: none;
      box-shadow: 0 4px 12px rgba(15, 118, 110, 0.25);

      .avatar-img {
        width: 100%;
        height: 100%;
        object-fit: contain;
        padding: 5px;
        border: none;
        box-shadow: none;
        background: transparent;
      }
    }
  }

  .assistant-bubble {
    background: var(--lx-surface);
    border: 1px solid var(--lx-border-soft);
    border-radius: 4px 16px 16px 16px;
    color: var(--lx-text);
    box-shadow: var(--lx-shadow-sm);

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
      color: var(--lx-primary);
      font-size: 12px;
      font-weight: 600;
    }

    .message-error {
      display: flex;
      align-items: flex-start;
      gap: 6px;
      margin: 12px 0;
      padding: 9px 12px;
      color: #b91c1c;
      background: var(--lx-status-danger-bg);
      border: 1px solid rgba(193, 65, 79, 0.22);
      border-radius: 8px;
      font-size: 12.5px;
      line-height: 1.5;

      .el-icon {
        flex: none;
        margin-top: 1px;
      }
    }
  }
}

/* 用户消息 */
.user-message-wrapper {
  display: flex;
  justify-content: flex-end;
  gap: 12px;

  .user-wrapper {
    min-width: 0;
    max-width: min(72%, 620px);
    text-align: right;
  }

  .user-header {
    justify-content: flex-end;

    .message-sender {
      color: var(--lx-primary);
    }
  }

  .message-avatar {
    align-self: flex-start;
    padding-top: 26px;

    .user-avatar-icon {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      box-shadow: 0 4px 12px rgba(15, 118, 110, 0.25);
    }
  }

  .user-bubble {
    background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
    color: white;
    border-radius: 16px 16px 4px 16px;
    box-shadow: 0 4px 14px rgba(15, 118, 110, 0.18);

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
  padding: 14px 16px;
  background: var(--lx-surface);
  border: 1px solid var(--lx-border-soft);
  border-radius: 4px 16px 16px 16px;
  max-width: 320px;
  margin-right: auto;
  margin-left: 48px;
  box-shadow: var(--lx-shadow-sm);

  .typing-indicator {
    display: flex;
    gap: 4px;

    .typing-dot {
      width: 8px;
      height: 8px;
      background: var(--seed-primary);
      border-radius: 50%;
      animation: typing 1.4s infinite ease-in-out;

      &:nth-child(1) { animation-delay: 0s; }
      &:nth-child(2) { animation-delay: 0.2s; }
      &:nth-child(3) { animation-delay: 0.4s; }
    }
  }

  .typing-text {
    font-size: 14px;
    color: var(--lx-muted);
    font-weight: 500;
  }
}

/* 输入区域 */
.chat-input-container {
  flex: none;
  border-top: 0;
  background: linear-gradient(180deg, transparent 0%, color-mix(in srgb, var(--lx-canvas) 92%, var(--lx-surface)) 26%);
  padding: 12px clamp(24px, 3.2vw, 48px) 30px;
  position: relative;

  .input-wrapper {
    width: min(100%, var(--chat-column-width));
    margin: 0 auto;
  }

  .input-main {
    padding: 4px;
    background: var(--lx-surface);
    border: 1px solid var(--lx-border);
    border-radius: 18px;
    box-shadow: 0 14px 34px rgba(18, 52, 59, 0.12), 0 2px 8px rgba(18, 52, 59, 0.05);
    transition: border-color 0.25s ease, box-shadow 0.25s ease;

    &:focus-within {
      border-color: var(--seed-primary);
      box-shadow: 0 0 0 4px var(--lx-primary-glow), var(--lx-shadow-md);
    }

    .attachment-input {
      display: none;
    }

    .pending-attachments {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 8px;
      padding: 10px 10px 0;
    }

    .pending-attachment {
      display: flex;
      align-items: center;
      gap: 9px;
      min-width: 0;
      padding: 7px 8px;
      background: var(--lx-primary-soft);
      border: 1px solid color-mix(in srgb, var(--seed-primary) 16%, var(--seed-surface));
      border-radius: 10px;

      img,
      .pending-file-icon {
        width: 34px;
        height: 34px;
        flex: none;
        border-radius: 8px;
      }

      img {
        object-fit: cover;
      }

      .pending-file-icon {
        display: grid;
        color: var(--lx-primary);
        background: var(--lx-surface);
        place-items: center;
        font-size: 17px;
      }

      .pending-file-meta {
        display: flex;
        min-width: 0;
        flex: 1;
        flex-direction: column;

        strong {
          overflow: hidden;
          color: var(--lx-text);
          font-size: 12px;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        small {
          margin-top: 2px;
          color: var(--lx-muted);
          font-size: 10px;
        }
      }
    }

    .message-input {
      :deep(.el-textarea__inner) {
        min-height: 56px;
        max-height: 160px;
        border: none !important;
        border-radius: 14px;
        padding: 14px 16px 6px;
        font-size: 15px;
        line-height: 1.6;
        resize: none;
        background: transparent;
        box-shadow: none !important;

        &:focus {
          border: none;
          box-shadow: none;
          background: transparent;
        }

        &::placeholder {
          color: var(--lx-muted);
        }
      }
    }

    .input-actions {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 10px 8px;

      .mode-btn,
      .preset-btn,
      .attachment-btn,
      .video-btn {
        height: 34px;
        padding: 0 12px;
        border: none;
        border-radius: 10px;
        background: transparent;
        color: var(--lx-muted);
        font-size: 13px;
        font-weight: 600;
        gap: 6px;
        transition: background-color 0.2s ease, color 0.2s ease;

        &:hover:not(:disabled) {
          background: var(--lx-canvas);
          color: var(--lx-text);
          transform: none;
        }

        .el-icon {
          font-size: 15px;
        }

        &.active-mode {
          background: var(--lx-primary-soft);
          color: var(--lx-primary);

          &:hover {
            background: color-mix(in srgb, var(--seed-primary) 14%, var(--seed-surface));
          }
        }
      }

      .attachment-btn {
        small {
          color: var(--lx-primary);
          font-size: 10px;
          font-weight: 700;
          font-variant-numeric: tabular-nums;
        }
      }

      .input-shortcut {
        margin-left: auto;
        color: var(--lx-muted);
        font-size: 11px;
        white-space: nowrap;
      }

      .send-btn {
        height: 34px;
        margin-left: 4px;
        padding: 0 18px;
        border: none;
        border-radius: 10px;
        background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
        color: white;
        font-size: 13.5px;
        font-weight: 600;
        gap: 6px;
        box-shadow: 0 4px 12px rgba(15, 118, 110, 0.28);
        transition: transform 0.2s ease, box-shadow 0.2s ease;

        &:hover:not(:disabled) {
          transform: translateY(-1px);
          box-shadow: 0 8px 18px rgba(15, 118, 110, 0.34);
        }

        &:disabled {
          opacity: 0.55;
          cursor: not-allowed;
          box-shadow: none;
        }

        .el-icon {
          font-size: 15px;
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
@media (max-width: 1180px) {
  .session-sidebar {
    width: 228px;
    flex-basis: 228px;
  }

  .chat-main {
    --chat-column-width: 800px;
  }

  .input-shortcut {
    display: none;
  }
}

.quick-video-dialog-heading {
  display: flex;
  align-items: center;
  gap: 12px;

  > span:last-child {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  strong {
    color: var(--lx-navy);
    font-size: 17px;
  }

  small {
    color: var(--lx-muted);
    font-size: 12px;
  }
}

.quick-video-dialog-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: none;
  place-items: center;
  color: white;
  background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
  border-radius: 13px;
  box-shadow: 0 7px 18px rgba(15, 118, 110, 0.24);
  font-size: 20px;
}

.quick-video-composer {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.quick-video-section {
  min-width: 0;
}

.quick-video-label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;

  label {
    color: var(--lx-text);
    font-size: 13.5px;
    font-weight: 700;
  }

  span {
    color: var(--lx-muted);
    font-size: 11px;
    text-align: right;
  }
}

.duration-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;

  button {
    display: grid;
    grid-template-columns: auto auto 1fr;
    align-items: baseline;
    gap: 3px;
    min-height: 58px;
    padding: 10px 13px;
    color: var(--lx-muted);
    background: var(--lx-canvas);
    border: 1px solid var(--lx-border-soft);
    border-radius: 12px;
    cursor: pointer;
    text-align: left;
    transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;

    &:hover {
      color: var(--lx-text);
      border-color: color-mix(in srgb, var(--seed-primary) 38%, var(--seed-surface));
      transform: translateY(-1px);
    }

    &:focus-visible {
      outline: 3px solid var(--lx-primary-glow);
      outline-offset: 2px;
    }

    &.active {
      color: var(--lx-primary);
      background: var(--lx-primary-soft);
      border-color: color-mix(in srgb, var(--seed-primary) 48%, var(--seed-surface));
      box-shadow: inset 0 0 0 1px rgba(15, 118, 110, 0.08);
    }

    strong {
      font-size: 22px;
      font-variant-numeric: tabular-nums;
    }

    small {
      font-size: 11px;
      font-weight: 700;
    }

    span {
      justify-self: end;
      font-size: 11px;
    }
  }
}

.quick-video-file-input {
  display: none;
}

.reference-filmstrip {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 22px 12px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 15%, rgba(45, 212, 191, 0.13), transparent 28%),
    linear-gradient(135deg, #153b46, #102a43);
  border-radius: 16px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.07);

  &::before,
  &::after {
    position: absolute;
    right: 8px;
    left: 8px;
    height: 6px;
    content: '';
    background: repeating-linear-gradient(
      90deg,
      rgba(255, 255, 255, 0.62) 0 10px,
      transparent 10px 18px
    );
    border-radius: 3px;
    opacity: 0.34;
  }

  &::before { top: 7px; }
  &::after { bottom: 7px; }
}

.reference-frame,
.add-reference-frame {
  position: relative;
  z-index: 1;
  min-width: 0;
  min-height: 118px;
  margin: 0;
  padding: 6px;
  border-radius: 10px;
}

.reference-frame {
  color: var(--lx-text);
  background: var(--lx-surface);
  box-shadow: 0 7px 18px rgba(3, 19, 29, 0.24);

  img {
    display: block;
    width: 100%;
    aspect-ratio: 16 / 9;
    border-radius: 6px;
    object-fit: cover;
  }

  > strong {
    display: block;
    overflow: hidden;
    padding: 7px 3px 2px;
    font-size: 11px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.frame-index {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 6px;
  color: white;
  background: rgba(8, 31, 42, 0.76);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  backdrop-filter: blur(6px);
  font-size: 9px;
  font-weight: 700;
}

.remove-reference {
  position: absolute;
  top: 9px;
  right: 9px;
  display: grid;
  width: 24px;
  height: 24px;
  padding: 0;
  color: white;
  background: rgba(8, 31, 42, 0.76);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 50%;
  cursor: pointer;
  place-items: center;

  &:hover,
  &:focus-visible {
    background: #b42338;
    outline: none;
  }
}

.add-reference-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  color: rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.055);
  border: 1px dashed rgba(255, 255, 255, 0.34);
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: white;
    background: rgba(45, 212, 191, 0.10);
    border-color: rgba(94, 234, 212, 0.72);
    outline: none;
  }

  strong { font-size: 12px; }
  small { color: rgba(255, 255, 255, 0.56); font-size: 9.5px; }
}

.add-reference-icon {
  display: grid;
  width: 32px;
  height: 32px;
  margin-bottom: 2px;
  background: rgba(255, 255, 255, 0.10);
  border-radius: 10px;
  place-items: center;
  font-size: 17px;
}

.quick-video-reference-note {
  margin: 8px 2px 0;
  color: var(--lx-muted);
  font-size: 11px;
  line-height: 1.55;
}

.quick-video-cost-note {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  color: #8a5a16;
  background: #fff8e6;
  border: 1px solid #f5ddac;
  border-radius: 10px;
  font-size: 11.5px;
  line-height: 1.55;

  .el-icon {
    flex: none;
    margin-top: 2px;
  }
}

.quick-video-task-panel {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
  min-height: 260px;
  padding: 28px;
  background: var(--lx-canvas);
  border: 1px solid var(--lx-border-soft);
  border-radius: 18px;

  &.is-success { background: linear-gradient(145deg, #f2fbf7, var(--lx-surface)); }
  &.is-danger { background: linear-gradient(145deg, #fff5f5, var(--lx-surface)); }
  &.is-warning { background: linear-gradient(145deg, #fff9ec, var(--lx-surface)); }
}

.quick-video-progress {
  display: grid;
  width: 88px;
  height: 88px;
  place-items: center;
  background: conic-gradient(
    var(--seed-primary) var(--quick-video-progress),
    var(--lx-border-soft) 0
  );
  border-radius: 50%;
  box-shadow: 0 10px 24px rgba(15, 118, 110, 0.16);

  &::before {
    grid-area: 1 / 1;
    width: 68px;
    height: 68px;
    background: var(--lx-surface);
    border-radius: 50%;
    content: '';
  }

  span {
    z-index: 1;
    color: var(--lx-primary);
    font-size: 11px;
    font-variant-numeric: tabular-nums;

    strong { font-size: 22px; }
  }
}

.quick-video-task-copy {
  min-width: 0;

  h3 {
    margin: 4px 0 7px;
    color: var(--lx-navy);
    font-size: 19px;
  }

  p {
    margin: 0 0 10px;
    color: var(--lx-muted);
    font-size: 13px;
    line-height: 1.6;
  }

  small {
    color: var(--lx-muted);
    font-size: 10.5px;
    font-variant-numeric: tabular-nums;
  }
}

.quick-video-task-eyebrow {
  color: var(--lx-primary);
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 0.1em;
}

.quick-video-result {
  grid-column: 1 / -1;
  width: 100%;
  max-height: 360px;
  margin-top: 8px;
  background: #081f2a;
  border-radius: 14px;
  object-fit: contain;
  box-shadow: 0 14px 32px rgba(3, 19, 29, 0.20);
}

.quick-video-open-result {
  grid-column: 1 / -1;
  justify-self: end;
  color: var(--lx-primary);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;

  &:hover { text-decoration: underline; }
}

.quick-video-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
}

.quick-video-submit {
  min-width: 112px;
  background: linear-gradient(135deg, var(--seed-primary), var(--seed-accent));
  border: none;
}

@media (max-width: 768px) {
  .ai-chat-page {
    padding: 0;
  }

  .chat-container {
    height: 100dvh;
    border-radius: 0;
    flex-direction: column;
  }

  .session-sidebar {
    width: 100%;
    height: auto;
    max-height: 184px;
    flex-basis: auto;
    border-right: none;
    border-bottom: 1px solid var(--lx-border-soft);
    padding: 10px 12px 8px;

    .sidebar-header {
      display: none;
    }

    .new-chat-btn {
      height: 38px;
      margin: 0 4px 8px;
    }

    .session-list-container {
      flex: none;

      .list-header {
        margin-bottom: 6px;
      }

      .session-scroll-area {
        display: flex;
        gap: 6px;
        overflow-x: auto;
        overflow-y: hidden;
        padding: 0 4px 4px;
      }

      .session-item {
        min-width: 190px;
        margin-bottom: 0;
      }
    }
  }

  .chat-header {
    padding: 12px 16px;
  }

  .chat-messages-container {
    padding: 20px 14px 10px;
  }

  .chat-input-container {
    padding: 10px 12px 20px;
  }

  .message-item .message-bubble {
    max-width: 100%;
  }

  .user-message-wrapper .user-wrapper {
    max-width: calc(100% - 48px);
  }

  .message-bubble :deep(.markdown-content img) {
    max-height: 46vh;
  }

  .welcome-content {
    padding: 20px !important;

    .quick-buttons {
      grid-template-columns: 1fr !important;
    }
  }

  .input-actions {
    flex-wrap: nowrap;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }

    .mode-btn,
    .preset-btn,
    .attachment-btn,
    .video-btn,
    .send-btn {
      flex: none;
      min-width: auto;
      justify-content: center;
    }

    .send-btn {
      position: sticky;
      right: 0;
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

  .duration-strip,
  .reference-filmstrip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-video-label-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 3px;

    span { text-align: left; }
  }

  .quick-video-task-panel {
    grid-template-columns: 1fr;
    justify-items: center;
    padding: 22px 16px;
    text-align: center;
  }

  .quick-video-result,
  .quick-video-open-result {
    grid-column: 1;
  }

  .quick-video-open-result {
    justify-self: center;
  }
}
</style>
