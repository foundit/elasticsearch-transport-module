package no.found.elasticsearch.transport.netty.ssl;

import org.elasticsearch.action.admin.cluster.state.ClusterStateAction;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClientNodesService;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.BaseTransportResponseHandler;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportRequestOptions;
import org.elasticsearch.transport.TransportService;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;


public class FoundTransportClientNodesService extends TransportClientNodesService {
    @Inject
    public FoundTransportClientNodesService(Settings settings, ClusterName clusterName, TransportService transportService, ThreadPool threadPool) {
        super(settings, clusterName, transportService, threadPool);


    }

    /*
    private class SniffNodesSampler implements NodeSampler {

        @Override
        public synchronized void sample() {
            if (closed) {
                return;
            }

            // the nodes we are going to ping include the core listed nodes that were added
            // and the last round of discovered nodes
            Set<DiscoveryNode> nodesToPing = Sets.newHashSet();
            for (DiscoveryNode node : listedNodes) {
                nodesToPing.add(node);
            }
            for (DiscoveryNode node : nodes) {
                nodesToPing.add(node);
            }

            final CountDownLatch latch = new CountDownLatch(nodesToPing.size());
            final Queue<ClusterStateResponse> clusterStateResponses = ConcurrentCollections.newQueue();
            for (final DiscoveryNode listedNode : nodesToPing) {
                threadPool.executor(ThreadPool.Names.MANAGEMENT).execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!transportService.nodeConnected(listedNode)) {
                                try {

                                    // if its one of hte actual nodes we will talk to, not to listed nodes, fully connect
                                    if (nodes.contains(listedNode)) {
                                        logger.trace("connecting to cluster node [{}]", listedNode);
                                        transportService.connectToNode(listedNode);
                                    } else {
                                        // its a listed node, light connect to it...
                                        logger.trace("connecting to listed node (light) [{}]", listedNode);
                                        transportService.connectToNodeLight(listedNode);
                                    }
                                } catch (Exception e) {
                                    logger.debug("failed to connect to node [{}], ignoring...", e, listedNode);
                                    latch.countDown();
                                    return;
                                }
                            }
                            transportService.sendRequest(listedNode, ClusterStateAction.NAME,
                                    Requests.clusterStateRequest()
                                            .filterAll().filterNodes(false).local(true),
                                    TransportRequestOptions.options().withHighType().withTimeout(pingTimeout),
                                    new BaseTransportResponseHandler<ClusterStateResponse>() {

                                        @Override
                                        public ClusterStateResponse newInstance() {
                                            return new ClusterStateResponse();
                                        }

                                        @Override
                                        public String executor() {
                                            return ThreadPool.Names.SAME;
                                        }

                                        @Override
                                        public void handleResponse(ClusterStateResponse response) {
                                            clusterStateResponses.add(response);
                                            latch.countDown();
                                        }

                                        @Override
                                        public void handleException(TransportException e) {
                                            logger.info("failed to get local cluster state for {}, disconnecting...", e, listedNode);
                                            transportService.disconnectFromNode(listedNode);
                                            latch.countDown();
                                        }
                                    });
                        } catch (Exception e) {
                            logger.info("failed to get local cluster state info for {}, disconnecting...", e, listedNode);
                            transportService.disconnectFromNode(listedNode);
                            latch.countDown();
                        }
                    }
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                return;
            }

            HashSet<DiscoveryNode> newNodes = new HashSet<DiscoveryNode>();
            for (ClusterStateResponse clusterStateResponse : clusterStateResponses) {
                if (!ignoreClusterName && !clusterName.equals(clusterStateResponse.getClusterName())) {
                    logger.warn("node {} not part of the cluster {}, ignoring...", clusterStateResponse.getState().nodes().localNode(), clusterName);
                }
                for (DiscoveryNode node : clusterStateResponse.getState().nodes().dataNodes().values()) {
                    newNodes.add(node);
                }
            }
            // now, make sure we are connected to all the updated nodes
            for (Iterator<DiscoveryNode> it = newNodes.iterator(); it.hasNext(); ) {
                DiscoveryNode node = it.next();
                if (!transportService.nodeConnected(node)) {
                    try {
                        logger.trace("connecting to node [{}]", node);
                        transportService.connectToNode(node);
                    } catch (Exception e) {
                        it.remove();
                        logger.debug("failed to connect to discovered node [" + node + "]", e);
                    }
                }
            }
            nodes = new ImmutableList.Builder<DiscoveryNode>().addAll(newNodes).build();
        }
    }
    */
}
