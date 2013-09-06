# Found Elasticsearch Transport Module

A transport module that works with Found Elasticsearch.

## Installing

To install, add a dependency to this module in your build system :

```xml
    <dependency>
        <groupId>no.found.elasticsearch</groupId>
        <artifactId>elasticsearch-transport-module</artifactId>
        <version>1.1.18-SNAPSHOT</version>
    </dependency>
```

The module is enabled by adding this project as a dependency to your application
and setting the ``transport.type`` setting in Elasticsearch to
``no.found.elasticsearch.transport.netty.FoundNettyTransportModule``.

Note: This is not a standard Elasticsearch plugin, it just needs to be on the
application classpath.

## Configuring an API key.

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

* **transport.found.host-suffixes**: A comma-separated list of host suffixes that
 trigger our attempt to authenticate with Found Elasticsearch. Defaults to
 ``foundcluster.com,found.no``.

* **transport.found.ssl-ports**: A comma-separated list of ports that trigger our
 SSL support. Defaults to ``9343``.

* **transport.found.api-key**: An API-key which is used to authorize this client
 when connecting to Found Elasticsearch. API-keys are managed via the console as
 a list of Strings under the root level key "api_keys". Defaults to
 ``missing-api-key``

* **transport.found.ssl.unsafe_allow_self_signed**: Whether to accept self-signed
 certificates when using SSL. This is unsafe and allows for MITM-attacks, but
 may be useful for testing. Defaults to ``false``.

**The transport is backwards-compatible with the default transport.**

## Example configuration

```java
// Build the settings for our transport client.
Settings settings = ImmutableSettings.settingsBuilder()
    // Setting "transport.type" enables this module:
    .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
    // Create an api key via the console and add it here:
    .put("transport.found.api-key", "YOUR_API_KEY")

    // Used by Elasticsearch:
    .put("cluster.name", "YOUR_CLUSTER_ID")
    .put("client.transport.ignore_cluster_name", false)

    .build();

// Instantiate a TransportClient and add Found Elasticsearch to the list of addresses to connect to.
// Only port 9343 (SSL-encrypted) is currently supported.
Client client = new TransportClient(settings)
    .addTransportAddress(new InetSocketTransportAddress("YOUR_CLUSTER_ID-REGION.foundcluster.com", 9343));
```

## Example usage:

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
