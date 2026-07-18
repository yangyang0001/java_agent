package deepblue.inaction_01_hello_agents.chapter_04.controller;

import deepblue.inaction_01_hello_agents.chapter_04.agent.ReflectionAgent;
import deepblue.inaction_01_hello_agents.chapter_04.entity.ChatMessage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reflection 的对话入口：执行 -> 反思 -> 优化 循环，
 * 与 ReAct(ChatController/ReActController)、Plan-and-Solve(PlanAndSolveController) 的入口相互独立。
 */
@RestController
public class ReflectionController {

    @Resource
    private ReflectionAgent reflectionAgent;

    @PostMapping("/reflection")
    public String reflect(@RequestBody ChatMessage message) {
        return reflectionAgent.run(message.getMessage());
    }
}
