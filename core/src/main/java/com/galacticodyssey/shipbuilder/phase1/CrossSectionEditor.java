package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.galacticodyssey.shipbuilder.CrossSectionDef;
import com.galacticodyssey.shipbuilder.DrydockScene;

public class CrossSectionEditor extends Window {
    private final CrossSectionDef crossSection;
    private final DrydockScene scene;
    private final Slider widthSlider;
    private final Slider heightSlider;
    private final Slider exponentSlider;
    private final Label widthLabel;
    private final Label heightLabel;
    private final Label exponentLabel;

    public CrossSectionEditor(CrossSectionDef crossSection, Skin skin, DrydockScene scene) {
        super("Cross-Section Editor (t=" + String.format("%.2f", crossSection.t) + ")", skin);
        this.crossSection = crossSection;
        this.scene = scene;

        widthSlider = new Slider(0.5f, 15f, 0.1f, false, skin);
        widthSlider.setValue(crossSection.width);
        widthLabel = new Label(String.format("%.1f", crossSection.width), skin);

        heightSlider = new Slider(0.5f, 15f, 0.1f, false, skin);
        heightSlider.setValue(crossSection.height);
        heightLabel = new Label(String.format("%.1f", crossSection.height), skin);

        exponentSlider = new Slider(1f, 6f, 0.1f, false, skin);
        exponentSlider.setValue(crossSection.exponent);
        exponentLabel = new Label(String.format("%.1f", crossSection.exponent), skin);

        ChangeListener listener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                crossSection.width = widthSlider.getValue();
                crossSection.height = heightSlider.getValue();
                crossSection.exponent = exponentSlider.getValue();
                widthLabel.setText(String.format("%.1f", crossSection.width));
                heightLabel.setText(String.format("%.1f", crossSection.height));
                exponentLabel.setText(String.format("%.1f", crossSection.exponent));
                scene.markMeshDirty();
            }
        };
        widthSlider.addListener(listener);
        heightSlider.addListener(listener);
        exponentSlider.addListener(listener);

        defaults().pad(4);
        add("Width:").left();
        add(widthSlider).width(200);
        add(widthLabel).width(40).row();
        add("Height:").left();
        add(heightSlider).width(200);
        add(heightLabel).width(40).row();
        add("Shape:").left();
        add(exponentSlider).width(200);
        add(exponentLabel).width(40).row();
        add("(2=round, >3=boxy)").colspan(3).center().row();
        pack();
        setModal(false);
        setMovable(true);
    }
}
