package deepblue.inaction_01_hello_agents.chapter_04.agent;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Plan-and-Solve 智能体：协调 Planner 与 Executor，先规划、后执行。
 */
@Component
public class PlanAndSolveAgent {

    @Resource
    private Planner planner;

    @Resource
    private Executor executor;

    public String run(String question) {
        System.out.println("\n--- 开始处理问题 ---\n问题: " + question);

        List<String> plan = planner.plan(question);
        if (plan.isEmpty()) {
            System.out.println("\n--- 任务终止 ---\n无法生成有效的行动计划。");
            return "无法生成有效的行动计划。";
        }

        String finalAnswer = executor.execute(question, plan);
        System.out.println("\n--- 任务完成 ---\n最终答案: " + finalAnswer);
        return finalAnswer;
    }
}
