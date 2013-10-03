package no.found.elasticsearch.transport.netty;

import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.netty.util.Timeout;
import org.elasticsearch.common.netty.util.Timer;
import org.elasticsearch.common.netty.util.TimerTask;
import org.elasticsearch.common.unit.TimeValue;

import java.util.concurrent.TimeUnit;

public class ConnectionKeepAliveHandler extends SimpleChannelHandler implements LifeCycleAwareChannelHandler {
    private final Timer timer;
    private final TimeValue keepAliveInterval;
    ChannelBuffer keepAliveBuffer = ChannelBuffers.copiedBuffer(new byte[]{'F', 'K', 0, 0, 0, 0});

    public ConnectionKeepAliveHandler(Timer timer, TimeValue keepAliveInterval) {
        this.timer = timer;
        this.keepAliveInterval = keepAliveInterval;
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelDisconnected(ctx, e);
    }

    private void addTimeoutTask(ChannelHandlerContext ctx) {
        timer.newTimeout(new KeepAliveTimerTask(ctx), 2, TimeUnit.SECONDS);
    }

    private long lastWrite;

    @Override
    synchronized public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        super.writeRequested(ctx, e);
        lastWrite = System.currentTimeMillis();
    }

    @Override
    public void beforeAdd(ChannelHandlerContext channelHandlerContext) throws Exception {
    }

    @Override
    synchronized public void afterAdd(ChannelHandlerContext channelHandlerContext) throws Exception {
        lastWrite = System.currentTimeMillis();
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
                    send(ctx, new DownstreamMessageEvent(ctx.getChannel(), Channels.future(ctx.getChannel()), keepAliveBuffer, ctx.getChannel().getRemoteAddress()));
                    addTimeoutTask(ctx);
                }
            });
        }
    }

    synchronized private void send(ChannelHandlerContext ctx, DownstreamMessageEvent downstreamMessageEvent) {
        long now = System.currentTimeMillis();

        if(now - lastWrite > keepAliveInterval.millis()) {
            lastWrite = now;
            ctx.sendDownstream(downstreamMessageEvent);
        }
    }
}
