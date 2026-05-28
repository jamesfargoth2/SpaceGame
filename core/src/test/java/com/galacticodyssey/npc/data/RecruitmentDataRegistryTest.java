package com.galacticodyssey.npc.data;

import com.galacticodyssey.npc.components.RecruitConditionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentDataRegistryTest {

    private RecruitmentDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RecruitmentDataRegistry();
    }

    @Test
    void registerAndRetrieveCondition() {
        var def = new RecruitConditionDefinition();
        def.id = "no_krethians";
        def.type = RecruitConditionType.SPECIES_AVERSION;
        def.targetId = "krethian";
        def.description = "Won't serve alongside Krethians";
        def.weight = 0.15f;

        registry.registerCondition(def);

        assertEquals(1, registry.getAllConditions().size());
        assertSame(def, registry.getCondition("no_krethians"));
    }

    @Test
    void registerAndRetrieveLayout() {
        var seat = new CantinaSeatDefinition();
        seat.id = "bar_stool_1";
        seat.x = 0.12f;
        seat.y = 0.35f;

        var layout = new CantinaLayoutDefinition();
        layout.backgroundKey = "cantina_nexus";
        layout.capacity = 5;
        layout.seats.add(seat);
        layout.hiringBoardX = 0.30f;
        layout.hiringBoardY = 0.22f;

        registry.registerLayout("nexus_station", layout);

        assertSame(layout, registry.getLayout("nexus_station"));
        assertEquals(5, layout.capacity);
        assertEquals(1, layout.seats.size());
    }

    @Test
    void getLayoutReturnsNullForUnknownStation() {
        assertNull(registry.getLayout("unknown"));
    }
}
