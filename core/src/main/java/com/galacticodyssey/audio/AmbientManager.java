package com.galacticodyssey.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** Manages multiple blending ambient loops with smooth volume fades on environment change. */
public class AmbientManager implements Disposable {

    private static final float FADE_SPEED = 0.5f;

    private static class AmbientLayer {
        Music music;
        float targetVolume;
        float currentVolume;
        boolean removing;
    }

    private final Array<AmbientLayer> layers = new Array<>();
    private float effectiveVolume = 1f;

    public void addLayer(String path, float targetVolume) {
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) return;
        AmbientLayer layer = new AmbientLayer();
        layer.music = Gdx.audio.newMusic(file);
        layer.music.setLooping(true);
        layer.music.setVolume(0f);
        layer.music.play();
        layer.currentVolume = 0f;
        layer.targetVolume = targetVolume;
        layers.add(layer);
    }

    /** Fade out and remove all current ambient layers. */
    public void clearLayers() {
        for (AmbientLayer layer : layers) {
            layer.targetVolume = 0f;
            layer.removing = true;
        }
    }

    public void applyVolume(float effectiveVolume) {
        this.effectiveVolume = effectiveVolume;
    }

    public void update(float delta) {
        for (int i = layers.size - 1; i >= 0; i--) {
            AmbientLayer layer = layers.get(i);
            float diff = layer.targetVolume - layer.currentVolume;
            if (Math.abs(diff) < 0.001f) {
                layer.currentVolume = layer.targetVolume;
            } else {
                layer.currentVolume += Math.signum(diff) * FADE_SPEED * delta;
                layer.currentVolume = Math.max(0f, Math.min(1f, layer.currentVolume));
            }
            layer.music.setVolume(layer.currentVolume * effectiveVolume);
            if (layer.removing && layer.currentVolume <= 0f) {
                layer.music.stop();
                layer.music.dispose();
                layers.removeIndex(i);
            }
        }
    }

    @Override
    public void dispose() {
        for (AmbientLayer layer : layers) {
            layer.music.stop();
            layer.music.dispose();
        }
        layers.clear();
    }
}
