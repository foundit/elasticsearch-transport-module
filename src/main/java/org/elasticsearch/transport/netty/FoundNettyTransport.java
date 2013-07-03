package org.elasticsearch.transport.netty;

import no.found.elasticsearch.transport.netty.FoundPrefixer;
import no.found.elasticsearch.transport.netty.FoundSSLHandler;
import no.found.elasticsearch.transport.netty.FoundSSLUtils;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.netty.bootstrap.ClientBootstrap;
import org.elasticsearch.common.netty.buffer.BigEndianHeapChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import javax.net.ssl.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class FoundNettyTransport extends NettyTransport {
    private final ClusterName clusterName;
    private final Injector injector;

    private final String[] hostSuffixes;
    private final int[] sslPorts;
    private final String apiKey;
    private final boolean unsafeAllowSelfSigned;

    @Inject
    public FoundNettyTransport(Settings settings, ClusterName clusterName, ThreadPool threadPool, NetworkService networkService, Injector injector) {
        super(settings, threadPool, networkService);

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

        this.clusterName = clusterName;

        this.apiKey = settings.get("transport.found.api-key");

        this.injector = injector;
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
                    return Channels.pipeline(new FoundSwitchingChannelHandler(logger, originalFactory, unsafeAllowSelfSigned, hostSuffixes, sslPorts, apiKey));
                }
            });

            clientBootstrapField.setAccessible(false);
        } catch (ReflectiveOperationException roe) {
            roe.printStackTrace();
        }
    }
}
