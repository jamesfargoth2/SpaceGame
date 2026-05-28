package com.galacticodyssey.combat.fleet.data;

import com.badlogic.ashley.core.Entity;

public final class FleetOrder {
    public final FleetOrderType type;
    public final Entity targetEntity;
    public final float targetX, targetY, targetZ;
    public final String formationTemplateId;
    public final int[] targetSquadrons;

    public FleetOrder(FleetOrderType type, Entity targetEntity,
                      float targetX, float targetY, float targetZ,
                      String formationTemplateId, int[] targetSquadrons) {
        this.type = type;
        this.targetEntity = targetEntity;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.formationTemplateId = formationTemplateId;
        this.targetSquadrons = targetSquadrons;
    }

    public static FleetOrder attackTarget(Entity target, int[] squadrons) {
        return new FleetOrder(FleetOrderType.ATTACK_TARGET, target, 0, 0, 0, null, squadrons);
    }

    public static FleetOrder moveTo(float x, float y, float z, int[] squadrons) {
        return new FleetOrder(FleetOrderType.MOVE_TO, null, x, y, z, null, squadrons);
    }

    public static FleetOrder holdPosition(int[] squadrons) {
        return new FleetOrder(FleetOrderType.HOLD_POSITION, null, 0, 0, 0, null, squadrons);
    }

    public static FleetOrder retreat() {
        return new FleetOrder(FleetOrderType.RETREAT, null, 0, 0, 0, null, null);
    }

    public static FleetOrder setFormation(String templateId) {
        return new FleetOrder(FleetOrderType.SET_FORMATION, null, 0, 0, 0, templateId, null);
    }

    public static FleetOrder launchFighters() {
        return new FleetOrder(FleetOrderType.LAUNCH_FIGHTERS, null, 0, 0, 0, null, null);
    }

    public static FleetOrder advance(int[] squadrons) {
        return new FleetOrder(FleetOrderType.ADVANCE, null, 0, 0, 0, null, squadrons);
    }

    public static FleetOrder regroup() {
        return new FleetOrder(FleetOrderType.REGROUP, null, 0, 0, 0, null, null);
    }

    public static FleetOrder recallFighters() {
        return new FleetOrder(FleetOrderType.RECALL_FIGHTERS, null, 0, 0, 0, null, null);
    }

    public static FleetOrder escortShip(Entity target, int[] squadrons) {
        return new FleetOrder(FleetOrderType.ESCORT_SHIP, target, 0, 0, 0, null, squadrons);
    }
}
