package deepblue.inaction_01_hello_agents.chapter_04.tool;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import deepblue.inaction_01_hello_agents.chapter_04.config.CommonConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 一个基于SerpApi的实战网页搜索引擎工具。
 * 它会智能地解析搜索结果，优先返回直接答案或知识图谱信息。
 */
@Component
public class SerpApiSearchTool {

    private static final String SERPAPI_ENDPOINT = "https://serpapi.com/search.json";

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private CommonConfig commonConfig;

    public String search(String query) {
        System.out.println("🔍 正在执行 [SerpApi] 网页搜索: " + query);
        try {
            if (!StringUtils.hasText(commonConfig.getSerpApiKey())) {
                return "错误:SERPAPI_API_KEY 未在配置中设置。";
            }

            URI uri = UriComponentsBuilder.fromHttpUrl(SERPAPI_ENDPOINT)
                    .queryParam("engine", "google")
                    .queryParam("q", query)
                    .queryParam("api_key", commonConfig.getSerpApiKey())
                    .queryParam("gl", "cn") // 国家代码
                    .queryParam("hl", "zh-cn") // 语言代码
                    .build()
                    .encode()
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            JSONObject results = JSONObject.parseObject(response);

            // 智能解析:优先寻找最直接的答案
            JSONArray answerBoxList = results.getJSONArray("answer_box_list");
            if (answerBoxList != null) {
                return answerBoxList.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("\n"));
            }

            JSONObject answerBox = results.getJSONObject("answer_box");
            if (answerBox != null && answerBox.containsKey("answer")) {
                return answerBox.getString("answer");
            }

            JSONObject knowledgeGraph = results.getJSONObject("knowledge_graph");
            if (knowledgeGraph != null && knowledgeGraph.containsKey("description")) {
                return knowledgeGraph.getString("description");
            }

            JSONArray organicResults = results.getJSONArray("organic_results");
            if (organicResults != null && !organicResults.isEmpty()) {
                // 如果没有直接答案，则返回前三个有机结果的摘要
                int limit = Math.min(3, organicResults.size());
                return IntStream.range(0, limit)
                        .mapToObj(i -> {
                            JSONObject res = organicResults.getJSONObject(i);
                            String title = res.getString("title");
                            String snippet = res.getString("snippet");
                            return "[" + (i + 1) + "] " + (title == null ? "" : title)
                                    + "\n" + (snippet == null ? "" : snippet);
                        })
                        .collect(Collectors.joining("\n\n"));
            }

            return "对不起，没有找到关于 '" + query + "' 的信息。";
        } catch (Exception e) {
            return "搜索时发生错误: " + e.getMessage();
        }
    }
}
