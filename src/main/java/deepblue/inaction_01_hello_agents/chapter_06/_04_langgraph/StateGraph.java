package deepblue.inaction_01_hello_agents.chapter_06._04_langgraph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 极简版的 LangGraph 风格图执行引擎:节点(Node)读写共享状态，
 * 边(Edge)决定下一个节点，条件边(Conditional Edge)支持按状态动态路由，甚至绕回之前的节点(形成环)。
 */
public class StateGraph {

    public static final String END = "END";

    private static final int MAX_STEPS = 10;

    private final Map<String, Consumer<GraphState>> nodes = new LinkedHashMap<>();

    private final Map<String, String> edges = new LinkedHashMap<>();

    private final Map<String, Function<GraphState, String>> conditionalEdges = new LinkedHashMap<>();

    private String entryPoint;

    public void addNode(String name, Consumer<GraphState> node) {
        nodes.put(name, node);
    }

    public void setEntryPoint(String name) {
        this.entryPoint = name;
    }

    public void addEdge(String from, String to) {
        edges.put(from, to);
    }

    public void addConditionalEdge(String from, Function<GraphState, String> router) {
        conditionalEdges.put(from, router);
    }

    public GraphState invoke(GraphState state) {
        String current = entryPoint;
        int step = 0;

        while (current != null && !END.equals(current)) {
            if (++step > MAX_STEPS) {
                System.out.println("⚠️ 已达到最大步数(" + MAX_STEPS + ")，强制结束以防死循环。");
                break;
            }

            System.out.println("--- 进入节点: " + current + " ---");
            Consumer<GraphState> node = nodes.get(current);
            if (node == null) {
                throw new IllegalStateException("未找到节点: " + current);
            }
            node.accept(state);

            Function<GraphState, String> router = conditionalEdges.get(current);
            current = router != null ? router.apply(state) : edges.get(current);
        }

        System.out.println("--- 图执行结束 ---");
        return state;
    }
}
