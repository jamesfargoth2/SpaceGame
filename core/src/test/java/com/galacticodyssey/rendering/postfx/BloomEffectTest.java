package com.galacticodyssey.rendering.postfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloomEffectTest {

    @Test
    void mipLevelCountFor1080p() {
        int levels = BloomEffect.calculateMipLevels(1920, 1080);
        assertEquals(6, levels);
    }

    @Test
    void mipLevelCountFor720p() {
        int levels = BloomEffect.calculateMipLevels(1280, 720);
        assertEquals(5, levels);
    }

    @Test
    void mipLevelCountFor4K() {
        int levels = BloomEffect.calculateMipLevels(3840, 2160);
        assertEquals(7, levels);
    }

    @Test
    void mipLevelCountMinimumIsTwo() {
        int levels = BloomEffect.calculateMipLevels(64, 64);
        assertEquals(2, levels);
    }
}
