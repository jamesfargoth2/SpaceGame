package com.galacticodyssey.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.galacticodyssey.audio.AmbientManager;
import com.galacticodyssey.audio.MusicManager;

public class AudioManager implements Disposable {

    private static final float MAX_AUDIBLE_DISTANCE = 5000f;

    private final GamePreferences preferences;
    private final MusicManager musicManager = new MusicManager();
    private final AmbientManager ambientManager = new AmbientManager();
    private final ObjectMap<String, Sound> soundCache = new ObjectMap<>();

    /** Legacy single-track field retained for callers that use playMusic(String) directly. */
    private Music legacyMusic;

    public AudioManager(GamePreferences preferences) {
        this.preferences = preferences;
    }

    // -------------------------------------------------------------------------
    // Music
    // -------------------------------------------------------------------------

    /** Simple looping music, no crossfade. Used by menus and simple screens. */
    public void playMusic(String path) {
        stopMusic();
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("AudioManager", "Music file not found: " + path);
            return;
        }
        legacyMusic = Gdx.audio.newMusic(file);
        legacyMusic.setLooping(true);
        legacyMusic.setVolume(preferences.getEffectiveMusicVolume());
        legacyMusic.play();
    }

    /**
     * Play music with a 2-second crossfade. Higher-priority tracks interrupt lower ones.
     * Priority: Combat = 3, Exploration = 2, Ambient = 1.
     */
    public void playMusicWithCrossfade(String path, int priority) {
        musicManager.play(path, priority, 2f);
    }

    public void stopMusic() {
        musicManager.stop();
        if (legacyMusic != null) {
            legacyMusic.stop();
            legacyMusic.dispose();
            legacyMusic = null;
        }
    }

    // -------------------------------------------------------------------------
    // SFX
    // -------------------------------------------------------------------------

    /** Non-positional sound at full SFX volume (for UI, etc.). */
    public void playSound(String path) {
        Sound sound = getOrLoadSound(path);
        if (sound == null) return;
        sound.play(preferences.getEffectiveSfxVolume());
    }

    /** 3D positional sound with distance attenuation and stereo pan. */
    public void play3D(String path, Vector3 sourcePos, Vector3 listenerPos,
                       Vector3 listenerForward, float volume) {
        float dist = sourcePos.dst(listenerPos);
        if (dist > MAX_AUDIBLE_DISTANCE) return;
        float attenuation = 1f / (1f + dist * dist * 0.001f);
        float pan = computePan(sourcePos, listenerPos, listenerForward);
        Sound sound = getOrLoadSound(path);
        if (sound == null) return;
        sound.play(volume * attenuation * preferences.getEffectiveSfxVolume(), 1f, pan);
    }

    // -------------------------------------------------------------------------
    // Ambient
    // -------------------------------------------------------------------------

    public AmbientManager getAmbientManager() {
        return ambientManager;
    }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    /** Must be called once per frame to drive music crossfades and ambient fades. */
    public void update(float delta) {
        musicManager.update(delta);
        ambientManager.update(delta);
    }

    // -------------------------------------------------------------------------
    // Volume
    // -------------------------------------------------------------------------

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
        ambientManager.applyVolume(preferences.getEffectiveSfxVolume());
    }

    public float getMasterVolume() { return preferences.getMasterVolume(); }
    public float getMusicVolume() { return preferences.getMusicVolume(); }
    public float getSfxVolume() { return preferences.getSfxVolume(); }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void applyMusicVolume() {
        if (legacyMusic != null) {
            legacyMusic.setVolume(preferences.getEffectiveMusicVolume());
        }
        musicManager.applyVolume(preferences.getEffectiveMusicVolume());
    }

    private float computePan(Vector3 sourcePos, Vector3 listenerPos, Vector3 listenerForward) {
        Vector3 toSource = new Vector3(sourcePos).sub(listenerPos).nor();
        Vector3 right = new Vector3(listenerForward).crs(Vector3.Y).nor();
        return MathUtils.clamp(toSource.dot(right), -1f, 1f);
    }

    private Sound getOrLoadSound(String path) {
        Sound sound = soundCache.get(path);
        if (sound == null) {
            if (Gdx.files == null) return null;
            FileHandle file = Gdx.files.internal(path);
            if (!file.exists()) return null;
            sound = Gdx.audio.newSound(file);
            soundCache.put(path, sound);
        }
        return sound;
    }

    @Override
    public void dispose() {
        stopMusic();
        musicManager.dispose();
        ambientManager.dispose();
        for (Sound sound : soundCache.values()) {
            sound.dispose();
        }
        soundCache.clear();
    }
}
