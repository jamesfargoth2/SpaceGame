package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.CantinaSeatComponent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.RecruitableComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.CrewMemberHiredEvent;

/**
 * Handles the hire action for the recruitment screen.
 *
 * <p>Removes the {@link RecruitableComponent} and {@link CantinaSeatComponent} from the NPC,
 * attaches a {@link CrewMemberComponent} with the negotiated (or max asking) wage, and publishes
 * a {@link CrewMemberHiredEvent} via the event bus.
 */
public class RecruitmentScreenSystem {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_MAPPER =
            ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUITABLE_MAPPER =
            ComponentMapper.getFor(RecruitableComponent.class);

    private RecruitmentScreenSystem() {}

    /**
     * Executes the hire transaction for {@code npc}.
     *
     * @param npc      the NPC entity being hired
     * @param eventBus the event bus to publish the hire event on
     */
    public static void executeHire(Entity npc, EventBus eventBus) {
        RecruitableComponent rc = RECRUITABLE_MAPPER.get(npc);
        NpcIdentityComponent identity = IDENTITY_MAPPER.get(npc);

        float wage = (rc != null && rc.negotiatedWage > 0f) ? rc.negotiatedWage : (rc != null ? rc.askingWageMax : 0f);

        CrewRole crewRole = null;
        if (identity != null && identity.role != null) {
            crewRole = identity.role.toCrewRole();
        }

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.wage = wage;
        crew.morale = 75f;
        crew.loyalty = 50f;
        crew.role = crewRole;
        npc.add(crew);

        npc.remove(RecruitableComponent.class);
        npc.remove(CantinaSeatComponent.class);

        if (eventBus != null) {
            eventBus.publish(new CrewMemberHiredEvent(npc, crewRole));
        }
    }
}
