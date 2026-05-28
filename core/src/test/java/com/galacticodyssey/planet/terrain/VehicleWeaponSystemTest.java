package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VehicleWeaponSystemTest {

    private Entity vehicle(FiringMode mode, int ammo) {
        Entity v = new Entity();
        v.add(new TransformComponent());
        v.add(new VehicleTagComponent());
        RangedWeaponComponent w = new RangedWeaponComponent();
        w.firingMode = mode; w.fireRate = 5f; w.magSize = 30; w.currentAmmo = ammo;
        w.hitscan = true; w.damage = 10f; w.range = 100f;
        v.add(w);
        v.add(new CombatInputComponent());
        return v;
    }

    @Test
    void firesAndConsumesAmmoOnInput() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        VehicleWeaponSystem sys = new VehicleWeaponSystem(bus);
        engine.addSystem(sys);
        Entity v = vehicle(FiringMode.AUTO, 30);
        v.getComponent(CombatInputComponent.class).fireHeld = true;
        engine.addEntity(v);

        sys.update(1f / 60f);

        assertEquals(1, shots.get());
        assertEquals(29, v.getComponent(RangedWeaponComponent.class).currentAmmo);
    }

    @Test
    void doesNotFireWhenMagEmpty() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        VehicleWeaponSystem sys = new VehicleWeaponSystem(bus);
        engine.addSystem(sys);
        Entity v = vehicle(FiringMode.AUTO, 0);
        v.getComponent(CombatInputComponent.class).fireHeld = true;
        engine.addEntity(v);

        sys.update(1f / 60f);

        assertEquals(0, shots.get());
    }

    @Test
    void respectsFireRateCooldown() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        VehicleWeaponSystem sys = new VehicleWeaponSystem(bus);
        engine.addSystem(sys);
        Entity v = vehicle(FiringMode.AUTO, 30);
        v.getComponent(CombatInputComponent.class).fireHeld = true;
        engine.addEntity(v);

        sys.update(1f / 60f); // fires
        sys.update(1f / 60f); // still on cooldown
        assertEquals(1, shots.get());
    }
}
