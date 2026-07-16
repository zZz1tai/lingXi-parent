# AI 小说视频自动化工作流模块设计

> 版本：v1.0
> 日期：2026-07-14
> 适用工程：`dkd-parent`（Spring Boot / MyBatis）与 `lingXi-vue`（Vue 3 / Element Plus）

## 1. 目标与边界

### 1.1 建设目标

将小说章节自动转换为可追溯、可复用、可审核的影视生产资料，并最终生成视频片段。系统的核心交付物不是一次性模型回答，而是结构化的生产包（`ScenePackage`）和资产库（`Asset Library`）。

```text
小说文本 → 故事理解 → 分场景/分镜 → 资产生成与沉淀 → 视频生成 → 配音/剪辑 → 章节成片
```

系统需要保证：

- 人物外观、服装、声音和关系在章节间保持连续；
- 场景、道具可跨镜头复用，不因重复生成而漂移；
- 每个图片、视频均可追溯到原文、提示词、模型、参数和上游资产；
- 生成失败只重试失败节点，不重跑整章；
- 不同图片、视频、语音供应商可以替换或并存。

### 1.2 第一阶段范围（MVP）

首期只处理“单章节 + 旁白/人物对白 + 关键帧”生产闭环：

1. 上传或粘贴一章小说；
2. 提取故事圣经增量、人物、场景、道具、对白；
3. 生成角色、服装、场景、道具参考图；
4. 自动拆分为 3～8 个场景和镜头；
5. 生成并质检每个镜头的关键帧；
6. 将资产和分镜 JSON 交付给下游视频、配音智能体。

视频生成、口型驱动、配乐和成片渲染属于第二阶段；但第一阶段的数据模型必须预留这些能力。

### 1.3 非目标

- 不在首期实现通用视频剪辑器；
- 不承诺“原文绝对无改写”；默认采用保守改编，并保留原文依据；
- 不将第三方模型临时 URL 作为正式资产；
- 不在业务逻辑中硬编码任一供应商的 API Key、模型名或参数格式。

## 2. 业务角色与权限

| 角色 | 权限 |
|---|---|
| 普通创作者 | 创建自己项目、查看资产、提交生成、下载已授权成片 |
| 项目编辑 | 编辑故事设定、确认规范资产、调整分镜、发起重生成 |
| 审核员 | 审核内容安全、角色形象和关键剧情变更 |
| 运维管理员 | 配置模型供应商、模板、存储、限额和失败告警 |

建议接入现有 `sys_user`、`sys_role`、菜单和权限体系。建议权限前缀：`aivideo:project`、`aivideo:asset`、`aivideo:storyboard`、`aivideo:task`、`aivideo:provider`。

## 3. 领域模型与资产分层

### 3.1 核心对象

```text
项目 Project
 ├─ 章节 Chapter
 │   ├─ 场景 Scene
 │   │   └─ 镜头 Shot
 │   └─ 章节解析版本 / 故事圣经版本
 ├─ 人物 Character
 ├─ 资产 Asset
 ├─ 生成任务 GenerationTask
 └─ 成片 Render
```

### 3.2 资产分类

| 代码 | 名称 | 举例 | 生命周期 |
|---|---|---|---|
| `STYLE_GUIDE` | 风格资产 | 色板、镜头语言、画风说明 | 项目级、长期复用 |
| `CHARACTER_REFERENCE` | 人物参考图 | 正侧背面、半身、全身 | 人物级、长期复用 |
| `COSTUME_REFERENCE` | 服装资产 | 灰色风衣、古装战甲 | 人物状态级、长期复用 |
| `SCENE_REFERENCE` | 场景资产 | 雨夜停车场、医院走廊 | 场景库、长期复用 |
| `PROP_REFERENCE` | 道具资产 | 病历、戒指、佩剑 | 项目级、长期复用 |
| `VOICE_REFERENCE` | 音色资产 | 人物声线/授权资料 | 人物级、严格受控 |
| `SHOT_KEYFRAME` | 镜头关键帧 | 当前构图的首帧/尾帧 | 镜头级、保留历史 |
| `VIDEO_CLIP` | 视频片段 | 单镜头图生视频结果 | 镜头级、可发布 |
| `AUDIO_CLIP` | 音频片段 | 台词、环境音、音乐 | 镜头级、可发布 |
| `FINAL_RENDER` | 成片资产 | 章节正片、预告、封面 | 项目/章节级 |

