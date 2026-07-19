package deepblue.inaction_01_hello_agents.chapter_06._01_autogen;

import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对应 AutoGen 的"对话驱动协作"范式:产品经理 -> 工程师 -> 审查员 轮流发言的圆桌会议。
 */
@RestController
public class AutoGenController {

    @Resource
    private AutoGenAgent autoGenAgent;

    @PostMapping("/autogen")
    public List<GroupChatMessage> autogen(@RequestBody RequirementRequest request) {
        return autoGenAgent.run(request.getRequirement());
    }
}
