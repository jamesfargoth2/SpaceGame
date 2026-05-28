package com.galacticodyssey.shipbuilder.planning;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.ShipDesign;

public class PlanningOverlay {
    private final Table rootTable;
    private final ShipSchematicRenderer schematicRenderer;
    private final BuildQueuePanel queuePanel;
    private boolean visible;

    public PlanningOverlay(ShipDesign design, BuildOrder buildOrder, int playerCredits, Skin skin) {
        schematicRenderer = new ShipSchematicRenderer();
        queuePanel = new BuildQueuePanel(buildOrder, playerCredits, skin);

        rootTable = new Table(skin);
        rootTable.setFillParent(true);
        rootTable.setBackground("default-rect");

        Table header = new Table(skin);
        header.add(new Label("SHIP MODIFICATIONS — " + design.name, skin)).expandX().left();
        header.add(new Label("[ESC to close]", skin)).right();
        rootTable.add(header).fillX().pad(8).row();

        Table content = new Table(skin);
        Table schematicArea = new Table(skin);
        schematicArea.add(new Label("SHIP SCHEMATIC — TOP VIEW", skin)).row();
        schematicArea.add(new Label("(2D schematic rendered separately)", skin)).expand().fill();
        content.add(schematicArea).expand().fill().pad(4);
        content.add(queuePanel).width(300).fillY().pad(4);
        rootTable.add(content).expand().fill().row();
    }

    public void show(Stage stage) {
        stage.addActor(rootTable);
        visible = true;
    }

    public void hide() {
        rootTable.remove();
        visible = false;
    }

    public boolean isVisible() { return visible; }

    public void refreshQueue() {
        queuePanel.refresh();
    }

    public void dispose() {
        schematicRenderer.dispose();
    }
}
