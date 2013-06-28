package no.found.elasticsearch.transport.netty;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;
import org.elasticsearch.common.netty.channel.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

public class FoundPrefixer extends SimpleChannelHandler {
    private final ClusterName clusterName;

    public FoundPrefixer(ClusterName clusterName) {
        this.clusterName = clusterName;
    }

    private boolean prefixed = false;

    @Override
    public void connectRequested(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.connectRequested(ctx, e);
    }

    @Override
    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelBound(ctx, e);

        prefixed = false;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if(!prefixed) {
            prefixed = true;

            SocketAddress remoteAddress = ctx.getChannel().getRemoteAddress();

            if(remoteAddress instanceof InetSocketAddress) {
                String remoteName = ((InetSocketAddress)remoteAddress).getHostString();
                if(remoteName.contains(".foundcluster.com") || remoteName.contains(".localhacks.com")) {
                    sendPrefix(ctx);
                }
            }
        }
        ctx.sendDownstream(e);
    }

    protected ChannelFuture sendPrefix(ChannelHandlerContext ctx) throws IOException {
        return ctx.getChannel().write(getPrefixBuffer());
    }

    public ChannelBuffer getPrefixBuffer() throws IOException {
        int versionLength = 4;
        byte[] clusterNameBytes = clusterName.value().getBytes(StandardCharsets.UTF_8);
        int clusterNameLength = clusterNameBytes.length;

        return ChannelBuffers.wrappedBuffer(
            concat(getIntBytes(clusterNameLength + 4 + versionLength),

                    getIntBytes(Version.CURRENT.id),

                    getIntBytes(clusterNameLength),
                    clusterNameBytes
            )
        );
    }

    protected byte[] getIntBytes(int i) throws IOException {
        byte[] bytes = new byte[4];

        bytes[0] = ((byte) (i >> 24));
        bytes[1] = ((byte) (i >> 16));
        bytes[2] = ((byte) (i >> 8));
        bytes[3] = ((byte) i);

        return bytes;
    }

    protected byte[] concat(byte[]... allBytes) {
        int size = 0;
        for (byte[] allByte1 : allBytes) {
            size += allByte1.length;
        }

        byte[] result = new byte[size];
        int current = 0;

        for (byte[] allByte : allBytes) {
            for (byte anAllByte : allByte) {
                result[current++] = anAllByte;
            }
        }
        return result;
    }
}
