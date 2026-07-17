package deepblue.inaction_00_websocket.config;

import deepblue.inaction_00_websocket.EchoWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// 开启 Spring 的 WebSocket 支持，并把具体的消息处理器（Handler）注册到指定路径上
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // EchoWebSocketHandler 现在是 @Component，需要维护在线连接的 Map，
    // 交给 Spring 管理成单例，而不是每次都 new 一个新的（否则每个连接都会有自己独立的在线列表）
    private final EchoWebSocketHandler echoWebSocketHandler;

    public WebSocketConfig(EchoWebSocketHandler echoWebSocketHandler) {
        this.echoWebSocketHandler = echoWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 同一个 Handler 注册到 4 个不同路径上，每个路径对应一种默认路由模式：
        // 客户端连哪个 URI，Handler 就用哪个路径名推断 mode（消息体里显式带 mode 字段则以字段为准）
        // setAllowedOrigins("*") 关闭跨域校验，方便本地用 Postman / 浏览器 / Java 客户端直接测试
        registry.addHandler(echoWebSocketHandler, "/ws/echo", "/ws/process", "/ws/broadcast", "/ws/unicast")
                .setAllowedOrigins("*");
    }
}
