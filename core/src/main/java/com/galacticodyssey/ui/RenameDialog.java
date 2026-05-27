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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

import java.util.function.Consumer;

public class RenameDialog extends Group implements Disposable {

    private final Texture overlayTexture;
    private final TextField textField;

    public RenameDialog(Stage stage, Skin skin, String currentName,
                        Consumer<String> onConfirm, Runnable onCancel) {

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

        overlay.addListener(new ClickListener() {});

        Table panel = new Table();
        panel.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));

        Label titleLabel = new Label("Rename Save", skin, "header");
        panel.add(titleLabel).pad(20).row();

        textField = new TextField(currentName, skin);
        textField.setMaxLength(64);
        panel.add(textField).width(350).pad(10).row();

        Table buttonRow = new Table();
        TextButton confirmBtn = new TextButton("Confirm", skin);
        TextButton cancelBtn = new TextButton("Cancel", skin);

        confirmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String newName = textField.getText().trim();
                dismiss();
                if (!newName.isEmpty()) {
                    onConfirm.accept(newName);
                }
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
        stage.setKeyboardFocus(textField);
        textField.selectAll();
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
