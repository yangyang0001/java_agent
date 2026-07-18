package deepblue.inaction_01_hello_agents.chapter_04.agent;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import deepblue.inaction_01_hello_agents.chapter_04.tool.ToolExecutor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 经典 ReAct 智能体：通过提示词工程显式驱动 Thought -> Action -> Observation 循环，
 * 每一步都要求模型输出纯文本的 Thought/Action，再由正则解析、交给 ToolExecutor 调度工具。
 * 与依赖模型原生 function-calling 协议的 ChatController 相互独立，互不影响。
 */
@Component
public class ReActAgent {

    private static final int MAX_STEPS = 5;

    private static final String REACT_PROMPT_TEMPLATE = """
            请注意，你是一个有能力调用外部工具的智能助手。

            可用工具如下:
            %s

            请严格按照以下格式进行回应:

            Thought: 你的思考过程，用于分析问题、拆解任务和规划下一步行动。
            Action: 你决定采取的行动，必须是以下格式之一:
            - `ToolName[ToolInput]`:调用一个可用工具。
            - `Finish[最终答案]`:当你认为已经获得最终答案时。
            - 当你收集到足够的信息，能够回答用户的最终问题时，你必须在Action:字段后使用 Finish[最终答案] 来输出最终答案。

            现在，请开始解决以下问题:
            Question: %s
            History: %s
            """;

    private static final Pattern THOUGHT_PATTERN = Pattern.compile("Thought:\\s*(.*?)(?=\\nAction:|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile("Action:\\s*(.*?)$", Pattern.DOTALL);
    private static final Pattern ACTION_CALL_PATTERN = Pattern.compile("(\\w+)\\[(.*)]", Pattern.DOTALL);
    private static final Pattern FINISH_PATTERN = Pattern.compile("Finish\\[(.*)]", Pattern.DOTALL);

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    @Resource
    private ToolExecutor toolExecutor;

    /**
     * 运行 ReAct 智能体来回答一个问题，返回 Finish[...] 中的最终答案。
     */
    public String run(String question) {
        List<String> history = new ArrayList<>();

        for (int step = 1; step <= MAX_STEPS; step++) {
            System.out.println("--- 第 " + step + " 步 ---");

            String prompt = REACT_PROMPT_TEMPLATE.formatted(
                    toolExecutor.getAvailableTools(),
                    question,
                    String.join("\n", history));

            String responseText = think(prompt);
            if (responseText == null || responseText.isBlank()) {
                return "错误:LLM未能返回有效响应。";
            }

            String thought = extract(THOUGHT_PATTERN, responseText);
            String action = extract(ACTION_PATTERN, responseText);

            if (thought != null) {
                System.out.println("🤔 思考: " + thought);
            }
            if (action == null) {
                System.out.println("警告:未能解析出有效的Action，流程终止。");
                return responseText;
            }

            Matcher finishMatcher = FINISH_PATTERN.matcher(action);
            if (finishMatcher.matches()) {
                String finalAnswer = finishMatcher.group(1).trim();
                System.out.println("🎉 最终答案: " + finalAnswer);
                return finalAnswer;
            }

            Matcher actionMatcher = ACTION_CALL_PATTERN.matcher(action);
            if (!actionMatcher.matches()) {
                System.out.println("警告:未能识别的Action格式 '" + action + "'。");
                history.add("Action: " + action);
                history.add("Observation: 错误:无法解析Action，请使用 ToolName[ToolInput] 或 Finish[最终答案] 的格式。");
                continue;
            }

            String toolName = actionMatcher.group(1);
            String toolInput = actionMatcher.group(2).trim();
            System.out.println("🎬 行动: " + toolName + "[" + toolInput + "]");

            Function<String, String> tool = toolExecutor.getTool(toolName);
            String observation = tool == null
                    ? "错误:未找到名为 '" + toolName + "' 的工具。"
                    : tool.apply(toolInput);
            System.out.println("👀 观察: " + observation);

            history.add("Action: " + action);
            history.add("Observation: " + observation);
        }

        System.out.println("已达到最大步数，流程终止。");
        return "已达到最大步数(" + MAX_STEPS + ")，流程终止。";
    }

    private String think(String prompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(commonConfig.getModel())
                .addUserMessage(prompt)
                .build();
        ChatCompletion completion = openAIClient.chat().completions().create(params);
        return completion.choices().get(0).message().content().orElse(null);
    }

    private String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
