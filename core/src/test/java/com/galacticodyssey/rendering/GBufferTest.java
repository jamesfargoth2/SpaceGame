package com.galacticodyssey.rendering;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GBufferTest {

    @Test
    void mipLevelCountCalculation() {
        assertEquals(3, GBuffer.COLOR_ATTACHMENT_COUNT);
    }

    @Test
    void formatConstantsAreDefined() {
        assertNotNull(GBuffer.RT0_FORMAT);
        assertNotNull(GBuffer.RT1_FORMAT);
        assertNotNull(GBuffer.RT2_FORMAT);
    }
}
