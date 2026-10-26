# Viber 客服双向消息链路接口设计方案

生成时间：2026-07-17 ; 最后修改时间：2026-07-26

本文聚焦 SOP 社媒域的 Viber Bot 客服双向接口。外部客服系统、客服网关和 Engine 内部实现不在
`social-sop-api` 代码中，本文只把它们作为上下游边界；SOP 行为以当前代码为准。

## 1. SOP 职责边界

```text
Viber 用户
  -> Viber Bot
  -> social-sop-api
  -> message-engine-api
  -> 客服网关
  -> 第三方客服系统

第三方客服系统
  -> 客服网关
  -> message-engine-api
  -> social-sop-api
  -> Viber Bot
  -> Viber 用户
```

SOP 负责：

- 接收 Viber Bot 回调。
- 复用现有订阅、分享手机号、注册和绑定流程。
- 判断普通消息是否处于客服 Flow。
- 将客户消息异步提交 Engine。
- 接收 Engine 的 Viber 发送请求。
- 调用 message-center Viber SDK。
- 建立 `messageId` 与 `message_token` 关联。
- 处理 `failed/delivered/seen` 状态回调。
- 把消息和状态记录发送到 Flow Topic。

SOP 不负责：

- Sobot 会话创建、客服排队和坐席分配。
- 客服消息在 Engine 或客服系统中的持久化。
- 媒体文件下载、转存 S3 或长期保存。
- 客服系统与网关之间的加密和验签契约。

## 2. SOP 接口目录

| 方向 | 方法 | 接口 | 当前代码 |
| --- | --- | --- | --- |
| Viber -> SOP | POST | `/viber/{uri}/callback` | `ViberWebHookController` |
| Engine -> SOP | POST | `/viber/sendMessages` | `SopFacade#sendViberMessages` |
| SOP -> Engine | POST | `/social/messages/webhook` | `MsgEngineServiceFacade#submitInboundMessage` |
| SOP -> Engine | POST | `/social/message/status` | `MsgEngineServiceFacade#reportMessageStatus` |

说明：

- 内网 Feign 调用当前没有 SOP 自定义鉴权。
- Viber Bot 回调当前没有实现签名校验。
- Bot 回调平台固定为 `viber`，不再由动态 path 参数接收其他平台。

## 3. Viber 用户消息进入客服

### 3.1 Viber 回调

```http
POST /viber/{uri}/callback
```

示例：

```json
{
  "event": "message",
  "timestamp": 1785036570921,
  "message_token": 6262006930918752921,
  "sender": {
    "id": "viber-user-id",
    "name": "User"
  },
  "message": {
    "type": "text",
    "text": "I need help"
  }
}
```

入口处理顺序：

1. Bean 反序列化并保留请求头。
2. 查找 `{uri}` 对应的 Viber Bot 配置。
3. 按回调类型执行最小字段校验。
4. 使用 `uri + message_token` 建立幂等门闩。
5. 执行用户准入和消息路由。

### 3.2 客服消息准入

必须同时满足：

- 用户已经绑定，`loginName` 非空。
- 用户当前订阅，`flag=1`。
- Redis 中存在当前 Bot 和 Viber 用户对应的有效客服 Flow 会话。
- 消息不是 `richParams` 或 `mt=rate` 等旧控制消息。

未绑定或已退订时发送分享手机号键盘。用户点击系统分享手机号按钮后，继续复用
`ContactMessageHandler` 的现有注册绑定逻辑。

### 3.3 SOP 内部 Kafka

```text
Topic: T_SOCIAL_BOT_INBOUND_TOPIC
key: Viber userId
value: ViberFlowEvent schemaVersion=3
```

回调线程等待 Broker ACK 后才向 Viber 返回成功。

### 3.4 SOP 提交 Engine

```http
POST /social/messages/webhook
```

请求示例：

```json
{
  "channelId": "BP_VB_CB_bptestbot1",
  "loginName": "user01",
  "customerId": "950000000000000001",
  "messageId": "stable-uuid",
  "phoneEncry": "encrypted-phone",
  "message": {
    "type": "text",
    "content": "I need help",
    "text": "I need help",
    "url": null,
    "thumbnail": "",
    "fileName": null,
    "fileSize": null
  },
  "timestamp": 1785036570921,
  "messageSource": "sobot"
}
```

