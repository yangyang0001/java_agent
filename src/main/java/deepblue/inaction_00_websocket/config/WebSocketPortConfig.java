package deepblue.inaction_00_websocket.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 给内嵌 Tomcat 额外开一个端口，让 WebSocket 用独立于 HTTP(8080) 的端口(8888)对外提供服务
@Configuration
public class WebSocketPortConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> websocketConnectorCustomizer() {
        return factory -> {
            // 新建一个 HTTP/1.1 协议的连接器（WebSocket 握手本质上是一次特殊的 HTTP 请求）
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(8888);
            // Connector 只是多加一个网络入口，和 8080 共用同一套 Servlet 上下文/映射关系，
            // 因此 8888 上除了 /ws/echo 也能访问到其他 HTTP 接口
            factory.addAdditionalTomcatConnectors(connector);
        };
    }
}
