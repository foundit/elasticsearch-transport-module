package org.elasticsearch.transport.netty;

import no.found.esproxy.FoundPrefixer;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.netty.bootstrap.ClientBootstrap;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class FoundNettyTransport extends NettyTransport {
    private final ClusterName clusterName;

    @Inject
    public FoundNettyTransport(Settings settings, ClusterName clusterName, ThreadPool threadPool, NetworkService networkService) {
        super(settings, threadPool, networkService);

        this.clusterName = clusterName;
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
                    return Channels.pipeline(new SimpleChannelHandler() {
                        @Override
                        public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                            ChannelPipeline pipeline = originalFactory.getPipeline();

                            boolean addedSsl = false;

                            SocketAddress socketAddress = ctx.getChannel().getRemoteAddress();
                            if(socketAddress instanceof InetSocketAddress) {
                                InetSocketAddress inetSocketAddress = (InetSocketAddress)socketAddress;

                                if(Integer.toString(inetSocketAddress.getPort()).endsWith("43")) {
                                    FoundSSLHandler handler = getSSLHandler(inetSocketAddress);
                                    ctx.getPipeline().addFirst("ssl", handler);

                                    addedSsl = true;
                                }
                            }

                            if(addedSsl) {
                                ctx.getPipeline().addAfter("ssl", "found-prefixer", new FoundPrefixer(clusterName));
                            } else {
                                ctx.getPipeline().addLast("found-prefixer", new FoundPrefixer(clusterName));
                            }

                            while(true) {
                                ChannelHandler handler = pipeline.getFirst();
                                if(handler == null) break;

                                ChannelHandlerContext handlerContext = pipeline.getContext(handler);

                                ctx.getPipeline().addLast(handlerContext.getName(), handler);
                                pipeline.remove(handler);
                            }

                            ctx.getPipeline().remove(this);

                            ctx.sendUpstream(e);

                            for(MessageEvent event: pendingEvents) ctx.sendDownstream(event);
                        }

                        List<MessageEvent> pendingEvents = new ArrayList<MessageEvent>();

                        @Override
                        public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                            pendingEvents.add(e);
                        }

                        private FoundSSLHandler getSSLHandler(InetSocketAddress inetSocketAddress) throws NoSuchAlgorithmException {
                            String hostString = inetSocketAddress.getHostString();
                            if(hostString.contains("localhacks.com")) {
                                hostString = "217aaf92c60b48eca43a9e9773eab36d-us-east-1.foundcluster.com";
                            }

                            SSLEngine engine = SSLContext.getDefault().createSSLEngine(hostString, inetSocketAddress.getPort());

                            engine.setEnabledCipherSuites(new String[] {
                                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
                                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                                    "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
                                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
                                    "TLS_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
                                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                                    "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                                    "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                                    "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                                    "SSL_RSA_WITH_RC4_128_SHA",
                                    "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
                                    "TLS_ECDH_RSA_WITH_RC4_128_SHA",
                                    "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                                    "SSL_RSA_WITH_RC4_128_SHA",
                                    "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
                                    "SSL_RSA_WITH_RC4_128_MD5",
                                    "TLS_ECDH_RSA_WITH_RC4_128_SHA",
                                    "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
                                    "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                                    "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
                                    "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
                                    "SSL_RSA_WITH_RC4_128_MD5",
                                    "TLS_EMPTY_RENEGOTIATION_INFO_SCSV"
                            });
                            engine.setUseClientMode(true);

                            SSLParameters sslParams = new SSLParameters();
                            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                            engine.setSSLParameters(sslParams);

                            engine.setEnableSessionCreation(true);
                            engine.setNeedClientAuth(true);

                            FoundSSLHandler handler = new FoundSSLHandler(engine);
                            handler.setIssueHandshake(true);
                            handler.setCloseOnSSLException(true);

                            return handler;
                        }
                    });
                }
            });
            
            clientBootstrapField.setAccessible(false);
        } catch (ReflectiveOperationException roe) {
            roe.printStackTrace();
        }
    }
}
