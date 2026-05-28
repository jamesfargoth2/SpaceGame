package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.galacticodyssey.shipbuilder.AppendageDef;

import java.util.function.Consumer;

public class AppendageCatalog extends Window {
    public AppendageCatalog(Skin skin, Consumer<AppendageDef.AppendageType> onSelect) {
        super("Appendage Catalog", skin);

        for (AppendageDef.AppendageType type : AppendageDef.AppendageType.values()) {
            TextButton btn = new TextButton(type.name().replace('_', ' '), skin);
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onSelect.accept(type);
                    remove();
                }
            });
            add(btn).fillX().pad(4).row();
        }
        pack();
        setModal(true);
        setMovable(true);
    }
}
