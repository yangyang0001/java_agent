# Viber 主动请求能力提取计划

生成时间：2026-07-07 ; 最后修改时间：2026-07-26

本文档用于规划 `social-sop-api/sop-gateway` 中 Viber 主动请求供应商能力的提取方案。这里的“主动请求”指本系统主动调用 Viber Bot API、Viber Channel API 或 Viber Business 供应商接口，不包含 Viber 回调本系统的 Webhook 入站处理。

接口地址、参数、鉴权/验签边界和当前代码入口已单独整理到 `Viber主动请求接口规格.md`，后续其他项目封装主动请求能力时应优先参考该规格文档。

## 1. 改造目标

将当前散落在 `sop-gateway` 中的 Viber 主动 HTTP 请求收口为清晰的 outbound 层，使调用 Viber 的能力具备统一入口、统一鉴权、统一日志、统一异常处理和可替换的实现边界。

本次提取不应把整个 Viber 业务抽象层做重。任务编排、Flow DSL、用户绑定、会话上下文、Kafka 状态流和数据库更新继续留在现有业务层中。

## 2. 范围边界

### 2.1 纳入范围

| 能力 | 外部接口 | 当前主要实现 |
| --- | --- | --- |
| Viber Bot 消息发送 | `https://chatapi.viber.com/pa/send_message` | `ViberApiServiceImpl.sendMsg(...)`、`sendKeyboard(...)`、`sharePhone(...)` |
| Viber Channel 主动发送 | `https://chatapi.viber.com/pa/post` | `ViberApiServiceImpl.sendChannelMessage(...)` |
| Viber Business 消息发送 | `https://api.promotexter.com/viber/send` | `ViberBizApiService.sendBizMsg(...)` |
| Bot 账号信息查询 | `https://chatapi.viber.com/pa/get_account_info` | `ViberApiServiceImpl.getAccountInfo(...)` |
| Webhook 注册 | `https://chatapi.viber.com/pa/set_webhook` | `ViberApiServiceImpl.setWebhook(...)`、`initializeWebhook(...)` |
| 用户资料查询 | `https://chatapi.viber.com/pa/get_user_details` | `ViberApiServiceImpl.getUserDetails(...)` |
| 在线状态查询 | `https://chatapi.viber.com/pa/get_online` | `ViberApiServiceImpl.getOnline(...)` |

### 2.2 不纳入范围

| 能力 | 原因 |
| --- | --- |
| Viber Webhook 回调处理 | 方向相反，是 Viber 请求本系统，不属于主动请求供应商能力。 |
| Flow DSL 执行 | 属于业务流程编排，不应进入 outbound client。 |
| 用户绑定和会话处理 | 涉及业务状态、Redis、DB 和上下文，不应和 HTTP 出口耦合。 |
| Kafka 发送结果流 | 属于系统内部异步数据流，不是 Viber 供应商请求。 |
| WhatsApp、Messenger、Telegram 能力 | 本计划只聚焦 Viber。 |

## 3. 现状问题

1. Viber Bot、Channel、Business 的主动请求分散在 `ViberApiServiceImpl`、`ViberBizApiService`、部分 Job 和工具类中。
2. `ViberApiServiceImpl` 同时承担 HTTP 请求、token 获取、session 判断、渠道消息记录、菜单构造旁路等职责，边界偏重。
3. `MessageTaskService` 等业务类直接依赖较细的 Viber API 方法，后续如果替换供应商、改造超时重试或统一日志，影响面较大。
4. 历史导入 Job 中存在直接拼 URL、直接使用 `RestTemplate` 请求 Viber 的旁路实现，容易绕过主链路治理。
5. 发送能力和配置查询能力混在同一服务接口中，不利于只提取轻量发送能力。

## 4. 目标结构

建议第一阶段仍放在 `sop-gateway` 内部，以包级边界先完成收口，暂不拆独立 Maven 模块。

