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

public class FoundPrefixer {
    private final String clusterName;
    private final String apiKey;

    private static final int revisionLength = 4;
    private static final int revision = 1;

    private static final int versionLength = 4;

    public FoundPrefixer(String clusterName, String apiKey) {
        this.clusterName = clusterName;
        this.apiKey = apiKey;
    }

    public ChannelBuffer getPrefixBuffer() throws IOException {
        byte[] clusterNameBytes = clusterName.getBytes(StandardCharsets.UTF_8);
        int clusterNameLength = clusterNameBytes.length;

        byte[] apiKeyBytes = apiKey.getBytes(StandardCharsets.UTF_8);
        int apiKeyLength = apiKeyBytes.length;

        return ChannelBuffers.wrappedBuffer(
            concat(getIntBytes(4 + revisionLength + 4 + versionLength + 4 + clusterNameLength + 4 + apiKeyLength),

                    getIntBytes(revisionLength),
                    getIntBytes(revision),

                    getIntBytes(versionLength),
                    getIntBytes(Version.CURRENT.id),

                    getIntBytes(clusterNameLength),
                    clusterNameBytes,

                    getIntBytes(apiKeyLength),
                    apiKeyBytes
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
