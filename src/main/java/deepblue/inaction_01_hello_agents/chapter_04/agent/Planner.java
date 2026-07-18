package deepblue.inaction_01_hello_agents.chapter_04.agent;

import com.alibaba.fastjson.JSONArray;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Plan-and-Solve 的规划器：一次性把用户问题拆解成一个有序的子任务列表。
 */
@Component
public class Planner {

    private static final String PLANNER_PROMPT_TEMPLATE = """
            你是一个顶级的AI规划专家。你的任务是将用户提出的复杂问题分解成一个由多个简单步骤组成的行动计划。
            请确保计划中的每个步骤都是一个独立的、可执行的子任务，并且严格按照逻辑顺序排列。
            你的输出必须是一个JSON数组，其中每个元素都是一个描述子任务的字符串。

            问题: %s

            请严格按照以下格式输出你的计划,```json与```作为前后缀是必要的:
            ```json
            ["步骤1", "步骤2", "步骤3"]
            ```
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    public List<String> plan(String question) {
        String prompt = PLANNER_PROMPT_TEMPLATE.formatted(question);

        System.out.println("--- 正在生成计划 ---");
        String responseText = think(prompt);
        if (responseText == null || responseText.isBlank()) {
            System.out.println("❌ 生成计划失败:LLM未能返回有效响应。");
            return Collections.emptyList();
        }
        System.out.println("✅ 计划已生成:\n" + responseText);

        try {
            String planStr = responseText.split("```json")[1].split("```")[0].trim();
            JSONArray planJson = JSONArray.parseArray(planStr);
            return planJson.toJavaList(String.class);
        } catch (Exception e) {
            System.out.println("❌ 解析计划时出错: " + e.getMessage());
            System.out.println("原始响应: " + responseText);
            return Collections.emptyList();
        }
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
