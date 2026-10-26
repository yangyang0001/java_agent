package deepblue.inaction_08_netty.support;

import io.netty.channel.ChannelHandlerContext;

/** 统一打印方向、Handler 名称、消息和执行线程，便于对照断点顺序。 */
public final class PipelineLogger {

    private PipelineLogger() {
    }

    public static void log(String direction, ChannelHandlerContext ctx, Object msg) {
        System.out.printf("[%s] %-10s msg=%s thread=%s%n",
                direction, ctx.name(), msg, Thread.currentThread().getName());
    }
}
