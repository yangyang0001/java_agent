package deepblue.inaction_01_hello_agents.chapter_06._04_langgraph;

import lombok.Data;

/**
 * LangGraph 风格的共享状态:图中的每个节点都读取、修改同一份状态对象。
 */
@Data
public class GraphState {

    private String question;

    private String understanding;

    private String searchResult;

    private String answer;

    private int retryCount;
}
