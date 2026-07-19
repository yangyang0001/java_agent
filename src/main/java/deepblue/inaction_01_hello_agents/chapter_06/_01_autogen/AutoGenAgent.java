package deepblue.inaction_01_hello_agents.chapter_06._01_autogen;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 AutoGen 的 RoundRobinGroupChat:多个具备固定角色人设的智能体按顺序轮流发言，
 * 每一轮都能看到此前的完整对话记录，直到"审查员"给出 APPROVE 或达到最大轮次。
 */
@Component
public class AutoGenAgent {

    private static final int MAX_ROUNDS = 3;

    private static final String APPROVE_MARKER = "APPROVE";

    private static final String[] ROLES = {"产品经理", "工程师", "审查员"};

    private static final String TURN_PROMPT_TEMPLATE = """
            你正在参与一场软件开发团队的圆桌会议，扮演角色:%s。

            团队角色分工:
            - 产品经理:负责把用户需求整理成清晰的功能点。
            - 工程师:负责根据产品经理的功能点给出具体的技术实现方案或代码。
            - 审查员:负责审查工程师的方案，指出问题；如果方案已经足够好，必须在发言末尾单独输出 %s。

            用户需求: %s

            此前的会议记录:
            %s

            请以「%s」的身份，输出你这一轮的发言(不要重复此前的记录，不要扮演其他角色):
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    public List<GroupChatMessage> run(String requirement) {
        List<GroupChatMessage> transcript = new ArrayList<>();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            System.out.println("--- 第 " + round + " 轮圆桌发言 ---");

            for (String role : ROLES) {
                String prompt = TURN_PROMPT_TEMPLATE.formatted(
                        role, APPROVE_MARKER, requirement, formatTranscript(transcript), role);

                String content = think(prompt);
                if (content == null || content.isBlank()) {
                    content = "(未能获取到有效发言)";
                }
                System.out.println("🗣 [" + role + "] " + content);
                transcript.add(new GroupChatMessage(role, content));

                if ("审查员".equals(role) && content.contains(APPROVE_MARKER)) {
                    System.out.println("✅ 审查员已通过，会议结束。");
                    return transcript;
                }
            }
        }

        System.out.println("已达到最大轮次(" + MAX_ROUNDS + ")，会议结束。");
        return transcript;
    }

    private String formatTranscript(List<GroupChatMessage> transcript) {
        if (transcript.isEmpty()) {
            return "(暂无发言)";
        }
        StringBuilder sb = new StringBuilder();
        for (GroupChatMessage message : transcript) {
            sb.append("[").append(message.getRole()).append("] ").append(message.getContent()).append("\n");
        }
        return sb.toString();
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
