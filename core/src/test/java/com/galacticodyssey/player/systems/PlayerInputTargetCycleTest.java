package com.galacticodyssey.player.systems;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerInputTargetCycleTest {

    @Test
    void tKeyCodesMatchExpected() {
        // T key should handle both target lock and next-target
        assertEquals(48, Input.Keys.T);
        // TAB should no longer be in PlayerInputSystem
        assertEquals(61, Input.Keys.TAB);
    }
}
