package deepblue.inaction_08_netty.handler.inbound;

import deepblue.inaction_08_netty.support.PipelineLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Locale;

/** 入站第二站：将 String 转成大写，然后继续传给业务 Handler。 */
public final class InboundB extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // BP4：入站转换消息，再把新的 String 交给 Business。
        PipelineLogger.log("IN", ctx, msg);
        String request = ((String) msg).toUpperCase(Locale.ROOT);
        ctx.fireChannelRead(request);
    }
}
