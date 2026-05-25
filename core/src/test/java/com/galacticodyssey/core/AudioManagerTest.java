package com.galacticodyssey.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioManagerTest {

    @Test
    void defaultMusicVolumeMatchesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        assertEquals(0.7f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void defaultSfxVolumeMatchesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        assertEquals(0.8f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(1.5f);
        assertEquals(1.0f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(-0.5f);
        assertEquals(0.0f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeAcceptsValidValue() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(0.4f);
        assertEquals(0.4f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setSfxVolume(2.0f);
        assertEquals(1.0f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setSfxVolume(-1.0f);
        assertEquals(0.0f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void masterVolumeDefaultsToOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        assertEquals(1.0f, audio.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsAboveOne() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMasterVolume(1.5f);
        assertEquals(1.0f, audio.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeClampsBelowZero() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMasterVolume(-0.5f);
        assertEquals(0.0f, audio.getMasterVolume(), 0.001f);
    }

    @Test
    void setMasterVolumeUpdatesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMasterVolume(0.5f);
        assertEquals(0.5f, prefs.getMasterVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeUpdatesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setMusicVolume(0.3f);
        assertEquals(0.3f, prefs.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeUpdatesPreferences() {
        GamePreferences prefs = new GamePreferences();
        AudioManager audio = new AudioManager(prefs);
        audio.setSfxVolume(0.6f);
        assertEquals(0.6f, prefs.getSfxVolume(), 0.001f);
    }
}
