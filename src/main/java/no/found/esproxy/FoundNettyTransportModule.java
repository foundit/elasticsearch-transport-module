package no.found.esproxy;

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.netty.FoundNettyTransport;

public class FoundNettyTransportModule extends AbstractModule {

    private final Settings settings;

    public FoundNettyTransportModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        bind(FoundNettyTransport.class).asEagerSingleton();
        bind(Transport.class).to(FoundNettyTransport.class).asEagerSingleton();
    }
}
