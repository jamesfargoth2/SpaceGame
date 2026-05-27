package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.persistence.ManifestData;
import com.galacticodyssey.persistence.SaveBackend;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SaveListBaseScreen implements Screen, SaveSlotListener {

    protected static final float WORLD_WIDTH = 1280f;
    protected static final float WORLD_HEIGHT = 720f;

    protected final GalacticOdyssey game;
    protected final Skin skin;
    protected final AudioManager audioManager;
    protected final SaveBackend saveBackend;
    protected final Screen returnTo;

    protected Stage stage;
    protected StarfieldBackground starfield;
    protected OrthographicCamera backgroundCamera;

    protected Table listTable;
    protected List<ManifestData> manifests = new ArrayList<>();
    protected final Map<String, Texture> thumbnailCache = new HashMap<>();
    protected final List<SaveSlotPanel> slotPanels = new ArrayList<>();

    public SaveListBaseScreen(GalacticOdyssey game, SaveBackend saveBackend, Screen returnTo) {
        this.game = game;
        this.skin = game.getSkin();
        this.audioManager = game.getAudioManager();
        this.saveBackend = saveBackend;
        this.returnTo = returnTo;
    }

    protected abstract String getTitle();

    public abstract void onSlotClicked(ManifestData manifest);

    protected void buildExtraSlots(Table listTable) {}

    @Override
    public void show() {
        stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        starfield = new StarfieldBackground(screenW, screenH);

        Gdx.input.setInputProcessor(stage);
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().center();

        // Title
        Label title = new Label(getTitle(), skin, "title");
        root.add(title).padTop(30).padBottom(20).row();

        // Scrollable list
        listTable = new Table();
        listTable.top();
        listTable.defaults().padBottom(8);

        buildExtraSlots(listTable);
        refreshSaveList();

        ScrollPane scrollPane = new ScrollPane(listTable, skin);
        scrollPane.setFadeScrollBars(true);
        scrollPane.setScrollingDisabled(true, false);
        root.add(scrollPane).width(820).expandY().fillY().padBottom(12).row();

        // Back button
        TextButton backBtn = new TextButton("BACK", skin);
        backBtn.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1) {
                    backBtn.setOrigin(Align.center);
                    backBtn.addAction(Actions.scaleTo(1.02f, 1.02f, 0.1f, Interpolation.smooth));
                    audioManager.playSound("audio/sfx/ui_hover.ogg");
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    backBtn.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                goBack();
            }
        });
        backBtn.setTransform(true);
        root.add(backBtn).width(200).height(45).padBottom(20).row();

        stage.addActor(root);
    }

    protected void refreshSaveList() {
        // Clear old panels
        for (SaveSlotPanel panel : slotPanels) {
            panel.dispose();
        }
        slotPanels.clear();
        disposeThumbnails();

        // Keep any extra slots (e.g. New Save) already added
        // Remove everything after the extra slots
        listTable.clearChildren();
        buildExtraSlots(listTable);

        manifests = saveBackend.listSaves();

        List<ManifestData> manualSaves = new ArrayList<>();
        List<ManifestData> autoSaves = new ArrayList<>();
        for (ManifestData m : manifests) {
            if (m.isAutosave()) {
                autoSaves.add(m);
            } else {
                manualSaves.add(m);
            }
        }

        for (ManifestData m : manualSaves) {
            addSlotPanel(m, false);
        }

        if (!autoSaves.isEmpty()) {
            // Autosave divider
            Table divider = new Table();
            Label dividerLabel = new Label("AUTOSAVES", skin, "slot-meta");
            divider.add(dividerLabel).padTop(8).padBottom(8);
            listTable.add(divider).row();

            for (ManifestData m : autoSaves) {
                addSlotPanel(m, true);
            }
        }
    }

    private void addSlotPanel(ManifestData manifest, boolean isAutosave) {
        Texture thumbnail = loadThumbnail(manifest.saveName);
        SaveSlotPanel panel = new SaveSlotPanel(manifest, skin, isAutosave, thumbnail, this);
        slotPanels.add(panel);
        listTable.add(panel).width(780).row();
    }

    protected Texture loadThumbnail(String saveId) {
        if (thumbnailCache.containsKey(saveId)) {
            return thumbnailCache.get(saveId);
        }
        try {
            File thumbFile = new File(getSavesRoot(), saveId + "/thumbnail.png");
            if (thumbFile.exists()) {
                Texture tex = new Texture(new com.badlogic.gdx.files.FileHandle(thumbFile));
                thumbnailCache.put(saveId, tex);
                return tex;
            }
        } catch (Exception e) {
            Gdx.app.error("SaveList", "Failed to load thumbnail for " + saveId, e);
        }
        return null;
    }

    protected File getSavesRoot() {
        String userHome = System.getProperty("user.home");
        return new File(userHome, ".galacticodyssey/saves");
    }

    protected void goBack() {
        game.setScreen(returnTo);
    }

    // SaveSlotListener defaults — subclasses override onSlotClicked
    @Override
    public void onRenameClicked(ManifestData manifest) {
        new RenameDialog(stage, skin, manifest.getDisplayNameOrFallback(),
            newName -> {
                manifest.displayName = newName;
                saveBackend.writeSave(manifest.saveName,
                    saveBackend.readSave(manifest.saveName));
                refreshSaveList();
            },
            () -> {}
        ).show(stage);
    }

    @Override
    public void onCopyClicked(ManifestData manifest) {
        String copyId = manifest.saveName + "-copy-" + System.currentTimeMillis();
        saveBackend.copySave(manifest.saveName, copyId);
        audioManager.playSound("audio/sfx/ui_click.ogg");
        refreshSaveList();
    }

    @Override
    public void onDeleteClicked(ManifestData manifest) {
        new ConfirmDialog(stage, skin,
            "Delete '" + manifest.getDisplayNameOrFallback() + "'? This cannot be undone.",
            "Delete", "Cancel",
            () -> {
                saveBackend.deleteSave(manifest.saveName);
                audioManager.playSound("audio/sfx/ui_click.ogg");
                refreshSaveList();
            },
            () -> {}
        ).show(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);
        if (stage == null) return;

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
        if (stage != null) stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage == null) return;
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
        for (SaveSlotPanel panel : slotPanels) {
            panel.dispose();
        }
        slotPanels.clear();
        disposeThumbnails();
        if (stage != null) { stage.dispose(); stage = null; }
        if (starfield != null) { starfield.dispose(); starfield = null; }
    }

    private void disposeThumbnails() {
        for (Texture tex : thumbnailCache.values()) {
            tex.dispose();
        }
        thumbnailCache.clear();
    }
}
