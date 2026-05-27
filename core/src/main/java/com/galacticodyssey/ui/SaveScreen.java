package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.persistence.ManifestData;
import com.galacticodyssey.persistence.SaveBackend;
import com.galacticodyssey.persistence.ThumbnailCapture;

import java.io.File;
import java.util.List;

public class SaveScreen extends SaveListBaseScreen {

    private final GameScreen gameScreen;
    private Texture newSaveBackground;
    private Texture newSavePlaceholder;
    private Pixmap capturedThumbnail;

    public SaveScreen(GalacticOdyssey game, SaveBackend saveBackend, GameScreen gameScreen) {
        super(game, saveBackend, gameScreen);
        this.gameScreen = gameScreen;
    }

    @Override
    protected String getTitle() {
        return "SAVE GAME";
    }

    @Override
    protected void buildExtraSlots(Table listTable) {
        // "New Save" card with dashed style
        Table newSaveCard = new Table();

        newSaveBackground = createDashedBackground();
        newSaveCard.setBackground(new TextureRegionDrawable(new TextureRegion(newSaveBackground)));
        newSaveCard.pad(10);

        newSavePlaceholder = createPlusPlaceholder();
        Image plusImage = new Image(new TextureRegionDrawable(new TextureRegion(newSavePlaceholder)));
        newSaveCard.add(plusImage).width(160).height(90).padRight(14);

        Table infoTable = new Table();
        infoTable.left();
        Label nameLabel = new Label("New Save", skin, "slot-name");
        infoTable.add(nameLabel).left().row();
        Label detailLabel = new Label("Create a new save slot", skin, "slot-detail");
        infoTable.add(detailLabel).left().padTop(2).row();

        newSaveCard.add(infoTable).expandX().fillX();

        newSaveCard.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audioManager.playSound("audio/sfx/ui_click.ogg");
                createNewSave();
            }
        });

        listTable.add(newSaveCard).width(780).padBottom(12).row();
    }

    @Override
    public void onSlotClicked(ManifestData manifest) {
        audioManager.playSound("audio/sfx/ui_click.ogg");
        new ConfirmDialog(stage, skin,
            "Overwrite '" + manifest.getDisplayNameOrFallback() + "'? This cannot be undone.",
            "Overwrite", "Cancel",
            () -> overwriteSave(manifest),
            () -> {}
        ).show(stage);
    }

    private void createNewSave() {
        int nextNumber = computeNextSaveNumber();
        String locationName = "Unknown"; // TODO: get from game state
        String displayName = "Save #" + nextNumber + " — " + locationName;
        String saveId = "save-" + System.currentTimeMillis();

        performSave(saveId, displayName);
    }

    private void overwriteSave(ManifestData manifest) {
        performSave(manifest.saveName, manifest.getDisplayNameOrFallback());
    }

    private void performSave(String saveId, String displayName) {
        Gdx.app.log("SaveScreen", "Saving to: " + saveId);

        // TODO: Wire SaveCoordinator.save() with display metadata
        // For now, just show the toast and go back
        SaveToast.show(stage, skin, "Game Saved");

        // Return to paused game after brief delay
        stage.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
            com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(0.5f),
            com.badlogic.gdx.scenes.scene2d.actions.Actions.run(this::goBack)
        ));
    }

    private int computeNextSaveNumber() {
        int max = 0;
        for (ManifestData m : manifests) {
            if (m.isAutosave()) continue;
            String name = m.getDisplayNameOrFallback();
            if (name.startsWith("Save #")) {
                try {
                    int dashIdx = name.indexOf('—');
                    String numStr;
                    if (dashIdx > 0) {
                        numStr = name.substring(6, dashIdx).trim();
                    } else {
                        numStr = name.substring(6).trim();
                    }
                    int num = Integer.parseInt(numStr);
                    if (num > max) max = num;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max + 1;
    }

    private Texture createDashedBackground() {
        Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0.03f, 0.05f, 0.3f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, 4, 4);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    private Texture createPlusPlaceholder() {
        int w = 160, h = 90;
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.05f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, w, h);
        // Draw + sign
        int cx = w / 2, cy = h / 2;
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.6f));
        pixmap.fillRectangle(cx - 1, cy - 12, 3, 25);
        pixmap.fillRectangle(cx - 12, cy - 1, 25, 3);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        super.dispose();
        if (newSaveBackground != null) { newSaveBackground.dispose(); newSaveBackground = null; }
        if (newSavePlaceholder != null) { newSavePlaceholder.dispose(); newSavePlaceholder = null; }
        if (capturedThumbnail != null) { capturedThumbnail.dispose(); capturedThumbnail = null; }
    }
}
