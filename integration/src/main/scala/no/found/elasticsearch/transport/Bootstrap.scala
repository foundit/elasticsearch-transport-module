package no.found.elasticsearch.transport

import java.util.concurrent.TimeUnit
import java.lang.Throwable
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.client.Requests


object Bootstrap extends App {
  val settings = ImmutableSettings.settingsBuilder()
    //.put("cluster.name", "20ef6fed67864b4aba920f3a2c45d7f3")
    //.put("client.transport.ignore_cluster_name", false)

    .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    .put("transport.found.host-suffixes", ".localhacks.com,.foundcluster.com")

    .put("transport.found.api-key", "foobarbaz")

    //.put("transport.netty.connections_per_node.low", 1)
    //.put("transport.netty.connections_per_node.med", 1)
    //.put("transport.netty.connections_per_node.high", 1)

    .build()

  var client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("20ef6fed67864b4aba920f3a2c45d7f3-local-1.localhack2s.com", 9300))
    //.addTransportAddress(new InetSocketTransportAddress("92e121b43d8eb1e2be00992154ecfa3c-eu-west-1.foundcluster.com", 9343))

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
      }
    }

    Thread.sleep(3000)
  }
}