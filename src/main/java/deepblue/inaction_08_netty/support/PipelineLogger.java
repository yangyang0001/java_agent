package deepblue.inaction_08_netty.support;

import io.netty.channel.ChannelHandlerContext;

/** 统一打印方向、Handler 方法、Context 名称、消息和执行线程，便于对照调用顺序。 */
public final class PipelineLogger {

    private PipelineLogger() {
    }

    public static void log(String direction, String method, ChannelHandlerContext ctx, Object msg) {
        System.out.printf("[%s] %s.%s ctx=%s msg=%s thread=%s%n",
                direction, ctx.handler().getClass().getSimpleName(), method,
                ctx.name(), msg, Thread.currentThread().getName());
    }
}
