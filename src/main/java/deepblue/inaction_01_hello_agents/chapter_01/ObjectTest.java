package deepblue.inaction_01_hello_agents.chapter_01;

/**
 * 模拟多个参数的方式进行
 */
public class ObjectTest {

    public static void main(String[] args) {

        String param1 = "zhangsan";

        Object result = process(param1, "aaaa", "bbbb");

        System.out.println(result);

    }

    public static Object process(Object param1, Object ... params) {
        // 模拟程序执行过程
        return "process";
    }

}
