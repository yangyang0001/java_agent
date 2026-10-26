package deepblue.inaction_08_netty.handler.inbound;

import deepblue.inaction_08_netty.model.Mode;
import deepblue.inaction_08_netty.support.PipelineLogger;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/** 消费入站请求，分别通过 ctx 和 channel 发起出站响应。 */
public final class InboundHandlerAdapterBusiness extends ChannelInboundHandlerAdapter {

    private final Mode mode;

    public InboundHandlerAdapterBusiness(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // BP5：业务消费请求，不再 fireChannelRead，因此不会传到 Tail。
        PipelineLogger.log("IN", "channelRead", ctx, msg);
        String response = "reply:" + msg;
        ChannelFuture future;
        if (mode == Mode.CTX_WRITE) {
            // BP6：从 InboundHandlerAdapterBusiness 的前方查找 Outbound，跳过 InboundHandlerAdapterB。
            // 路径：OutboundHandlerAdapterA；InboundHandlerAdapterBusiness 后面的 OutboundHandlerAdapterB 不会执行。
            future = ctx.writeAndFlush(response);
        } else {
            // BP7：从 Tail 查找 Outbound，先执行 OutboundHandlerAdapterB，再执行 OutboundHandlerAdapterA。
            future = ctx.channel().writeAndFlush(response);
        }
        future.addListener(done -> {
            // BP10：EmbeddedChannel 可能已经同步完成，因此回调可能立即执行。
            // 真正网络 Channel 的完成时机不同；不要在 I/O 回调里 sync 等待。
            System.out.println("[FUTURE] success=" + done.isSuccess());
        });
    }
}
