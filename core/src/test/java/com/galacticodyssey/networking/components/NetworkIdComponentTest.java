package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkIdComponentTest {

    @Test
    void networkIdComponentStoresId() {
        NetworkIdComponent net = new NetworkIdComponent(42);
        assertEquals(42, net.networkId);
    }

    @Test
    void authorityComponentDefaultsToServer() {
        AuthorityComponent auth = new AuthorityComponent();
        assertEquals(AuthorityComponent.Owner.SERVER, auth.owner);
    }

    @Test
    void authorityComponentTracksZoneId() {
        AuthorityComponent auth = new AuthorityComponent();
        auth.owner = AuthorityComponent.Owner.ZONE_SERVER;
        auth.ownerZoneId = "zone-alpha";
        assertEquals("zone-alpha", auth.ownerZoneId);
    }

    @Test
    void ashleyFamilyMatchesNetworkedEntities() {
        Engine engine = new Engine();
        Entity e = new Entity();
        e.add(new NetworkIdComponent(1));
        e.add(new AuthorityComponent());
        engine.addEntity(e);

        var family = Family.all(NetworkIdComponent.class, AuthorityComponent.class).get();
        assertEquals(1, engine.getEntitiesFor(family).size());
    }
}
