package deepblue.inaction_01_hello_agents.chapter_04.controller;

import deepblue.inaction_01_hello_agents.chapter_04.agent.ReActAgent;
import deepblue.inaction_01_hello_agents.chapter_04.entity.ChatMessage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经典 ReAct(提示词工程 + 正则解析 Thought/Action) 的对话入口，
 * 与承载原生 function-calling ReAct 循环的 ChatController 相互独立。
 */
@RestController
public class ReActController {

    @Resource
    private ReActAgent reActAgent;

    @PostMapping("/react")
    public String react(@RequestBody ChatMessage message) {
        return reActAgent.run(message.getMessage());
    }
}
