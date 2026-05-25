package com.galacticodyssey.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

public class AudioManager implements Disposable {

    private Music currentMusic;
    private float musicVolume = 0.7f;
    private float sfxVolume = 0.8f;
    private final ObjectMap<String, Sound> soundCache = new ObjectMap<>();

    public void playMusic(String path) {
        stopMusic();
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("AudioManager", "Music file not found: " + path);
            return;
        }
        currentMusic = Gdx.audio.newMusic(file);
        currentMusic.setLooping(true);
        currentMusic.setVolume(musicVolume);
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
        sound.play(sfxVolume);
    }

    public void setMusicVolume(float volume) {
        musicVolume = Math.max(0f, Math.min(1f, volume));
        if (currentMusic != null) {
            currentMusic.setVolume(musicVolume);
        }
    }

    public void setSfxVolume(float volume) {
        sfxVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public float getSfxVolume() {
        return sfxVolume;
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