### 3.3 规范资产与派生资产

- **规范资产（canonical）**：当前生产标准，例如“林晚形象 v1”。新镜头默认使用它。
- **派生资产（derived）**：基于规范资产生成的单次结果，例如“第 12 章镜头 4 关键帧”。
- **批准资产（approved）**：已通过自动质检或编辑确认，可供下游任务使用。
- **废弃资产（deprecated）**：保留可追溯历史，但不再用于新任务。

规范资产不得覆盖更新；人物换装或形象调整必须新增版本。资产间通过 `ai_video_asset_relation` 保存血缘关系。

## 4. 端到端自动工作流

### 4.1 总体编排

```text
创建项目
  → 导入小说 / 章节
  → 文本清洗、分段、敏感内容预检
  → 生成故事圣经（人物、关系、时间线、道具、视觉风格）
  → 生成/更新规范资产（人物、服装、场景、道具、音色）
  → 场景拆解
  → 对白与分镜生成
  → 镜头关键帧生成
  → 资产一致性质检
  → [第二期] 图生视频、配音、口型、剪辑、渲染
```

所有箭头均对应异步任务，不在 HTTP 请求中等待模型结果。

### 4.2 节点职责与输入输出

| 节点 | 输入 | 输出 | 失败处理 |
|---|---|---|---|
| 文本解析 | 小说文本、章节配置 | 段落、章节摘要、引用范围 | 标记解析失败，可重新提交 |
| 故事圣经 | 当前章节、上章摘要、历史圣经 | 人物/关系/时间线/道具增量 | JSON Schema 修复后重试 |
| 资产规划 | 圣经、项目风格 | 待生成资产清单 | 缺少信息则生成保守描述 |
| 参考图生成 | 资产提示词、参考资产 | 图片文件、生成参数 | 更换 seed/模型后局部重试 |
| 场景与分镜 | 原文引用、圣经、资产索引 | 场景、对白、镜头计划 | 结构化输出校验失败则重试 |
| 关键帧生成 | 镜头计划、人物/场景/道具图 | 关键帧资产 | 仅重做当前镜头 |
| 质检 | 关键帧、镜头计划、规范资产 | 评分、问题清单、通过结果 | 按问题类型触发局部重生成 |
| 视频/音频生成 | 批准关键帧、台词、模型配置 | 视频/音频资产 | 任务超时、回调重复均幂等处理 |

### 4.3 状态机

任务状态：

```text
PENDING → QUEUED → RUNNING → WAITING_CALLBACK → QUALITY_CHECK → SUCCEEDED
                   ↘ FAILED ↗                  ↘ REJECTED
                     RETRYING                    NEEDS_REVIEW
```

关键规则：

- 任务开始前以 `idempotency_key` 去重；
- 回调以 `provider_task_id + event_id` 去重；
- 有上游依赖的任务只在依赖资产为 `APPROVED` 时调度；
- 失败指数退避重试，默认最多 3 次；内容安全、参数校验和版权拒绝不重试；
- 人工处理不是必经步骤；仅在自动修复次数耗尽或关键剧情冲突时进入 `NEEDS_REVIEW`。

## 5. 小说理解、剧本和分镜契约

### 5.1 故事圣经（Story Bible）

故事圣经是项目的事实来源。模型生成场景时必须携带与当前章节相关的事实，而不是整本小说原文。

包含：

- 世界观、时代、地点和视觉风格；
- 人物固定设定、说话习惯和人物关系；
- 事件时间线；
- 当前章节开始和结束时的角色状态；
- 可改变与不可改变的剧情事实；
- 对白模式：`FAITHFUL`（尽量保留原文）或 `ADAPTIVE`（允许保守扩写）。

