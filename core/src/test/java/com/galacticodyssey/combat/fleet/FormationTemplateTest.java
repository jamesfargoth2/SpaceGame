package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.FormationTemplate;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormationTemplateTest {

    @Test
    void wedgeFormationSlotZeroIsOrigin() {
        FormationTemplate wedge = FormationTemplate.wedge(20);
        Vector3 slot0 = wedge.getSlotOffset(0);
        assertEquals(0f, slot0.x, 0.01f);
        assertEquals(0f, slot0.y, 0.01f);
        assertEquals(0f, slot0.z, 0.01f);
    }

    @Test
    void slotCountMatchesRequested() {
        FormationTemplate line = FormationTemplate.line(10);
        assertEquals(10, line.slotCount());
    }

    @Test
    void lineFormationSpreadsAlongX() {
        FormationTemplate line = FormationTemplate.line(5);
        Vector3 slot0 = line.getSlotOffset(0);
        Vector3 slot4 = line.getSlotOffset(4);
        assertTrue(Math.abs(slot4.x - slot0.x) > 1f);
        assertEquals(slot0.z, slot4.z, 0.01f);
    }

    @Test
    void outOfBoundsSlotWraps() {
        FormationTemplate wedge = FormationTemplate.wedge(5);
        Vector3 slot5 = wedge.getSlotOffset(5);
        assertNotNull(slot5);
    }
}