关键规则：

- `message` 是单个对象。
- `messageId` 由 `uri + message_token` 确定性生成。
- `timestamp` 使用毫秒。
- `customerId` 在 Kafka 消费端通过双向索引和用户域查询补全。
- Engine 当前支持 `text/image/video`；其他 Viber 类型在接口边界转换为 `other`。

Engine 通过 `BaseResponse.success()` 表达是否接受。非接受响应会使 Kafka 消费失败并进入有限重试。

## 4. 客服回复用户

### 4.1 Engine 调用 SOP

```http
POST /viber/sendMessages
```

请求示例：

```json
{
  "channelId": "BP_VB_CB_bptestbot1",
  "loginName": "user01",
  "customerId": "950000000000000001",
  "messageId": "engine-message-id",
  "phoneEncry": "",
  "message": [
    {
      "type": "image",
      "imageText": "Screenshot",
      "imageUrl": "https://example.com/image.jpg"
    }
  ],
  "timestamp": 1785036570921,
  "messageSource": "sobot",
  "market": false
}
```

约束：

- 一次请求只能包含一条消息。
- `loginName/customerId` 至少提供一个。
- `market=false` 表示客服回复。
- `channelId` 的 Bot 名允许包含下划线。

### 4.2 消息类型

| 类型 | 主要字段 | 当前支持 |
| --- | --- | --- |
| `text` | `content` | 是 |
| `image` | `imageText`、`imageUrl` | 是 |
| `video` | `videoUrl`、`videoSize`、可选 `videoThumbnail` | 是 |
| `rich` | `content`、`imageUrl`、`buttonLabel`、`buttonUrl` | 是 |

`rich` 的 Viber 展示顺序固定为：

```text
上方文本
中间图片
下方按钮
```

### 4.3 SOP 发送顺序

客服回复使用可恢复检查点：

```text
记录出站 MESSAGE 到 T_SOCIAL_BOT_FLOW_TOPIC
  -> Bot 维度限流
  -> Messenger.viber().sendToBot
  -> 解析供应商结果
  -> 保存 messageId / message_token 关联
  -> 返回 SimpleResult<ViberMessageResp>
```

发送失败：

```text
SDK 失败或 Viber 拒绝
  -> 记录 SEND_FAILED 到 T_SOCIAL_BOT_FLOW_TOPIC
  -> 保存失败检查点
  -> 返回 1001 或 2001
```

### 4.4 返回对象

```json
{
  "messageToken": "6262006934517515605",
  "viberStatus": 0,
  "viberStatusMessage": "ok",
  "billingStatus": 0
}
```

## 5. 状态回调与失败上报

### 5.1 Viber 回调关联

成功发送后保存：

```text
messageId -> message_token
botUri + message_token -> messageId + channelId + user + sendMode
```

收到 `failed/delivered/seen`：

1. 先按 `botUri + message_token` 查询新链路关联。
2. 命中后发布 `T_SOCIAL_BOT_INBOUND_TOPIC`。
3. 未命中则回退旧 `MessageStatusHandler`。
4. Kafka 消费者推进状态机。

### 5.2 状态规则

```text
SENT -> FAILED | DELIVERED | SEEN
FAILED -> DELIVERED | SEEN
DELIVERED -> SEEN
SEEN -> 不再变化
```

`DELIVERED` 之后到达的 `FAILED` 无效；`FAILED` 之后到达的 `DELIVERED/SEEN` 可以推进。

### 5.3 SOP 上报 Engine

当前只上报 `FAILED` 类型，不上报 `DELIVERED/SEEN`：

```http
POST /social/message/status
```

请求示例：

```json
{
  "channelId": "BP_VB_CB_bptestbot1",
  "userId": "950000000000000001",
  "messageId": "engine-message-id",
  "messageStatus": -1,
  "timestamp": 1785036570921
}
```

`userId` 优先使用 `customerId`，为空时使用 `loginName`。

当前消费者的执行条件是回调处理后缓存状态仍为 `FAILED`，没有检查 `statusAccepted` 和
`sendMode`。因此重复 `FAILED` 或已关联营销消息的 `FAILED` 也可能再次调用该接口；这是
当前代码事实，不是接口契约要求。

