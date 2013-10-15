/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package org.elasticsearch.transport.netty;

import no.found.elasticsearch.transport.netty.FoundSwitchingChannelHandler;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.netty.bootstrap.ClientBootstrap;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.netty.util.HashedWheelTimer;
import org.elasticsearch.common.netty.util.Timer;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;

import javax.net.ssl.SSLException;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A Transport that replaces the default Netty pipeline with one
 * that supports authentication and SSL.
 */
public class FoundNettyTransport extends NettyTransport {
    private final String[] hostSuffixes;
    private final int[] sslPorts;
    private final String apiKey;
    private final boolean unsafeAllowSelfSigned;
    private final Timer timer;
    private final TimeValue keepAliveInterval;
    private final ClusterName clusterName;

    /**
     * Returns the settings with new defaults for:
     *
     *  - transport.netty.connections_per_node.low = 1
     *  - transport.netty.connections_per_node.med = 1
     *  - transport.netty.connections_per_node.high = 1
     *
     *  This is done as a reasonable default to avoid opening a lot
     *  of usually unused connections.
     */
    protected static Settings updatedDefaultSettings(Settings settings) {
        ImmutableSettings.Builder builder = ImmutableSettings.builder();
        builder.put(settings);

        if(settings.getAsInt("transport.netty.connections_per_node.low", -1) == -1) {
            builder = builder.put("transport.netty.connections_per_node.low", 1);
        }
        if(settings.getAsInt("transport.netty.connections_per_node.med", -1) == -1) {
            builder = builder.put("transport.netty.connections_per_node.med", 1);
        }
        if(settings.getAsInt("transport.netty.connections_per_node.high", -1) == -1) {
            builder = builder.put("transport.netty.connections_per_node.high", 1);
        }

        return builder.build();
    }

    @Inject
    public FoundNettyTransport(Settings settings, ThreadPool threadPool, NetworkService networkService, ClusterName clusterName, Version version) {
        super(updatedDefaultSettings(settings), threadPool, networkService, version);

        this.clusterName = clusterName;

        timer = new HashedWheelTimer();
        keepAliveInterval = settings.getAsTime("transport.found.connection-keep-alive-interval", new TimeValue(20000, TimeUnit.MILLISECONDS));
        unsafeAllowSelfSigned = settings.getAsBoolean("transport.found.ssl.unsafe_allow_self_signed", false);
        hostSuffixes = settings.getAsArray("transport.found.host-suffixes", new String[]{".foundcluster.com", ".found.no"});

        List<Integer> ports = new LinkedList<Integer>();
        for(String strPort: settings.getAsArray("transport.found.ssl-ports", new String[] {"9343"})) {
            try {
                ports.add(Integer.parseInt(strPort));
            } catch (NumberFormatException nfe) {
                // ignore
            }
        }
        sslPorts = new int[ports.size()];
        for(int i=0; i<ports.size(); i++) {
            sslPorts[i] = ports.get(i);
        }

        this.apiKey = settings.get("transport.found.api-key", "missing-api-key");
    }

    void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if(e.getCause() instanceof SSLException) {
            return;
        }
        super.exceptionCaught(ctx, e);
    }

    @Override
    protected void doStart() throws ElasticSearchException {
        super.doStart();

        try {
            Field clientBootstrapField = getClass().getSuperclass().getDeclaredField("clientBootstrap");
            clientBootstrapField.setAccessible(true);
            ClientBootstrap clientBootstrap = (ClientBootstrap)clientBootstrapField.get(this);

            final ChannelPipelineFactory originalFactory = clientBootstrap.getPipelineFactory();

            clientBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                @Override
                public ChannelPipeline getPipeline() throws Exception {
                    return Channels.pipeline(new FoundSwitchingChannelHandler(logger, originalFactory, clusterName, timer, keepAliveInterval, unsafeAllowSelfSigned, hostSuffixes, sslPorts, apiKey));
                }
            });

            clientBootstrapField.setAccessible(false);
        } catch (ReflectiveOperationException roe) {
            logger.error("Unable to update the transport pipeline. Plugin upgrade required.", roe);
        }
    }

    @Override
    protected void doStop() throws ElasticSearchException {
        super.doStop();
        timer.stop();
    }
}
