package deepblue.inaction_00_websocket.client;

import com.alibaba.fastjson.JSON;
import deepblue.inaction_00_websocket.common.WebsocketMsg;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import java.net.URI;
import java.util.Scanner;

/**
 * BROADCAST 模式专用客户端：发的内容会被服务端转发给除自己外的所有在线客户端。
 * 至少运行两个实例（比如在两个 IDEA 窗口 / 两次 Run 里各跑一个）才能看到互通效果。
 */
@ClientEndpoint
public class BroadcastClient {

    @OnOpen
    public void onOpen(Session session) {
        // 注意：这里的 session.getId() 是客户端本地连接 ID，不是服务端分配的 sessionId；
        // 真正的服务端 sessionId 会随后以 [系统消息] 的形式在 onMessage 里收到
        System.out.println("[连接成功]，等待服务端下发 sessionId...");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("[收到消息] " + message);
    }

    @OnClose
    public void onClose(CloseReason reason) {
        System.out.println("[连接关闭] " + reason);
    }

    public static void main(String[] args) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        Session session = container.connectToServer(new BroadcastClient(), new URI("ws://localhost:8888/ws/broadcast"));

        System.out.println("输入内容后回车发送（连的是 /ws/broadcast，服务端会自动按路径识别为 BROADCAST 模式，输入 exit 退出）：");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if ("exit".equalsIgnoreCase(line)) {
                break;
            }
            // 不需要再手动设置 mode，服务端会根据连接路径 /ws/broadcast 自动识别
            WebsocketMsg message = new WebsocketMsg();
            message.setContent(line);
            session.getBasicRemote().sendText(JSON.toJSONString(message));
        }
        session.close();
    }
}
