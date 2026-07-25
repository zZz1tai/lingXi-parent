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
