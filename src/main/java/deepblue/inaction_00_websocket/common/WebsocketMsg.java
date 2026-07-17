package deepblue.inaction_00_websocket.common;

import lombok.Data;

// 客户端和服务端之间约定的统一消息格式，mode 字段决定服务器怎么路由这条消息
@Data
public class WebsocketMsg {
    // 路由模式：ECHO / PROCESS / UNICAST / BROADCAST
    private WebsocketMode mode;
    // 发送者的 sessionId，由服务器在收到消息时自动填充，客户端不需要传
    private String from;
    // 目标 sessionId，只有 UNICAST 模式需要
    private String to;
    // 消息正文
    private String content;
}
