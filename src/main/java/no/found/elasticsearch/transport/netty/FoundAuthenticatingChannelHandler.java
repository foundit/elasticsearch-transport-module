/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package no.found.elasticsearch.transport.netty;

import no.found.elasticsearch.transport.netty.ssl.FoundSSLHandler;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.netty.util.Timer;
import org.elasticsearch.common.unit.TimeValue;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;

/**
 * A {@link ChannelHandler} that can work with both Found Elasticsearch and the
 * default Elasticsearch transport pipeline.
 *
 * For Found Elasticsearch, it adds an SSL handler at the beginning of the pipeline
 * if it's connecting to a pre-configured SSL-port (which usually should be 9343).
 * Additionally, it sends a header message that contains identifies and authenticates
 * the client to the service. The service will then respond with a header response.
 *
 * If the header response is "OK" (200 <= status code <= 299), the connection has
 * been established and the original Elasticsearch transport pipeline handlers are
 * added to the pipeline and this handler removes itself from the pipeline.
 */
public class FoundAuthenticatingChannelHandler extends SimpleChannelHandler {
    private final ESLogger logger;
    private final ClusterName clusterName;
    private final String[] hostSuffixes;
    private final int[] sslPorts;
    private final String apiKey;
    private final Timer timer;
    private final TimeValue keepAliveInterval;
    private final boolean unsafeAllowSelfSigned;

    ChannelBuffer buffered = ChannelBuffers.EMPTY_BUFFER;
    boolean isFoundCluster = false;
    boolean headerSent = false;
    boolean handshakeComplete = false;

    public FoundAuthenticatingChannelHandler(ESLogger logger, ClusterName clusterName, Timer timer, TimeValue keepAliveInterval, boolean unsafeAllowSelfSigned, String[] hostSuffixes, int[] sslPorts, String apiKey) {
        this.logger = logger;

        this.clusterName = clusterName;
        this.timer = timer;
        this.keepAliveInterval = keepAliveInterval;
        this.unsafeAllowSelfSigned = unsafeAllowSelfSigned;
        this.hostSuffixes = hostSuffixes;
        this.sslPorts = sslPorts;
        this.apiKey = apiKey;
    }

    /**
     * Detects if we're connecting to a Found Elasticsearch cluster (using pre-configured
     * host suffixes) and adds a SSL handler at the beginning of the pipeline if we're connecting
     * to a SSL-endpoint (using a list of pre-configured ports).
     */
    @Override
    public void connectRequested(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        if(e.getValue() instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress)e.getValue();

            for(String suffix: hostSuffixes) isFoundCluster = isFoundCluster || inetSocketAddress.getHostString().endsWith(suffix);

            if(isFoundCluster) {
                for(int sslPort: sslPorts) {
                    if(inetSocketAddress.getPort() == sslPort) {
                        logger.info("Enabling SSL on transport layer with unsafeAllowSelfSigned=[{}].", unsafeAllowSelfSigned);
                        FoundSSLHandler handler = FoundSSLUtils.getSSLHandler(unsafeAllowSelfSigned, inetSocketAddress);
                        ctx.getPipeline().addFirst("ssl", handler);
                        break;
                    }
                }
            } else {
                ctx.getPipeline().remove(this);
            }
        }
        super.connectRequested(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(isFoundCluster && !headerSent) {
            headerSent = true;
            logger.info("Authenticating with Found Elasticsearch at [{}]", ctx.getChannel().getRemoteAddress());
            ChannelBuffer message = new FoundTransportHeader(clusterName.value(), apiKey).getHeaderBuffer();

            ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel(), Channels.future(ctx.getChannel()), message, ctx.getChannel().getRemoteAddress()));
        }
        super.writeRequested(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelConnected(ctx, e);
    }


    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(handshakeComplete) {
            super.messageReceived(ctx, e);
        } else {
            if (e.getMessage() instanceof ChannelBuffer) {
                ChannelBuffer newBuffer = (ChannelBuffer) e.getMessage();
                buffered = ChannelBuffers.copiedBuffer(buffered, newBuffer);

                if (buffered.readableBytes() < 8) {
                    return;
                }
                int payloadLength = buffered.getInt(0);
                int revision = buffered.getInt(4);

                boolean handshakeSuccessful = false;

                if (revision == 1 || revision == -1) {
                    if (buffered.readableBytes() < payloadLength + 4) {
                        return;
                    }
                    buffered.skipBytes(8);

                    if (revision == 1) {
                        handshakeSuccessful = handleRevision1Response(ctx, payloadLength);
                    } else {
                        handshakeSuccessful = handleGenericResponse(ctx, payloadLength);
                    }
                } else {
                    handshakeSuccessful = handleUnknownRevisionResponse(ctx);
                }

                if(!handshakeSuccessful) {
                    ctx.getChannel().close();
                }

                if(keepAliveInterval.millis() > 0)
                    ctx.getPipeline().addBefore(ctx.getName(), "connection-keep-alive", new ConnectionKeepAliveHandler(timer, keepAliveInterval));

                handshakeComplete = true;

                ChannelBuffer remaining = buffered.slice();
                if (remaining.readableBytes() > 0)
                    ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel(), remaining, ctx.getChannel().getRemoteAddress()));

                ctx.getPipeline().remove(this);
            }
        }
    }

    private boolean handleUnknownRevisionResponse(ChannelHandlerContext ctx) {
        logger.error("Unknown revision response received.");
        return false;
    }

    private boolean handleRevision1Response(ChannelHandlerContext ctx, int payloadLength) throws Exception {
        int code = buffered.readInt();

        int descriptionLength = buffered.readInt();
        byte[] descBytes = new byte[descriptionLength];
        buffered.readBytes(descBytes, 0, descBytes.length);

        String description = new String(descBytes, StandardCharsets.UTF_8);

        logger.debug("Decoded payload with length:[{}], code:[{}], descriptionLength:[{}], description:[{}]", payloadLength, code, descriptionLength, description);

        if(200 <= code && code <= 299) {
            logger.info("Connected to Found Elasticsearch: [{}]: [{}]", code, description);
            return true;
        } else {
            logger.error("Unable to connect to Found Elasticsearch: [{}]: [{}]", code, description);
            return false;
        }
    }

    private boolean handleGenericResponse(ChannelHandlerContext ctx, int payloadLength) throws Exception {
        int code = buffered.readInt();

        int descriptionLength = buffered.readInt();
        byte[] descBytes = new byte[descriptionLength];
        buffered.readBytes(descBytes, 0, descBytes.length);

        String description = new String(descBytes, StandardCharsets.UTF_8);

        logger.error("Unable to connect to Found Elasticsearch: [{}]: [{}]", code, description);

        return false;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof ClosedChannelException) {
            // do nothing
        } else if(e.getCause() instanceof UnresolvedAddressException) {
            logger.error("Unable to resolve one of the server addresses: [{}]", e.getCause().getMessage());
        } else if(e.getCause() instanceof ConnectException) {
            logger.error("Unable to connect: [{}]", e.getCause().getMessage());
        } else if(e.getCause().getMessage() != null && e.getCause().getMessage().contains("Connection reset by peer")) {
            // still do nothing
        } else {
            super.exceptionCaught(ctx, e);
        }
    }
}
