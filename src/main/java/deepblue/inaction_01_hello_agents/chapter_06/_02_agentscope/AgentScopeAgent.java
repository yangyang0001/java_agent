package deepblue.inaction_01_hello_agents.chapter_06._02_agentscope;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟 AgentScope 的消息驱动多智能体协作:以"三国狼人杀"为例，
 * 所有玩家智能体只通过 {@link MsgHub} 收发消息，不直接互相调用彼此的方法。
 */
@Component
public class AgentScopeAgent {

    private static final int MAX_ROUNDS = 2;

    private static final Map<String, String> ROLE_OF = new LinkedHashMap<>();

    static {
        ROLE_OF.put("曹操", "狼人");
        ROLE_OF.put("孙权", "狼人");
        ROLE_OF.put("刘备", "村民");
        ROLE_OF.put("貂蝉", "村民");
        ROLE_OF.put("诸葛亮", "预言家");
    }

    private static final String NIGHT_PROMPT_TEMPLATE = """
            这是一局「三国杀·狼人杀」游戏，你扮演 %s，你的秘密身份是「狼人」。
            现在是夜晚，狼人需要在存活玩家中选择一名非狼人角色作为击杀目标。

            存活玩家: %s

            请只输出你要击杀的一个玩家姓名，不要输出其他任何内容。
            """;

    private static final String SPEECH_PROMPT_TEMPLATE = """
            这是一局「三国杀·狼人杀」游戏，你扮演 %s，你的秘密身份是「%s」(此身份只有你自己知道，发言时不要直接暴露)。

            当前存活玩家: %s

            目前为止的公开发言记录:
            %s

            请发表一段简短的发言，尝试推理场上局势并影响其他玩家的判断:
            """;

    private static final String VOTE_PROMPT_TEMPLATE = """
            这是一局「三国杀·狼人杀」游戏，你扮演 %s，你的秘密身份是「%s」。

            当前存活玩家: %s

            目前为止的公开发言记录:
            %s

            请只输出你要投票放逐的一个玩家姓名，不要输出其他任何内容。
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    public List<PlayerMessage> run() {
        MsgHub hub = new MsgHub();
        Map<String, Boolean> alive = new LinkedHashMap<>();
        ROLE_OF.keySet().forEach(player -> alive.put(player, true));

        hub.broadcast("主持人", "游戏开始，存活玩家: " + alivePlayers(alive));

        for (int round = 1; round <= MAX_ROUNDS && !isGameOver(alive); round++) {
            System.out.println("--- 第 " + round + " 天 ---");

            String victim = nightPhase(alive);
            if (victim != null) {
                alive.put(victim, false);
                hub.broadcast("主持人", "天亮了，昨晚 " + victim + " 死亡。当前存活玩家: " + alivePlayers(alive));
            }
            if (isGameOver(alive)) {
                break;
            }

            for (String player : alivePlayers(alive)) {
                String role = ROLE_OF.get(player);
                String prompt = SPEECH_PROMPT_TEMPLATE.formatted(
                        player, role, alivePlayers(alive), hub.getVisibleHistory());
                String speech = think(prompt);
                hub.broadcast(player, speech == null ? "(沉默)" : speech.trim());
            }

            String voted = votePhase(hub, alive);
            if (voted != null) {
                alive.put(voted, false);
                hub.broadcast("主持人", voted + " 被投票放逐出局。当前存活玩家: " + alivePlayers(alive));
            }
        }

        hub.broadcast("主持人", isGameOver(alive) ? "游戏结束: " + winner(alive) : "已达到最大天数，游戏强制结束。");
        return hub.getLog();
    }

    private String nightPhase(Map<String, Boolean> alive) {
        String firstAliveWolf = ROLE_OF.entrySet().stream()
                .filter(e -> "狼人".equals(e.getValue()) && alive.get(e.getKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (firstAliveWolf == null) {
            return null;
        }

        String prompt = NIGHT_PROMPT_TEMPLATE.formatted(firstAliveWolf, alivePlayers(alive));
        String response = think(prompt);
        return response == null ? null : matchPlayer(response, alive);
    }

    private String votePhase(MsgHub hub, Map<String, Boolean> alive) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        for (String player : alivePlayers(alive)) {
            String role = ROLE_OF.get(player);
            String prompt = VOTE_PROMPT_TEMPLATE.formatted(
                    player, role, alivePlayers(alive), hub.getVisibleHistory());
            String response = think(prompt);
            String votedPlayer = response == null ? null : matchPlayer(response, alive);
            if (votedPlayer != null) {
                votes.merge(votedPlayer, 1, Integer::sum);
            }
        }
        return votes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String matchPlayer(String text, Map<String, Boolean> alive) {
        return alivePlayers(alive).stream()
                .filter(text::contains)
                .findFirst()
                .orElse(null);
    }

    private List<String> alivePlayers(Map<String, Boolean> alive) {
        return alive.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean isGameOver(Map<String, Boolean> alive) {
        long aliveWolves = alivePlayers(alive).stream().filter(p -> "狼人".equals(ROLE_OF.get(p))).count();
        long aliveOthers = alivePlayers(alive).size() - aliveWolves;
        return aliveWolves == 0 || aliveWolves >= aliveOthers;
    }

    private String winner(Map<String, Boolean> alive) {
        long aliveWolves = alivePlayers(alive).stream().filter(p -> "狼人".equals(ROLE_OF.get(p))).count();
        return aliveWolves == 0 ? "好人阵营胜利" : "狼人阵营胜利";
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
