package com.galacticodyssey.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

/** Manages a single active music track with crossfade transitions and priority tiers.
 *  Priority: Combat (3) > Exploration (2) > Ambient (1). */
public class MusicManager implements Disposable {

    private static final float DEFAULT_CROSSFADE_DURATION = 2f;

    private Music currentMusic;
    private Music nextMusic;
    private float crossfadeTimer;
    private float crossfadeDuration;
    private int currentPriority = -1;
    private float effectiveVolume = 0.7f;

    /** Request a music track. Ignored if a higher-priority track is already playing. */
    public void play(String path, int priority, float crossfadeDuration) {
        if (priority < currentPriority && currentMusic != null && currentMusic.isPlaying()) {
            return;
        }
        stopNext();
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            Gdx.app.log("MusicManager", "File not found: " + path);
            return;
        }
        nextMusic = Gdx.audio.newMusic(file);
        nextMusic.setLooping(true);
        nextMusic.setVolume(0f);
        nextMusic.play();
        this.crossfadeDuration = crossfadeDuration > 0 ? crossfadeDuration : DEFAULT_CROSSFADE_DURATION;
        this.crossfadeTimer = 0f;
        this.currentPriority = priority;
    }

    public void update(float delta) {
        if (nextMusic == null) return;
        crossfadeTimer += delta;
        float t = Math.min(crossfadeTimer / crossfadeDuration, 1f);
        if (currentMusic != null) {
            currentMusic.setVolume(effectiveVolume * (1f - t));
        }
        nextMusic.setVolume(effectiveVolume * t);
        if (t >= 1f) {
            disposeCurrent();
            currentMusic = nextMusic;
            nextMusic = null;
        }
    }

    public void applyVolume(float effectiveVolume) {
        this.effectiveVolume = effectiveVolume;
        if (currentMusic != null && nextMusic == null) {
            currentMusic.setVolume(effectiveVolume);
        }
    }

    public void stop() {
        stopNext();
        disposeCurrent();
        currentPriority = -1;
    }

    @Override
    public void dispose() {
        stop();
    }

    private void disposeCurrent() {
        if (currentMusic != null) {
            currentMusic.stop();
            currentMusic.dispose();
            currentMusic = null;
        }
    }

    private void stopNext() {
        if (nextMusic != null) {
            nextMusic.stop();
            nextMusic.dispose();
            nextMusic = null;
        }
    }
}
