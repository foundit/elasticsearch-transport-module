/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package no.found.elasticsearch.transport

import java.util.concurrent.TimeUnit
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.client.Requests

object Bootstrap extends App {
  val settings = ImmutableSettings.settingsBuilder()
    .put("cluster.name", System.getProperty("cluster.name", "elasticsearch"))

    .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    .put("transport.found.host-suffixes", ".localhacks.com,.foundcluster.com")

    .put("transport.found.api-key", System.getProperty("api-key", "my-api-key"))
    .build()

  var client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress(System.getProperty("transport.hostname", "localhost"), Integer.parseInt(System.getProperty("transport.port", "9300"))))

  while(true) {
    try {
      val healthRequest = client.admin().cluster().health(Requests.clusterHealthRequest())
      val response = healthRequest.get(100, TimeUnit.SECONDS)

      println("STATUS: " + response.getStatus)
    } catch {
      case t: Throwable => {
        println(t.getMessage)
      }
    }

    Thread.sleep(1000)
  }
  client.close()
}