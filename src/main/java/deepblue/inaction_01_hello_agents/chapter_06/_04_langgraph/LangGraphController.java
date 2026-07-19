package deepblue.inaction_01_hello_agents.chapter_06._04_langgraph;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对应 LangGraph 的状态图范式:"理解 -> 搜索 -> 回答" 三步问答助手。
 */
@RestController
public class LangGraphController {

    @Resource
    private LangGraphAgent langGraphAgent;

    @PostMapping("/langgraph")
    public GraphState langgraph(@RequestBody QuestionRequest request) {
        return langGraphAgent.run(request.getQuestion());
    }
}
