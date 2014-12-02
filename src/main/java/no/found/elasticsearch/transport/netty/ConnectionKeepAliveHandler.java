package no.found.elasticsearch.transport.netty;

import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.unit.TimeValue;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ConnectionKeepAliveHandler extends SimpleChannelHandler implements LifeCycleAwareChannelHandler {
    private final ScheduledExecutorService scheduler;
    private final TimeValue keepAliveInterval;
    ChannelBuffer keepAliveBuffer = ChannelBuffers.copiedBuffer(new byte[]{'F', 'K', 0, 0, 0, 0});
    private ScheduledFuture<?> currentScheduled;

    public ConnectionKeepAliveHandler(ScheduledExecutorService scheduler, TimeValue keepAliveInterval) {
        this.scheduler = scheduler;
        this.keepAliveInterval = keepAliveInterval;
    }


    private void addTimeoutTask(ChannelHandlerContext ctx) {
        cancelCurrentScheduled();
        currentScheduled = scheduler.schedule(new KeepAliveRunnable(ctx), 2, TimeUnit.SECONDS);
    }

    private void cancelCurrentScheduled() {
        if(currentScheduled != null && !currentScheduled.isCancelled() && !currentScheduled.isDone()) {
            currentScheduled.cancel(true);
        }
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
        cancelCurrentScheduled();
    }

    class KeepAliveRunnable implements Runnable {
        private final ChannelHandlerContext ctx;

        public KeepAliveRunnable(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.getChannel().isConnected()) return;

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
