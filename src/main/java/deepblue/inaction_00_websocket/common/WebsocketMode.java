package deepblue.inaction_00_websocket.common;

// 服务器收到消息后可选的四种路由策略
public enum WebsocketMode {
    // 原样发回给发送者自己
    ECHO,
    // 服务器做一点本地处理后，只把结果发回给发送者（这里用字符串反转模拟"处理"）
    PROCESS,
    // 按目标 sessionId，只转发给指定的那一个在线客户端
    UNICAST,
    // 转发给除发送者外的所有在线客户端
    BROADCAST
}
