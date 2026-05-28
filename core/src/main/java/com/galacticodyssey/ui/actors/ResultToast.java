package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public class ResultToast extends Table {

    private final Skin skin;

    public ResultToast(Skin skin) {
        this.skin = skin;
        setVisible(false);
    }

    public void show(String message, Runnable onComplete) {
        clearChildren();
        clearActions();
        setVisible(true);

        Label label = new Label(message, skin, "header");
        label.setColor(0.2f, 1f, 0.47f, 1f);
        add(label).center();

        getColor().a = 0f;
        addAction(Actions.sequence(
            Actions.fadeIn(0.3f),
            Actions.delay(2f),
            Actions.fadeOut(0.5f),
            Actions.run(() -> {
                setVisible(false);
                if (onComplete != null) onComplete.run();
            })
        ));
    }
}
