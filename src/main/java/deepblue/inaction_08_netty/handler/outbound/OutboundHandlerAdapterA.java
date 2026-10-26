package deepblue.inaction_08_netty.handler.outbound;

import deepblue.inaction_08_netty.support.PipelineLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/** 位于 InboundHandlerAdapterBusiness 前方，两种发送入口都会经过这里。 */
public final class OutboundHandlerAdapterA extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // BP9：两种发送入口都会到达这里。
        PipelineLogger.log("OUT", "write", ctx, msg);
        // 继续向前查找 Outbound，并传递原 Promise。
        ctx.write(msg, promise);
    }
}
