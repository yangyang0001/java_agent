<div style="font-family: 'Courier New', Courier, monospace;">

# WebSocket 通信原理与四种通信方式

## 1. WebSocket 的通信方式

HTTP 是"请求-响应"模型：客户端发一个请求，服务端回一个响应，服务端没法主动给客户端推数据。WebSocket 建立在 TCP 之上，是**全双工、长连接**协议：握手一次成功后，客户端和服务端谁都可以随时主动给对方发消息，不需要一问一答。

握手复用了 HTTP，本质是一次特殊的 HTTP 请求：

```
GET /ws/echo HTTP/1.1
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```

服务端同意升级会返回 **101 Switching Protocols**，之后这条 TCP 连接就不再走 HTTP 语义，双方发送的都是 **WebSocket 帧（Frame）**，直到某一方主动关闭。应用层（Spring）把帧的细节都封装好了：收到一帧完整的文本消息会包装成 `TextMessage` 回调给 Handler；调用 `session.sendMessage(...)`，框架负责打包成帧发给对方。

**关键点：客户端和客户端之间没有直接连接**，都只是各自跟服务器建立了一条连接。谁的消息该转给谁、要不要处理、处理成什么样，完全由服务器端代码决定——**服务器是消息必经的载体**，不是一根打通两端的水管。

## 2. 本项目实现的四种通信方式

同一个 `EchoWebSocketHandler` 注册在 4 个不同 URI 上，每个 URI 对应一种默认路由模式（`WebsocketMode` 枚举）：

| URI | mode | 服务器行为 | 类比 |
|---|---|---|---|
| `/ws/echo` | `ECHO` | 原样发回给发送者自己，不转发给任何人 | 测试连接是否通畅 |
| `/ws/process` | `PROCESS` | 服务器做一点本地处理（示例是字符串反转），只把结果发回给发送者 | 服务端计算/业务处理后应答 |
| `/ws/broadcast` | `BROADCAST` | 转发给除发送者外的所有在线客户端 | 群聊、群发通知 |
| `/ws/unicast` | `UNICAST` | 按消息里的 `to`（目标 sessionId），只转发给指定的那一个客户端 | 私聊 |

四种模式共用同一份在线连接表（`Map<sessionId, WebSocketSession>`），不管客户端从哪个 URI 连进来都在同一个池子里，所以 BROADCAST/UNICAST 能触达任意端点连进来的客户端。

服务器怎么知道用哪个 mode：`EchoWebSocketHandler.resolveMode()` 优先看消息体里有没有显式的 `mode` 字段，没有就取 `session.getUri()` 的最后一段路径去匹配 `WebsocketMode` 枚举——连哪个 URI 默认就是哪种模式，不用每条消息都带 `mode` 字段（但仍然支持显式指定，会优先生效）。

代码分布：

- `config/WebSocketConfig.java`：把 `EchoWebSocketHandler` 同时绑定到 4 个路径上
- `config/WebSocketPortConfig.java`：给 WebSocket 额外开一个 `8888` 端口（HTTP 接口仍是 `8080`）
- `common/WebsocketMode.java`：`ECHO`/`PROCESS`/`UNICAST`/`BROADCAST` 四个枚举值
- `common/WebsocketMsg.java`：统一消息格式，字段 `mode`（可选）、`from`（服务端自动填充）、`to`（仅 UNICAST 用）、`content`
- `EchoWebSocketHandler.java`：`afterConnectionEstablished` 记录在线连接并广播上线通知；`handleTextMessage` 解析消息、推断 mode、分发到四个私有方法；`afterConnectionClosed` 移除连接并广播下线通知
- `client/` 目录：四个各自连独立 URI 的 Java 客户端（`EchoClient`/`ProcessClient`/`BroadcastClient`/`UnicastClient`），直接在 IDEA 里跑 `main` 方法即可，不用手动设置 `mode`

## 3. 怎么测试这四种通信方式

### 3.0 准备工作

1. 启动 `AgentApplication`（HTTP 走 8080，WebSocket 走 8888）。
2. 连上任意 URI 后，服务端会先推送一条系统消息：
   ```
   [系统消息] 你的 sessionId = 3fa2eb10-xxxx
   ```
   BROADCAST/UNICAST 测试需要两个客户端，记下各自的 sessionId。
3. Postman 里新建 WebSocket 请求，消息类型切到 **Text**，JSON 里可以不带 `mode` 字段（服务端按 URI 自动推断），直接发内容即可。也可以用 `client/` 目录下对应的 Java 客户端代替 Postman，运行 `main` 方法。

### 3.1 测试 ECHO —— 只回给自己

连 `ws://localhost:8888/ws/echo`，发送：
```json
{"content":"hello"}
```
预期：自己收到 `echo: hello`；没有其他客户端会收到任何东西。

用 IDEA 客户端：直接跑 `client/EchoClient.java` 的 `main` 方法，输入 `hello` 回车。

### 3.2 测试 PROCESS —— 服务端处理后只回给自己

连 `ws://localhost:8888/ws/process`，发送：
```json
{"content":"hello"}
```
预期：自己收到 `[处理结果] olleh`（`hello` 反转后的结果）；不会转发给任何人。

用 IDEA 客户端：跑 `client/ProcessClient.java`，输入 `hello` 回车。

### 3.3 测试 BROADCAST —— 转发给除自己外的所有人

开两个连接 **A**、**B**，都连 `ws://localhost:8888/ws/broadcast`。在 **A** 发送：
```json
{"content":"大家好"}
```
预期：
- **A** 自己收不到这条消息（服务端排除了发送者）
- **B** 收到 `[广播 from A的sessionId] 大家好`

反过来在 **B** 发一条，**A** 也能收到，验证双向都通。

用 IDEA 客户端：跑两个 `client/BroadcastClient.java` 实例（比如在 IDEA 里 Run 两次），各自输入内容互相验证。

### 3.4 测试 UNICAST —— 只转发给指定目标

开两个连接 **A**、**B**，都连 `ws://localhost:8888/ws/unicast`。把 **B** 的 sessionId 填进 `to` 字段，在 **A** 发送：
```json
{"to":"填B的sessionId","content":"你好呀"}
```
预期：
- **B** 收到 `[私聊 from A的sessionId] 你好呀`
- **A** 自己和其他任何连接都收不到

再试一个不存在的 sessionId，预期 **A** 收到报错：
```
[系统消息] 目标 sessionId 不存在或已下线: xxxxx
```

用 IDEA 客户端：跑两个 `client/UnicastClient.java` 实例，启动后会先提示输入对方的 sessionId，再输入内容发送。

### 3.5 测试断线通知

任意关闭一个连接（Postman 点 Disconnect，或 IDEA 客户端输入 `exit`），其余在线连接会收到：
```
[系统消息] xxx 下线，当前在线人数 N
```
验证 `afterConnectionClosed` 清理在线连接表并广播下线通知的逻辑生效。

</div>
