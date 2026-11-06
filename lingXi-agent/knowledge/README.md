# 灵犀内部知识索引

当前支持 UTF-8 JSONL 索引：每行是一个可独立理解的知识片段。索引应由受控
发布流程生成，不接受聊天用户上传，也不应包含实时库存、订单明细、密钥、
验证码或不必要的个人信息。

示例结构：

```json
{"document_id":"sop-replenishment-2026","title":"补货工单操作规范","section":"3.2 完成工单","content":"此处填写已审核的完整片段内容。","document_type":"sop","version":"2026-06","effective_from":"2026-06-01","effective_to":null,"visibility_roles":["1001","1002"],"product_model":null,"source_id":"sop-replenishment-2026#3.2","source_uri":"knowledge://sop/replenishment/2026","keywords":["补货工单","完成工单"],"is_current":true}
```

发布要求：

1. `source_id` 在整个索引中唯一且可追溯。
2. 旧版本设置 `is_current=false`；同一章节存在多个当前版本时，检索器只采用
   `effective_from` 较新的版本。
3. `visibility_roles` 为空表示所有已认证角色可见；受限文档必须列出角色代码。
4. `effective_from`、`effective_to` 控制生效时间，过期和未生效内容不会参与打分。
5. 上线前应运行权限、版本、无答案和引用准确率评测，再原子替换索引文件。

## 引用证据快照

对话页的“参考资料”可展开查看这次检索实际提供给模型的原文片段、章节、版本和来源标识。
片段最多 2000 个 Unicode 字符；受到模型上下文预算或展示上限裁剪时，页面显示截断提示。
不返回 `source_uri`、角色列表或其他内部元数据，也不会根据来源标识自动下载文件。

成功回答的引用随消息保存在既有 `ui_json` 中，每条回答最多 20 个来源，按 `source_id` 首次去重。
既有数据库需先执行 `dkd-parent/sql/migration_ai_model_history_ui_json_capacity.sql`，将字段扩容为
`MEDIUMTEXT`，并同步扩容正文 `content`，避免合法长中文消息或多条证据超过原 `TEXT` 的
64 KiB 容量；新环境初始化脚本已包含扩容。
审批暂停后恢复的回答会重放当前轮已检索的引用，再随成功结果保存；失败或取消的回答不保存快照。
读取历史仍校验会话归属。快照代表回答生成时已授权取得的证据，不是实时查询；文档更新、
过期或角色后续变化不会改写已保存对话中的片段。旧历史没有证据快照时不会补造原文。
