package deepblue.inaction_08_netty;

import deepblue.inaction_08_netty.handler.inbound.Business;
import deepblue.inaction_08_netty.handler.inbound.InboundA;
import deepblue.inaction_08_netty.handler.inbound.InboundB;
import deepblue.inaction_08_netty.handler.outbound.OutboundA;
import deepblue.inaction_08_netty.handler.outbound.OutboundB;
import deepblue.inaction_08_netty.model.Mode;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * 在 IDEA 中 Debug main 即可，不启动 Spring，不监听端口，也不需要客户端。
 * EmbeddedChannel 运行真正的 Netty Pipeline，但不模拟真实 socket 和线程调度。
 *
 * 添加顺序：Head -> InboundA -> OutboundA -> InboundB -> Business -> OutboundB -> Tail
 * 入站顺序：InboundA -> InboundB -> Business
 * ctx 出站：Business -> OutboundA -> Head
 * channel 出站：Tail -> OutboundB -> OutboundA -> Head
 *
 * 建议先在所有标记 BP 的位置打断点，通过 F7 进入 Netty 源码，F8 看业务顺序。
 */
public final class MinePipelineObject {

    public static void main(String[] args) {
        // 单独调试一种模式时，注释另外两行即可。
        run(Mode.CTX_WRITE);
        run(Mode.CHANNEL_WRITE);
        run(Mode.STOP_INBOUND);
    }

    private static void run(Mode mode) {
        System.out.println("\n========== " + mode + " ==========");
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            // BP0：逐行添加，查看 pipeline 内各 Context 的 prev / next。
            channel.pipeline().addLast("inboundA", new InboundA(mode));
            channel.pipeline().addLast("outboundA", new OutboundA());
            channel.pipeline().addLast("inboundB", new InboundB());
            channel.pipeline().addLast("business", new Business(mode));
            channel.pipeline().addLast("outboundB", new OutboundB());

            // BP1：模拟 socket 读到一条消息；从 Pipeline 的入站方向注入。
            // 此例消息都是 String，用来专注观察责任链，不涉及 ByteBuf 所有权。
            channel.writeInbound("hello");
            channel.runPendingTasks();
            channel.checkException();

            String response = channel.readOutbound();
            System.out.println("[RESULT] " + response);
            String expected = mode == Mode.STOP_INBOUND ? null : "reply:HELLO";
            if (expected == null ? response != null : !expected.equals(response)) {
                throw new IllegalStateException("Unexpected outbound response: " + response);
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
