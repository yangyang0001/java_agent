package deepblue.inaction_01_hello_agents.chapter_04.controller;

import deepblue.inaction_01_hello_agents.chapter_04.agent.PlanAndSolveAgent;
import deepblue.inaction_01_hello_agents.chapter_04.entity.ChatMessage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan-and-Solve 的对话入口：先一次性规划，再逐步执行，
 * 与 ReAct 的两套实现(ChatController/ReActController)相互独立。
 */
@RestController
public class PlanAndSolveController {

    @Resource
    private PlanAndSolveAgent planAndSolveAgent;

    @PostMapping("/plan-and-solve")
    public String planAndSolve(@RequestBody ChatMessage message) {
        return planAndSolveAgent.run(message.getMessage());
    }
}
