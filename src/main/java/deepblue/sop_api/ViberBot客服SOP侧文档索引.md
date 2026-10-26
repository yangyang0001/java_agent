# ViberBot 客服 SOP 侧文档索引

生成时间：2026-07-06 ; 最后修改时间：2026-07-26

本文只维护当前代码对应的客服链路文档索引。实现事实以
`social/social-sop-api/sop-gateway` 当前工作树为准。

## 0. 快速交接

文档：`social/docs/Viber客服系统快速交接手册.md`

该文档集中维护：

- 客服相关文档总览及阅读顺序。
- 入站、出站、回执和注册链路的关键代码位置。
- `[VIBER_IN]`、`[VIBER_OUT]` 和 `[VIBER_WEBHOOK]` 日志节点字典。
- 按 `traceId/messageId/message_token` 排查问题的搜索方法。
- 当前已知风险和临时接手值班清单。

## 1. 入站链路

文档：`social/docs/ViberBot客服SOP侧入站链路开发计划.md`

当前链路：

```text
Viber Bot 回调
  -> POST /viber/{uri}/callback
  -> Bean 反序列化、Bot 配置和结构字段校验
  -> uri + message_token 幂等门闩
  -> 用户准入、订阅状态、旧菜单和客服 Flow 路由
  -> T_SOCIAL_BOT_INBOUND_TOPIC
  -> ViberInboundListener
  -> POST /social/messages/webhook
  -> T_SOCIAL_BOT_FLOW_TOPIC
```

说明：

- 当前入口保留请求头，但不执行 Viber Webhook 签名校验。
- 未绑定用户和已绑定但已退订用户均先进入分享手机号流程。
- 只有 Redis 中存在有效客服 Flow 会话的普通消息才进入客服 Kafka。
- `delivered/seen/failed` 回执也进入统一入站 Topic；只上报 `failed`，但当前重复
  `failed` 仍可能再次上报 Engine。

## 2. 出站链路

文档：`social/docs/ViberBot客服SOP侧出站链路开发计划.md`

当前链路：

```text
Engine
  -> POST /viber/sendMessages
  -> ViberMessageDispatchService
  -> ViberMessageService
  -> 客服回复先写 T_SOCIAL_BOT_FLOW_TOPIC
  -> message-center: Messenger.viber().sendToBot
  -> 保存 messageId / message_token Redis 关联
  -> Viber 状态回调进入统一入站链路
```

说明：

- `VB_CB` 进入 Viber Bot 链路；`VB_BIZ` 进入独立 Biz 链路。
- 单次请求必须且只能包含一条 `text/image/video/rich` 消息。
- `isMarket=false` 使用客服回复可恢复状态机；`isMarket=true` 使用营销简单幂等流程。
- 客服消息不写 SOP 业务数据库，消息记录通过 Flow Topic 交给下游持久化。

## 3. 当前实现复核

文档：`social/docs/ViberBot客服SOP侧当前实现流程复核.md`

该文档维护：

- 入站与出站当前流程图。
- 每一步对应的代码位置。
- 当前已实现能力和仍存在的代码事实。
- Trace、Kafka、Redis、Engine 和 Flow Topic 的边界。

## 4. 接口设计

文档：`social/docs/Viber客服双向消息链路接口设计方案.md`

该文档保留跨系统接口背景，但 SOP 章节以当前代码接口为准：

- Viber 回调：`POST /viber/{uri}/callback`
- Engine 入站：`POST /social/messages/webhook`
- Engine 失败状态：`POST /social/message/status`
- Engine 调用 SOP：`POST /viber/sendMessages`

## 5. 维护规则

1. 代码是实现事实的唯一依据，计划文档不得继续保留已经失效的类名和接口。
2. “计划”章节同时标注当前实现状态，不把已经落地的能力描述为待开发。
3. 不把 Viber 回调事件、Kafka 消息事件和 SOP 日志节点统称为“事件”。
4. 文档修改必须更新标题下方同一行的最后修改时间。
5. 本轮文档同步不修改生产代码。
