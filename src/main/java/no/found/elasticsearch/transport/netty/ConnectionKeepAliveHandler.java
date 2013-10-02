package no.found.elasticsearch.transport.netty;

import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.netty.util.Timeout;
import org.elasticsearch.common.netty.util.Timer;
import org.elasticsearch.common.netty.util.TimerTask;

import java.util.concurrent.TimeUnit;

public class ConnectionKeepAliveHandler extends SimpleChannelHandler implements LifeCycleAwareChannelHandler {
    private final Timer timer;
    ChannelBuffer keepAliveBuffer = ChannelBuffers.copiedBuffer(new byte[]{'F', 'K', 0, 0, 0, 0});

    public ConnectionKeepAliveHandler(Timer timer) {
        this.timer = timer;
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
    }

    private void addTimeoutTask(ChannelHandlerContext ctx) {
        // TODO: make this configurable?
        timer.newTimeout(new KeepAliveTimerTask(ctx), 45, TimeUnit.SECONDS);
    }

    @Override
    public void beforeAdd(ChannelHandlerContext channelHandlerContext) throws Exception {
    }

    @Override
    public void afterAdd(ChannelHandlerContext channelHandlerContext) throws Exception {
        addTimeoutTask(channelHandlerContext);
    }

    @Override
    public void beforeRemove(ChannelHandlerContext channelHandlerContext) throws Exception {
    }

    @Override
    public void afterRemove(ChannelHandlerContext channelHandlerContext) throws Exception {
    }

    class KeepAliveTimerTask implements TimerTask {
        private final ChannelHandlerContext ctx;

        public KeepAliveTimerTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled() || !ctx.getChannel().isConnected()) return;

            ctx.getPipeline().execute(new Runnable() {
                @Override
                public void run() {
                    ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel(), Channels.future(ctx.getChannel()), keepAliveBuffer, ctx.getChannel().getRemoteAddress()));
                    addTimeoutTask(ctx);
                }
            });
        }
    }
}
