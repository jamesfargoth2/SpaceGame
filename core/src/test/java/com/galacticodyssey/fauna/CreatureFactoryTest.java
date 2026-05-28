package com.galacticodyssey.fauna;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.components.CreatureComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureFactoryTest {

    private CreatureGenerator generator;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[{\"socketId\":\"hd\",\"partType\":\"HEAD\"}]} }" +
          "] }");
        reg.validate();
        generator = new CreatureGenerator(reg);
    }

    @Test
    void buildsEntityWithCoreComponents() {
        Engine engine = new Engine();
        CreatureSpec spec = generator.generate("quad", 5L);
        Entity e = new CreatureFactory().create(engine, spec, new Vector3(10, 0, -3));

        assertNotNull(e.getComponent(TransformComponent.class));
        assertEquals(10f, e.getComponent(TransformComponent.class).position.x, 1e-4f);

        CreatureComponent cc = e.getComponent(CreatureComponent.class);
        assertNotNull(cc);
        assertEquals("quad", cc.archetypeId);
        assertSame(spec, cc.spec);

        HealthComponent hp = e.getComponent(HealthComponent.class);
        assertNotNull(hp);
        assertEquals(spec.maxHP, hp.maxHP, 1e-4f);
        assertEquals(spec.maxHP, hp.currentHP, 1e-4f);
        assertTrue(hp.alive);

        assertEquals(1, engine.getEntities().size());
    }
}
