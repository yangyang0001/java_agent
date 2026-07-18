package deepblue.inaction_01_hello_agents.chapter_04.agent;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Plan-and-Solve 的执行器：严格按照 Planner 生成的计划逐步求解，
 * 并维护跨步骤的历史结果，作为下一步的上下文输入。
 */
@Component
public class Executor {

    private static final String EXECUTOR_PROMPT_TEMPLATE = """
            你是一位顶级的AI执行专家。你的任务是严格按照给定的计划，一步步地解决问题。
            你将收到原始问题、完整的计划、以及到目前为止已经完成的步骤和结果。
            请你专注于解决"当前步骤"，并仅输出该步骤的最终答案，不要输出任何额外的解释或对话。

            # 原始问题:
            %s

            # 完整计划:
            %s

            # 历史步骤与结果:
            %s

            # 当前步骤:
            %s

            请仅输出针对"当前步骤"的回答:
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    public String execute(String question, List<String> plan) {
        StringBuilder history = new StringBuilder();
        String responseText = "";

        System.out.println("\n--- 正在执行计划 ---");

        for (int i = 0; i < plan.size(); i++) {
            String step = plan.get(i);
            System.out.println("\n-> 正在执行步骤 " + (i + 1) + "/" + plan.size() + ": " + step);

            String prompt = EXECUTOR_PROMPT_TEMPLATE.formatted(
                    question,
                    plan,
                    history.length() == 0 ? "无" : history.toString(),
                    step);

            responseText = think(prompt);
            if (responseText == null) {
                responseText = "";
            }

            history.append("步骤 ").append(i + 1).append(": ").append(step)
                    .append("\n结果: ").append(responseText).append("\n\n");

            System.out.println("✅ 步骤 " + (i + 1) + " 已完成，结果: " + responseText);
        }

        return responseText;
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
