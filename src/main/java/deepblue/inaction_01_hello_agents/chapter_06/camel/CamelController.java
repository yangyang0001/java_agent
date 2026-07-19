package deepblue.inaction_01_hello_agents.chapter_06.camel;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对应 CAMEL 的角色扮演双智能体协作范式:心理学家(指导者) 与 作家(执行者) 共创一本关于拖延症的电子书。
 */
@RestController
public class CamelController {

    private static final String DEFAULT_TASK = "合作撰写一本关于《克服拖延症》的电子书大纲及第一章内容";

    @Resource
    private CamelAgent camelAgent;

    @PostMapping("/camel")
    public List<DialogueMessage> camel(@RequestBody(required = false) TaskRequest request) {
        String task = (request == null || request.getTask() == null || request.getTask().isBlank())
                ? DEFAULT_TASK
                : request.getTask();
        return camelAgent.run(task);
    }
}
