# 数据校验（DataAuthorize）接口统计

## 一、机制说明

项目中数据权限校验通过自定义注解 `@DataAuthorize` 实现，**作用于方法参数**，而非常见的类/方法级 AOP 拦截。

- 注解定义：`src/main/java/com/digiplus/social/operation/aspect/annotation/DataAuthorize.java`
  - `value`：请求头名称，默认 `Data-Authorize`
  - `required`：是否必须携带该请求头，默认 `false`
  - `defaultValue`：请求头缺省值

- 实际生效逻辑：`src/main/java/com/digiplus/social/operation/component/HeaderParamArgumentResolver.java`
  - 实现 `HandlerMethodArgumentResolver`，识别参数上的 `@DataAuthorize` 注解
  - 从 HTTP 请求头 `Data-Authorize` 中取值
  - 尝试按 JSON 解析为 `Map<String, Object>`；失败则调用 `BotAuthUtil.parseDataAuth(...)` 按自定义格式（`Key:Value&Key:Value`）解析
  - 解析结果注入到被标注的方法参数（一般类型为 `Map<String, List<String>>`），随后由各 Controller 传给 Service 层用于过滤查询数据

- 注册方式：`src/main/java/com/digiplus/social/operation/config/SocialOperationWebMvcConfig.java` 中通过 `addArgumentResolvers` 注册了 `HeaderParamArgumentResolver`，全局生效。

- **注意**：`src/main/java/com/digiplus/social/operation/aspect/DataAuthorizeAspect.java` 中还存在一个基于 AOP `@Around` 的实现，但**整个类被注释掉，未启用**，属于废弃/未生效代码，实际不参与数据校验流程。

## 二、统计结果

| 项目 | 数量 |
|---|---|
| Controller 文件总数 | 36 |
| 接口总数（`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping`） | 202 |
| 使用 `@DataAuthorize` 做数据校验的接口数 | **5** |
| 占比 | 约 2.5% |

## 三、使用了 `@DataAuthorize` 的接口清单

| # | Controller | HTTP方法 | 路径 | Controller方法 | 数据校验参数传递到 |
|---|---|---|---|---|---|
| 1 | `CommonController` | GET | `/api/accountList` | `accountList` | `commonService.accountList(req, dataAuthorize)` |
| 2 | `MessageTemplateController` | GET | `/api/message-template/list` | `list` | `messageTemplateService.list(req, dataAuthorize)` |
| 3 | `MessageTaskController` | POST | `/api/messageTask/list` | `list` | `messageTaskService.queryTaskList(queryReq, dataAuthorize)` |
| 4 | `SystemTaskController` | GET | `/api/system-task/list` | `page` | `systemTaskService.page(dto, page, dataAuthorize)` |
| 5 | `MessageChannelTaskController` | POST | `/api/channelTask/list` | `list` | `messageChannelTaskService`（组装 `req` 后调用 `messageTaskService`） |

对应源码位置：
- `controller/CommonController.java:64`
- `controller/MessageTemplateController.java:32`
- `controller/MessageTaskController.java:92`
- `controller/SystemTaskController.java:37`
- `controller/MessageChannelTaskController.java:55`

## 四、各 Controller 接口数量明细（含是否走数据校验）

| Controller | 接口数 | 是否含数据校验接口 |
|---|---|---|
| AccountInfoStatisticController | 4 | 否 |
| AccountMessageRecordController | 4 | 否 |
| AccountNurturingRecordController | 7 | 否 |
| AccountProfileController | 2 | 否 |
| AccountTagController | 3 | 否 |
| AutoMessageFlowController | 10 | 否 |
| BizAccountConfigController | 6 | 否 |
| BotTokenController | 7 | 否 |
| ChannelTokenController | 4 | 否 |
| CommonController | 11 | **是（1个）** |
| ControlStrategyController | 4 | 否 |
| ControlSubTaskController | 1 | 否 |
| ControlTaskController | 7 | 否 |
| DialogueContentController | 3 | 否 |
| event/EventConfigController | 6 | 否 |
| EventPublishController | 5 | 否 |
| event/EventRewardsConfigController | 8 | 否 |
| event/EventRewardsRecordController | 8 | 否 |
| event/EventRuleConfigController | 7 | 否 |
| event/EventSchemeConfigController | 7 | 否 |
| GamePlacementController | 3 | 否 |
| LiteSkinController | 2 | 否 |
| MessageApiAccountController | 5 | 否 |
| MessageApiAuthorizationController | 5 | 否 |
| MessageAuditController | 5 | 否 |
| MessageChannelTaskController | 6 | **是（1个）** |
| MessageTaskController | 11 | **是（1个）** |
| MessageTemplateController | 6 | **是（1个）** |
| OperationToolController | 4 | 否 |
| OpsTestController | 11 | 否 |
| RpaApiController | 1 | 否 |
| SocialAccountController | 9 | 否 |
| SystemTaskController | 8 | **是（1个）** |
| UserManagementController | 2 | 否 |
| VipButtonController | 5 | 否 |
| VipUserController | 5 | 否 |
| **合计** | **202** | **5 个接口** |

## 五、结论 / 建议

1. 目前仅有 5 个接口（分布在 4 个业务模块：账号列表、消息模板、消息任务、系统任务、渠道任务）通过 `Data-Authorize` 请求头做了数据权限过滤，其余 197 个接口未接入该机制。
2. `DataAuthorizeAspect` 是一段已注释、未启用的历史代码，建议确认是否需要清理，避免误认为"已有 AOP 校验"。
3. 由于 `required` 默认 `false` 且请求头缺失时会直接放行（`headerValue == null` 时仍返回，不强制拦截），即使是这 5 个接口，若客户端未传 `Data-Authorize` 头，也不会报错，是否需要按业务收紧为强制校验建议进一步确认。
