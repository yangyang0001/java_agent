package deepblue.inaction_01_hello_agents.chapter_04.tool;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.function.Function;

@SpringBootTest
class ToolExecutorTest {

    @Resource
    private ToolExecutor toolExecutor;

    @Test
    void demoSearchAction() {
        System.out.println("\n--- 可用的工具 ---");
        System.out.println(toolExecutor.getAvailableTools());

        String toolName = "Search";
        String toolInput = "英伟达最新的GPU型号是什么";

        System.out.println("\n--- 执行 Action: " + toolName + "['" + toolInput + "'] ---");
        Function<String, String> tool = toolExecutor.getTool(toolName);
        if (tool != null) {
            String observation = tool.apply(toolInput);
            System.out.println("--- 观察 (Observation) ---");
            System.out.println(observation);
        } else {
            System.out.println("错误:未找到名为 '" + toolName + "' 的工具。");
        }
    }
}
