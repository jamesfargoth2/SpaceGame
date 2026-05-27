package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.galaxy.GalaxyGenerationPipeline;

/**
 * Async loading screen shown after the player clicks "Create Game" in GameSetupScreen.
 * Spawns a worker thread running GalaxyGenerationPipeline.run(session), displays animated
 * flavour text as each phase completes, then transitions to GameScreen on success or shows
 * an error + Back button on failure.
 */
public class GalaxyLoadingScreen implements Screen {

    private static final float WORLD_WIDTH  = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    /** Total generation phases; matches the number of log lines GalaxyGenerationPipeline emits. */
    private static final int PHASE_COUNT = 5;

    private final GalacticOdyssey game;
    private final GameSession session;
    private final Skin skin;
    private final Stage stage;

    private ProgressBar progressBar;
    private Table logTable;
    private Label errorLabel;
    private TextButton backButton;

    /** How many log entries have already been added to logTable. */
    private int displayedLogCount = 0;

    /** Guard against calling game.setScreen() more than once. */
    private boolean transitioned = false;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public GalaxyLoadingScreen(GalacticOdyssey game, GameSession session) {
        this.game    = game;
        this.session = session;
        this.skin    = game.getSkin();
        this.stage   = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        buildUi();
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.center();

        // 1. Galaxy name title
        Label titleLabel = new Label(session.galaxyName, skin, "title");
        root.add(titleLabel).padBottom(30).row();

        // 2. Progress bar (0–5, step 1, horizontal)
        progressBar = new ProgressBar(0f, PHASE_COUNT, 1f, false, skin);
        progressBar.setValue(0f);
        root.add(progressBar).width(600).padBottom(24).row();

        // 3. Log table — flavour text lines appear here
        logTable = new Table();
        logTable.top().left();
        root.add(logTable).width(700).padBottom(20).row();

        // 4. "Please wait…" label
        Label waitLabel = new Label("Please wait…", skin);
        root.add(waitLabel).padBottom(20).row();

        // 5. Error label (red, invisible by default)
        errorLabel = new Label("", skin);
        errorLabel.setColor(Color.RED);
        errorLabel.setVisible(false);
        root.add(errorLabel).width(700).padBottom(16).row();

        // 6. Back button (invisible by default, goes to GameSetupScreen)
        backButton = new TextButton("Back", skin);
        backButton.setVisible(false);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.getAudioManager().playSound("audio/sfx/ui_click.ogg");
                game.setScreen(new GameSetupScreen(game));
            }
        });
        root.add(backButton).width(180).height(50).row();

        stage.addActor(root);
    }

    // ------------------------------------------------------------------
    // Screen lifecycle
    // ------------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);

        Thread worker = new Thread(() -> {
            try {
                GalaxyGenerationPipeline.run(session);
                session.complete = true;
            } catch (Throwable t) {
                session.error  = t;
                session.failed = true;
                Gdx.app.error("GalaxyLoadingScreen", "Generation failed", t);
            }
        }, "galaxy-gen");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        // Drain any new log lines from the worker thread
        int logSize = session.log.size();
        for (int i = displayedLogCount; i < logSize; i++) {
            String line = session.log.get(i);
            Label lineLabel = new Label(line, skin);
            lineLabel.setColor(1f, 1f, 1f, 0f); // start transparent for fade-in
            lineLabel.addAction(Actions.fadeIn(0.5f));
            logTable.add(lineLabel).left().padBottom(4).row();
            progressBar.setValue(progressBar.getValue() + 1f);
        }
        displayedLogCount = logSize;

        // Transition to GameScreen on success
        if (session.complete && !transitioned) {
            transitioned = true;
            // TODO: Task 8 changes this to new GameScreen(game, session)
            game.setScreen(new GameScreen(game));
        }

        // Show error UI on failure
        if (session.failed && errorLabel != null && !errorLabel.isVisible()) {
            String msg = session.error != null
                ? "Generation failed: " + session.error.getMessage()
                : "Generation failed: unknown error";
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            backButton.setVisible(true);
        }

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
