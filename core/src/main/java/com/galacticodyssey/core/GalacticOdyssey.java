package com.galacticodyssey.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.galacticodyssey.persistence.SaveBackend;
import com.galacticodyssey.persistence.LocalFileSaveBackend;
import com.galacticodyssey.ui.MainMenuScreen;
import com.galacticodyssey.ui.UiFactory;
import java.io.File;

public class GalacticOdyssey extends Game {

    private Skin skin;
    private AudioManager audioManager;
    private GamePreferences preferences;
    private SaveBackend saveBackend;

    @Override
    public void create() {
        Gdx.app.log("GalacticOdyssey", "Galactic Odyssey starting...");
        Bullet.init();
        skin = UiFactory.createSkin();
        preferences = new GamePreferences();
        preferences.load();
        audioManager = new AudioManager(preferences);
        File savesDir = new File(System.getProperty("user.home"), ".galacticodyssey/saves");
        saveBackend = new LocalFileSaveBackend(savesDir);
        setScreen(new MainMenuScreen(this));
    }

    public Skin getSkin() {
        return skin;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    public GamePreferences getPreferences() {
        return preferences;
    }

    public SaveBackend getSaveBackend() {
        return saveBackend;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (skin != null) skin.dispose();
        if (audioManager != null) audioManager.dispose();
        Gdx.app.log("GalacticOdyssey", "Shutting down.");
    }
}