`delivered/seen` 不调用 Engine 状态接口，只推进 SOP Redis 状态、输出日志并写 Flow Topic。

## 6. Flow Topic 数据

```text
Topic: T_SOCIAL_BOT_FLOW_TOPIC
payload: List<ImMessageRecordMq> JSON
```

写入类型：

- 用户入站 `MESSAGE`
- 客服回复出站 `MESSAGE`
- 客服回复 `SEND_FAILED`
- 状态机接受的 `FAILED/DELIVERED/SEEN`

状态机拒绝的乱序回执不写 Flow Topic。

SOP 不为客服消息新增本地业务表；下游消费者负责持久化。

## 7. Kafka 失败处理

统一入站消费者：

- `MANUAL_IMMEDIATE` ACK。
- 默认并发 3。
- 默认固定间隔 1000 毫秒。
- 默认重试 3 次。
- 重试耗尽后打印完整 payload 和 Kafka 位置信息。
- 不使用 DLT。
- 恢复后提交消费位点。

## 8. 配置资源

必须确认：

- 共用 Kafka 集群配置可用。
- `T_SOCIAL_BOT_INBOUND_TOPIC` 已创建。
- `T_SOCIAL_BOT_FLOW_TOPIC` 已创建。
- `c66-fdt-msg-engine-api` 能被 Feign 发现，或提供
  `feign.message-engine.url`。
- Bot token、uri、botName、botType 和 Flow 配置已加载。
- Redis 可用于幂等、Flow 会话、限流和 token 关联。

客服业务配置前缀为 `sop.viber.cs`，完整清单见入站和出站计划。

## 9. 当前安全与可靠性边界

1. Viber Bot Webhook 当前没有签名校验。
2. 调试模式默认开启，生产应显式关闭。
3. token 关联由 `ViberOutboundStatusService` 固定保留24小时，当前不能通过 Nacos 调整。
4. 成功但 token 为空时无法关联后续回执。
5. Kafka 重试耗尽没有 DLT，需要日志告警和人工补偿。
6. Engine 非成功错误码没有细分类。

## 10. 代码索引

| 作用 | 代码 |
| --- | --- |
| Viber 回调 | `callback/ViberWebHookController.java` |
| 回调校验 | `callback/validation/ViberCallbackValidator.java` |
| 回调幂等 | `callback/idempotency/ViberInboundIdempotencyService.java` |
| 用户消息路由 | `callback/handler/ViberMessageCoordinator.java` |
| 分享手机号 | `callback/handler/message/ContactMessageHandler.java` |
| 客服 Flow | `callback/action/ViberFlowEnterAction.java` |
| 入站 Kafka | `service/viber/ViberInboundEventPublisher.java` |
| 统一消费 | `listener/ViberInboundListener.java` |
| Engine Feign | `client/MsgEngineServiceFacade.java` |
| Engine 出站门面 | `facade/SopFacade.java` |
| Bot/Biz 分流 | `service/viber/ViberMessageDispatchService.java` |
| Bot 出站编排 | `service/viber/ViberMessageService.java` |
| SDK 发送 | `service/viber/ViberOutboundSendService.java` |
| token 关联 | `service/viber/ViberOutboundStatusService.java` |
| Flow 发布 | `service/viber/ViberFlowEventPublisher.java` |

## 11. 联调验收

1. 新用户和已退订用户均先完成分享手机号。
2. 已注册用户重复分享手机号不会重复获得奖励，下游奖励接口按固定 transactionId 幂等。
3. 客服 Flow 激活后文本、图片、视频和附件类消息能进入 Engine。
4. Engine 单条回复能由 SOP 发送到正确 Bot 会话。
5. 客服回复在 Viber 调用前已经进入 Flow Topic。
6. 发送失败产生 `SEND_FAILED`。
7. `message_token` 能关联回 Engine `messageId`。
8. `failed` 上报 Engine，`delivered/seen` 不上报。
9. 营销 `delivered/seen` 仍进入旧记录中心；当前重复状态可能再次发布。
10. 重复 callback 和重复 messageId 不产生重复副作用。
