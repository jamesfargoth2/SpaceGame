package com.galacticodyssey.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.galacticodyssey.ui.UiFactory;

public class GalacticOdyssey extends Game {

    private Skin skin;
    private AudioManager audioManager;

    @Override
    public void create() {
        Gdx.app.log("GalacticOdyssey", "Galactic Odyssey starting...");
        skin = UiFactory.createSkin();
        audioManager = new AudioManager();
    }

    public Skin getSkin() {
        return skin;
    }

    public AudioManager getAudioManager() {
        return audioManager;
    }

    @Override
    public void dispose() {
        Screen currentScreen = getScreen();
        super.dispose();
        if (currentScreen != null) {
            currentScreen.dispose();
        }
        skin.dispose();
        audioManager.dispose();
        Gdx.app.log("GalacticOdyssey", "Shutting down.");
    }
}