### 5.2 角色连续性状态

角色不仅有静态档案，还需要按章节记录状态：服装、伤势、携带道具、所在地和情绪基线。这样“第三章右手受伤”会自动影响第四章关键帧。

### 5.3 ScenePackage（下游智能体输入）

```json
{
  "sceneId": "sc_1202",
  "source": {"chapterId": 12, "paragraphFrom": 8, "paragraphTo": 15},
  "time": "深夜，大雨",
  "location": "医院地下停车场",
  "dramaticGoal": "林晚质问顾沉病历被篡改一事",
  "characters": ["char_lw", "char_gc"],
  "requiredAssets": ["asset_char_lw_v1", "asset_scene_parking_rain_v1"],
  "dialogues": [
    {"sequence": 1, "speakerId": "char_lw", "line": "病历上的签名，是你改的？", "emotion": "压抑愤怒", "sourceMode": "FAITHFUL"}
  ],
  "shots": [
    {"shotNo": 1, "durationMs": 4500, "shotSize": "MEDIUM_CLOSE_UP", "camera": "SLOW_PUSH_IN", "keyframeAssetId": "asset_shot_120201_v1"}
  ]
}
```

服务端必须对所有 LLM 输出使用 JSON Schema 校验；出现自然语言夹杂、字段遗漏、引用不存在的资产 ID 时，不可直接进入生成链路。

## 6. 前端信息架构与页面设计

### 6.1 设计定位

定位为“影视生产工作台”，而不是传统表格式后台。后台权限、用户管理等沿用现有系统样式；AI 视频工作区采用低饱和深色创作界面，突出媒体预览、时间线和任务状态。

视觉基调：深墨蓝画布、暖橙操作色、柔和灰白文字、卡片轻描边。避免大面积渐变和无信息装饰。

| Token | 建议值 | 用途 |
|---|---|---|
| `--studio-bg` | `#101318` | 页面背景 |
| `--studio-panel` | `#181D25` | 卡片、侧栏 |
| `--studio-panel-raised` | `#222935` | 悬浮/选中容器 |
| `--studio-primary` | `#F39A4A` | 主操作、进行中 |
| `--studio-success` | `#56C596` | 审核通过、完成 |
| `--studio-danger` | `#F06A6A` | 失败、风险 |
| `--studio-text` | `#F2F4F8` | 主文字 |
| `--studio-muted` | `#99A3B3` | 辅助说明 |

字体建议使用系统中文字体栈，数字、时长和任务 ID 使用等宽字体。媒体卡片采用 16:9、9:16、1:1 三种明确比例，禁止拉伸预览。

### 6.2 页面清单

| 页面 | 路由建议 | 核心内容 |
|---|---|---|
| 项目列表 | `/ai-video/projects` | 项目卡片、完成度、成本、最近任务、创建项目 |
| 新建项目向导 | `/ai-video/projects/create` | 小说导入、改编模式、画风、目标比例、模型选择 |
| 项目总览 | `/ai-video/projects/:id` | 章节进度、资产健康度、任务队列、最近预览 |
| 故事圣经 | `/ai-video/projects/:id/bible` | 人物关系、时间线、世界观、连续性状态 |
| 章节工作台 | `/ai-video/chapters/:id` | 原文、场景列表、分镜、任务与预览联动 |
| 资产库 | `/ai-video/projects/:id/assets` | 角色/服装/场景/道具/音频/视频筛选与版本 |
| 分镜工作台 | `/ai-video/chapters/:id/storyboard` | 镜头卡、关键帧、对白、时长和重生成 |
| 生成队列 | `/ai-video/tasks` | 实时进度、失败原因、重试、成本和供应商状态 |
| 成片中心 | `/ai-video/renders` | 章节成片、字幕、封面、导出与版本 |
| 模型配置 | `/ai-video/providers` | 管理员配置供应商能力、额度、模板和回调 |

### 6.3 项目总览页

页面采用三栏布局：

