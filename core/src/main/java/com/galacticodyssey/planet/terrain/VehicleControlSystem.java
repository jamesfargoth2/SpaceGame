package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;

/** Routes driver input to the controlled vehicle while the player is in DRIVING mode. */
public class VehicleControlSystem extends EntitySystem {

    public static final int PRIORITY = 0;

    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<PlayerInputComponent> INPUT_M =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private static final ComponentMapper<CombatInputComponent> COMBAT_M =
        ComponentMapper.getFor(CombatInputComponent.class);
    private static final ComponentMapper<GroundVehicleComponent> VEHICLE_M =
        ComponentMapper.getFor(GroundVehicleComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> players;
    private final Matrix4 tmpMat = new Matrix4();
    private final Quaternion tmpQuat = new Quaternion();
    private final Vector3 tmpForward = new Vector3();

    public VehicleControlSystem() { super(PRIORITY); }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class,
            PlayerInputComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < players.size(); i++) {
            Entity player = players.get(i);
            PlayerStateComponent state = STATE_M.get(player);
            if (state.currentMode != PlayerMode.DRIVING || state.currentVehicle == null) continue;

            Entity vehicle = state.currentVehicle;
            GroundVehicleComponent gv = VEHICLE_M.get(vehicle);
            if (gv == null) continue;

            PlayerInputComponent in = INPUT_M.get(player);
            gv.throttleInput = MathUtils.clamp(in.moveForward, -1f, 1f);
            gv.steerInput = MathUtils.clamp(in.moveStrafe, -1f, 1f);

            CombatInputComponent vehicleCombat = COMBAT_M.get(vehicle);
            CombatInputComponent playerCombat = COMBAT_M.get(player);
            if (vehicleCombat != null) {
                if (playerCombat != null) {
                    vehicleCombat.fireRequested = playerCombat.fireRequested;
                    vehicleCombat.fireHeld = playerCombat.fireHeld;
                }
                TransformComponent t = TRANSFORM_M.get(vehicle);
                if (t != null) {
                    tmpMat.set(t.position, t.rotation);
                    tmpMat.getRotation(tmpQuat);
                    tmpForward.set(0f, 0f, -1f).mul(tmpQuat).nor();
                    vehicleCombat.aimDirection.set(tmpForward);
                }
            }
        }
    }
}
