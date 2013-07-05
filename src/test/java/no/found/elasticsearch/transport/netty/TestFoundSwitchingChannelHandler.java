package no.found.elasticsearch.transport.netty;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.netty.buffer.BigEndianHeapChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class TestFoundSwitchingChannelHandler {
    ESLogger logger = ESLoggerFactory.getLogger(getClass().getCanonicalName());

    private Channel channel;
    private ChannelPipeline channelPipeline;
    private ChannelHandlerContext context;
    private ChannelStateEvent event;

    private ChannelPipelineFactory originalFactory;
    private ChannelPipeline originalPipeline;
    private ChannelHandler originalHandler;
    private ChannelHandlerContext originalContext;
    private InetSocketAddress socketAddress;

    private final String API_KEY = "my-api-key";
    private final String FOUND_HOST = "test-host";
    private final int SSL_PORT = 9343;

    @Before
    public void setUp() throws Exception {
        socketAddress = new InetSocketAddress(FOUND_HOST, SSL_PORT);

        channel = mock(Channel.class);
        channelPipeline = mock(ChannelPipeline.class);
        context = mock(ChannelHandlerContext.class);
        event = mock(ChannelStateEvent.class);

        when(context.getChannel()).thenReturn(channel);
        when(context.getPipeline()).thenReturn(channelPipeline);
        when(channel.getRemoteAddress()).thenReturn(socketAddress);

        originalFactory = mock(ChannelPipelineFactory.class);
        originalPipeline = mock(ChannelPipeline.class);
        originalHandler = mock(ChannelHandler.class);
        originalContext = mock(ChannelHandlerContext.class);

        when(originalFactory.getPipeline()).thenReturn(originalPipeline);
        when(originalPipeline.getFirst()).thenReturn(originalHandler, (ChannelHandler)null);
        when(originalPipeline.getContext(originalHandler)).thenReturn(originalContext);
        when(originalContext.getName()).thenReturn("test-handler");
    }

    public FoundSwitchingChannelHandler getChannelHandler(String knownHost, int sslPort) {
        return getChannelHandler(false, knownHost, sslPort, API_KEY);
    }

    public FoundSwitchingChannelHandler getChannelHandler(boolean unsafeAllowSelfSigned, String knownHost, int sslPort, String apiKey) {
        return new FoundSwitchingChannelHandler(logger, originalFactory, unsafeAllowSelfSigned, new String[] {knownHost}, new int[] {sslPort}, apiKey);
    }

    @Test
    public void testSSLNotAddedForUnknownHost() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler("unknown", socketAddress.getPort());

        handler.channelBound(context, event);

        verify(channelPipeline, never()).addFirst(anyString(), any(ChannelHandler.class));
    }

    @Test
    public void testSSLNotAddedForUnknownPort() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), 1234);

        handler.channelBound(context, event);

        verify(channelPipeline, never()).addFirst(anyString(), any(ChannelHandler.class));
    }

    @Test
    public void testSSLAddedForKnownHostPort() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.channelBound(context, event);

        verify(channelPipeline).addFirst(anyString(), any(FoundSSLHandler.class));
    }

    @Test
    public void testHeaderNotSentForUnknownHost() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler("unknown", socketAddress.getPort());

        handler.channelBound(context, event);
        handler.channelConnected(context, event);

        verify(context, never()).sendDownstream(any(MessageEvent.class));
    }

    @Test
    public void testHeaderSentForKnownHost() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        ArgumentCaptor<DownstreamMessageEvent> argument = ArgumentCaptor.forClass(DownstreamMessageEvent.class);

        handler.channelBound(context, event);
        handler.channelConnected(context, event);

        verify(context).sendDownstream(argument.capture());

        DownstreamMessageEvent msgEvent = argument.getValue();
        assertEquals(BigEndianHeapChannelBuffer.class, msgEvent.getMessage().getClass());

        ChannelBuffer buffer = (ChannelBuffer)msgEvent.getMessage();
        assertEquals(new FoundTransportHeader(socketAddress.getHostString(), API_KEY).getHeaderBuffer(), buffer);
    }

    @Test
    public void testPipelineSwitchedWhenHeaderResponseReceived() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(
                new byte[] {0, 0, 0, (byte)14}, // msg size
                new byte[] {0, 0, 0, (byte)1}, // revision
                new byte [] {0, 0, 0, (byte)200}, // 200
                new byte[] {0, 0, 0, (byte)2}, // description-length (2)
                "OK".getBytes(UTF_8) // OK as bytes
            ), socketAddress));

        verify(originalPipeline, times(2)).getFirst();
        verify(channelPipeline).addLast(anyString(), eq(originalHandler));
        verify(channelPipeline).remove(eq(handler));
    }

    @Test
    public void testPipelineSwitchedWhenHeaderResponseReceivedWithChunkedData() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        // payload length
        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(new byte[] {0, 0}), socketAddress));
        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(new byte[] {0, (byte)14}), socketAddress));

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(new byte[] {0, 0, 0, (byte)1}), socketAddress));
        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(new byte[] {0, 0, 0, (byte)200}), socketAddress));
        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(new byte[] {0, 0, 0, (byte)2}), socketAddress));

        verify(channelPipeline, never()).remove(eq(handler));
        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer("OK".getBytes(UTF_8)), socketAddress));

        verify(originalPipeline, times(2)).getFirst();
        verify(channelPipeline).addLast(anyString(), eq(originalHandler));
        verify(channelPipeline).remove(eq(handler));
    }

    @Test
    public void testPipelineClosedWhenBadStatusReceived() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(
                new byte[] {0, 0, 0, (byte)17}, // msg size
                new byte[] {0, 0, 0, (byte)1}, // revision
                new byte [] {0, 0, 0, (byte)403}, // 403
                new byte[] {0, 0, 0, (byte)5}, // description-length (2)
                "NOTOK".getBytes(UTF_8) // NOTOK as bytes
        ), socketAddress));

        verify(channelPipeline).remove(eq(handler));
        verify(channel).close();
    }

    @Test
    public void testMessagesHeldAndTransferredWhenHeaderResponseReceived() throws Exception {
        when(originalFactory.getPipeline()).thenReturn(originalPipeline);

        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        MessageEvent writeEvent = mock(MessageEvent.class);
        handler.writeRequested(context, writeEvent);
        verify(context, never()).sendDownstream(writeEvent);

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(
                new byte[] {0, 0, 0, (byte)14}, // msg size
                new byte[] {0, 0, 0, (byte)1}, // revision
                new byte [] {0, 0, 0, (byte)200}, // 200
                new byte[] {0, 0, 0, (byte)2}, // description-length (2)
                "OK".getBytes(UTF_8) // OK as bytes
        ), socketAddress));

        verify(context).sendDownstream(writeEvent);
    }

    @Test
    public void testPipelineClosedWhenInvalidHeaderResponseReceived() throws Exception {
        FoundSwitchingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(
                new byte[] {(byte)123, (byte)123, (byte)123, (byte)182}, // msg size invalid
                new byte[] {0, 0, 0, (byte)-1}, // revision invalid
                new byte [] {0, 0, 0, (byte)200}, // 200
                new byte[] {0, 0, 0, (byte)5}, // description-length invalid
                "OK".getBytes(UTF_8) // OK as bytes
        ), socketAddress));

        verify(channel).close();
    }
}
