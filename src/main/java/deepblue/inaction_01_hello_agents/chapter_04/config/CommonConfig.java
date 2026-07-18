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

    @Value("${serpapi.api-key:f9c09a5d868eedd5b9d2f280c03a331adc9911996a63ce283b9075bd15524842}")
    private String serpApiKey;

}
