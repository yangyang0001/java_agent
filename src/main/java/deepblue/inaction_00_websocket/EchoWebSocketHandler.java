package deepblue.inaction_00_websocket;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_00_websocket.common.WebsocketMode;
import deepblue.inaction_00_websocket.common.WebsocketMsg;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 统一消息路由的 WebSocket 处理器：客户端在消息里带上 mode 字段，
// 服务器据此决定这条消息该发给谁，演示 ECHO / PROCESS / UNICAST / BROADCAST 四种路由策略
@Component
public class EchoWebSocketHandler extends TextWebSocketHandler {

    // 按 sessionId 索引在线连接，UNICAST 需要按 id 精确查找目标会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        // 告诉客户端自己的 sessionId，方便测试 UNICAST 时知道要把消息发给谁
        session.sendMessage(new TextMessage("[系统消息] 你的 sessionId = " + session.getId()));
        broadcastSystemMessage(session.getId() + " 上线，当前在线人数 " + sessions.size(), session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        broadcastSystemMessage(session.getId() + " 下线，当前在线人数 " + sessions.size(), session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebsocketMsg websocketMsg = JSON.parseObject(message.getPayload(), WebsocketMsg.class);
        WebsocketMode mode = resolveMode(session, websocketMsg);
        if (websocketMsg == null || mode == null) {
            session.sendMessage(new TextMessage(
                    "[系统消息] 无法识别 mode：既没有在消息里带 mode 字段，连接路径也不是 /ws/echo|process|broadcast|unicast 之一"));
            return;
        }
        websocketMsg.setFrom(session.getId());

        switch (mode) {
            case ECHO -> handleEcho(session, websocketMsg);
            case PROCESS -> handleProcess(session, websocketMsg);
            case UNICAST -> handleUnicast(session, websocketMsg);
            case BROADCAST -> handleBroadcast(session, websocketMsg);
        }
    }

    // 消息体里显式带 mode 字段就用它；否则按连接时用的路径（比如 /ws/broadcast）推断默认 mode，
    // 这样每个 Client 只要连上专属的 URI，就不用再手动指定 mode 了
    private WebsocketMode resolveMode(WebSocketSession session, WebsocketMsg websocketMsg) {
        if (websocketMsg != null && websocketMsg.getMode() != null) {
            return websocketMsg.getMode();
        }
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        try {
            return WebsocketMode.valueOf(lastSegment.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ECHO：原样发回给发送者自己，不经过任何处理，也不转发给别人
    private void handleEcho(WebSocketSession session, WebsocketMsg websocketMsg) throws Exception {
        session.sendMessage(new TextMessage("echo: " + websocketMsg.getContent()));
    }

    // PROCESS：服务器做一点本地处理（这里用字符串反转模拟业务逻辑），结果只发回给发送者
    private void handleProcess(WebSocketSession session, WebsocketMsg websocketMsg) throws Exception {
        String processed = new StringBuilder(websocketMsg.getContent()).reverse().toString();
        session.sendMessage(new TextMessage("[处理结果] " + processed));
    }

    // UNICAST：按 to 字段里的 sessionId，只转发给指定的那一个客户端
    private void handleUnicast(WebSocketSession session, WebsocketMsg websocketMsg) throws Exception {
        WebSocketSession target = sessions.get(websocketMsg.getTo());
        if (target == null || !target.isOpen()) {
            session.sendMessage(new TextMessage("[系统消息] 目标 sessionId 不存在或已下线: " + websocketMsg.getTo()));
            return;
        }
        target.sendMessage(new TextMessage("[私聊 from " + session.getId() + "] " + websocketMsg.getContent()));
    }

    // BROADCAST：转发给除发送者外的所有在线客户端
    private void handleBroadcast(WebSocketSession session, WebsocketMsg websocketMsg) throws Exception {
        TextMessage outbound = new TextMessage("[广播 from " + session.getId() + "] " + websocketMsg.getContent());
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen() && !s.getId().equals(session.getId())) {
                s.sendMessage(outbound);
            }
        }
    }

    // 上线/下线通知：发给除当事人外的所有在线客户端
    private void broadcastSystemMessage(String text, String excludeSessionId) throws Exception {
        TextMessage outbound = new TextMessage("[系统消息] " + text);
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen() && !s.getId().equals(excludeSessionId)) {
                s.sendMessage(outbound);
            }
        }
    }
}
