package deepblue.inaction_01_hello_agents.chapter_06.camel;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 CAMEL 的角色扮演双智能体协作(Inception Prompting):
 * "指导者"只负责下达指令，"执行者"只负责给出方案，两者通过提示词被"锁定"在各自的角色设定里。
 */
@Component
public class CamelAgent {

    private static final int MAX_TURNS = 4;

    private static final String DONE_MARKER = "<CAMEL_TASK_DONE>";

    private static final String INSTRUCTOR_PROMPT_TEMPLATE = """
            你正在进行一场角色扮演协作。
            你的角色是「心理学家」，担任「指导者」，绝不能自己完成任务，只能向「执行者」下达指令。
            对方的角色是「作家」，担任「执行者」，负责根据你的指令给出具体方案。

            总任务: %s

            此前的协作记录:
            %s

            请输出你下一步的指令，格式为:
            Instruction: <你的具体指令>

            如果你认为总任务已经完整完成，请只输出 %s，不要输出其他任何内容。
            """;

    private static final String ASSISTANT_PROMPT_TEMPLATE = """
            你正在进行一场角色扮演协作。
            你的角色是「作家」，担任「执行者」，只需要严格按照「指导者」最新给出的指令给出方案，不要反过来提出新指令。
            对方的角色是「心理学家」，担任「指导者」。

            总任务: %s

            此前的协作记录:
            %s

            指导者刚刚给出的指令:
            %s

            请输出你的方案，格式为:
            Solution: <你的具体方案内容>
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    public List<DialogueMessage> run(String task) {
        List<DialogueMessage> transcript = new ArrayList<>();

        for (int turn = 1; turn <= MAX_TURNS; turn++) {
            System.out.println("--- 第 " + turn + " 轮协作 ---");

            String instruction = think(INSTRUCTOR_PROMPT_TEMPLATE.formatted(
                    task, formatTranscript(transcript), DONE_MARKER));
            if (instruction == null || instruction.isBlank()) {
                break;
            }
            System.out.println("🧑‍⚕️ [心理学家-指导者] " + instruction);
            transcript.add(new DialogueMessage("心理学家(指导者)", instruction));

            if (instruction.contains(DONE_MARKER)) {
                System.out.println("✅ 指导者判定任务已完成。");
                break;
            }

            String solution = think(ASSISTANT_PROMPT_TEMPLATE.formatted(
                    task, formatTranscript(transcript), instruction));
            if (solution == null || solution.isBlank()) {
                solution = "(未能获取到有效方案)";
            }
            System.out.println("✍️ [作家-执行者] " + solution);
            transcript.add(new DialogueMessage("作家(执行者)", solution));
        }

        return transcript;
    }

    private String formatTranscript(List<DialogueMessage> transcript) {
        if (transcript.isEmpty()) {
            return "(暂无记录)";
        }
        StringBuilder sb = new StringBuilder();
        for (DialogueMessage message : transcript) {
            sb.append("[").append(message.getSpeaker()).append("] ").append(message.getContent()).append("\n");
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
