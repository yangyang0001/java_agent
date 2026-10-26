package deepblue.inaction_08_netty.handler.inbound;

import deepblue.inaction_08_netty.model.Mode;
import deepblue.inaction_08_netty.support.PipelineLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/** 入站第一站：演示继续传播与主动截断。示例只接收 String。 */
public final class InboundHandlerAdapterA extends ChannelInboundHandlerAdapter {

    private final Mode mode;

    public InboundHandlerAdapterA(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // BP2：ctx.name() = inboundA；查看 msg 和 ctx 的 prev / next。
        PipelineLogger.log("IN", "channelRead", ctx, msg);
        if (mode == Mode.STOP_INBOUND) {
            System.out.println("[STOP] 不调用 fireChannelRead，后续入站不再执行");
            return;
        }
        // BP3：F7 进入这里，观察 findContextInbound 跳过 OutboundHandlerAdapterA。
        ctx.fireChannelRead(msg);
    }
}
