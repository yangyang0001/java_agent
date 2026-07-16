package deepblue.inaction_01_hello_agents.chapter_04.controller;

import com.alibaba.fastjson.JSON;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import deepblue.inaction_01_hello_agents.chapter_04.entity.ChatMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final OpenAIClient openAIClient;
    private final CommonConfig commonConfig;

    public ChatController(OpenAIClient openAIClient, CommonConfig commonConfig) {
        this.openAIClient = openAIClient;
        this.commonConfig = commonConfig;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatMessage message) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(commonConfig.getModel())
                .addUserMessage(JSON.toJSONString(message)).build();

        ChatCompletion completion = openAIClient.chat().completions().create(params);

        return completion.choices().get(0).message().content().orElse("");
    }
}
