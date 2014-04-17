/*
 * Copyright (c) 2013, Found AS.
 * See LICENSE for details.
 */

package no.found.elasticsearch.transport.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.netty.FoundNettyTransport;
import org.junit.Test;

import java.lang.reflect.Field;

public class TestFoundNettyTransportModule {
    @Test
    public void testInjection() throws Exception {
        Settings settings = ImmutableSettings.settingsBuilder()
            .put("transport.type", "no.found.elasticsearch.transport.netty.FoundNettyTransportModule")
            .build();

        TransportClient client = new TransportClient(settings);

        Field injectorField = client.getClass().getDeclaredField("injector");
        injectorField.setAccessible(true);
        Injector injector = (Injector)injectorField.get(client);

        assertEquals(FoundNettyTransport.class, injector.getInstance(Transport.class).getClass());
    }

    @Test
    public void testNotInjected() throws Exception {
        Settings settings = ImmutableSettings.settingsBuilder()
                .build();

        TransportClient client = new TransportClient(settings);

        Field injectorField = client.getClass().getDeclaredField("injector");
        injectorField.setAccessible(true);
        Injector injector = (Injector)injectorField.get(client);

        assertNotEquals(FoundNettyTransport.class, injector.getInstance(Transport.class).getClass());
    }
}