```text
com.digiplus.sop.gateway.viber.outbound
  ViberBotOutboundClient
  ViberBusinessOutboundClient
  ViberAccountOutboundClient
  impl/
    ViberBotOutboundClientImpl
    ViberBusinessOutboundClientImpl
    ViberAccountOutboundClientImpl
  support/
    ViberAuthHeaderFactory
    ViberHttpProperties
    ViberOutboundException
```

### 4.1 接口职责

| 接口 | 职责 |
| --- | --- |
| `ViberBotOutboundClient` | 负责 Bot `send_message` 和 Channel `post` 两类发送请求。 |
| `ViberBusinessOutboundClient` | 负责 Promotexter Viber Business 发送请求。 |
| `ViberAccountOutboundClient` | 负责账号信息、Webhook、用户资料、在线状态等配置/查询能力。 |
| `ViberAuthHeaderFactory` | 根据 token 或 Business 账号信息构造请求头，统一脱敏日志。 |
| `ViberHttpProperties` | 管理 Viber URL、超时、开关、默认参数等配置。 |
| `ViberOutboundException` | 统一主动请求 Viber 时的异常类型，保留供应商响应摘要。 |

### 4.2 业务层仍保留的职责

| 业务职责 | 保留位置 |
| --- | --- |
| 查询 `MessageRecord`、`MessageTask`、`BotTokenConfig`、`BizAccountConfig` | `MessageTaskService` 等现有业务服务 |
| 构造键盘、Rich Media、菜单 URL、sessionId | `ViberApiHelper`、各类 helper/action |
| Redis 幂等控制 | 现有业务服务 |
| Kafka 投递 `MSG_RECORD_UPDATE_TOPIC`、`TASK_RESULT_COUNT_TOPIC` 等内部消息 | 现有 listener/service |
| 更新 DB 状态 | 现有 repo/service |

## 5. 分阶段计划

### P0：发送能力收口

目标：优先覆盖最核心、调用量最高、业务价值最大的主动发送能力。

改造内容：

1. 新增 `ViberBotOutboundClient`。
2. 新增 `ViberBusinessOutboundClient`。
3. 将 `send_message`、`post`、Promotexter `/viber/send` 的 HTTP 调用迁入 outbound 实现。
4. 保留 `ViberApiService` 与 `ViberBizApiService` 对外方法签名，先改为委托 outbound client，避免一次性修改所有调用方。
5. 重点验证以下调用路径行为不变：
   - `MessageTaskService.sendBotRecord(...)`
   - `MessageTaskService.sendRecord(...)`
   - `MessageTaskService.sendOtpRecord(...)`
   - `SendTemplateMsgListener`
   - `SopFacadeImpl.sendChannelMessage(...)`
   - Viber 交互 action 中的 `sendKeyboard(...)`

验收标准：

1. 主链路所有 Viber Bot 消息发送都经过 `ViberBotOutboundClient`。
2. Viber Channel 主动发送经过 `ViberBotOutboundClient`。
3. Viber Business 发送经过 `ViberBusinessOutboundClient`。
4. `MessageTaskService` 中不出现 Viber 供应商 URL、HTTP Header 组装或 `RestTemplate.exchange(...)` 细节。
5. 发送成功、发送失败、Kafka 状态回写行为与改造前一致。

### P1：账号配置与查询能力收口

目标：将非发送类主动请求也统一到 outbound 层，但保持与 P0 分离。

改造内容：

1. 新增 `ViberAccountOutboundClient`。
2. 迁移以下能力：
   - `get_account_info`
   - `set_webhook`
   - `get_user_details`
   - `get_online`
3. 调整 `SystemController.setWebHook(...)`、`ChannelSubUserHelper`、`ViberController.getOnline(...)` 等调用方。
4. 保留现有响应 DTO，避免引入大范围 DTO 迁移。

验收标准：

1. Webhook 初始化能力行为不变。
2. 用户资料查询行为不变。
3. 在线状态查询行为不变。
4. 所有 Bot API 请求头生成逻辑收口到统一 helper。

