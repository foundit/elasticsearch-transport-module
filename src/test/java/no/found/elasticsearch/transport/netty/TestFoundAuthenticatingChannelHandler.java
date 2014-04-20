/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package no.found.elasticsearch.transport.netty;

import no.found.elasticsearch.transport.netty.ssl.FoundSSLHandler;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.netty.util.HashedWheelTimer;
import org.elasticsearch.common.netty.util.Timer;
import org.elasticsearch.common.unit.TimeValue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class TestFoundAuthenticatingChannelHandler {
    ESLogger logger = ESLoggerFactory.getLogger(getClass().getCanonicalName());

    private Channel channel;
    private ChannelPipeline channelPipeline;
    private ChannelHandlerContext context;
    private ChannelStateEvent event;

    private InetSocketAddress socketAddress;

    private static final String API_KEY = "my-api-key";
    private static final String FOUND_HOST = "test-host";
    private static final int SSL_PORT = 9343;
    private ClusterName clusterName = new ClusterName("test-cluster-name");
    private Timer timer = new HashedWheelTimer();
    private TimeValue keepAliveInterval = new TimeValue(0);

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
    }

    public FoundAuthenticatingChannelHandler getChannelHandler(String knownHost, int sslPort) {
        return getChannelHandler(false, knownHost, sslPort, API_KEY);
    }

    public FoundAuthenticatingChannelHandler getChannelHandler(boolean unsafeAllowSelfSigned, String knownHost, int sslPort, String apiKey) {
        return new FoundAuthenticatingChannelHandler(logger, clusterName, timer, keepAliveInterval, unsafeAllowSelfSigned, new String[] {knownHost}, new int[] {sslPort}, apiKey);
    }

    @Test
    public void testSSLNotAddedForUnknownHost() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler("unknown", socketAddress.getPort());

        handler.channelBound(context, event);

        verify(channelPipeline, never()).addFirst(anyString(), any(ChannelHandler.class));
    }

    @Test
    public void testSSLNotAddedForUnknownPort() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), 1234);

        handler.channelBound(context, event);

        verify(channelPipeline, never()).addFirst(anyString(), any(ChannelHandler.class));
    }

    @Test
    public void testSSLAddedForKnownHostPort() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.channelBound(context, event);

        verify(channelPipeline).addFirst(anyString(), any(FoundSSLHandler.class));
    }

    @Test
    public void testHeaderNotSentForUnknownHost() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler("unknown", socketAddress.getPort());

        handler.channelBound(context, event);
        handler.channelConnected(context, event);

        verify(context, never()).sendDownstream(any(MessageEvent.class));
    }

    @Test
    public void testHandlerRemovedForUnknownHost() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler("unknown", socketAddress.getPort());

        handler.channelBound(context, event);

        verify(channelPipeline).remove(handler);
    }

    @Test
    public void testHeaderSentForKnownHost() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        ArgumentCaptor<ChannelBuffer> argument = ArgumentCaptor.forClass(ChannelBuffer.class);

        handler.channelBound(context, event);
        handler.channelConnected(context, event);

        verify(channel).write(argument.capture());

        ChannelBuffer message = argument.getValue();
        assertEquals(new FoundTransportHeader(clusterName.value(), API_KEY).getHeaderBuffer(), message);
    }

    @Test
    public void testHandlerRemovedOnSuccessfulAuthentication() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(
                new byte[] {0, 0, 0, (byte)14}, // msg size
                new byte[] {0, 0, 0, (byte)1}, // revision
                new byte [] {0, 0, 0, (byte)200}, // 200
                new byte[] {0, 0, 0, (byte)2}, // description-length (2)
                "OK".getBytes(UTF_8) // NOTOK as bytes
        ), socketAddress));

        verify(channelPipeline).remove(handler);
    }

    @Test
    public void testPipelineClosedWhenBadStatusReceived() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

        handler.messageReceived(context, new UpstreamMessageEvent(channel, ChannelBuffers.wrappedBuffer(
                new byte[] {0, 0, 0, (byte)17}, // msg size
                new byte[] {0, 0, 0, (byte)1}, // revision
                new byte [] {0, 0, 1, (byte)147}, // 403
                new byte[] {0, 0, 0, (byte)5}, // description-length (5)
                "NOTOK".getBytes(UTF_8) // NOTOK as bytes
        ), socketAddress));

        verify(channel).close();
    }

    @Test
    public void testPipelineClosedWhenInvalidHeaderResponseReceived() throws Exception {
        FoundAuthenticatingChannelHandler handler = getChannelHandler(socketAddress.getHostString(), socketAddress.getPort());

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
