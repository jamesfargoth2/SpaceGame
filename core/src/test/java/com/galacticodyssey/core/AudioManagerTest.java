package com.galacticodyssey.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioManagerTest {

    @Test
    void defaultMusicVolumeIs07() {
        AudioManager audio = new AudioManager();
        assertEquals(0.7f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void defaultSfxVolumeIs08() {
        AudioManager audio = new AudioManager();
        assertEquals(0.8f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsAboveOne() {
        AudioManager audio = new AudioManager();
        audio.setMusicVolume(1.5f);
        assertEquals(1.0f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeClampsBelowZero() {
        AudioManager audio = new AudioManager();
        audio.setMusicVolume(-0.5f);
        assertEquals(0.0f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setMusicVolumeAcceptsValidValue() {
        AudioManager audio = new AudioManager();
        audio.setMusicVolume(0.4f);
        assertEquals(0.4f, audio.getMusicVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsAboveOne() {
        AudioManager audio = new AudioManager();
        audio.setSfxVolume(2.0f);
        assertEquals(1.0f, audio.getSfxVolume(), 0.001f);
    }

    @Test
    void setSfxVolumeClampsBelowZero() {
        AudioManager audio = new AudioManager();
        audio.setSfxVolume(-1.0f);
        assertEquals(0.0f, audio.getSfxVolume(), 0.001f);
    }
}
