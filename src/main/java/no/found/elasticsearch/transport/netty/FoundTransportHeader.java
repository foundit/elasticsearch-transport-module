/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package no.found.elasticsearch.transport.netty;

import org.elasticsearch.Version;
import org.elasticsearch.common.netty.buffer.ChannelBuffer;
import org.elasticsearch.common.netty.buffer.ChannelBuffers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * An utility class for generating the initial headers for auth.
 */
public class FoundTransportHeader {
    private final String clusterName;
    private final String apiKey;

    private static final int revisionLength = 4;
    private static final int revision = 1;

    private static final int versionLength = 4;
    private static final int moduleVersionLength = 4;

    public FoundTransportHeader(String clusterName, String apiKey) {
        this.clusterName = clusterName;
        this.apiKey = apiKey;
    }

    /**
     * Constructs and returns a new ChannelBuffer with the correct header for the given
     * cluster and API-key.
     *
     * @return The ChannelBuffer containing the header.
     * @throws IOException
     */
    public ChannelBuffer getHeaderBuffer() throws IOException {
        byte[] clusterNameBytes = clusterName.getBytes(StandardCharsets.UTF_8);
        int clusterNameLength = clusterNameBytes.length;

        byte[] apiKeyBytes = apiKey.getBytes(StandardCharsets.UTF_8);
        int apiKeyLength = apiKeyBytes.length;

        ChannelBuffer headerPayload = ChannelBuffers.wrappedBuffer(
                getIntBytes(revisionLength),
                getIntBytes(revision),

                getIntBytes(versionLength + moduleVersionLength),
                getIntBytes(Version.CURRENT.id),
                getIntBytes(FoundModuleVersion.CURRENT.id),

                getIntBytes(clusterNameLength),
                clusterNameBytes,

                getIntBytes(apiKeyLength),
                apiKeyBytes
        );

        return ChannelBuffers.wrappedBuffer(
                ChannelBuffers.wrappedBuffer(getIntBytes(headerPayload.readableBytes())),
                headerPayload
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
}
