package com.galacticodyssey.ui.outfitter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ui.actors.ItemDetailPanel;

import java.util.List;
import java.util.function.Consumer;

public class OutfitterInventoryPanel extends Table {

    private final Skin skin;
    private TextButton cargoTab;
    private TextButton stationTab;
    private TextField filterField;
    private Table itemListTable;
    private ScrollPane scrollPane;
    private boolean showingCargo = true;

    private Consumer<ShipModuleData> onModuleSelected;
    private Consumer<ShipWeaponData> onWeaponSelected;
    private Consumer<ShipModuleData> onModuleDoubleClicked;
    private boolean stationMode;

    public OutfitterInventoryPanel(Skin skin) {
        this.skin = skin;
    }

    public void initialize(boolean stationMode) {
        this.stationMode = stationMode;
        pad(8);
        defaults().fillX();

        Table tabBar = new Table();
        cargoTab = new TextButton("Cargo", skin);
        stationTab = new TextButton("Station", skin);
        cargoTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { showCargo(); }
        });
        stationTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { showStation(); }
        });
        tabBar.add(cargoTab).expandX().fillX();
        if (stationMode) tabBar.add(stationTab).expandX().fillX();
        add(tabBar).padBottom(6).row();

        filterField = new TextField("", skin);
        filterField.setMessageText("Filter...");
        add(filterField).padBottom(6).row();

        itemListTable = new Table();
        itemListTable.top().defaults().fillX().padBottom(3);
        scrollPane = new ScrollPane(itemListTable, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).expand().fill();
    }

    public void setOnModuleSelected(Consumer<ShipModuleData> callback) { this.onModuleSelected = callback; }
    public void setOnWeaponSelected(Consumer<ShipWeaponData> callback) { this.onWeaponSelected = callback; }
    public void setOnModuleDoubleClicked(Consumer<ShipModuleData> callback) { this.onModuleDoubleClicked = callback; }

    public void refreshModules(List<ShipModuleData> modules, ShipModuleSlot selectedSlot) {
        itemListTable.clear();
        String filter = filterField.getText().toLowerCase();

        for (ShipModuleData mod : modules) {
            if (!filter.isEmpty() && !mod.name.toLowerCase().contains(filter)) continue;
            if (selectedSlot != null && !selectedSlot.accepts(mod)) continue;

            Table row = new Table(skin);
            row.pad(4, 6, 4, 6);
            Label nameLabel = new Label(mod.name, skin);
            nameLabel.setColor(ItemDetailPanel.getQualityColor(mod.qualityTier));
            Label infoLabel = new Label(mod.category.name() + " | " + mod.size, skin);
            infoLabel.setColor(Color.GRAY);
            infoLabel.setFontScale(0.8f);

            row.add(nameLabel).left().row();
            row.add(infoLabel).left();

            row.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    if (getTapCount() >= 2 && onModuleDoubleClicked != null) {
                        onModuleDoubleClicked.accept(mod);
                    } else if (onModuleSelected != null) {
                        onModuleSelected.accept(mod);
                    }
                }
            });

            itemListTable.add(row).row();
        }
    }

    public void refreshWeapons(List<ShipWeaponData> weapons, HardpointSize maxSize) {
        itemListTable.clear();
        String filter = filterField.getText().toLowerCase();

        for (ShipWeaponData wep : weapons) {
            if (!filter.isEmpty() && !wep.name.toLowerCase().contains(filter)) continue;

            Table row = new Table(skin);
            row.pad(4, 6, 4, 6);
            Label nameLabel = new Label(wep.name, skin);
            Label infoLabel = new Label(wep.category + " | " + String.format("%.0f dmg", wep.damage), skin);
            infoLabel.setColor(Color.GRAY);
            infoLabel.setFontScale(0.8f);

            row.add(nameLabel).left().row();
            row.add(infoLabel).left();

            row.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    if (onWeaponSelected != null) onWeaponSelected.accept(wep);
                }
            });

            itemListTable.add(row).row();
        }
    }

    public boolean isShowingCargo() { return showingCargo; }
    public String getFilterText() { return filterField.getText(); }
    public void toggleTab() { if (showingCargo) showStation(); else showCargo(); }

    private void showCargo() {
        showingCargo = true;
        cargoTab.setColor(Color.WHITE);
        stationTab.setColor(Color.GRAY);
    }

    private void showStation() {
        if (!stationMode) return;
        showingCargo = false;
        cargoTab.setColor(Color.GRAY);
        stationTab.setColor(Color.WHITE);
    }
}
