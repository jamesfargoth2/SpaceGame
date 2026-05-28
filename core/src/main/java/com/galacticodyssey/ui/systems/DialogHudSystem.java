package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.data.DialogChoice;
import com.galacticodyssey.npc.data.DialogNode;
import com.galacticodyssey.npc.events.DialogClosedEvent;
import com.galacticodyssey.npc.events.DialogNodeChangedEvent;
import com.galacticodyssey.npc.events.DialogOpenedEvent;

public class DialogHudSystem extends EntitySystem implements Disposable {

    private static final float MAX_TEXT_WIDTH = 700f;
    private static final float PAD_BOTTOM = 40f;
    private static final float PAD_LEFT = 20f;

    private final Skin skin;
    private Stage stage;
    private Table dialogTable;
    private Label speakerLabel;
    private Label textLabel;
    private final Label[] choiceLabels = new Label[4];
    private Label hintLabel;
    private Texture bgTexture;
    private boolean visible;

    public DialogHudSystem(EventBus eventBus, Skin skin) {
        super(0);
        this.skin = skin;

        eventBus.subscribe(DialogOpenedEvent.class, this::onDialogOpened);
        eventBus.subscribe(DialogNodeChangedEvent.class, this::onNodeChanged);
        eventBus.subscribe(DialogClosedEvent.class, this::onDialogClosed);
    }

    public void initialize() {
        stage = new Stage(new ScreenViewport());

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0f, 0f, 0f, 0.75f));
        pix.fill();
        bgTexture = new Texture(pix);
        pix.dispose();

        dialogTable = new Table();
        dialogTable.setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));
        dialogTable.pad(16);
        dialogTable.defaults().left();

        speakerLabel = new Label("", skin, "header");
        textLabel = new Label("", skin, "setting");
        textLabel.setWrap(true);

        dialogTable.add(speakerLabel).left().padBottom(8).row();
        dialogTable.add(textLabel).width(MAX_TEXT_WIDTH).left().padBottom(12).row();

        for (int i = 0; i < choiceLabels.length; i++) {
            choiceLabels[i] = new Label("", skin, "setting");
            choiceLabels[i].setColor(new Color(1f, 0.9f, 0.4f, 1f));
            dialogTable.add(choiceLabels[i]).left().padBottom(4).row();
        }

        hintLabel = new Label("", skin, "body");
        dialogTable.add(hintLabel).left().padTop(8).row();

        dialogTable.setVisible(false);
        stage.addActor(dialogTable);
    }

    private void onDialogOpened(DialogOpenedEvent event) {
        visible = true;
        showNode(event.npcName, event.node);
    }

    private void onNodeChanged(DialogNodeChangedEvent event) {
        showNode(event.npcName, event.node);
    }

    private void onDialogClosed(DialogClosedEvent event) {
        visible = false;
        dialogTable.setVisible(false);
    }

    private void showNode(String npcName, DialogNode node) {
        speakerLabel.setText(node.speakerLabel != null ? node.speakerLabel : npcName);
        textLabel.setText(node.text);

        for (int i = 0; i < choiceLabels.length; i++) {
            if (i < node.choices.size()) {
                DialogChoice choice = node.choices.get(i);
                choiceLabels[i].setText("[" + (i + 1) + "] " + choice.text);
                choiceLabels[i].setVisible(true);
            } else {
                choiceLabels[i].setText("");
                choiceLabels[i].setVisible(false);
            }
        }

        if (node.isEndNode()) {
            hintLabel.setText("[Space] Continue");
            hintLabel.setVisible(true);
        } else {
            hintLabel.setVisible(false);
        }

        dialogTable.setVisible(true);
        dialogTable.pack();
        layoutDialog();
    }

    private void layoutDialog() {
        float x = PAD_LEFT;
        float y = PAD_BOTTOM;
        dialogTable.setPosition(x, y);
    }

    public void render(float delta) {
        if (!visible || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
            if (visible) layoutDialog();
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (bgTexture != null) bgTexture.dispose();
    }
}
