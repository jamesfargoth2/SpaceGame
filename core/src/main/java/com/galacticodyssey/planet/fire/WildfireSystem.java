package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.fire.events.IgniteAtEvent;
import com.galacticodyssey.planet.fire.events.WildfireCellIgnitedEvent;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;

/**
 * Propagates fire across a {@link FuelGridComponent}. Burning cells consume fuel, emit heat,
 * and deterministically raise neighbours' {@code ignitionProgress} weighted by flammability
 * (fuel presence), dryness (1 - moisture), wind alignment, and local oxygen. A cell ignites
 * when its progress reaches 1.0. Spread/burn stop when oxygen is below the combustion minimum.
 *
 * <p>Priority 28 -- runs before {@link com.galacticodyssey.planet.thermal.ObjectTemperatureSystem} (31).</p>
 */
public class WildfireSystem extends EntitySystem {

    public static final int PRIORITY = 28;
    public static final float BURN_RATE = 4000f;     // W per burning cell
    public static final float SPREAD_RATE = 0.6f;    // base progress/sec from one burning neighbour
    public static final float O2_MIN = 0.10f;

    private static final ComponentMapper<FuelGridComponent> GRID_M =
            ComponentMapper.getFor(FuelGridComponent.class);

    private final EventBus eventBus;
    private final ThermalEnvironment environment;
    private ImmutableArray<Entity> gridEntities;

    private final Vector3 scratch = new Vector3();
    private final Vector3 wind = new Vector3();

    // Pending ignition requests (worldX, worldZ, strength) applied at the start of update.
    private final com.badlogic.gdx.utils.Array<IgniteAtEvent> pending =
            new com.badlogic.gdx.utils.Array<>();

    public WildfireSystem(EventBus eventBus, ThermalEnvironment environment) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.environment = environment;
        eventBus.subscribe(IgniteAtEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        gridEntities = engine.getEntitiesFor(Family.all(FuelGridComponent.class).get());
    }

    @Override
    public void update(float dt) {
        if (gridEntities.size() == 0) { pending.clear(); return; }
        FuelGridComponent grid = GRID_M.get(gridEntities.get(0));

        applyPendingIgnitions(grid);
        propagate(grid, dt);
    }

    private void applyPendingIgnitions(FuelGridComponent grid) {
        for (int i = 0; i < pending.size; i++) {
            IgniteAtEvent ev = pending.get(i);
            int cx = grid.cellX(ev.worldX);
            int cy = grid.cellY(ev.worldZ);
            if (!grid.inBounds(cx, cy)) continue;
            int idx = grid.index(cx, cy);
            if (grid.state[idx] == FuelGridComponent.UNBURNT && grid.fuelLoad[idx] > 0f) {
                grid.ignitionProgress[idx] += ev.strength;
                maybeIgnite(grid, cx, cy, idx);
            }
        }
        pending.clear();
    }

    private void propagate(FuelGridComponent grid, float dt) {
        boolean o2ok = environmentO2Ok(grid);

        // Pass 1: burn fuel in BURNING cells; spread progress to neighbours.
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                int idx = grid.index(x, y);
                if (grid.state[idx] != FuelGridComponent.BURNING) continue;

                if (!o2ok) { grid.state[idx] = FuelGridComponent.BURNT; continue; }

                grid.burnTimer[idx] += dt;
                grid.fuelLoad[idx] -= BURN_RATE * dt;
                if (grid.fuelLoad[idx] <= 0f) {
                    grid.fuelLoad[idx] = 0f;
                    grid.state[idx] = FuelGridComponent.BURNT;
                    continue;
                }
                spreadToNeighbours(grid, x, y, dt);
            }
        }

        // Pass 2: promote cells whose progress reached threshold.
        if (o2ok) {
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    int idx = grid.index(x, y);
                    if (grid.state[idx] == FuelGridComponent.UNBURNT) {
                        maybeIgnite(grid, x, y, idx);
                    }
                }
            }
        }
    }

    private void spreadToNeighbours(FuelGridComponent grid, int x, int y, float dt) {
        environment.wind(scratch.set(grid.cellCenterX(x), 0f, grid.cellCenterZ(y)), wind);
        addProgress(grid, x + 1, y, +1f, 0f, dt);
        addProgress(grid, x - 1, y, -1f, 0f, dt);
        addProgress(grid, x, y + 1, 0f, +1f, dt);
        addProgress(grid, x, y - 1, 0f, -1f, dt);
    }

    private void addProgress(FuelGridComponent grid, int nx, int ny,
                             float dirX, float dirZ, float dt) {
        if (!grid.inBounds(nx, ny)) return;
        int nidx = grid.index(nx, ny);
        if (grid.state[nidx] != FuelGridComponent.UNBURNT || grid.fuelLoad[nidx] <= 0f) return;

        float dryness = 1f - clamp01(grid.moisture[nidx]);
        // Wind alignment in [0.5 .. 1.5]: downwind cells catch faster, no wind = neutral 1.0.
        float windMag = wind.len();
        float windAlign = 1f;
        if (windMag > 1e-4f) {
            float dot = (dirX * wind.x + dirZ * wind.z) / windMag;
            windAlign = 1f + 0.5f * dot;
        }
        grid.ignitionProgress[nidx] += SPREAD_RATE * dryness * windAlign * dt;
    }

    private void maybeIgnite(FuelGridComponent grid, int x, int y, int idx) {
        if (grid.ignitionProgress[idx] >= 1f && grid.fuelLoad[idx] > 0f) {
            grid.state[idx] = FuelGridComponent.BURNING;
            eventBus.publish(new WildfireCellIgnitedEvent(
                    x, y, grid.cellCenterX(x), grid.cellCenterZ(y)));
        }
    }

    private boolean environmentO2Ok(FuelGridComponent grid) {
        return environment.oxygenFraction(
                scratch.set(grid.cellCenterX(grid.width / 2), 0f, grid.cellCenterZ(grid.height / 2)))
                >= O2_MIN;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
