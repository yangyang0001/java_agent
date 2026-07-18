package deepblue.inaction_01_hello_agents.chapter_04.controller;

import deepblue.inaction_01_hello_agents.chapter_04.entity.SearchResult;
import deepblue.inaction_01_hello_agents.chapter_04.tool.ToolExecutor;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Function;

/**
 * 通过 ToolExecutor 调度已注册的工具，用于单独验证 Action -> Observation 流程，
 * 与承载 ReAct 对话循环的 ChatController 相互独立。
 */
@RestController
public class SearchController {

    @Resource
    private ToolExecutor toolExecutor;

    @GetMapping("/tools")
    public String listTools() {
        return toolExecutor.getAvailableTools();
    }

    @PostMapping("/search")
    public Object search(@RequestParam(defaultValue = "Search") String tool, @RequestParam String query) {

        SearchResult result = new SearchResult();
        result.setQuery(query);

        Function<String, String> toolFunction = toolExecutor.getTool(tool);
        if (toolFunction == null) {
            result.setResponse("错误:未找到名为 '" + tool + "' 的工具。");
            return result;
        }

        result.setResponse(toolFunction.apply(query));
        return result;
    }
}
