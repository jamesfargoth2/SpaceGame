package com.galacticodyssey.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;

public interface ManagedScreen extends Disposable {

    String getDisplayName();

    void open();

    void close();

    boolean isOpen();

    Stage getStage();

    void render(float delta);

    void resize(int width, int height);
}