```text
左：章节树 / 筛选                        中：当前章节进度与媒体预览             右：任务队列和风险提示
第 01 章 [完成]                           [16:9 关键帧或视频预览]                生成中 3
第 02 章 [制作中]                         场景 6 | 镜头 28 | 资产 42              待审核 1
第 03 章 [未开始]                         [继续生成] [查看分镜]                  失败 0
```

顶部固定显示项目名称、改编模式、画风、总成本、存储占用和“生成下一步”主按钮。主按钮根据项目状态显示“解析章节”“生成资产”“生成关键帧”或“生成视频”，不要让用户猜下一步。

### 6.4 章节工作台

采用可拖拽的三栏编辑器：

- 左栏：原文段落与解析锚点；点击段落高亮关联场景；
- 中栏：场景和镜头卡，显示人物、场景、对白、关键帧、质检状态；
- 右栏：资产预览和属性面板，可替换规范资产、调整提示词、指定重生成范围。

镜头卡必须展示“原文依据”“使用资产版本”“生成参数”“质检结果”，使编辑可以在不阅读日志的情况下判断是否可用。

### 6.5 资产库

顶部为分类筛选，左侧为标签/状态，右侧为瀑布流媒体卡。每张卡显示：缩略图、资产类型、版本、审批状态、引用次数和来源。

点击资产打开抽屉：预览、提示词、负面词、模型、seed、父资产、被哪些镜头引用、版本比较、设为规范资产、废弃/归档。禁止直接覆盖图片文件。

### 6.6 分镜工作台

时间线按场景分组，每个镜头卡展示：关键帧、镜头编号、时长、景别、运镜、台词、资产标签、生成状态。底部可切换“剧情模式”和“时间线模式”。

重生成提供三个明确选项：

1. 仅重生成关键帧；
2. 从关键帧重生成视频；
3. 从当前镜头起重新规划（会创建新版本，不覆盖旧版本）。

## 7. 前端交互与状态管理

- 前端为 `lingXi-vue` 新增 `views/aivideo/`、`api/aivideo/` 和 Pinia store；
- 列表和详情使用 REST API；生成进度优先 SSE，无法连接时以 3 秒轮询降级；
- 图片/视频预览使用签名 URL，不将对象存储永久公开；
- 用户对资产、分镜的手动修改记录“用户覆盖字段”，后续自动重规划不得无提示覆盖；
- 提交生成前展示预计任务数、预计积分/费用与受影响资产；
- 长任务离开页面后持续执行；返回项目时从服务端恢复状态，不依赖浏览器内存。

## 8. 后端模块设计

### 8.1 模块建议

在 `dkd-parent/lingXi-manage` 下新增包 `com.lingXi.manage.aivideo`：

```text
aivideo/
  controller/        ProjectController、AssetController、StoryboardController、TaskController、ProviderCallbackController
  domain/            实体、DTO、VO、枚举
  mapper/            MyBatis Mapper
  service/           项目、解析、资产、分镜、任务、质检服务
  service/provider/  图片/视频/语音/安全供应商适配器
  worker/            队列消费者、轮询器、重试调度器
  orchestrator/      工作流编排与依赖决策
  validation/        JSON Schema、业务规则、一致性校验
```

### 8.2 分层职责

| 层 | 职责 |
|---|---|
| Controller | 鉴权、请求校验、幂等键接收、返回任务 ID；不做长耗时生成 |
| Application Service | 事务、权限、状态流转、创建任务和资产记录 |
| Orchestrator | 根据项目/章节状态构建 DAG，调度可执行任务 |
| Provider Adapter | 屏蔽供应商请求、回调、轮询、错误码和能力差异 |
| Worker | 消费任务、调用适配器、上传产物、推进下游任务 |
| Validation | 原文事实、JSON、资产关联、内容安全、媒体技术质量检查 |
| Storage | 上传、签名下载、转存、哈希去重、生命周期清理 |

### 8.3 Provider 统一接口

