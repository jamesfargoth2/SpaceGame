package com.galacticodyssey.ui.actors;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.items.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemDetailPanelTest {

    @Test
    void qualityColorMappingReturnsCorrectColors() {
        assertEquals(new com.badlogic.gdx.graphics.Color(0.7f, 0.7f, 0.7f, 1f),
            ItemDetailPanel.getQualityColor(QualityTier.SALVAGED));
        assertEquals(com.badlogic.gdx.graphics.Color.WHITE,
            ItemDetailPanel.getQualityColor(QualityTier.COMMON));
        assertEquals(new com.badlogic.gdx.graphics.Color(0.2f, 0.8f, 0.2f, 1f),
            ItemDetailPanel.getQualityColor(QualityTier.REFINED));
        assertEquals(new com.badlogic.gdx.graphics.Color(0.3f, 0.5f, 1f, 1f),
            ItemDetailPanel.getQualityColor(QualityTier.MILITARY));
        assertEquals(new com.badlogic.gdx.graphics.Color(0.7f, 0.3f, 1f, 1f),
            ItemDetailPanel.getQualityColor(QualityTier.EXPERIMENTAL));
        assertEquals(new com.badlogic.gdx.graphics.Color(1f, 0.5f, 0f, 1f),
            ItemDetailPanel.getQualityColor(QualityTier.ALIEN));
        assertEquals(new com.badlogic.gdx.graphics.Color(1f, 0.84f, 0f, 1f),
            ItemDetailPanel.getQualityColor(QualityTier.PRECURSOR));
    }

    @Test
    void buildStatLinesForArmorItem() {
        var resistances = new java.util.EnumMap<com.galacticodyssey.combat.CombatEnums.DamageType, Float>(
            com.galacticodyssey.combat.CombatEnums.DamageType.class);
        resistances.put(com.galacticodyssey.combat.CombatEnums.DamageType.BALLISTIC, 0.2f);
        var armor = new com.galacticodyssey.equipment.items.ArmorItem(
            "test_chest", "Test Vest", "A test vest.", "icon_vest",
            QualityTier.MILITARY, 2, 2, 3.0f, 25f, resistances,
            com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot.CHEST, 100f);

        var lines = ItemDetailPanel.buildStatLines(armor);
        assertTrue(lines.stream().anyMatch(l -> l.contains("Armor")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Durability")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("BALLISTIC")));
    }

    @Test
    void buildStatLinesForConsumableItem() {
        var item = new com.galacticodyssey.equipment.items.ConsumableItem(
            "medkit", "Medkit", "Heals 50 HP.", "icon_medkit",
            QualityTier.COMMON, 0.5f, 50f, "", 1.5f, 5);

        var lines = ItemDetailPanel.buildStatLines(item);
        assertTrue(lines.stream().anyMatch(l -> l.contains("Heal")));
    }
}
