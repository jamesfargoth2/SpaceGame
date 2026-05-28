package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.items.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class EquipmentSlotsActor extends Table implements Disposable {

    private static final float SLOT_SIZE = 64f;
    private static final float SLOT_PAD = 6f;

    private final Skin skin;
    private final Map<EquipmentSlot, Table> slotCells = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, Label> slotNameLabels = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, Label> slotItemLabels = new EnumMap<>(EquipmentSlot.class);
    private Texture emptySlotTexture;
    private BiConsumer<EquipmentSlot, Item> onSlotRightClicked;

    public EquipmentSlotsActor(Skin skin) {
        this.skin = skin;
    }

    public void initialize() {
        Pixmap pix = new Pixmap((int) SLOT_SIZE, (int) SLOT_SIZE, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0.2f, 0.2f, 0.25f, 0.8f));
        pix.fill();
        pix.setColor(new Color(0.4f, 0.4f, 0.45f, 1f));
        pix.drawRectangle(0, 0, (int) SLOT_SIZE, (int) SLOT_SIZE);
        emptySlotTexture = new Texture(pix);
        pix.dispose();

        buildLayout();
    }

    public void setOnSlotRightClicked(BiConsumer<EquipmentSlot, Item> callback) {
        this.onSlotRightClicked = callback;
    }

    public void refresh(EquipmentSlotsComponent equip) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            Item item = equip.getSlot(slot);
            Label itemLabel = slotItemLabels.get(slot);
            if (item != null) {
                itemLabel.setText(item.name);
                itemLabel.setColor(ItemDetailPanel.getQualityColor(item.qualityTier));
            } else {
                itemLabel.setText("—");
                itemLabel.setColor(Color.DARK_GRAY);
            }
        }
    }

    private void buildLayout() {
        defaults().pad(SLOT_PAD);

        // Row 1: Helmet centered
        add().width(SLOT_SIZE);
        add(makeSlotCell(EquipmentSlot.HELMET)).size(SLOT_SIZE);
        add().width(SLOT_SIZE);
        row();

        // Row 2: Primary | Chest | Secondary
        add(makeSlotCell(EquipmentSlot.PRIMARY_WEAPON)).size(SLOT_SIZE);
        add(makeSlotCell(EquipmentSlot.CHEST)).size(SLOT_SIZE);
        add(makeSlotCell(EquipmentSlot.SECONDARY_WEAPON)).size(SLOT_SIZE);
        row();

        // Row 3: Utility1 | Legs | Utility2
        add(makeSlotCell(EquipmentSlot.UTILITY_1)).size(SLOT_SIZE);
        add(makeSlotCell(EquipmentSlot.LEGS)).size(SLOT_SIZE);
        add(makeSlotCell(EquipmentSlot.UTILITY_2)).size(SLOT_SIZE);
        row();

        // Row 4: Melee | Boots | (empty)
        add(makeSlotCell(EquipmentSlot.MELEE_WEAPON)).size(SLOT_SIZE);
        add(makeSlotCell(EquipmentSlot.BOOTS)).size(SLOT_SIZE);
        add().width(SLOT_SIZE);
        row();
    }

    private Table makeSlotCell(EquipmentSlot slot) {
        Table cell = new Table();
        cell.setBackground(new TextureRegionDrawable(new TextureRegion(emptySlotTexture)));

        Label nameLabel = new Label(slotLabel(slot), skin, "body");
        nameLabel.setColor(Color.LIGHT_GRAY);
        nameLabel.setFontScale(0.7f);

        Label itemLabel = new Label("—", skin, "body");
        itemLabel.setColor(Color.DARK_GRAY);
        itemLabel.setFontScale(0.65f);

        cell.add(nameLabel).center().row();
        cell.add(itemLabel).center();

        cell.addListener(new ClickListener(com.badlogic.gdx.Input.Buttons.RIGHT) {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (onSlotRightClicked != null) {
                    onSlotRightClicked.accept(slot, null);
                }
            }
        });

        slotCells.put(slot, cell);
        slotNameLabels.put(slot, nameLabel);
        slotItemLabels.put(slot, itemLabel);

        return cell;
    }

    public static EquipmentSlot getMatchingSlot(Item item) {
        if (item instanceof ArmorItem) {
            return ((ArmorItem) item).slotType;
        } else if (item instanceof WeaponItem) {
            return EquipmentSlot.PRIMARY_WEAPON;
        } else if (item instanceof MeleeWeaponItem) {
            return EquipmentSlot.MELEE_WEAPON;
        } else if (item instanceof ConsumableItem) {
            return EquipmentSlot.UTILITY_1;
        }
        return null;
    }

    public static String slotLabel(EquipmentSlot slot) {
        switch (slot) {
            case PRIMARY_WEAPON:   return "Primary";
            case SECONDARY_WEAPON: return "Secondary";
            case MELEE_WEAPON:     return "Melee";
            case HELMET:           return "Helmet";
            case CHEST:            return "Chest";
            case LEGS:             return "Legs";
            case BOOTS:            return "Boots";
            case UTILITY_1:        return "Utility 1";
            case UTILITY_2:        return "Utility 2";
            default:               return slot.name();
        }
    }

    public Map<EquipmentSlot, Table> getSlotCells() {
        return slotCells;
    }

    @Override
    public void dispose() {
        if (emptySlotTexture != null) emptySlotTexture.dispose();
    }
}