```java
public interface ImageGenerationProvider {
    ProviderTaskResult submit(ImageGenerationCommand command);
    ProviderTaskStatus query(String providerTaskId);
    void cancel(String providerTaskId);
}

public interface VideoGenerationProvider {
    ProviderTaskResult submit(VideoGenerationCommand command);
    ProviderTaskStatus query(String providerTaskId);
    void cancel(String providerTaskId);
}
```

模型能力通过 `ai_video_provider_model.capability_json` 描述，例如是否支持图生视频、参考图数量、首尾帧、音频驱动口型。编排器据此选择工作流，不让业务代码判断某个供应商名字。

### 8.4 异步与可靠性

- 使用 Redis Stream、RabbitMQ 或现有可用消息队列；没有 MQ 时以数据库任务表 + Quartz 轮询作为首期实现；
- 数据库创建任务与写入 Outbox 记录在同一事务中，消费者异步投递，避免“数据库成功但任务未提交”；
- 回调接口校验供应商签名、时间戳和事件 ID；
- 回调和轮询均可更新同一任务，使用乐观锁 `version` 防止乱序覆盖；
- 供应商结果先转存到 OSS/MinIO/S3，再把自有对象地址写为正式资产地址；
- 生成任务按项目和用户限流，防止单个项目耗尽额度。

### 8.5 关键服务方法

```text
createProject()                     创建项目及默认风格资产
importChapter()                     保存原文并创建解析任务
generateStoryBibleIncrementally()   合并当前章节事实，输出版本化圣经
planAssets()                        计算缺失的规范资产和派生资产
generateSceneAndStoryboard()        输出严格 ScenePackage
createGenerationTask()              写任务、Outbox、成本预估和幂等键
handleProviderCallback()            验签、幂等、转存、推进状态
validateAsset()                     生成评分和问题清单
approveAsset()                      升级为可用资产，触发下游任务
regenerateScope()                   基于范围创建新版本任务图
```

## 9. REST API 设计

