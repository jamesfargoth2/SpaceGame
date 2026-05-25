package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.ui.SettingsScreen;

public class MainMenuScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final GalacticOdyssey game;
    private final Skin skin;
    private final AudioManager audioManager;
    private final Stage stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera backgroundCamera;

    public MainMenuScreen(GalacticOdyssey game) {
        this.game = game;
        this.skin = game.getSkin();
        this.audioManager = game.getAudioManager();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        this.starfield = new StarfieldBackground(screenW, screenH);

        buildUi();
    }

    private void buildUi() {

        Table root = new Table();
        root.setFillParent(true);
        root.center();

        Label title = new Label("GALACTIC ODYSSEY", skin, "title");
        root.add(title).padBottom(50).row();

        addMenuButton(root, "New Game", skin, false,
            () -> Gdx.app.log("Menu", "New Game pressed"));
        addMenuButton(root, "Continue", skin, true,
            () -> Gdx.app.log("Menu", "Continue pressed"));
        addMenuButton(root, "Multiplayer", skin, false,
            () -> Gdx.app.log("Menu", "Multiplayer pressed"));
        addMenuButton(root, "Settings", skin, false,
            () -> game.setScreen(new SettingsScreen(game)));
        addMenuButton(root, "Encyclopedia", skin, false,
            () -> Gdx.app.log("Menu", "Encyclopedia pressed"));
        addMenuButton(root, "Credits", skin, false,
            () -> Gdx.app.log("Menu", "Credits pressed"));
        addMenuButton(root, "Exit", skin, false,
            () -> Gdx.app.exit());

        stage.addActor(root);

        Table versionTable = new Table();
        versionTable.setFillParent(true);
        versionTable.bottom().right();
        versionTable.add(new Label("v0.1.0", skin, "version")).pad(10);
        stage.addActor(versionTable);
    }

    private void addMenuButton(Table table, String text, Skin skin, boolean disabled, Runnable action) {
        TextButton button = new TextButton(text, skin);
        button.setDisabled(disabled);
        button.setTransform(true);

        AudioManager audio = audioManager;

        button.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1 && !button.isDisabled()) {
                    button.setOrigin(Align.center);
                    button.addAction(Actions.scaleTo(1.02f, 1.02f, 0.1f, Interpolation.smooth));
                    audio.playSound("audio/sfx/ui_hover.ogg");
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    button.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (button.isDisabled()) return;
                audio.playSound("audio/sfx/ui_click.ogg");
                action.run();
            }
        });

        table.add(button).width(300).height(50).padBottom(12).row();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        audioManager.playMusic("audio/music/MainMenuBackgroundMusic.wav");
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        starfield.update(delta);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, screenW, screenH);

        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin();
        starfield.render(batch);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        audioManager.stopMusic();
    }

    @Override
    public void dispose() {
        stage.dispose();
        starfield.dispose();
    }
}
