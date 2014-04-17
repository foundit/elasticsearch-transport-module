/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package org.elasticsearch.transport.netty;

import no.found.elasticsearch.transport.netty.FoundAuthenticatingChannelHandler;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.netty.bootstrap.ClientBootstrap;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.netty.util.HashedWheelTimer;
import org.elasticsearch.common.netty.util.Timer;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.threadpool.ThreadPool;

import javax.net.ssl.SSLException;
import java.lang.reflect.Field;
import java.util.Arrays;
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
    private static final Timer timer = new HashedWheelTimer();
    private final TimeValue keepAliveInterval;
    private final ClusterName clusterName;

    @Inject
    public FoundNettyTransport(Settings settings, ThreadPool threadPool, NetworkService networkService, ClusterName clusterName, Version version) {
        super(settings, threadPool, networkService, version);

        this.clusterName = clusterName;

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

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if(e.getCause() instanceof SSLException) {
            return;
        }
        super.exceptionCaught(ctx, e);
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        super.doStart();

        try {
            Field clientBootstrapField = getClass().getSuperclass().getDeclaredField("clientBootstrap");
            clientBootstrapField.setAccessible(true);
            ClientBootstrap clientBootstrap = (ClientBootstrap)clientBootstrapField.get(this);

            final ChannelPipelineFactory originalFactory = clientBootstrap.getPipelineFactory();

            clientBootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                @Override
                public ChannelPipeline getPipeline() throws Exception {
                    ChannelPipeline pipeline =  originalFactory.getPipeline();
                    pipeline.addFirst("found-switching-channel-handler", new FoundAuthenticatingChannelHandler(logger, clusterName, timer, keepAliveInterval, unsafeAllowSelfSigned, hostSuffixes, sslPorts, apiKey));
                    return pipeline;
                }
            });

            clientBootstrapField.setAccessible(false);
        } catch (ReflectiveOperationException roe) {
            logger.error("Unable to update the transport pipeline. Plugin upgrade required.", roe);
        }
    }

    @Override
    public void connectToNode(DiscoveryNode node, boolean light) {
        // we hook into the connection here and use reflection in order to update the
        // resolved address of the given node by resolving it again. the rationale behind
        // this is that the ELB addresses may change and that Elasticsearch otherwise doesn't
        // try to resolve it again.

        if(node.address() instanceof InetSocketTransportAddress) {
            InetSocketTransportAddress oldAddress = (InetSocketTransportAddress)node.address();
            InetSocketTransportAddress newAddress = new InetSocketTransportAddress(oldAddress.address().getHostString(), oldAddress.address().getPort());

            boolean oldResolved = !oldAddress.address().isUnresolved();
            boolean newResolved = !newAddress.address().isUnresolved();

            boolean resolvedOk = !oldResolved || newResolved;

            // only update it if the old one was not resolved, or the new address is resolved AND the address has changed.
            if(resolvedOk && !Arrays.equals(oldAddress.address().getAddress().getAddress(), newAddress.address().getAddress().getAddress())) {
                try {
                    Field addressField = node.getClass().getDeclaredField("address");

                    boolean wasAccessible = addressField.isAccessible();
                    addressField.setAccessible(true);

                    addressField.set(node, newAddress);

                    addressField.setAccessible(wasAccessible);

                    logger.info("Updated the resolved address of [{}] from [{}] to [{}]", node, oldAddress, newAddress);
                } catch (ReflectiveOperationException roe) {
                    logger.error("Unable to update the resolved address of [{}]. Plugin upgrade likely required.", roe, node);
                }
            }
        }
        super.connectToNode(node, light);
    }
}
