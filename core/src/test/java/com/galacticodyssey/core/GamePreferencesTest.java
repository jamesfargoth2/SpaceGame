package com.galacticodyssey.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamePreferencesTest {

    @Test
    void defaultDisplayModeIsWindowed() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(GamePreferences.DisplayMode.WINDOWED, prefs.getDisplayMode());
    }

    @Test
    void defaultResolutionIs1280x720() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(1280, prefs.getResolutionWidth());
        assertEquals(720, prefs.getResolutionHeight());
    }

    @Test
    void defaultVsyncIsTrue() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(true, prefs.isVsync());
    }

    @Test
    void defaultMasterVolumeIs1() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(1.0f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void defaultMusicVolumeIs07() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(0.7f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void defaultSfxVolumeIs08() {
        GamePreferences prefs = new GamePreferences();
        assertEquals(0.8f, prefs.getSfxVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(1.5f);
        assertEquals(1.0f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(-0.5f);
        assertEquals(0.0f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMusicVolume(2.0f);
        assertEquals(1.0f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMusicVolume(-1.0f);
        assertEquals(0.0f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        prefs.setSfxVolume(3.0f);
        assertEquals(1.0f, prefs.getSfxVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setSfxVolume(-0.1f);
        assertEquals(0.0f, prefs.getSfxVolume(), 0.001f);
    }

    @Test
    void effectiveMusicVolumeMultipliesMasterAndMusic() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(0.5f);
        prefs.setMusicVolume(0.6f);
        assertEquals(0.3f, prefs.getEffectiveMusicVolume(), 0.001f);
    }

    @Test
    void effectiveSfxVolumeMultipliesMasterAndSfx() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(0.5f);
        prefs.setSfxVolume(0.4f);
        assertEquals(0.2f, prefs.getEffectiveSfxVolume(), 0.001f);
    }

    @Test
    void effectiveVolumeIsZeroWhenMasterIsZero() {
        GamePreferences prefs = new GamePreferences();
        prefs.setMasterVolume(0.0f);
        assertEquals(0.0f, prefs.getEffectiveMusicVolume(), 0.001f);
        assertEquals(0.0f, prefs.getEffectiveSfxVolume(), 0.001f);
    }
}
