# Found Elasticsearch Transport Module

A [MIT-licensed](https://github.com/foundit/elasticsearch-transport-module/blob/develop/LICENSE)
transport module that works with Found Elasticsearch.

**The transport module is backwards-compatible with the default transport
module.** This means that it can safely be added as a ``transport.type``,
and it will only enable its authentication and SSL support when connecting
to a Found-hosted Elasticsearch cluster. This equals less setting-differences
between local development, staging and production.

## Versions

Elasticsearch | This Module | Notes
--- | --- | ---
1.4.0 -> 1.5.x | 0.8.8-1.4.0 | ``transport.type`` [has changed](#changes).
1.2.0 -> 1.3.x | 0.8.7-1.2.0 | For 1.4.0+, ``transport.type`` [has changed](#changes).
1.0.0 -> 1.1.x | 0.8.7-1.0.0
0.90.3 -> 0.90.x | 0.8.7-0.90.3
0.20.x -> 0.90.2 | 0.8.7-0.20.0

### Changes

0.8.8 -> Replace the HashedWheelTimer used for the connection-level Keep-Alive
    messages with a ScheduledExecutorService provided by Elasticsearch.

0.8.7 -> (for 1.4.0+): The Transport startup in Elasticsearch has been
    [refactored](https://github.com/elasticsearch/elasticsearch/commit/247ff7d80117ee841b3e8296d125df5aad6f0d30),
    so the ``transport.type`` should now point directly to our ``Transport`` instead of the ``TransportModule``.
    The new transport type value shuold be: ``org.elasticsearch.transport.netty.FoundNettyTransport``.

0.8.7 -> Extends the default netty ChannelPipeline instead of replacing it and
    avoids delaying initial messages in order to fix some race conditions
    experienced during (re-)connection.

0.8.6 -> Resolves addresses of nodes when connecting periodically in case of DNS
    changes. This release was not published to Maven Central.

0.8.5 -> Sends the current transport module version to the server along with the
    current Elasticsearch version.

0.8.4 -> Fix a bug caused by stopping the re-used timers that showed up if the
    Transport Client was restarted.

0.8.3 -> Attempt to re-use timers and cancel timeouts when reconnecting.

0.8.1 -> Fixes an issue where Heroku users were unable to use the transport module
    due to a too narrow cipher suite selection.

### Older versions

We do not recommend using an older version unless you have a very good reason.
Older versions are not guaranteed to work as expected.

## Installing

To install, add a dependency to this module in your build system :

```xml
    <dependency>
        <groupId>no.found.elasticsearch</groupId>
        <artifactId>elasticsearch-transport-module</artifactId>
        <version>{version-from-the-above-table}</version>
    </dependency>
```

The module is enabled by adding this project as a dependency to your application
and setting the ``transport.type`` setting in Elasticsearch to
``no.found.elasticsearch.transport.netty.FoundNettyTransportModule``.

Note: This is not a standard Elasticsearch plugin, it just needs to be on the
application classpath.

## Configuring an API key

In order to use this module, you must configure one or more API-keys. API-keys
are stored as a list of acceptable keys under ``api_keys`` at the root level
of the ACL. For example:

```yaml
default: deny

api_keys:
    - s6aW9aAMZjDMbuhj
    - 6hKZsTqBru9KnVaW

auth:
  users:
    ...

rules:
  - ...
```

In the above example, both ``s6aW9aAMZjDMbuhj`` and ``6hKZsTqBru9KnVaW`` would be
valid API-keys.

## New Elasticsearch settings

New settings introduced by this module:

* ``transport.found.host-suffixes``: A comma-separated list of host suffixes that
 trigger our attempt to authenticate with Found Elasticsearch. Defaults to
 ``foundcluster.com,found.no``.

* ``transport.found.ssl-ports``: A comma-separated list of ports that trigger our
 SSL support. Defaults to ``9343``.

* ``transport.found.api-key``: An API-key which is used to authorize this client
 when connecting to Found Elasticsearch. API-keys are managed via the console as
 a list of Strings under the root level key "api_keys". Defaults to
 ``missing-api-key``

* ``transport.found.ssl.unsafe_allow_self_signed``: Whether to accept self-signed
 certificates when using SSL. This is unsafe and allows for MITM-attacks, but
 may be useful for testing. Defaults to ``false``.

*  ``transport.found.connection-keep-alive-interval``: The interval in which to send
 keep-alive messages. Defaults to ``20s``. Set to ``0`` to disable.

## Recommended tweaks to existing settings:

We recommend setting ``client.transport.nodes_sampler_interval`` to ``30s`` and setting
``client.transport.ping_timeout`` to ``30s`` when using Elasticsearch over non-local networks (this also goes for deployments in the same Amazon EC2 region, as the connections may be routed across a regions availability zones).

Not doing so may greatly increase the number of disconnects and reconnects due to intermittent slow routers / congested networks / garbage collection and a host of other transient problems.

## Example configuration

```java
// Build the settings for our transport client.
Settings settings = ImmutableSettings.settingsBuilder()
    // Setting "transport.type" enables this module, depending on the Elasticsearch version
    // - for versions 1.4.0 and later:
    .put("transport.type", "org.elasticsearch.transport.netty.FoundNettyTransport")
    // - for earlier versions (1.2.0 -> 1.3.x):
    //.put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    
    // Create an api key via the console and add it here:
    .put("transport.found.api-key", "YOUR_API_KEY")

    .put("cluster.name", "YOUR_CLUSTER_ID")

    .put("client.transport.ignore_cluster_name", false)
    .put("client.transport.nodes_sampler_interval", "30s")
    .put("client.transport.ping_timeout", "30s")

    .build();

// Instantiate a TransportClient and add Found Elasticsearch to the list of addresses to connect to.
// Only port 9343 (SSL-encrypted) is currently supported.
Client client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("YOUR_CLUSTER_ID-REGION.foundcluster.com", 9343));
```

## Example usage

```java
while(true) {
    try {
        System.out.print("Getting cluster health... "); System.out.flush();
        ActionFuture<ClusterHealthResponse> healthFuture = client.admin().cluster().health(Requests.clusterHealthRequest());
        ClusterHealthResponse healthResponse = healthFuture.get(5, TimeUnit.SECONDS);
        System.out.println("Got response: " + healthResponse.getStatus());
    } catch(Throwable t) {
        System.out.println("Error: " + t);
    }
    try {
        Thread.sleep(3000);
    } catch (InterruptedException ie) { ie.printStackTrace(); }
}
```

### DNS-caching

Note that the JVM will cache DNS lookups infinitely, by default. Since this client is connecting to a load balancer which can change IPs, it’s important to disable this functionality.

This can be done by setting the security property ``networkaddress.cache.ttl`` in ``$JAVA_HOME/lib/security/java.security``. You can also do it programmatically, e.g. ``java.security.Security.setProperty("networkaddress.cache.ttl" , "60")``.

Note that you can not configure it as a JVM-property with e.g. ~~-Dnetworkaddress.cache.ttl=60.~~

## Complete example

A complete, runnable example can be found at
[https://github.com/foundit/elasticsearch-transport-module-example](https://github.com/foundit/elasticsearch-transport-module-example)


## Contributing

Report issues in the [issue tracker](https://github.com/foundit/elasticsearch-transport-module/issues).
