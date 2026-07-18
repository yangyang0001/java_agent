package deepblue.inaction_01_hello_agents.chapter_04.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Reflection 智能体使用的短期记忆模块，按顺序存储每一轮"执行/反思"的轨迹。
 * 每次 {@link ReflectionAgent#run(String)} 都会 new 一个新的 Memory，不作为 Spring 单例状态持有，
 * 避免并发请求之间互相污染。
 */
public class Memory {

    public enum RecordType {
        EXECUTION, REFLECTION
    }

    private record Record(RecordType type, String content) {
    }

    private final List<Record> records = new ArrayList<>();

    /**
     * 向记忆中添加一条新记录。
     */
    public void addRecord(RecordType type, String content) {
        records.add(new Record(type, content));
        System.out.println("📝 记忆已更新，新增一条 '" + type + "' 记录。");
    }

    /**
     * 将所有记忆记录格式化为一段连贯的文本，可直接插入提示词。
     */
    public String getTrajectory() {
        StringBuilder trajectory = new StringBuilder();
        for (Record record : records) {
            if (trajectory.length() > 0) {
                trajectory.append("\n\n");
            }
            if (record.type() == RecordType.EXECUTION) {
                trajectory.append("--- 上一轮尝试 (代码) ---\n").append(record.content());
            } else {
                trajectory.append("--- 评审员反馈 ---\n").append(record.content());
            }
        }
        return trajectory.toString();
    }

    /**
     * 获取最近一次的执行结果(最新生成的代码)，不存在则返回 null。
     */
    public String getLastExecution() {
        for (int i = records.size() - 1; i >= 0; i--) {
            Record record = records.get(i);
            if (record.type() == RecordType.EXECUTION) {
                return record.content();
            }
        }
        return null;
    }
}
