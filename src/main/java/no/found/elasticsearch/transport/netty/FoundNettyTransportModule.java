package no.found.elasticsearch.transport.netty;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.netty.FoundNettyTransport;

/**
 * A transport module that works with Found Elasticsearch.
 *
 * <p>Binds the {@link Transport} to {@link FoundNettyTransport}, which replaces
 * the default client transport with one that does an initial authentication-step
 * and supports SSL without any external dependencies.</p>
 *
 * <p>New settings introduced by this module:</p>
 *
 * <ul>
 *  <li>{@code transport.found.host-suffixes}: A comma-separated list of host suffixes that
 *  trigger our attempt to authenticate with Found Elasticsearch. Defaults to
 *  {@code foundcluster.com,found.no}".</li>
 *
 *  <li>{@code transport.found.ssl-ports}: A comma-separated list of ports that trigger our
 *  SSL support. Defaults to {@code 9343}".</li>
 *
 *  <li>{@code transport.found.api-key}: An API-key which is used to authorize this client
 *  when connecting to Found Elasticsearch. API-keys are managed via the console as
 *  a list of Strings under the root level key "api_keys". Defaults to
 *  {@code missing-api-key}</li>
 *
 *  <li>{@code transport.found.ssl.unsafe_allow_self_signed}: Whether to accept self-signed
 *  certificates when using SSL. This is unsafe and allows for MITM-attacks, but
 *  may be useful for testing. Defaults to {@code false}.</li>
 * </ul>
 *
 * <p><b>The transport is backwards-compatible with the default transport.</b></p>
 *
 * <p>Example configuration:</p>
 *
 * <pre>
 * {@code // Build the settings for our client.
 *   Settings settings = ImmutableSettings.settingsBuilder()
 *       // Setting "transport.type" enables this module:
 *       .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
 *       // Create an api key via the console and add it here:
 *       .put("transport.found.api-key", "YOUR_API_KEY")
 *
 *       // Used by Elasticsearch:
 *       .put("cluster.name", "YOUR_CLUSTER_ID")
 *       .put("client.transport.ignore_cluster_name", false)
 *
 *       .build();
 *
 *   // Instantiate a TransportClient and add Found Elasticsearch to the list of addresses to connect to.
 *   // Only port 9343 (SSL-encrypted) is currently supported.
 *   Client client = new TransportClient(settings)
 *       .addTransportAddress(new InetSocketTransportAddress("YOUR_CLUSTER_ID-REGION.foundcluster.com", 9343));
 * }
 * </pre>
 *
 * <p>Example usage:</p>
 *
 * <pre>
 * {@code while(true) {
 *      try {
 *          System.out.print("Getting cluster health... "); System.out.flush();
 *          ActionFuture<ClusterHealthResponse> healthFuture = client.admin().cluster().health(Requests.clusterHealthRequest());
 *          ClusterHealthResponse healthResponse = healthFuture.get(5, TimeUnit.SECONDS);
 *          System.out.println("Got response: " + healthResponse.getStatus());
 *      } catch(Throwable t) {
 *          System.out.println("Error: " + t);
 *      }
 *      try {
 *          Thread.sleep(3000);
 *      } catch (InterruptedException ie) { ie.printStackTrace(); }
 *  }
 * }
 * </pre>
 */
public class FoundNettyTransportModule extends AbstractModule {

    private final Settings settings;

    public FoundNettyTransportModule(Settings settings) {
        this.settings = settings;

        if(settings.getAsBoolean("client.transport.sniff", false)) {
            throw new ElasticSearchException("The transport client setting \"client.transport.sniff\" is [true], which is not supported by this transport.");
        }
    }

    @Override
    protected void configure() {
        bind(FoundNettyTransport.class).asEagerSingleton();
        bind(Transport.class).to(FoundNettyTransport.class).asEagerSingleton();
    }
}
