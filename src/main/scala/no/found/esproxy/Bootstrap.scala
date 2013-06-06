package no.found.esproxy


import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.Requests


object Bootstrap extends App {
  val settings = ImmutableSettings.settingsBuilder()
    .put("cluster.name", "foobarbazkasfasef")
    .put("client.transport.ignore_cluster_name", true)
    .put("transport.type", "no.found.esproxy.FoundNettyTransportModule")
    .put("transport.netty.connections_per_node.low", 1)
    .put("transport.netty.connections_per_node.med", 1)
    .put("transport.netty.connections_per_node.high", 1)
    .build()

  var client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("foo.localhacks.com", 9343))

  println("whuts.")

  val healthRequest = client.admin().cluster().health(Requests.clusterHealthRequest())
  val response = healthRequest.get()

  println("STATUS: " + response.getStatus)
}