package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes {@link FleetOrderEvent}s to the matching fleet's tactics queue and executes
 * enqueued orders once per {@value #TICK_INTERVAL}-second tick.
 *
 * <p>Orders are collected from the event bus between ticks and applied immediately on the
 * next {@link #update} call before the standard tick logic runs, so single-frame latency is
 * guaranteed even at low tick rates.
 *
 * <p>Priority {@value #PRIORITY} — runs after simulation ({@code 2}) and formation ({@code 3})
 * systems so state changes take effect in the following frame.
 */
public class FleetCommandSystem extends EntitySystem {

    public static final int PRIORITY = 5;
    private static final float TICK_INTERVAL = 1.0f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetTacticsComponent> TACTICS_M =
        ComponentMapper.getFor(FleetTacticsComponent.class);
    private static final ComponentMapper<FleetFormationComponent> FORMATION_M =
        ComponentMapper.getFor(FleetFormationComponent.class);

    private static final Family FLEET_FAMILY = Family.all(
        FleetComponent.class, FleetTacticsComponent.class, FleetFormationComponent.class
    ).get();

    private final EventBus eventBus;
    private final List<FleetOrderEvent> pendingOrders = new ArrayList<>();
    private float accumulator;
    private Engine engine;

    public FleetCommandSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(FleetOrderEvent.class, pendingOrders::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        if (!pendingOrders.isEmpty()) {
            List<FleetOrderEvent> orders = new ArrayList<>(pendingOrders);
            pendingOrders.clear();
            for (FleetOrderEvent evt : orders) {
                processOrder(evt);
            }
        }

        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(e);
            if (!fc.expanded) continue;

            FleetTacticsComponent tc = TACTICS_M.get(e);
            while (!tc.orders.isEmpty()) {
                FleetOrder order = tc.orders.poll();
                executeOrder(e, fc, tc, order);
            }
        }
    }

    private void processOrder(FleetOrderEvent evt) {
        for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(e);
            if (fc.fleetId.equals(evt.fleetId)) {
                FleetTacticsComponent tc = TACTICS_M.get(e);
                tc.orders.add(evt.order);
                break;
            }
        }
    }

    private void executeOrder(Entity fleetEntity, FleetComponent fc,
                              FleetTacticsComponent tc, FleetOrder order) {
        switch (order.type) {
            case RETREAT:
                FleetState oldState = fc.state;
                fc.state = FleetState.RETREATING;
                eventBus.publish(new FleetStateChangedEvent(fc.fleetId, oldState, fc.state));
                break;
            case SET_FORMATION:
                if (order.formationTemplateId != null) {
                    FleetFormationComponent ffc = FORMATION_M.get(fleetEntity);
                    if (ffc != null) {
                        ffc.formationTemplateId = order.formationTemplateId;
                    }
                }
                break;
            case HOLD_POSITION:
                fc.state = FleetState.ENGAGED;
                break;
            case ATTACK_TARGET:
                fc.state = FleetState.ENGAGED;
                break;
            case ADVANCE:
                fc.state = FleetState.INTERCEPT;
                break;
            case REGROUP:
                fc.state = FleetState.REGROUPING;
                break;
            case MOVE_TO:
                break;
            case ESCORT_SHIP:
                break;
            case LAUNCH_FIGHTERS:
                break;
            case RECALL_FIGHTERS:
                break;
        }
    }
}
