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
 * UNICAST 模式专用客户端：只把内容发给指定的目标 sessionId，其他在线客户端收不到。
 * 需要跑两个实例：先各自记下连接成功后打印的 sessionId，再把对方的 sessionId 填给这边当目标。
 */
@ClientEndpoint
public class UnicastClient {

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
        Session session = container.connectToServer(new UnicastClient(), new URI("ws://localhost:8888/ws/unicast"));

        Scanner scanner = new Scanner(System.in);
        System.out.println("请输入对方的 sessionId（从对方控制台的 [系统消息] 里复制）：");
        String targetSessionId = scanner.nextLine().trim();

        System.out.println("输入内容后回车发送（连的是 /ws/unicast，服务端会自动按路径识别为 UNICAST 模式，只有 "
                + targetSessionId + " 能收到，输入 exit 退出）：");
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if ("exit".equalsIgnoreCase(line)) {
                break;
            }
            // mode 不用手动设置（按路径 /ws/unicast 自动识别），但 to 字段（目标 sessionId）没法从路径推断，必须手动带上
            WebsocketMsg message = new WebsocketMsg();
            message.setTo(targetSessionId);
            message.setContent(line);
            session.getBasicRemote().sendText(JSON.toJSONString(message));
        }
        session.close();
    }
}