### P2：历史旁路治理

目标：清理绕过主链路的直接 HTTP 调用，降低后续供应商能力治理盲区。

改造内容：

1. 检查并迁移以下类中的直接 Viber HTTP 调用：
   - `ImportOldViberUserJob`
   - `ImportApOldViberUserJob`
   - `ImportXlsx`
   - `ViberTools`
2. 对仍需保留的 Job，改为调用 outbound client。
3. 对仅用于历史测试或临时导入的工具类，标记废弃或移出生产路径。

验收标准：

1. 生产代码中不再直接拼接 `https://chatapi.viber.com/pa/*`。
2. 生产代码中不再绕过 outbound client 直接请求 Promotexter `/viber/send`。
3. 工具类或历史 Job 的保留理由清晰。

## 6. 推荐执行顺序

1. 先建立 outbound 包和接口，不改业务行为。
2. 将 `ViberApiServiceImpl` 内部的 `send_message` 和 `post` HTTP 调用迁到 `ViberBotOutboundClientImpl`。
3. 将 `ViberBizApiService.sendBizMsg(...)` 内部 HTTP 调用迁到 `ViberBusinessOutboundClientImpl`。
4. 保留原服务作为兼容门面，编译通过后跑发送主链路测试。
5. 再逐步将业务调用方从 `ViberApiService` 切到 outbound client，或继续保持门面层，视改动风险决定。
6. 完成 P0 后再启动 P1，不要在同一轮里混改 Webhook、用户资料查询和发送链路。

## 7. 风险与控制

| 风险 | 控制方式 |
| --- | --- |
| 发送链路状态回写变化 | P0 阶段只迁移 HTTP 出口，不改 `MessageRecord` 构造和 Kafka 投递。 |
| token 选择逻辑变化 | token 仍由调用方传入 outbound client，不在 outbound 内部查 DB。 |
| Viber Bot 与 Business 语义混淆 | 分成 `ViberBotOutboundClient` 和 `ViberBusinessOutboundClient`。 |
| 抽象过重 | 不引入统一 `ViberMessage` 大模型，先复用现有 DTO。 |
| 历史工具类遗漏 | P2 单独治理，避免影响 P0 主链路上线。 |
| 日志泄露敏感信息 | outbound 层统一对 token、手机号、Business 密钥脱敏。 |

## 8. 测试与验证

### 8.1 编译验证

在 `social-sop-api/sop-gateway` 所属 Maven 项目中执行编译，确保接口迁移没有破坏依赖关系。

```cmd
mvn -s D:\apache-maven-3.9.9\conf\settings.xml -pl sop-gateway -am compile
```

如果当前项目结构不支持 `-pl sop-gateway`，则从 `social-sop-api` 根目录执行：

```cmd
mvn -s D:\apache-maven-3.9.9\conf\settings.xml compile
```

### 8.2 主链路验证

| 场景 | 验证点 |
| --- | --- |
| Viber Bot 文本/键盘发送 | 返回 `message_token`，并按原逻辑投递 `MSG_RECORD_UPDATE_TOPIC`。 |
| Viber Rich Media 发送 | 请求体与改造前一致，按钮、图片、session 参数不丢失。 |
| Viber Channel 发送 | `/viber/sendChannelMessage` 行为不变。 |
| Viber Business 普通发送 | Promotexter 响应解析不变，`transactionId` 正常写入发送记录。 |
| Viber Business OTP 发送 | OTP lineage、callback 和 Redis 映射逻辑不变。 |
| 发送失败 | 错误响应仍能被转换为原有失败状态回写。 |

## 9. 交付物

1. 新增 outbound 包和接口实现。
2. `ViberApiServiceImpl`、`ViberBizApiService` 改为委托 outbound client。
3. P0 主链路调用方行为保持不变。
4. 更新 `Viber消息流程说明.md` 中主动请求 Viber API 的实现说明。
5. 如后续完成 P1/P2，继续更新本文档的最后修改时间和执行状态。
