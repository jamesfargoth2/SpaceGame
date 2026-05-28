package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.items.AmmoItem;
import com.galacticodyssey.equipment.items.ArmorItem;
import com.galacticodyssey.equipment.items.ConsumableItem;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.items.MeleeWeaponItem;
import com.galacticodyssey.equipment.items.WeaponItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemDetailPanel extends Table {

    private final Skin skin;
    private Label nameLabel;
    private Label typeLabel;
    private Label descriptionLabel;
    private Label weightLabel;
    private final List<Label> statLabels = new ArrayList<>();

    public ItemDetailPanel(Skin skin) {
        this.skin = skin;
        pad(12);
        defaults().left();
    }

    public void initialize() {
        nameLabel = new Label("", skin, "header");
        typeLabel = new Label("", skin, "body");
        descriptionLabel = new Label("", skin, "body");
        descriptionLabel.setWrap(true);
        weightLabel = new Label("", skin, "body");

        clear();
    }

    public void showItem(Item item) {
        if (item == null) {
            clearItem();
            return;
        }

        clear();
        statLabels.clear();

        Color qualityColor = getQualityColor(item.qualityTier);

        nameLabel.setText(item.name);
        nameLabel.setColor(qualityColor);
        add(nameLabel).left().padBottom(4).row();

        typeLabel.setText(item.getType().name() + "  |  " + item.qualityTier.name());
        typeLabel.setColor(Color.LIGHT_GRAY);
        add(typeLabel).left().padBottom(8).row();

        List<String> lines = buildStatLines(item);
        for (String line : lines) {
            Label statLabel = new Label(line, skin, "body");
            statLabels.add(statLabel);
            add(statLabel).left().padBottom(2).row();
        }

        weightLabel.setText(String.format("Weight: %.1f kg", item.getTotalWeight()));
        add(weightLabel).left().padTop(8).padBottom(4).row();

        if (item.description != null && !item.description.isEmpty()) {
            descriptionLabel.setText(item.description);
            add(descriptionLabel).width(500).left().padTop(4).row();
        }
    }

    public void clearItem() {
        clear();
        statLabels.clear();
    }

    public static Color getQualityColor(QualityTier tier) {
        switch (tier) {
            case SALVAGED:      return new Color(0.7f, 0.7f, 0.7f, 1f);
            case COMMON:        return Color.WHITE;
            case REFINED:       return new Color(0.2f, 0.8f, 0.2f, 1f);
            case MILITARY:      return new Color(0.3f, 0.5f, 1f, 1f);
            case EXPERIMENTAL:  return new Color(0.7f, 0.3f, 1f, 1f);
            case ALIEN:         return new Color(1f, 0.5f, 0f, 1f);
            case PRECURSOR:     return new Color(1f, 0.84f, 0f, 1f);
            default:            return Color.WHITE;
        }
    }

    public static List<String> buildStatLines(Item item) {
        List<String> lines = new ArrayList<>();
        if (item instanceof ArmorItem) {
            ArmorItem armor = (ArmorItem) item;
            lines.add(String.format("Armor: %.0f", armor.armorRating));
            lines.add(String.format("Durability: %.0f / %.0f", armor.durability, armor.maxDurability));
            for (Map.Entry<DamageType, Float> e : armor.resistances.entrySet()) {
                lines.add(String.format("  %s Resist: %.0f%%", e.getKey().name(), e.getValue() * 100f));
            }
        } else if (item instanceof WeaponItem) {
            WeaponItem weapon = (WeaponItem) item;
            lines.add("Frame: " + weapon.assembly.frameId);
            if (weapon.assembly.barrelId != null) {
                lines.add("Barrel: " + weapon.assembly.barrelId);
            }
            if (weapon.assembly.ammoTypeId != null) {
                lines.add("Ammo: " + weapon.assembly.ammoTypeId);
            }
        } else if (item instanceof MeleeWeaponItem) {
            MeleeWeaponItem melee = (MeleeWeaponItem) item;
            lines.add("Frame: " + melee.assembly.frameId);
        } else if (item instanceof ConsumableItem) {
            ConsumableItem consumable = (ConsumableItem) item;
            if (consumable.healAmount > 0) {
                lines.add(String.format("Heal: %.0f HP", consumable.healAmount));
            }
            if (consumable.buffEffect != null && !consumable.buffEffect.isEmpty()) {
                lines.add("Effect: " + consumable.buffEffect);
            }
            lines.add(String.format("Use Time: %.1fs", consumable.useTime));
        } else if (item instanceof AmmoItem) {
            AmmoItem ammo = (AmmoItem) item;
            lines.add("Ammo Type: " + ammo.ammoTypeId);
        }
        if (item.stackable) {
            lines.add(String.format("Stack: %d / %d", item.currentStack, item.maxStack));
        }
        lines.add(String.format("Grid Size: %dx%d", item.gridWidth, item.gridHeight));
        return lines;
    }
}
