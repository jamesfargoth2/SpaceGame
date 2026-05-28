package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WeaponSystemDrivingGuardTest {
    private Entity playerWithWeapon(PlayerMode mode) {
        Entity p = new Entity();
        p.add(new TransformComponent());
        RangedWeaponComponent w = new RangedWeaponComponent();
        w.firingMode = FiringMode.AUTO; w.fireRate = 5f; w.magSize = 30; w.currentAmmo = 30; w.hitscan = true;
        p.add(w);
        p.add(new WeaponInventoryComponent());
        CombatInputComponent ci = new CombatInputComponent();
        ci.fireHeld = true;
        p.add(ci);
        PlayerStateComponent st = new PlayerStateComponent();
        st.currentMode = mode;
        p.add(st);
        return p;
    }

    @Test
    void doesNotFirePlayerWeaponWhileDriving() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        WeaponSystem sys = new WeaponSystem(bus);
        engine.addSystem(sys);
        engine.addEntity(playerWithWeapon(PlayerMode.DRIVING));

        sys.update(1f / 60f);
        assertEquals(0, shots.get());
    }

    @Test
    void firesPlayerWeaponOnFoot() {
        Engine engine = new Engine();
        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(WeaponFiredEvent.class, e -> shots.incrementAndGet());
        WeaponSystem sys = new WeaponSystem(bus);
        engine.addSystem(sys);
        engine.addEntity(playerWithWeapon(PlayerMode.ON_FOOT_EXTERIOR));

        sys.update(1f / 60f);
        assertEquals(1, shots.get());
    }
}
