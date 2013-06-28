package org.elasticsearch.transport.netty;

import no.found.elasticsearch.transport.netty.FoundPrefixer;
import no.found.elasticsearch.transport.netty.FoundSSLHandler;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.netty.bootstrap.ClientBootstrap;
import org.elasticsearch.common.netty.channel.*;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import javax.net.ssl.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
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

    @Inject
    public FoundNettyTransport(Settings settings, ClusterName clusterName, ThreadPool threadPool, NetworkService networkService, Injector injector) {
        super(settings, threadPool, networkService);

        hostSuffixes = settings.getAsArray("transport.found.host-suffixes", new String[] {".foundcluster.com", ".found.no"});

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
        this.injector = injector;
    }

/*

    @Override
    public void sendRequest(DiscoveryNode node, long requestId, String action, TransportRequest request, TransportRequestOptions options) throws IOException, TransportException {
        if(!nodeConnected(node)) {
            connectToNode(node);
        }
        super.sendRequest(node, requestId, action, request, options);
    }

*/

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
                    return Channels.pipeline(new SimpleChannelHandler() {
                        List<MessageEvent> pendingEvents = new ArrayList<MessageEvent>();

                        @Override
                        public void channelBound(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
                            final ChannelPipeline pipeline = originalFactory.getPipeline();

                            SocketAddress socketAddress = ctx.getChannel().getRemoteAddress();

                            boolean removedThis = false;

                            if(socketAddress instanceof InetSocketAddress) {
                                InetSocketAddress inetSocketAddress = (InetSocketAddress)socketAddress;

                                boolean isFoundCluster = false;
                                for(String suffix: hostSuffixes) isFoundCluster = isFoundCluster || inetSocketAddress.getHostString().endsWith(suffix);

                                if(isFoundCluster) {
                                    for(int sslPort: sslPorts) {
                                        if(inetSocketAddress.getPort() == sslPort) {
                                            FoundSSLHandler handler = getSSLHandler(inetSocketAddress);
                                            ctx.getPipeline().addFirst("ssl", handler);
                                            break;
                                        }
                                    }

                                    //ctx.getPipeline().addLast("found-prefixer", new FoundPrefixer(clusterName));
                                    //new FoundPrefixer(clusterName).sendPrefix(ctx);
                                    //ctx.sendDownstream(new DownstreamMessageEvent());

                                    ctx.getPipeline().remove(this);
                                    ctx.sendUpstream(e);
                                    ctx.getChannel().write(new FoundPrefixer(clusterName).getPrefixBuffer());

                                    removedThis = true;
                                }
                            }

                            if(!removedThis) {
                                ctx.getPipeline().remove(this);
                                ctx.sendUpstream(e);
                            }

                            while(true) {
                                ChannelHandler handler = pipeline.getFirst();
                                if(handler == null) break;

                                ChannelHandlerContext handlerContext = pipeline.getContext(handler);

                                ctx.getPipeline().addLast(handlerContext.getName(), handler);
                                pipeline.remove(handler);
                            }

                            for(MessageEvent event: pendingEvents) ctx.sendDownstream(event);
                            pendingEvents.clear();
                        }


                        @Override
                        public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                            pendingEvents.add(e);
                        }

                        private FoundSSLHandler getSSLHandler(InetSocketAddress inetSocketAddress) throws NoSuchAlgorithmException {
                            String hostString = inetSocketAddress.getHostString();

                            SSLEngine engine = createSslEngine(inetSocketAddress, hostString);

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
                            engine.setNeedClientAuth(false);

                            FoundSSLHandler handler = new FoundSSLHandler(engine);
                            handler.setIssueHandshake(false);
                            handler.setCloseOnSSLException(false);
                            handler.setEnableRenegotiation(true);

                            return handler;
                        }

                        private SSLEngine createSslEngine(InetSocketAddress inetSocketAddress, final String hostString) throws NoSuchAlgorithmException {
                            if(settings.getAsBoolean("transport.found.ssl.unsafe_allow_self_signed", false)) {
                                final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                                    @Override
                                    public void checkClientTrusted( final X509Certificate[] chain, final String authType ) {

                                    }
                                    @Override
                                    public void checkServerTrusted( final X509Certificate[] chain, final String authType ) throws CertificateException {

                                        for(X509Certificate cert: chain) {
                                            String dn = cert.getSubjectX500Principal().getName();

                                            if(dn.contains("CN="+hostString)) return;

                                            String[] hostParts = hostString.split("\\.", 2);
                                            if(hostParts.length > 1) {
                                                String lastHostPart = hostParts[1];

                                                if(dn.contains("CN=*."+lastHostPart)) return;
                                            }
                                        }

                                        throw new CertificateException("No name matching " + hostString + " found");
                                    }
                                    @Override
                                    public X509Certificate[] getAcceptedIssuers() {
                                        return new X509Certificate[0];
                                    }
                                } };

                                // Install the all-trusting trust manager
                                SSLContext sslContext = SSLContext.getInstance( "SSL" );
                                try {
                                    sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
                                } catch (KeyManagementException e) {
                                    e.printStackTrace();
                                }
                                return sslContext.createSSLEngine(hostString, inetSocketAddress.getPort());
                            } else {
                                return SSLContext.getDefault().createSSLEngine(hostString, inetSocketAddress.getPort());
                            }
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
