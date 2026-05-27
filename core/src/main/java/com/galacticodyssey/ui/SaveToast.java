package com.galacticodyssey.ui;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

public class SaveToast {

    private static final float DISPLAY_DURATION = 2f;
    private static final float FADE_DURATION = 0.5f;

    public static void show(Stage stage, Skin skin, String message) {
        Table toast = new Table();
        Label label = new Label(message, skin, "header");
        toast.add(label).pad(12, 24, 12, 24);

        toast.pack();
        toast.setPosition(
            (stage.getWidth() - toast.getWidth()) / 2f,
            stage.getHeight() - toast.getHeight() - 40f);

        toast.getColor().a = 0f;
        toast.addAction(Actions.sequence(
            Actions.fadeIn(0.3f, Interpolation.smooth),
            Actions.delay(DISPLAY_DURATION),
            Actions.fadeOut(FADE_DURATION, Interpolation.smooth),
            Actions.removeActor()
        ));

        stage.addActor(toast);
    }
}
