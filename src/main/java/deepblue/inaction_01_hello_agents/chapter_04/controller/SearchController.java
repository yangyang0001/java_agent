package deepblue.inaction_01_hello_agents.chapter_04.controller;

import deepblue.inaction_01_hello_agents.chapter_04.entity.SearchResult;
import deepblue.inaction_01_hello_agents.chapter_04.tool.SerpApiSearchTool;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    @Resource
    private SerpApiSearchTool serpApiSearchTool;

    @PostMapping("/search")
    public Object search(@RequestParam String query) {

        SearchResult result = new SearchResult();
        result.setQuery(query);
        result.setResponse(serpApiSearchTool.search(query));

        return result;
    }
}
