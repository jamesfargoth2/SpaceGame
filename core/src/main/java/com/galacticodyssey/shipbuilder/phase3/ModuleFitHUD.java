package com.galacticodyssey.shipbuilder.phase3;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.galacticodyssey.shipbuilder.*;

public class ModuleFitHUD {
    private final Table statsTable;
    private final Label dpsLabel, rangeLabel, thrustLabel, shieldLabel, powerLabel, weightLabel;

    public ModuleFitHUD(Skin skin) {
        statsTable = new Table(skin);
        statsTable.setBackground("default-rect");
        statsTable.defaults().pad(4).left();

        statsTable.add("SHIP PERFORMANCE").colspan(2).center().row();
        statsTable.add("DPS:").left(); dpsLabel = new Label("0", skin); statsTable.add(dpsLabel).right().row();
        statsTable.add("Range:").left(); rangeLabel = new Label("0m", skin); statsTable.add(rangeLabel).right().row();
        statsTable.add("Thrust:").left(); thrustLabel = new Label("0 kN", skin); statsTable.add(thrustLabel).right().row();
        statsTable.add("Shields:").left(); shieldLabel = new Label("0 HP", skin); statsTable.add(shieldLabel).right().row();
        statsTable.add("Power:").left(); powerLabel = new Label("0 kW", skin); statsTable.add(powerLabel).right().row();
        statsTable.add("Weight:").left(); weightLabel = new Label("0 t", skin); statsTable.add(weightLabel).right().row();
        statsTable.pack();
    }

    public void update(ShipStatsCalculator calc, ShipDesign design) {
        ShipStatsCalculator.ShipStats stats = calc.computeStats(design);
        dpsLabel.setText(String.format("%.0f", stats.totalDps));
        rangeLabel.setText(String.format("%.0fm", stats.maxWeaponRange));
        thrustLabel.setText(String.format("%.0f kN", stats.totalThrust / 1000f));
        shieldLabel.setText(String.format("%.0f HP", stats.totalShieldHp));
        powerLabel.setText(String.format("%.0f kW", stats.totalPowerDraw));
        weightLabel.setText(String.format("%.1f t", stats.totalWeight / 1000f));
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
