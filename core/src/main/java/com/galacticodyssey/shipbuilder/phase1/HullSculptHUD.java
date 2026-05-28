package com.galacticodyssey.shipbuilder.phase1;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class HullSculptHUD {
    private final Table statsTable;
    private final Label volumeLabel;
    private final Label lengthLabel;
    private final Label widthLabel;
    private final Label heightLabel;
    private final Label spinePointsLabel;
    private final Label crossSectionsLabel;

    public HullSculptHUD(Skin skin) {
        statsTable = new Table(skin);
        statsTable.setBackground("default-rect");
        statsTable.defaults().pad(4).left();

        statsTable.add("HULL STATS").colspan(2).center().row();
        statsTable.add("Length:").left(); lengthLabel = new Label("0m", skin); statsTable.add(lengthLabel).right().row();
        statsTable.add("Max Width:").left(); widthLabel = new Label("0m", skin); statsTable.add(widthLabel).right().row();
        statsTable.add("Max Height:").left(); heightLabel = new Label("0m", skin); statsTable.add(heightLabel).right().row();
        statsTable.add("Int. Volume:").left(); volumeLabel = new Label("0 m³", skin); statsTable.add(volumeLabel).right().row();
        statsTable.add("Spine Pts:").left(); spinePointsLabel = new Label("0", skin); statsTable.add(spinePointsLabel).right().row();
        statsTable.add("Cross-Sects:").left(); crossSectionsLabel = new Label("0", skin); statsTable.add(crossSectionsLabel).right().row();
        statsTable.pack();
    }

    public void update(ShipDesign design) {
        float length = design.hull.estimateSpineLength();
        float width = design.hull.estimateMaxWidth();
        float height = design.hull.estimateMaxHeight();
        float volume = length * width * height * 0.5f;
        lengthLabel.setText(String.format("%.1fm", length));
        widthLabel.setText(String.format("%.1fm", width));
        heightLabel.setText(String.format("%.1fm", height));
        volumeLabel.setText(String.format("%.0f m³", volume));
        spinePointsLabel.setText(String.valueOf(design.hull.spinePoints.size()));
        crossSectionsLabel.setText(String.valueOf(design.hull.crossSections.size()));
    }

    public void attachTo(Stage stage) {
        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(statsTable).pad(10);
        stage.addActor(root);
    }

    public void detach() {
        statsTable.remove();
    }
}
