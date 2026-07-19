package deepblue.inaction_01_hello_agents.chapter_06._03_camel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DialogueMessage {

    private String speaker;

    private String content;
}
