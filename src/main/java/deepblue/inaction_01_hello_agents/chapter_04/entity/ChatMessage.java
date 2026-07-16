package deepblue.inaction_01_hello_agents.chapter_04.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    private String id;

    private String message;
}
