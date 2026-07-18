package deepblue.inaction_01_hello_agents.chapter_04.tool;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 一个工具执行器，负责管理和执行工具。
 */
@Component
public class ToolExecutor {

    private record RegisteredTool(String description, Function<String, String> func) {
    }

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    @Resource
    private SerpApiSearchTool serpApiSearchTool;

    @PostConstruct
    public void init() {
        registerTool("Search", "一个网页搜索引擎。当你需要回答关于时事、事实以及在你的知识库中找不到的信息时，应使用此工具。",
                serpApiSearchTool::search);
    }

    /**
     * 向工具箱中注册一个新工具。
     */
    public void registerTool(String name, String description, Function<String, String> func) {
        if (tools.containsKey(name)) {
            System.out.println("警告:工具 '" + name + "' 已存在，将被覆盖。");
        }
        tools.put(name, new RegisteredTool(description, func));
        System.out.println("工具 '" + name + "' 已注册。");
    }

    /**
     * 根据名称获取一个工具的执行函数。
     */
    public Function<String, String> getTool(String name) {
        RegisteredTool tool = tools.get(name);
        return tool == null ? null : tool.func();
    }

    /**
     * 获取所有可用工具的格式化描述字符串。
     */
    public String getAvailableTools() {
        return tools.entrySet().stream()
                .map(e -> "- " + e.getKey() + ": " + e.getValue().description())
                .collect(Collectors.joining("\n"));
    }
}
