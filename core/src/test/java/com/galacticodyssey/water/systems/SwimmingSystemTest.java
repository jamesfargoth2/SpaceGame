package com.galacticodyssey.water.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.water.SwimState;
import com.galacticodyssey.water.SwimmingStateComponent;
import com.galacticodyssey.water.data.SwimConfigData;
import com.galacticodyssey.water.data.WaterDataRegistry;
import com.galacticodyssey.water.events.PlayerEnteredWaterEvent;
import com.galacticodyssey.water.events.PlayerExitedWaterEvent;
import com.galacticodyssey.water.events.PlayerStartedDivingEvent;
import com.galacticodyssey.water.events.PlayerSurfacedEvent;
import com.galacticodyssey.water.events.BreathDepletedEvent;
import com.galacticodyssey.water.events.PlayerDrowningEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwimmingSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private SwimmingSystem swimmingSystem;
    private Entity player;
    private SwimmingStateComponent swimState;
    private PlayerInputComponent input;
    private MovementStateComponent movement;
    private TransformComponent transform;

    private final List<PlayerEnteredWaterEvent> enteredEvents = new ArrayList<>();
    private final List<PlayerExitedWaterEvent> exitedEvents = new ArrayList<>();
    private final List<PlayerStartedDivingEvent> diveEvents = new ArrayList<>();
    private final List<PlayerSurfacedEvent> surfacedEvents = new ArrayList<>();
    private final List<BreathDepletedEvent> breathEvents = new ArrayList<>();
    private final List<PlayerDrowningEvent> drowningEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        WaterDataRegistry registry = new WaterDataRegistry();
        registry.setSwimConfig(new SwimConfigData());

        swimmingSystem = new SwimmingSystem(15, eventBus, registry);
        engine = new Engine();
        engine.addSystem(swimmingSystem);

        player = new Entity();
        swimState = new SwimmingStateComponent();
        input = new PlayerInputComponent();
        movement = new MovementStateComponent();
        transform = new TransformComponent();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();

        player.add(swimState);
        player.add(input);
        player.add(movement);
        player.add(transform);
        player.add(physics);
        engine.addEntity(player);

        eventBus.subscribe(PlayerEnteredWaterEvent.class, enteredEvents::add);
        eventBus.subscribe(PlayerExitedWaterEvent.class, exitedEvents::add);
        eventBus.subscribe(PlayerStartedDivingEvent.class, diveEvents::add);
        eventBus.subscribe(PlayerSurfacedEvent.class, surfacedEvents::add);
        eventBus.subscribe(BreathDepletedEvent.class, breathEvents::add);
        eventBus.subscribe(PlayerDrowningEvent.class, drowningEvents::add);
    }

    @Test
    void startsInDryState() {
        assertEquals(SwimState.DRY, swimState.swimState);
    }

    @Test
    void transitionsDryToWadingWhenFeetInWater() {
        transform.position.set(0, 0.5f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.7f);
        movement.isGrounded = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.WADING, swimState.swimState);
        assertEquals(1, enteredEvents.size());
    }

    @Test
    void transitionsWadingToDryWhenWaterRecedes() {
        swimState.swimState = SwimState.WADING;
        transform.position.set(0, 2.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(1.5f);
        movement.isGrounded = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.DRY, swimState.swimState);
        assertEquals(1, exitedEvents.size());
    }

    @Test
    void transitionsWadingToSurfaceWhenDeep() {
        swimState.swimState = SwimState.WADING;
        transform.position.set(0, 0.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(1.5f);
        movement.isGrounded = false;

        engine.update(1f / 60f);

        assertEquals(SwimState.SURFACE, swimState.swimState);
    }

    @Test
    void transitionsSurfaceToDivingOnCrouchInput() {
        swimState.swimState = SwimState.SURFACE;
        swimState.breath = 30f;
        transform.position.set(0, 0.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.3f);
        input.crouch = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.DIVING, swimState.swimState);
        assertEquals(1, diveEvents.size());
    }

    @Test
    void transitionsDivingToSubmergedAtDepthThreshold() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 30f;
        transform.position.set(0, -5.5f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);

        engine.update(1f / 60f);

        assertEquals(SwimState.SUBMERGED, swimState.swimState);
    }

    @Test
    void transitionsSubmergedToDivingOnAscentAboveThreshold() {
        swimState.swimState = SwimState.SUBMERGED;
        swimState.breath = 30f;
        transform.position.set(0, -3.5f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);

        engine.update(1f / 60f);

        assertEquals(SwimState.DIVING, swimState.swimState);
    }

    @Test
    void transitionsDivingToSurfaceWhenNoDiveInput() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 30f;
        transform.position.set(0, -0.3f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        input.crouch = false;

        engine.update(1f / 60f);

        assertEquals(SwimState.SURFACE, swimState.swimState);
        assertEquals(1, surfacedEvents.size());
    }

    @Test
    void breathDrainsWhileDiving() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 10f;
        transform.position.set(0, -2.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        input.crouch = true;

        engine.update(1.0f);

        assertTrue(swimState.breath < 10f);
    }

    @Test
    void transitionsToDrowningWhenBreathDepleted() {
        swimState.swimState = SwimState.DIVING;
        swimState.breath = 0.5f;
        transform.position.set(0, -2.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        input.crouch = true;

        engine.update(1.0f);

        assertEquals(SwimState.DROWNING, swimState.swimState);
        assertEquals(1, breathEvents.size());
        assertEquals(1, drowningEvents.size());
    }

    @Test
    void breathRefillsAtSurface() {
        swimState.swimState = SwimState.SURFACE;
        swimState.breath = 10f;
        swimState.maxBreath = 30f;
        transform.position.set(0, 0.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.3f);

        engine.update(1.0f);

        assertTrue(swimState.breath > 10f);
    }

    @Test
    void staysInDryStateWhenAboveWater() {
        transform.position.set(0, 5.0f, 0);
        swimmingSystem.setTestWaterSurfaceHeight(0.0f);
        movement.isGrounded = true;

        engine.update(1f / 60f);

        assertEquals(SwimState.DRY, swimState.swimState);
        assertTrue(enteredEvents.isEmpty());
    }
}
