package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ui.actors.ItemDetailPanel;

import java.util.Map;

public class OutfitterDetailPanel extends Table {

    private final Skin skin;
    private Label slotHeaderLabel;
    private Table currentSection;
    private Table candidateSection;
    private Table actionButtons;
    private TextButton installButton;
    private TextButton buyInstallButton;
    private TextButton uninstallButton;
    private TextButton sellButton;

    private Runnable onInstall;
    private Runnable onBuyInstall;
    private Runnable onUninstall;
    private Runnable onSell;

    public OutfitterDetailPanel(Skin skin) {
        this.skin = skin;
    }

    public void initialize() {
        pad(10);
        defaults().left().fillX();

        slotHeaderLabel = new Label("Select a slot", skin);
        add(slotHeaderLabel).padBottom(8).row();

        currentSection = new Table();
        add(currentSection).padBottom(8).row();

        candidateSection = new Table();
        add(candidateSection).padBottom(12).row();

        actionButtons = new Table();
        installButton = new TextButton("Install", skin);
        buyInstallButton = new TextButton("Buy & Install", skin);
        uninstallButton = new TextButton("Uninstall", skin);
        sellButton = new TextButton("Sell", skin);

        installButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (onInstall != null) onInstall.run();
            }
        });
        buyInstallButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (onBuyInstall != null) onBuyInstall.run();
            }
        });
        uninstallButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (onUninstall != null) onUninstall.run();
            }
        });
        sellButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (onSell != null) onSell.run();
            }
        });

        actionButtons.defaults().fillX().padBottom(4);
        actionButtons.add(installButton).row();
        actionButtons.add(buyInstallButton).row();
        actionButtons.add(uninstallButton).row();
        actionButtons.add(sellButton).row();
        add(actionButtons).expandY().bottom();
    }

    public void setCallbacks(Runnable onInstall, Runnable onBuyInstall, Runnable onUninstall, Runnable onSell) {
        this.onInstall = onInstall;
        this.onBuyInstall = onBuyInstall;
        this.onUninstall = onUninstall;
        this.onSell = onSell;
    }

    public void showSlot(ShipModuleSlot slot, boolean stationMode) {
        slotHeaderLabel.setText(slot.id + " — " + slot.slotType + " (" + slot.size + ")");
        showCurrentModule(slot.installedModule);
        candidateSection.clear();
        updateButtons(slot, null, stationMode, false);
    }

    public void showCurrentModule(ShipModuleData module) {
        currentSection.clear();
        currentSection.defaults().left();
        Label header = new Label("CURRENTLY INSTALLED", skin);
        header.setColor(Color.GRAY);
        currentSection.add(header).padBottom(4).row();

        if (module == null) {
            currentSection.add(new Label("Empty", skin)).row();
        } else {
            Label nameLabel = new Label(module.name, skin);
            nameLabel.setColor(ItemDetailPanel.getQualityColor(module.qualityTier));
            currentSection.add(nameLabel).padBottom(2).row();
            addStatLines(currentSection, module, null);
        }
    }

    public void showCandidate(ShipModuleData candidate, ShipModuleData current) {
        candidateSection.clear();
        if (candidate == null) return;
        candidateSection.defaults().left();

        Label header = new Label("COMPARING TO", skin);
        header.setColor(Color.GRAY);
        candidateSection.add(header).padBottom(4).row();

        Label nameLabel = new Label(candidate.name, skin);
        nameLabel.setColor(ItemDetailPanel.getQualityColor(candidate.qualityTier));
        candidateSection.add(nameLabel).padBottom(2).row();
        addStatLines(candidateSection, candidate, current);
    }

    public void updateButtons(ShipModuleSlot slot, ShipModuleData candidate,
                              boolean stationMode, boolean fromStation) {
        installButton.setVisible(candidate != null && !fromStation);
        buyInstallButton.setVisible(candidate != null && fromStation && stationMode);
        uninstallButton.setVisible(slot != null && !slot.isEmpty());
        sellButton.setVisible(stationMode && slot != null && !slot.isEmpty() && !slot.mandatory);
    }

    public void setInstallEnabled(boolean enabled) {
        installButton.setDisabled(!enabled);
        buyInstallButton.setDisabled(!enabled);
    }

    private void addStatLines(Table table, ShipModuleData module, ShipModuleData compareWith) {
        addStatRow(table, "Power", module.powerDraw, compareWith != null ? compareWith.powerDraw : Float.NaN, "MW", true);
        addStatRow(table, "Mass", module.mass, compareWith != null ? compareWith.mass : Float.NaN, "t", true);
        for (Map.Entry<String, Float> entry : module.stats.entrySet()) {
            float compareVal = compareWith != null ? compareWith.stats.getOrDefault(entry.getKey(), 0f) : Float.NaN;
            addStatRow(table, entry.getKey(), entry.getValue(), compareVal, "", false);
        }
    }

    private void addStatRow(Table table, String label, float value, float compareValue, String unit, boolean lowerIsBetter) {
        String text = label + ": " + String.format("%.1f", value) + unit;
        if (!Float.isNaN(compareValue) && Math.abs(value - compareValue) > 0.01f) {
            float diff = value - compareValue;
            boolean better = lowerIsBetter ? diff < 0 : diff > 0;
            String arrow = better ? " ▲" : " ▼";
            text += arrow;
            Label l = new Label(text, skin);
            l.setColor(better ? Color.GREEN : Color.RED);
            table.add(l).padBottom(1).row();
        } else {
            table.add(new Label(text, skin)).padBottom(1).row();
        }
    }

    public void clearAll() {
        slotHeaderLabel.setText("Select a slot");
        currentSection.clear();
        candidateSection.clear();
        installButton.setVisible(false);
        buyInstallButton.setVisible(false);
        uninstallButton.setVisible(false);
        sellButton.setVisible(false);
    }
}
