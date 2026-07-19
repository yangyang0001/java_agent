package deepblue.inaction_01_hello_agents.chapter_06._02_agentscope;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对应 AgentScope 的消息驱动多智能体协作范式:"三国狼人杀"演示。
 */
@RestController
public class AgentScopeController {

    @Resource
    private AgentScopeAgent agentScopeAgent;

    @PostMapping("/agentscope")
    public List<PlayerMessage> agentscope() {
        return agentScopeAgent.run();
    }
}
