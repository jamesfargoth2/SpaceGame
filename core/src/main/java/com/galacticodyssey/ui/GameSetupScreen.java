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
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.galaxy.GalaxySize;
import com.galacticodyssey.galaxy.GalaxyType;
import com.galacticodyssey.galaxy.StartingRegion;

import java.util.Random;

/**
 * "New Game" configuration form. Collects galaxy settings from the player,
 * builds a {@link GameSession}, and transitions to {@code GalaxyLoadingScreen}.
 */
public class GameSetupScreen implements Screen {

    private static final float WORLD_WIDTH  = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final String[] GALAXY_SUFFIXES =
        { "Expanse", "Galaxy", "Reaches", "Cluster", "Nebula", "Rift", "Arm", "Void" };

    // ---- UI references ----
    private TextField galaxyNameField;
    private TextField seedField;
    private SelectBox<String> galaxyTypeBox;
    private int selectedSizeIndex   = 1; // default: MEDIUM
    private int selectedRegionIndex = 1; // default: INNER_RIM
    private TextButton[] sizeButtons;
    private TextButton[] regionButtons;

    // ---- Infrastructure ----
    private final GalacticOdyssey      game;
    private final Skin                 skin;
    private final AudioManager         audioManager;
    private final Stage                stage;
    private final StarfieldBackground  starfield;
    private final OrthographicCamera   backgroundCamera;

    public GameSetupScreen(GalacticOdyssey game) {
        this.game            = game;
        this.skin            = game.getSkin();
        this.audioManager    = game.getAudioManager();
        this.stage           = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        this.starfield = new StarfieldBackground(screenW, screenH);

        buildUi();
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.center();

        Label title = new Label("NEW GAME", skin, "title");
        root.add(title).colspan(2).padBottom(40).row();

        // --- Galaxy Name ---
        long initialSeed = System.nanoTime();
        String initialName = generateGalaxyName(initialSeed);

        root.add(new Label("Galaxy Name:", skin)).right().padRight(12).padBottom(10);
        galaxyNameField = new TextField(initialName, skin);
        root.add(galaxyNameField).width(400).height(40).padBottom(10).row();

        // --- Seed ---
        root.add(new Label("Seed:", skin)).right().padRight(12).padBottom(10);
        seedField = new TextField(Long.toString(initialSeed), skin);
        root.add(seedField).width(400).height(40).padBottom(10).row();

        // --- Galaxy Type ---
        root.add(new Label("Galaxy Type:", skin)).right().padRight(12).padBottom(10);
        galaxyTypeBox = new SelectBox<>(skin);
        Array<String> typeItems = new Array<>();
        typeItems.add("Spiral");
        typeItems.add("Barred Spiral");
        typeItems.add("Elliptical");
        typeItems.add("Irregular");
        galaxyTypeBox.setItems(typeItems);
        root.add(galaxyTypeBox).width(400).height(40).padBottom(10).row();

        // --- Galaxy Size segmented control ---
        root.add(new Label("Galaxy Size:", skin)).right().padRight(12).padBottom(10);
        root.add(buildSizeControl()).padBottom(10).row();

        // --- Starting Region segmented control ---
        root.add(new Label("Starting Region:", skin)).right().padRight(12).padBottom(24);
        root.add(buildRegionControl()).padBottom(24).row();

        // --- Action buttons ---
        Table buttonRow = new Table();
        TextButton backButton   = buildActionButton("Back");
        TextButton createButton = buildActionButton("Create Game");

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                game.setScreen(new SinglePlayerMenuScreen(game));
            }
        });

        createButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                GameSession session = buildSession();
                game.setScreen(new GalaxyLoadingScreen(game, session));
            }
        });

        buttonRow.add(backButton).width(180).height(50).padRight(20);
        buttonRow.add(createButton).width(180).height(50);
        root.add(buttonRow).colspan(2).padTop(10).row();

        stage.addActor(root);
    }

    /** Builds the Galaxy Size segmented control (Small / Medium / Large). */
    private Table buildSizeControl() {
        sizeButtons = new TextButton[3];
        String[] labels = { "Small", "Medium", "Large" };
        Table row = new Table();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextButton btn = new TextButton(labels[i], skin);
            btn.setTransform(true);
            if (i == selectedSizeIndex) {
                btn.getLabel().setColor(Color.YELLOW);
            }
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    audioManager.playSound("audio/sfx/ui_click.ogg");
                    selectedSizeIndex = index;
                    refreshSegmentColors(sizeButtons, selectedSizeIndex);
                }
            });
            sizeButtons[i] = btn;
            row.add(btn).width(120).height(40).padRight(i < labels.length - 1 ? 8 : 0);
        }
        return row;
    }

    /** Builds the Starting Region segmented control (Core / Inner Rim / Frontier). */
    private Table buildRegionControl() {
        regionButtons = new TextButton[3];
        String[] labels = { "Core", "Inner Rim", "Frontier" };
        Table row = new Table();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextButton btn = new TextButton(labels[i], skin);
            btn.setTransform(true);
            if (i == selectedRegionIndex) {
                btn.getLabel().setColor(Color.YELLOW);
            }
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    audioManager.playSound("audio/sfx/ui_click.ogg");
                    selectedRegionIndex = index;
                    refreshSegmentColors(regionButtons, selectedRegionIndex);
                }
            });
            regionButtons[i] = btn;
            row.add(btn).width(120).height(40).padRight(i < labels.length - 1 ? 8 : 0);
        }
        return row;
    }

    /** Highlights the selected segment button and resets others to default. */
    private static void refreshSegmentColors(TextButton[] buttons, int selectedIndex) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].getLabel().setColor(i == selectedIndex ? Color.YELLOW : Color.WHITE);
        }
    }

    /** Creates a standard action button with hover animation. */
    private TextButton buildActionButton(String text) {
        TextButton button = new TextButton(text, skin);
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
        });
        return button;
    }

    // ------------------------------------------------------------------
    // Session construction
    // ------------------------------------------------------------------

    private GameSession buildSession() {
        long seed = parseSeed(seedField.getText());
        String name = galaxyNameField.getText().trim().isEmpty()
            ? generateGalaxyName(seed) : galaxyNameField.getText().trim();
        GalaxyType type     = GalaxyType.values()[galaxyTypeBox.getSelectedIndex()];
        GalaxySize size     = GalaxySize.values()[selectedSizeIndex];
        StartingRegion region = StartingRegion.values()[selectedRegionIndex];
        return new GameSession(seed, name, type, size, region);
    }

    private static long parseSeed(String text) {
        if (text == null || text.trim().isEmpty()) return System.nanoTime();
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return (long) text.trim().hashCode() * 2654435761L;
        }
    }

    private static String generateGalaxyName(long seed) {
        SpaceNameGenerator gen = new SpaceNameGenerator();
        String base   = gen.factionName(new Random(seed));
        String suffix = GALAXY_SUFFIXES[(int) (Math.abs(seed) % GALAXY_SUFFIXES.length)];
        return base + " " + suffix;
    }

    // ------------------------------------------------------------------
    // Screen lifecycle
    // ------------------------------------------------------------------

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
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
        starfield.dispose();
    }
}
