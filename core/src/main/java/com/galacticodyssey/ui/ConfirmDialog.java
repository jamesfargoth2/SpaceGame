package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

public class ConfirmDialog extends Group implements Disposable {

    private final Texture overlayTexture;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmDialog(Stage stage, Skin skin, String message,
                         String confirmText, String cancelText,
                         Runnable onConfirm, Runnable onCancel) {
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        setSize(stage.getWidth(), stage.getHeight());

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0f, 0f, 0.7f));
        pixmap.fill();
        overlayTexture = new Texture(pixmap);
        pixmap.dispose();

        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        addActor(overlay);

        // Consume clicks on the overlay so they don't pass through
        overlay.addListener(new ClickListener() {});

        Table panel = new Table();
        panel.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));

        Label messageLabel = new Label(message, skin, "setting");
        messageLabel.setWrap(true);
        panel.add(messageLabel).width(400).pad(20).row();

        Table buttonRow = new Table();
        TextButton confirmBtn = new TextButton(confirmText, skin);
        TextButton cancelBtn = new TextButton(cancelText, skin);

        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dismiss();
                onConfirm.run();
            }
        });

        cancelBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                dismiss();
                onCancel.run();
            }
        });

        buttonRow.add(confirmBtn).width(150).height(40).padRight(16);
        buttonRow.add(cancelBtn).width(150).height(40);
        panel.add(buttonRow).padBottom(20).row();

        overlay.add(panel);
    }

    public void show(Stage stage) {
        stage.addActor(this);
    }

    public void dismiss() {
        remove();
        dispose();
    }

    @Override
    public void dispose() {
        if (overlayTexture != null) {
            overlayTexture.dispose();
        }
    }
}