接口统一使用现有登录态和权限控制。创建类接口支持 `Idempotency-Key` 请求头。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/aivideo/projects` | 创建项目 |
| `GET` | `/aivideo/projects` | 项目列表 |
| `GET` | `/aivideo/projects/{id}` | 项目总览和生产统计 |
| `POST` | `/aivideo/projects/{id}/chapters` | 导入章节文本 |
| `POST` | `/aivideo/chapters/{id}/pipeline` | 触发解析/资产/分镜流水线 |
| `GET` | `/aivideo/chapters/{id}/storyboard` | 获取场景、对白和镜头 |
| `PATCH` | `/aivideo/shots/{id}` | 手动覆盖镜头参数，生成新版本 |
| `POST` | `/aivideo/shots/{id}/regenerate` | 局部重生成 |
| `GET` | `/aivideo/projects/{id}/assets` | 资产列表与筛选 |
| `GET` | `/aivideo/assets/{id}` | 资产详情和血缘 |
| `POST` | `/aivideo/assets/{id}/approve` | 批准资产/设为规范资产 |
| `POST` | `/aivideo/assets/{id}/deprecate` | 废弃资产 |
| `GET` | `/aivideo/tasks` | 生成任务列表 |
| `POST` | `/aivideo/tasks/{id}/retry` | 重试可重试任务 |
| `GET` | `/aivideo/events` | SSE 任务进度流 |
| `POST` | `/aivideo/callbacks/{provider}` | 供应商回调（验签、不可登录调用） |

## 10. 自动质检与治理

### 10.1 必检项

- 原文忠实性：场景、人物、关键事件是否有来源段落；
- 连续性：人物性别、脸部特征、服装、伤势、道具和地点是否匹配；
- 视觉质量：分辨率、黑屏、模糊、肢体畸形、水印、字幕误入画面；
- 对白质量：说话人、字数、敏感词、音频时长；
- 内容安全：输入小说、提示词、图片、视频均需检查；
- 权利治理：用户上传的肖像、声音、参考图保存授权声明与删除能力。

### 10.2 质量结果

质检不只保存一个“通过/失败”。应保存指标分数、问题、建议动作和模型版本。低风险缺陷可自动重试；剧情事实冲突、敏感内容和版权风险必须停在 `NEEDS_REVIEW`。

## 11. 数据库设计原则

- 数据库：MySQL 8.0，`utf8mb4`；大段原文、提示词和模型原始响应使用 `LONGTEXT`/`JSON`；
- 采用逻辑外键（索引但不建强制 FK），符合现有后台项目的迁移与软删除习惯；
- 所有业务表包含 `create_by`、`create_time`、`update_by`、`update_time`、`del_flag`；
- 媒体文件只存对象键、哈希、大小、MIME，存储系统负责签名 URL；
- 资产、圣经、分镜和成片均使用版本号，不覆盖历史；
- 完整 DDL 位于 [`dkd-parent/sql/ai_video_workflow.sql`](../dkd-parent/sql/ai_video_workflow.sql)。

### 11.1 表清单

| 表名 | 作用 |
|---|---|
| `ai_video_project` | 项目与全局生成设置 |
| `ai_video_chapter` | 原始章节、解析状态和摘要 |
| `ai_video_story_bible` | 版本化故事圣经 |
| `ai_video_character` | 人物基础档案 |
| `ai_video_character_state` | 人物在章节范围内的连续性状态 |
| `ai_video_scene` | 场景和原文引用 |
| `ai_video_shot` | 分镜、台词、运镜、时长和资产需求 |
| `ai_video_asset` | 统一资产主表 |
| `ai_video_asset_relation` | 资产血缘、使用和替代关系 |
| `ai_video_generation_task` | 所有异步生成/解析/质检任务 |
| `ai_video_quality_report` | 自动质检结果 |
| `ai_video_prompt_template` | 可版本化的提示词模板 |
| `ai_video_provider_model` | 模型供应商能力和费用配置 |
| `ai_video_render` | 章节或项目成片渲染任务 |
| `ai_video_task_outbox` | 可靠异步投递记录 |

## 12. 推荐实施顺序

1. 执行 DDL，完成项目、章节、人物、资产、任务基础 CRUD；
2. 实现对象存储转存、统一资产库、资产版本和血缘关系；
3. 接入一个 LLM，严格输出故事圣经和 `ScenePackage`；
4. 接入一个图片供应商，先完成角色/场景/关键帧；
5. 实现异步任务、回调、重试、SSE 和质检闭环；
6. 完成项目工作台、资产库、分镜工作台；
7. 再接图生视频、配音、口型、时间线渲染和成本结算。

## 13. 验收标准

首期完成时，应能验证以下场景：

1. 导入一章小说后，自动得到可追溯到原文的场景、对白和镜头；
2. 同一人物在三个镜头中复用同一个已批准参考资产；
3. 场景关键帧可显示其人物、服装、场景和道具来源；
4. 单个关键帧失败后仅重试该任务，不重复生成其他已批准资产；
5. 修改人物资产至 v2 后，旧资产仍可追溯，新任务默认使用 v2；
6. 回调重复到达不会生成重复资产或重复扣减额度；
7. 管理端可查看每个资产和任务的提示词、模型、成本、质量报告和原文依据。

## 14. Wanx 运行配置

图片和图生视频均复用 DashScope API Key。密钥应通过部署环境变量或受保护配置注入，不能提交到代码库。

```yaml
dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  image-model: wanx2.1-t2i-turbo
  video-model: wanx2.1-i2v-turbo

# 当默认文件存储平台不是 OSS/CDN 时才需要：外部 Wanx 服务可访问的资产公网根地址。
# 当前项目使用默认阿里云 OSS 平台时不需要配置，系统直接使用 OSS 返回的 URL。
aivideo:
  public-asset-base-url: https://media.example.com
  wanx:
    poll-interval-ms: 5000
    video-poll-interval-ms: 8000
```

当前实现会把 Wanx 结果通过项目已有的 Dromara 默认文件存储平台转存；生产环境默认平台配置为 OSS 时，资产表直接保存 OSS 返回的公网 URL。图生视频优先使用该 URL；只有资产仍是相对路径时，才会使用 `public-asset-base-url + 资源路径` 作为关键帧 URL。
