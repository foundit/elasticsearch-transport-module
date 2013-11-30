# Found Elasticsearch Transport Module

A [MIT-licensed](https://github.com/foundit/elasticsearch-transport-module/blob/develop/LICENSE)
transport module that works with Found Elasticsearch.

**The transport module is backwards-compatible with the default transport
module.** This means that it can safely be added as a ``transport.type``,
and it will only enable its authentication and SSL support when connecting
to a Found-hosted Elasticsearch cluster. This equals less setting-differences
between local development, staging and production.

## Versions

Elasticsearch | This Module
--- | ---
 0.90.3 -> 0.90.x | 0.8.1-0907
 0.20.x -> 0.90.2 | 0.8.1-0902

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

## Example configuration

```java
// Build the settings for our transport client.
Settings settings = ImmutableSettings.settingsBuilder()
    // Setting "transport.type" enables this module:
    .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    // Create an api key via the console and add it here:
    .put("transport.found.api-key", "YOUR_API_KEY")

    .put("cluster.name", "YOUR_CLUSTER_ID")

    .put("client.transport.ignore_cluster_name", false)

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

## Complete example

A complete, runnable example can be found at
[https://github.com/foundit/elasticsearch-transport-module-example](https://github.com/foundit/elasticsearch-transport-module-example)


## Contributing

Report issues in the [issue tracker](https://github.com/foundit/elasticsearch-transport-module/issues).
