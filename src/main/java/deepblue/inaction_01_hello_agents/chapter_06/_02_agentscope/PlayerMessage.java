package deepblue.inaction_01_hello_agents.chapter_06._02_agentscope;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerMessage {

    private String player;

    private String content;
}
