package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.persistence.ManifestData;
import com.galacticodyssey.persistence.SaveBackend;
import com.badlogic.gdx.Screen;

public class LoadScreen extends SaveListBaseScreen {

    public enum Origin { MAIN_MENU, PAUSE_MENU }

    private final Origin origin;
    private final GameScreen gameScreen;

    public LoadScreen(GalacticOdyssey game, SaveBackend saveBackend,
                      Screen returnTo, Origin origin) {
        this(game, saveBackend, returnTo, origin, null);
    }

    public LoadScreen(GalacticOdyssey game, SaveBackend saveBackend,
                      Screen returnTo, Origin origin, GameScreen gameScreen) {
        super(game, saveBackend, returnTo);
        this.origin = origin;
        this.gameScreen = gameScreen;
    }

    @Override
    protected String getTitle() {
        return "LOAD GAME";
    }

    @Override
    public void onSlotClicked(ManifestData manifest) {
        audioManager.playSound("audio/sfx/ui_click.ogg");

        if (origin == Origin.PAUSE_MENU) {
            new ConfirmDialog(stage, skin,
                "Load '" + manifest.getDisplayNameOrFallback()
                    + "'? Unsaved progress will be lost.",
                "Load", "Cancel",
                () -> loadSave(manifest),
                () -> {}
            ).show(stage);
        } else {
            loadSave(manifest);
        }
    }

    private void loadSave(ManifestData manifest) {
        Gdx.app.log("LoadScreen", "Loading save: " + manifest.saveName);
        // Dispose old GameScreen if loading from pause
        if (gameScreen != null) {
            gameScreen.dispose();
        }
        // TODO: Use SaveCoordinator to load game state, then switch to GameScreen
        // For now, transition to a fresh GameScreen
        game.setScreen(new GameScreen(game));
    }
}
