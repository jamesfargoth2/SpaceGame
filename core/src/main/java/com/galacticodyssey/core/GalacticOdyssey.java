package com.galacticodyssey.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.galacticodyssey.ui.MainMenuScreen;
import com.galacticodyssey.ui.UiFactory;

public class GalacticOdyssey extends Game {

    private Skin skin;
    private AudioManager audioManager;
    private GamePreferences preferences;

    @Override
    public void create() {
        Gdx.app.log("GalacticOdyssey", "Galactic Odyssey starting...");
        skin = UiFactory.createSkin();
        preferences = new GamePreferences();
        preferences.load();
        audioManager = new AudioManager(preferences);
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

    @Override
    public void dispose() {
        super.dispose();
        if (skin != null) skin.dispose();
        if (audioManager != null) audioManager.dispose();
        Gdx.app.log("GalacticOdyssey", "Shutting down.");
    }
}
