package deepblue.inaction_01_hello_agents.chapter_06.agentscope;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 AgentScope 的 MsgHub:一个共享的消息广播枢纽，所有加入的智能体发言后
 * 都会广播给枢纽内的其他成员，成员之间不直接调用彼此，只通过枢纽收发消息。
 */
public class MsgHub {

    private final List<PlayerMessage> broadcastLog = new ArrayList<>();

    public void broadcast(String player, String content) {
        broadcastLog.add(new PlayerMessage(player, content));
        System.out.println("📢 [MsgHub广播] " + player + ": " + content);
    }

    public String getVisibleHistory() {
        if (broadcastLog.isEmpty()) {
            return "(暂无广播记录)";
        }
        StringBuilder sb = new StringBuilder();
        for (PlayerMessage message : broadcastLog) {
            sb.append("[").append(message.getPlayer()).append("] ").append(message.getContent()).append("\n");
        }
        return sb.toString();
    }

    public List<PlayerMessage> getLog() {
        return broadcastLog;
    }
}
