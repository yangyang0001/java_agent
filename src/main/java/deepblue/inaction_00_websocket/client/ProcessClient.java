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
 * PROCESS 模式专用客户端：服务端收到内容后先做本地处理（示例里是反转字符串），
 * 再把处理结果发回来，只回给自己。在 IDEA 里直接运行 main 方法，输入内容回车即可。
 */
@ClientEndpoint
public class ProcessClient {

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
        Session session = container.connectToServer(new ProcessClient(), new URI("ws://localhost:8888/ws/process"));

        System.out.println("输入内容后回车发送（连的是 /ws/process，服务端会自动按路径识别为 PROCESS 模式，输入 exit 退出）：");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if ("exit".equalsIgnoreCase(line)) {
                break;
            }
            // 不需要再手动设置 mode，服务端会根据连接路径 /ws/process 自动识别
            WebsocketMsg message = new WebsocketMsg();
            message.setContent(line);
            session.getBasicRemote().sendText(JSON.toJSONString(message));
        }
        session.close();
    }
}
