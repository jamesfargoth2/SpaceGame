package com.galacticodyssey.shipbuilder.phase2;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.AdjacencyBonusCalculator;
import com.galacticodyssey.shipbuilder.RoomDesign;
import com.galacticodyssey.shipbuilder.ShipDesign;

import java.util.List;

public class RoomLayoutHUD {
    private final Table budgetTable;
    private final Label powerLabel;
    private final Label weightLabel;
    private final Label volumeLabel;
    private final Label bonusLabel;

    public RoomLayoutHUD(Skin skin) {
        budgetTable = new Table(skin);
        budgetTable.setBackground("default-rect");
        budgetTable.defaults().pad(4).left();

        budgetTable.add("SHIP BUDGET").colspan(2).center().row();
        budgetTable.add("Power:").left(); powerLabel = new Label("0 / 100 kW", skin); budgetTable.add(powerLabel).right().row();
        budgetTable.add("Weight:").left(); weightLabel = new Label("0 / 20 t", skin); budgetTable.add(weightLabel).right().row();
        budgetTable.add("Volume:").left(); volumeLabel = new Label("0 / 300 m³", skin); budgetTable.add(volumeLabel).right().row();
        budgetTable.add("Bonuses:").left(); bonusLabel = new Label("None", skin); budgetTable.add(bonusLabel).right().row();
        budgetTable.pack();
    }

    public void update(ShipDesign design) {
        int totalVolume = design.totalRoomVolume();
        volumeLabel.setText(totalVolume + " m³");

        float totalWeight = 0;
        for (RoomDesign room : design.rooms) totalWeight += room.volume() * 50;
        weightLabel.setText(String.format("%.1f t", totalWeight / 1000f));

        AdjacencyBonusCalculator calc = new AdjacencyBonusCalculator();
        List<AdjacencyBonusCalculator.LayoutBonus> bonuses = calc.computeBonuses(design);
        if (bonuses.isEmpty()) {
            bonusLabel.setText("None");
        } else {
            StringBuilder sb = new StringBuilder();
            for (AdjacencyBonusCalculator.LayoutBonus b : bonuses) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(b.label);
            }
            bonusLabel.setText(sb.toString());
        }
    }

    public void attachTo(Stage stage) {
        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(budgetTable).pad(10);
        stage.addActor(root);
    }

    public void detach() {
        budgetTable.remove();
    }
}
