package com.galacticodyssey.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

public class GamePreferences {

    public enum DisplayMode { WINDOWED, FULLSCREEN, BORDERLESS }

    private static final String PREFS_NAME = "GalacticOdyssey";

    private DisplayMode displayMode = DisplayMode.WINDOWED;
    private int resolutionWidth = 1280;
    private int resolutionHeight = 720;
    private boolean vsync = true;
    private float masterVolume = 1.0f;
    private float musicVolume = 0.7f;
    private float sfxVolume = 0.8f;

    public DisplayMode getDisplayMode() { return displayMode; }
    public void setDisplayMode(DisplayMode displayMode) { this.displayMode = displayMode; }

    public int getResolutionWidth() { return resolutionWidth; }
    public void setResolutionWidth(int width) { this.resolutionWidth = width; }

    public int getResolutionHeight() { return resolutionHeight; }
    public void setResolutionHeight(int height) { this.resolutionHeight = height; }

    public boolean isVsync() { return vsync; }
    public void setVsync(boolean vsync) { this.vsync = vsync; }

    public float getMasterVolume() { return masterVolume; }

    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getMusicVolume() { return musicVolume; }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getSfxVolume() { return sfxVolume; }

    public void setSfxVolume(float volume) {
        this.sfxVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getEffectiveMusicVolume() {
        return masterVolume * musicVolume;
    }

    public float getEffectiveSfxVolume() {
        return masterVolume * sfxVolume;
    }

    public void load() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        try {
            displayMode = DisplayMode.valueOf(prefs.getString("displayMode", "WINDOWED"));
        } catch (IllegalArgumentException e) {
            displayMode = DisplayMode.WINDOWED;
        }
        resolutionWidth = prefs.getInteger("resolutionWidth", 1280);
        resolutionHeight = prefs.getInteger("resolutionHeight", 720);
        vsync = prefs.getBoolean("vsync", true);
        masterVolume = Math.max(0f, Math.min(1f, prefs.getFloat("masterVolume", 1.0f)));
        musicVolume = Math.max(0f, Math.min(1f, prefs.getFloat("musicVolume", 0.7f)));
        sfxVolume = Math.max(0f, Math.min(1f, prefs.getFloat("sfxVolume", 0.8f)));
    }

    public void save() {
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putString("displayMode", displayMode.name());
        prefs.putInteger("resolutionWidth", resolutionWidth);
        prefs.putInteger("resolutionHeight", resolutionHeight);
        prefs.putBoolean("vsync", vsync);
        prefs.putFloat("masterVolume", masterVolume);
        prefs.putFloat("musicVolume", musicVolume);
        prefs.putFloat("sfxVolume", sfxVolume);
        prefs.flush();
    }
}
