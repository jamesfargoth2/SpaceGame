package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.core.GamePreferences;

public class SettingsScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final GalacticOdyssey game;
    private final Stage stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera backgroundCamera;
    private final GamePreferences preferences;
    private final AudioManager audio;

    private Table displayTable;
    private Table audioTable;
    private TextButton displayTabBtn;
    private TextButton audioTabBtn;

    private SelectBox<String> displayModeBox;
    private SelectBox<String> resolutionBox;
    private CheckBox vsyncCheckBox;
    private final Array<int[]> resolutions = new Array<>();

    private Slider masterSlider;
    private Slider musicSlider;
    private Slider sfxSlider;
    private Label masterValueLabel;
    private Label musicValueLabel;
    private Label sfxValueLabel;

    private float revertTimer;
    private Table revertDialogTable;
    private Label revertCountdownLabel;
    private boolean revertPending;
    private Texture dialogBackgroundTexture;

    private GamePreferences.DisplayMode savedDisplayMode;
    private int savedResolutionWidth;
    private int savedResolutionHeight;
    private boolean savedVsync;
    private float savedMasterVolume;
    private float savedMusicVolume;
    private float savedSfxVolume;

    public SettingsScreen(GalacticOdyssey game) {
        this.game = game;
        this.preferences = game.getPreferences();
        this.audio = game.getAudioManager();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        this.starfield = new StarfieldBackground(screenW, screenH);

        snapshotSavedState();
        buildUi();
    }

    private void snapshotSavedState() {
        savedDisplayMode = preferences.getDisplayMode();
        savedResolutionWidth = preferences.getResolutionWidth();
        savedResolutionHeight = preferences.getResolutionHeight();
        savedVsync = preferences.isVsync();
        savedMasterVolume = preferences.getMasterVolume();
        savedMusicVolume = preferences.getMusicVolume();
        savedSfxVolume = preferences.getSfxVolume();
    }

    private void buildUi() {
        Skin skin = game.getSkin();

        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(30);

        Label title = new Label("SETTINGS", skin, "title");
        root.add(title).padBottom(30).row();

        Table tabRow = new Table();
        displayTabBtn = new TextButton("Display", skin);
        audioTabBtn = new TextButton("Audio", skin);
        tabRow.add(displayTabBtn).width(200).height(45).padRight(10);
        tabRow.add(audioTabBtn).width(200).height(45);
        root.add(tabRow).padBottom(20).row();

        displayTable = buildDisplayTab(skin);
        audioTable = buildAudioTab(skin);

        Stack contentStack = new Stack();
        contentStack.add(displayTable);
        contentStack.add(audioTable);
        audioTable.setVisible(false);

        root.add(contentStack).width(700).height(300).row();

        Table bottomRow = new Table();
        TextButton backBtn = new TextButton("Back", skin);
        TextButton applyBtn = new TextButton("Apply", skin);

        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                revertUnsavedChanges();
                game.setScreen(new MainMenuScreen(game));
            }
        });

        applyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                applySettings();
            }
        });

        bottomRow.add(backBtn).width(200).height(50).expandX().left();
        bottomRow.add(applyBtn).width(200).height(50).expandX().right();
        root.add(bottomRow).width(700).padTop(30).row();

        stage.addActor(root);

        setupTabListeners(skin);
        selectTab(true);
    }

    private void setupTabListeners(Skin skin) {
        displayTabBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                selectTab(true);
            }
        });

        audioTabBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                selectTab(false);
            }
        });
    }

    private void selectTab(boolean displayActive) {
        displayTable.setVisible(displayActive);
        audioTable.setVisible(!displayActive);
        displayTabBtn.setDisabled(displayActive);
        audioTabBtn.setDisabled(!displayActive);
    }

    private Table buildDisplayTab(Skin skin) {
        Table table = new Table();
        table.top().padTop(20);

        table.add(new Label("Display Mode", skin, "setting")).left().padRight(20);
        displayModeBox = new SelectBox<>(skin);
        displayModeBox.setItems("Windowed", "Fullscreen", "Borderless");
        displayModeBox.setSelectedIndex(preferences.getDisplayMode().ordinal());
        table.add(displayModeBox).width(250).height(35).left().row();

        table.add().height(15).row();

        table.add(new Label("Resolution", skin, "setting")).left().padRight(20);
        resolutionBox = new SelectBox<>(skin);
        populateResolutions();
        resolutionBox.setDisabled(preferences.getDisplayMode() == GamePreferences.DisplayMode.FULLSCREEN);
        table.add(resolutionBox).width(250).height(35).left().row();

        table.add().height(15).row();

        table.add(new Label("VSync", skin, "setting")).left().padRight(20);
        vsyncCheckBox = new CheckBox("", skin);
        vsyncCheckBox.setChecked(preferences.isVsync());
        table.add(vsyncCheckBox).left().row();

        displayModeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                boolean isFullscreen = displayModeBox.getSelectedIndex() == 1;
                resolutionBox.setDisabled(isFullscreen);
            }
        });

        return table;
    }

    private void populateResolutions() {
        resolutions.clear();
        Graphics.DisplayMode[] modes = Gdx.graphics.getDisplayModes();
        Array<String> seen = new Array<>();
        Array<String> items = new Array<>();

        for (Graphics.DisplayMode mode : modes) {
            String key = mode.width + "x" + mode.height;
            if (!seen.contains(key, false)) {
                seen.add(key);
                resolutions.add(new int[]{mode.width, mode.height});
                items.add(mode.width + " x " + mode.height);
            }
        }

        resolutions.sort((a, b) -> b[0] != a[0] ? b[0] - a[0] : b[1] - a[1]);
        items.clear();
        for (int[] res : resolutions) {
            items.add(res[0] + " x " + res[1]);
        }

        resolutionBox.setItems(items);

        String currentRes = preferences.getResolutionWidth() + " x " + preferences.getResolutionHeight();
        int idx = items.indexOf(currentRes, false);
        if (idx >= 0) {
            resolutionBox.setSelectedIndex(idx);
        }
    }

    private Table buildAudioTab(Skin skin) {
        Table table = new Table();
        table.top().padTop(20);

        masterSlider = new Slider(0f, 1f, 0.01f, false, skin);
        masterSlider.setValue(preferences.getMasterVolume());
        masterValueLabel = new Label(toPercent(preferences.getMasterVolume()), skin, "setting");
        addVolumeRow(table, skin, "Master Volume", masterSlider, masterValueLabel);

        musicSlider = new Slider(0f, 1f, 0.01f, false, skin);
        musicSlider.setValue(preferences.getMusicVolume());
        musicValueLabel = new Label(toPercent(preferences.getMusicVolume()), skin, "setting");
        addVolumeRow(table, skin, "Music Volume", musicSlider, musicValueLabel);

        sfxSlider = new Slider(0f, 1f, 0.01f, false, skin);
        sfxSlider.setValue(preferences.getSfxVolume());
        sfxValueLabel = new Label(toPercent(preferences.getSfxVolume()), skin, "setting");
        addVolumeRow(table, skin, "SFX Volume", sfxSlider, sfxValueLabel);

        masterSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setMasterVolume(masterSlider.getValue());
                masterValueLabel.setText(toPercent(masterSlider.getValue()));
            }
        });

        musicSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setMusicVolume(musicSlider.getValue());
                musicValueLabel.setText(toPercent(musicSlider.getValue()));
            }
        });

        sfxSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                audio.setSfxVolume(sfxSlider.getValue());
                sfxValueLabel.setText(toPercent(sfxSlider.getValue()));
            }
        });

        return table;
    }

    private void addVolumeRow(Table table, Skin skin, String label, Slider slider, Label valueLabel) {
        table.add(new Label(label, skin, "setting")).left().width(200);
        table.add(slider).width(300).padLeft(10).padRight(10);
        table.add(valueLabel).width(60).right();
        table.row().padTop(15);
    }

    private String toPercent(float value) {
        return Math.round(value * 100) + "%";
    }

    private void applySettings() {
        GamePreferences.DisplayMode selectedMode =
            GamePreferences.DisplayMode.values()[displayModeBox.getSelectedIndex()];
        int selectedWidth = preferences.getResolutionWidth();
        int selectedHeight = preferences.getResolutionHeight();
        if (resolutionBox.getSelectedIndex() >= 0 && resolutionBox.getSelectedIndex() < resolutions.size) {
            int[] res = resolutions.get(resolutionBox.getSelectedIndex());
            selectedWidth = res[0];
            selectedHeight = res[1];
        }
        boolean selectedVsync = vsyncCheckBox.isChecked();

        boolean displayChanged =
            selectedMode != savedDisplayMode ||
            selectedWidth != savedResolutionWidth ||
            selectedHeight != savedResolutionHeight;

        preferences.setDisplayMode(selectedMode);
        preferences.setResolutionWidth(selectedWidth);
        preferences.setResolutionHeight(selectedHeight);
        preferences.setVsync(selectedVsync);

        Gdx.graphics.setVSync(selectedVsync);

        if (displayChanged) {
            applyDisplayMode(selectedMode, selectedWidth, selectedHeight);
            showRevertDialog();
        } else {
            preferences.save();
            snapshotSavedState();
        }
    }

    private void applyDisplayMode(GamePreferences.DisplayMode mode, int width, int height) {
        switch (mode) {
            case WINDOWED:
                Gdx.graphics.setUndecorated(false);
                Gdx.graphics.setWindowedMode(width, height);
                break;
            case FULLSCREEN:
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                break;
            case BORDERLESS:
                Graphics.DisplayMode desktop = Gdx.graphics.getDisplayMode();
                Gdx.graphics.setUndecorated(true);
                Gdx.graphics.setWindowedMode(desktop.width, desktop.height);
                break;
        }
    }

    private void showRevertDialog() {
        revertPending = true;
        revertTimer = 10f;
        Skin skin = game.getSkin();

        revertDialogTable = new Table();
        revertDialogTable.setFillParent(true);
        revertDialogTable.center();

        Table dialogContent = new Table();
        if (dialogBackgroundTexture != null) {
            dialogBackgroundTexture.dispose();
        }
        Pixmap bgPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        bgPixmap.setColor(new Color(0f, 0f, 0f, 0.85f));
        bgPixmap.fill();
        dialogBackgroundTexture = new Texture(bgPixmap);
        dialogContent.setBackground(new TextureRegionDrawable(
            new TextureRegion(dialogBackgroundTexture)));
        bgPixmap.dispose();
        dialogContent.pad(30);

        revertCountdownLabel = new Label("Keep these settings?\nReverting in 10s...", skin, "setting");
        revertCountdownLabel.setAlignment(Align.center);
        dialogContent.add(revertCountdownLabel).padBottom(20).row();

        Table dialogButtons = new Table();
        TextButton keepBtn = new TextButton("Keep", skin);
        TextButton revertBtn = new TextButton("Revert", skin);

        keepBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                confirmDisplayChange();
            }
        });

        revertBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                revertDisplayChange();
            }
        });

        dialogButtons.add(keepBtn).width(150).height(45).padRight(20);
        dialogButtons.add(revertBtn).width(150).height(45);
        dialogContent.add(dialogButtons);

        revertDialogTable.add(dialogContent);
        stage.addActor(revertDialogTable);
    }

    private void confirmDisplayChange() {
        revertPending = false;
        if (revertDialogTable != null) {
            revertDialogTable.remove();
            revertDialogTable = null;
        }
        preferences.save();
        snapshotSavedState();
    }

    private void revertDisplayChange() {
        revertPending = false;
        if (revertDialogTable != null) {
            revertDialogTable.remove();
            revertDialogTable = null;
        }
        preferences.setDisplayMode(savedDisplayMode);
        preferences.setResolutionWidth(savedResolutionWidth);
        preferences.setResolutionHeight(savedResolutionHeight);
        preferences.setVsync(savedVsync);
        applyDisplayMode(savedDisplayMode, savedResolutionWidth, savedResolutionHeight);
        Gdx.graphics.setVSync(savedVsync);

        displayModeBox.setSelectedIndex(savedDisplayMode.ordinal());
        vsyncCheckBox.setChecked(savedVsync);
        String savedRes = savedResolutionWidth + " x " + savedResolutionHeight;
        Array<String> items = new Array<>();
        for (int[] r : resolutions) items.add(r[0] + " x " + r[1]);
        int idx = items.indexOf(savedRes, false);
        if (idx >= 0) resolutionBox.setSelectedIndex(idx);
    }

    private void revertUnsavedChanges() {
        preferences.setMasterVolume(savedMasterVolume);
        preferences.setMusicVolume(savedMusicVolume);
        preferences.setSfxVolume(savedSfxVolume);
        audio.setMasterVolume(savedMasterVolume);
        audio.setMusicVolume(savedMusicVolume);
        audio.setSfxVolume(savedSfxVolume);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
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

        if (revertPending) {
            revertTimer -= delta;
            int remaining = Math.max(0, (int) Math.ceil(revertTimer));
            revertCountdownLabel.setText("Keep these settings?\nReverting in " + remaining + "s...");
            if (revertTimer <= 0) {
                revertDisplayChange();
            }
        }

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
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
        starfield.dispose();
        if (dialogBackgroundTexture != null) {
            dialogBackgroundTexture.dispose();
            dialogBackgroundTexture = null;
        }
    }
}
