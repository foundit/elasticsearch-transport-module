package no.found.esproxy


import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.Requests
import java.util.concurrent.TimeUnit


object Bootstrap extends App {
  val settings = ImmutableSettings.settingsBuilder()
    .put("cluster.name", "c70ce4781ef74c0aa41e315241556a5b")
    .put("client.transport.ignore_cluster_name", true)

    .put("client.transport.sniff", false)

    .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    .put("transport.found.host-suffixes", ".localhacks.com,.foundcluster.com")
    .put("transport.found.ssl.unsafe_allow_self_signed", true)
    .put("transport.found.ssl-ports", "9343")

    .put("transport.found.api-key", "foobarbaz")

    .put("transport.netty.connections_per_node.low", 1)
    .put("transport.netty.connections_per_node.med", 1)
    .put("transport.netty.connections_per_node.high", 1)

    .build()

  var client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("c70ce4781ef74c0aa41e315241556a5b.localhacks.com", 9343))

  while(true) {
    try {
      val healthRequest = client.admin().cluster().health(Requests.clusterHealthRequest())
      print("Getting response... ")
      System.out.flush()
      val response = healthRequest.get(5, TimeUnit.SECONDS)
      println(" got response.")

      println("STATUS: " + response.getStatus)
    } catch {
      case t: Throwable => {
        println(t.getMessage)
        //client.addTransportAddress(new InetSocketTransportAddress("foo.localhacks.com", 9343))
      }
    }

    Thread.sleep(3000)
  }
}