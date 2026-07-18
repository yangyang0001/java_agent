package deepblue.inaction_01_hello_agents.chapter_04.agent;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Reflection 智能体：执行(Execution) -> 反思(Reflection) -> 优化(Refinement) 的
 * 迭代自我校正循环，直到评审员认为"无需改进"或达到最大迭代次数。
 */
@Component
public class ReflectionAgent {

    private static final int MAX_ITERATIONS = 3;

    private static final String NO_IMPROVEMENT_MARKER = "无需改进";

    private static final String INITIAL_PROMPT_TEMPLATE = """
            你是一位资深的Python程序员。请根据以下要求，编写一个Python函数。
            你的代码必须包含完整的函数签名、文档字符串，并遵循PEP 8编码规范。

            要求: %s

            请直接输出代码，不要包含任何额外的解释。
            """;

    private static final String REFLECT_PROMPT_TEMPLATE = """
            你是一位极其严格的代码评审专家和资深算法工程师，对代码的性能有极致的要求。
            你的任务是审查以下Python代码，并专注于找出其在算法效率上的主要瓶颈。

            # 原始任务:
            %s

            # 待审查的代码:
            ```python
            %s
            ```

            请分析该代码的时间复杂度，并思考是否存在一种算法上更优的解决方案来显著提升性能。
            如果存在，请清晰地指出当前算法的不足，并提出具体的、可行的改进算法建议（例如，使用筛法替代试除法）。
            如果代码在算法层面已经达到最优，才能回答"无需改进"。

            请直接输出你的反馈，不要包含任何额外的解释。
            """;

    private static final String REFINE_PROMPT_TEMPLATE = """
            你是一位资深的Python程序员。你正在根据一位代码评审专家的反馈来优化你的代码。

            # 原始任务:
            %s

            # 你上一轮尝试的代码:
            %s

            # 评审员的反馈:
            %s

            请根据评审员的反馈，生成一个优化后的新版本代码。
            你的代码必须包含完整的函数签名、文档字符串，并遵循PEP 8编码规范。
            请直接输出优化后的代码，不要包含任何额外的解释。
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    public String run(String task) {
        System.out.println("\n--- 开始处理任务 ---\n任务: " + task);
        Memory memory = new Memory();

        System.out.println("\n--- 正在进行初始尝试 ---");
        String initialCode = think(INITIAL_PROMPT_TEMPLATE.formatted(task));
        memory.addRecord(Memory.RecordType.EXECUTION, initialCode);

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            System.out.println("\n--- 第 " + i + "/" + MAX_ITERATIONS + " 轮迭代 ---");

            System.out.println("\n-> 正在进行反思...");
            String lastCode = memory.getLastExecution();
            String feedback = think(REFLECT_PROMPT_TEMPLATE.formatted(task, lastCode));
            memory.addRecord(Memory.RecordType.REFLECTION, feedback);

            if (feedback != null && feedback.contains(NO_IMPROVEMENT_MARKER)) {
                System.out.println("\n✅ 反思认为代码已无需改进，任务完成。");
                break;
            }

            System.out.println("\n-> 正在进行优化...");
            String refinedCode = think(REFINE_PROMPT_TEMPLATE.formatted(task, lastCode, feedback));
            memory.addRecord(Memory.RecordType.EXECUTION, refinedCode);
        }

        String finalCode = memory.getLastExecution();
        System.out.println("\n--- 任务完成 ---\n最终生成的代码:\n" + finalCode);
        return finalCode;
    }

    private String think(String prompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(commonConfig.getModel())
                .addUserMessage(prompt)
                .build();
        ChatCompletion completion = openAIClient.chat().completions().create(params);
        return completion.choices().get(0).message().content().orElse(null);
    }
}
