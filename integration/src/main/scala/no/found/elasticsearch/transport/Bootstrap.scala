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
import org.elasticsearch.common.logging.ESLoggerFactory

object Bootstrap extends App {
  val logger = ESLoggerFactory.getLogger(getClass.getCanonicalName)

  val settings = ImmutableSettings.settingsBuilder()
    .put("cluster.name", System.getProperty("cluster.name", "elasticsearch"))

    .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    .put("transport.found.host-suffixes", ".found.io,.localhacks.com,.foundcluster.com")

    .put("transport.found.api-key", System.getProperty("api-key", "my-api-key"))
    .build()

  var client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress(System.getProperty("transport.host", "localhost"), Integer.parseInt(System.getProperty("transport.port", "9300"))))

  val healthChecks = Integer.parseInt(System.getProperty("healthchecks", "10"))
  var count = 0
  var done = false

  var errors = 0

  while(!done) {
    try {
      val healthRequest = client.admin().cluster().health(Requests.clusterHealthRequest().listenerThreaded(false))
      val response = healthRequest.get(100, TimeUnit.SECONDS)

      logger.info("Status: [{}]", response.getStatus)
    } catch {
      case t: Throwable => {
        errors += 1
        logger.error("Failed status: [{}]", t.getMessage)
      }
    }

    count += 1
    if(healthChecks == -1 || count < healthChecks)
      Thread.sleep(1000)
    else {
      done = true
    }
  }
  client.close()

  System.exit(if(errors == 0) 0 else 1)
}