package deepblue.inaction_08_netty.handler.outbound;

import deepblue.inaction_08_netty.support.PipelineLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/** 位于 InboundHandlerAdapterBusiness 后方，只有从 Channel 链尾发送时才会经过这里。 */
public final class OutboundHandlerAdapterB extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // BP8：只有 CHANNEL_WRITE 会到达这里。
        PipelineLogger.log("OUT", "write", ctx, msg);
        // 不要换成 ctx.channel().write(...)，否则又从 Tail 开始，可能递归。
        ctx.write(msg, promise);
    }
}
