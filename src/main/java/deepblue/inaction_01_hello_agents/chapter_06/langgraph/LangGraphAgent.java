package deepblue.inaction_01_hello_agents.chapter_06.langgraph;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 模拟 LangGraph 的状态图范式:"理解 -> 搜索 -> 回答" 三个节点，
 * 当"回答"节点认为信息不足时，会通过条件边回到"理解"节点重新来一轮(体现图对环的支持)。
 */
@Component
public class LangGraphAgent {

    private static final int MAX_RETRIES = 1;

    private static final String UNCERTAIN_MARKER = "不确定";

    private static final String UNDERSTAND_PROMPT_TEMPLATE = """
            请提炼用户问题背后真正想问的核心意图，用一句话概括，不要展开回答。

            用户问题: %s
            """;

    private static final String SEARCH_PROMPT_TEMPLATE = """
            假设你是一个搜索引擎，请针对以下检索意图，给出3条简短的相关信息摘要(可以是你已知的事实性知识):

            检索意图: %s
            """;

    private static final String ANSWER_PROMPT_TEMPLATE = """
            请结合以下信息，回答用户的原始问题。
            如果检索到的信息不足以支撑一个确定的回答，请在你的回答开头注明"%s"。

            用户问题: %s
            理解后的意图: %s
            检索到的信息:
            %s
            """;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    private StateGraph graph;

    @PostConstruct
    public void init() {
        graph = new StateGraph();
        graph.addNode("understand", this::understand);
        graph.addNode("search", this::search);
        graph.addNode("answer", this::answer);

        graph.setEntryPoint("understand");
        graph.addEdge("understand", "search");
        graph.addEdge("search", "answer");
        graph.addConditionalEdge("answer", state -> {
            boolean uncertain = state.getAnswer() != null && state.getAnswer().contains(UNCERTAIN_MARKER);
            if (uncertain && state.getRetryCount() < MAX_RETRIES) {
                state.setRetryCount(state.getRetryCount() + 1);
                System.out.println("🔁 回答不确定，回到 understand 节点重新理解(第 " + state.getRetryCount() + " 次重试)。");
                return "understand";
            }
            return StateGraph.END;
        });
    }

    public GraphState run(String question) {
        GraphState state = new GraphState();
        state.setQuestion(question);
        return graph.invoke(state);
    }

    private void understand(GraphState state) {
        String understanding = think(UNDERSTAND_PROMPT_TEMPLATE.formatted(state.getQuestion()));
        state.setUnderstanding(understanding);
        System.out.println("🧠 理解: " + understanding);
    }

    private void search(GraphState state) {
        String searchResult = think(SEARCH_PROMPT_TEMPLATE.formatted(state.getUnderstanding()));
        state.setSearchResult(searchResult);
        System.out.println("🔍 检索: " + searchResult);
    }

    private void answer(GraphState state) {
        String answer = think(ANSWER_PROMPT_TEMPLATE.formatted(
                UNCERTAIN_MARKER, state.getQuestion(), state.getUnderstanding(), state.getSearchResult()));
        state.setAnswer(answer);
        System.out.println("💬 回答: " + answer);
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
