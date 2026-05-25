package com.galacticodyssey.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

public class AudioManager implements Disposable {

    private final GamePreferences preferences;
    private Music currentMusic;
    private final ObjectMap<String, Sound> soundCache = new ObjectMap<>();

    public AudioManager(GamePreferences preferences) {
        this.preferences = preferences;
    }

    public void playMusic(String path) {
        stopMusic();
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("AudioManager", "Music file not found: " + path);
            return;
        }
        currentMusic = Gdx.audio.newMusic(file);
        currentMusic.setLooping(true);
        currentMusic.setVolume(preferences.getEffectiveMusicVolume());
        currentMusic.play();
    }

    public void stopMusic() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }
    }

    public void playSound(String path) {
        Sound sound = soundCache.get(path);
        if (sound == null) {
            FileHandle file = Gdx.files.internal(path);
            if (!file.exists()) {
                return;
            }
            sound = Gdx.audio.newSound(file);
            soundCache.put(path, sound);
        }
        sound.play(preferences.getEffectiveSfxVolume());
    }

    public void setMasterVolume(float volume) {
        preferences.setMasterVolume(volume);
        applyMusicVolume();
    }

    public void setMusicVolume(float volume) {
        preferences.setMusicVolume(volume);
        applyMusicVolume();
    }

    public void setSfxVolume(float volume) {
        preferences.setSfxVolume(volume);
    }

    public float getMasterVolume() {
        return preferences.getMasterVolume();
    }

    public float getMusicVolume() {
        return preferences.getMusicVolume();
    }

    public float getSfxVolume() {
        return preferences.getSfxVolume();
    }

    private void applyMusicVolume() {
        if (currentMusic != null) {
            currentMusic.setVolume(preferences.getEffectiveMusicVolume());
        }
    }

    @Override
    public void dispose() {
        stopMusic();
        for (Sound sound : soundCache.values()) {
            sound.dispose();
        }
        soundCache.clear();
    }
}
