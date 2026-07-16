package deepblue.inaction_01_hello_agents.chapter_04.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class CommonConfig {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.base-url:}")
    private String baseUrl;

    @Value("${openai.model:}")
    private String model;

}
