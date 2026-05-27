package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.persistence.ManifestData;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SaveSlotPanel extends Table implements Disposable {

    private static final float CARD_WIDTH = 780f;
    private static final float CARD_HEIGHT = 110f;
    private static final float THUMB_WIDTH = 160f;
    private static final float THUMB_HEIGHT = 90f;

    private final ManifestData manifest;
    private Texture placeholderTexture;
    private Texture backgroundTexture;

    public SaveSlotPanel(ManifestData manifest, Skin skin, boolean isAutosave,
                         Texture thumbnail, SaveSlotListener listener) {
        this.manifest = manifest;

        backgroundTexture = createCardBackground();
        setBackground(new TextureRegionDrawable(new TextureRegion(backgroundTexture)));

        pad(10);
        setTransform(true);
        setOrigin(Align.center);

        // Thumbnail
        Image thumbImage;
        if (thumbnail != null) {
            thumbImage = new Image(new TextureRegionDrawable(new TextureRegion(thumbnail)));
        } else {
            placeholderTexture = createPlaceholder();
            thumbImage = new Image(new TextureRegionDrawable(new TextureRegion(placeholderTexture)));
        }
        add(thumbImage).width(THUMB_WIDTH).height(THUMB_HEIGHT).padRight(14);

        // Info block
        Table infoTable = new Table();
        infoTable.left();

        String displayName = manifest.getDisplayNameOrFallback();
        Label nameLabel = new Label(displayName, skin, "slot-name");
        infoTable.add(nameLabel).left().row();

        String detail = manifest.locationDetail != null ? manifest.locationDetail : "";
        if (manifest.locationName != null && !detail.isEmpty()) {
            detail = manifest.locationName + " • " + detail;
        } else if (manifest.locationName != null) {
            detail = manifest.locationName;
        }
        Label detailLabel = new Label(detail, skin, "slot-detail");
        infoTable.add(detailLabel).left().padTop(2).row();

        String meta = formatMeta(manifest);
        Label metaLabel = new Label(meta, skin, "slot-meta");
        infoTable.add(metaLabel).left().padTop(6).row();

        add(infoTable).expandX().fillX().padRight(14);

        // Action buttons
        Table actions = new Table();
        if (!isAutosave) {
            TextButton renameBtn = new TextButton("Rename", skin, "small");
            renameBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    event.stop();
                    listener.onRenameClicked(manifest);
                }
            });
            actions.add(renameBtn).width(70).height(24).padBottom(5).row();
        }

        TextButton copyBtn = new TextButton("Copy", skin, "small");
        copyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
                listener.onCopyClicked(manifest);
            }
        });
        actions.add(copyBtn).width(70).height(24).padBottom(5).row();

        TextButton deleteBtn = new TextButton("Delete", skin, "small-red");
        deleteBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
                listener.onDeleteClicked(manifest);
            }
        });
        actions.add(deleteBtn).width(70).height(24).row();

        add(actions).right();

        // Card body click -> primary action
        addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1) {
                    addAction(Actions.scaleTo(1.01f, 1.01f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                listener.onSlotClicked(manifest);
            }
        });
    }

    private String formatMeta(ManifestData m) {
        StringBuilder sb = new StringBuilder();
        if (m.timestampMillis > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
            sb.append(sdf.format(new Date(m.timestampMillis)));
        }
        if (m.playtimeSeconds > 0) {
            long hours = m.playtimeSeconds / 3600;
            long minutes = (m.playtimeSeconds % 3600) / 60;
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(hours).append("h ").append(minutes).append("m");
        }
        if (m.playerCredits > 0) {
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(String.format("%,d cr", m.playerCredits));
        }
        if (m.shipName != null && !m.shipName.isEmpty()) {
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(m.shipName);
        }
        return sb.toString();
    }

    private Texture createCardBackground() {
        Pixmap pixmap = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0.03f, 0.05f, 0.6f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, 4, 4);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    private Texture createPlaceholder() {
        Pixmap pixmap = new Pixmap((int) THUMB_WIDTH, (int) THUMB_HEIGHT, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0.1f, 0.1f, 0.18f, 1f));
        pixmap.fill();
        pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.3f));
        pixmap.drawRectangle(0, 0, pixmap.getWidth(), pixmap.getHeight());
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        if (placeholderTexture != null) placeholderTexture.dispose();
        if (backgroundTexture != null) backgroundTexture.dispose();
    }
}
