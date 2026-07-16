package deepblue.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OpenAiConfig {

    @Resource
    private CommonConfig commonConfig;

    @Bean
    public OpenAIClient openAIClient() {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(commonConfig.getApiKey());

        if (StringUtils.hasText(commonConfig.getBaseUrl())) {
            builder.baseUrl(commonConfig.getBaseUrl());
        }

        return builder.build();
    }
}
