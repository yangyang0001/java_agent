package deepblue.inaction_01_hello_agents.chapter_04.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import deepblue.inaction_01_hello_agents.chapter_04.entity.ChatMessage;
import deepblue.inaction_01_hello_agents.chapter_04.tool.SerpApiSearchTool;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct: 模型在 Reasoning(推理) 与 Acting(调用工具) 之间循环，
 * 直到不再请求工具调用为止，才把最终答案返回给调用方。
 */
@RestController
public class ChatController {

    private static final String WEB_SEARCH_TOOL = "web_search";

    private static final int MAX_REACT_ROUNDS = 5;

    @Resource
    private OpenAIClient openAIClient;

    @Resource
    private CommonConfig commonConfig;

    @Resource
    private SerpApiSearchTool serpApiSearchTool;

    @PostMapping("/chat")
    public String chat(@RequestBody ChatMessage message) {
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(commonConfig.getModel())
                .addUserMessage(JSON.toJSONString(message))
                .addFunctionTool(webSearchFunctionDefinition());

        for (int round = 0; round < MAX_REACT_ROUNDS; round++) {
            ChatCompletion completion = openAIClient.chat().completions().create(params.build());
            ChatCompletionMessage assistantMessage = completion.choices().get(0).message();

            List<ChatCompletionMessageToolCall> toolCalls = assistantMessage.toolCalls().orElse(List.of());
            if (toolCalls.isEmpty()) {
                return assistantMessage.content().orElse("");
            }

            params.addMessage(assistantMessage);
            for (ChatCompletionMessageToolCall toolCall : toolCalls) {
                if (!toolCall.isFunction()) {
                    continue;
                }
                ChatCompletionMessageFunctionToolCall functionToolCall = toolCall.asFunction();
                String toolResult = executeToolCall(functionToolCall.function());
                params.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(functionToolCall.id())
                        .content(toolResult)
                        .build());
            }
        }

        return "已达到最大推理轮数(" + MAX_REACT_ROUNDS + ")，仍未得到最终答案。";
    }

    private String executeToolCall(ChatCompletionMessageFunctionToolCall.Function function) {
        if (!WEB_SEARCH_TOOL.equals(function.name())) {
            return "错误: 未知工具 " + function.name();
        }
        String query = JSONObject.parseObject(function.arguments()).getString("query");
        return serpApiSearchTool.search(query);
    }

    private FunctionDefinition webSearchFunctionDefinition() {
        Map<String, Object> queryProperty = new LinkedHashMap<>();
        queryProperty.put("type", "string");
        queryProperty.put("description", "要在网络上搜索的查询内容，例如：今天大阪的天气怎么样");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", queryProperty);

        FunctionParameters parameters = FunctionParameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(properties))
                .putAdditionalProperty("required", JsonValue.from(List.of("query")))
                .build();

        return FunctionDefinition.builder()
                .name(WEB_SEARCH_TOOL)
                .description("当需要回答天气、新闻、股价等实时或模型自身知识无法覆盖的问题时，调用网页搜索引擎获取信息。")
                .parameters(parameters)
                .build();
    }
}
