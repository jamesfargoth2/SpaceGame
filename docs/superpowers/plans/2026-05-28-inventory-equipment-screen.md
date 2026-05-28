# Inventory / Equipment Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real-time inventory/equipment overlay toggled by TAB, with drag-and-drop + right-click equipping, using Scene2D.

**Architecture:** The system follows the existing DialogHudSystem overlay pattern — an `InventoryScreenSystem` owns a Scene2D Stage with a FitViewport, manages open/close state via EventBus, and integrates with GameScreen's InputMultiplexer. The UI is composed of custom Scene2D actors: `InventoryGridActor` (split left/right), `EquipmentSlotsActor` (center body layout), `ItemDetailPanel` (bottom), and `DraggedItemActor` (cursor-following drag visual). Item movement uses Scene2D's built-in `DragAndDrop` utility.

**Tech Stack:** Java 17, libGDX Scene2D, Ashley ECS, JUnit 5 + Mockito

---

### Task 1: Events — InventoryOpenedEvent and InventoryClosedEvent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/events/InventoryOpenedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/events/InventoryClosedEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/ui/events/InventoryEventsTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ui.events;

import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class InventoryEventsTest {

    @Test
    void inventoryOpenedEventPublishesAndReceives() {
        EventBus bus = new EventBus();
        AtomicBoolean received = new AtomicBoolean(false);
        bus.subscribe(InventoryOpenedEvent.class, e -> received.set(true));
        bus.publish(new InventoryOpenedEvent());
        assertTrue(received.get());
    }

    @Test
    void inventoryClosedEventPublishesAndReceives() {
        EventBus bus = new EventBus();
        AtomicBoolean received = new AtomicBoolean(false);
        bus.subscribe(InventoryClosedEvent.class, e -> received.set(true));
        bus.publish(new InventoryClosedEvent());
        assertTrue(received.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.events.InventoryEventsTest" --info`
Expected: Compilation failure — classes don't exist yet.

- [ ] **Step 3: Create InventoryOpenedEvent**

```java
package com.galacticodyssey.ui.events;

public final class InventoryOpenedEvent {
}
```

- [ ] **Step 4: Create InventoryClosedEvent**

```java
package com.galacticodyssey.ui.events;

public final class InventoryClosedEvent {
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.events.InventoryEventsTest" --info`
Expected: PASS — both tests green.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/events/InventoryOpenedEvent.java \
       core/src/main/java/com/galacticodyssey/ui/events/InventoryClosedEvent.java \
       core/src/test/java/com/galacticodyssey/ui/events/InventoryEventsTest.java
git commit -m "feat(inventory): add InventoryOpenedEvent and InventoryClosedEvent"
```

---

### Task 2: ItemDetailPanel — bottom info display

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/ItemDetailPanel.java`
- Test: `core/src/test/java/com/galacticodyssey/ui/actors/ItemDetailPanelTest.java`

- [ ] **Step 1: Write the test**

The test validates the panel's data-binding logic without requiring GL. We test the public methods return correct state.

```java
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
        resistances.put(com.galacticodyssey.combat.CombatEnums.DamageType.KINETIC, 0.2f);
        var armor = new com.galacticodyssey.equipment.items.ArmorItem(
            "test_chest", "Test Vest", "A test vest.", "icon_vest",
            QualityTier.MILITARY, 2, 2, 3.0f, 25f, resistances,
            com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot.CHEST, 100f);

        var lines = ItemDetailPanel.buildStatLines(armor);
        assertTrue(lines.stream().anyMatch(l -> l.contains("Armor")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Durability")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("KINETIC")));
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.actors.ItemDetailPanelTest" --info`
Expected: Compilation failure — ItemDetailPanel doesn't exist.

- [ ] **Step 3: Create ItemDetailPanel**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.items.*;

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
        if (item instanceof ArmorItem armor) {
            lines.add(String.format("Armor: %.0f", armor.armorRating));
            lines.add(String.format("Durability: %.0f / %.0f", armor.durability, armor.maxDurability));
            for (Map.Entry<DamageType, Float> e : armor.resistances.entrySet()) {
                lines.add(String.format("  %s Resist: %.0f%%", e.getKey().name(), e.getValue() * 100f));
            }
        } else if (item instanceof WeaponItem weapon) {
            lines.add("Frame: " + weapon.assembly.frameId);
            if (weapon.assembly.barrelId != null) {
                lines.add("Barrel: " + weapon.assembly.barrelId);
            }
            if (weapon.assembly.ammoTypeId != null) {
                lines.add("Ammo: " + weapon.assembly.ammoTypeId);
            }
        } else if (item instanceof MeleeWeaponItem melee) {
            lines.add("Frame: " + melee.assembly.frameId);
        } else if (item instanceof ConsumableItem consumable) {
            if (consumable.healAmount > 0) {
                lines.add(String.format("Heal: %.0f HP", consumable.healAmount));
            }
            if (consumable.buffEffect != null && !consumable.buffEffect.isEmpty()) {
                lines.add("Effect: " + consumable.buffEffect);
            }
            lines.add(String.format("Use Time: %.1fs", consumable.useTime));
        } else if (item instanceof AmmoItem ammo) {
            lines.add("Ammo Type: " + ammo.ammoTypeId);
        }
        if (item.stackable) {
            lines.add(String.format("Stack: %d / %d", item.currentStack, item.maxStack));
        }
        lines.add(String.format("Grid Size: %dx%d", item.gridWidth, item.gridHeight));
        return lines;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.actors.ItemDetailPanelTest" --info`
Expected: PASS — all tests green.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/actors/ItemDetailPanel.java \
       core/src/test/java/com/galacticodyssey/ui/actors/ItemDetailPanelTest.java
git commit -m "feat(inventory): add ItemDetailPanel with quality colors and stat lines"
```

---

### Task 3: EquipmentSlotsActor — center equipment layout

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/EquipmentSlotsActor.java`
- Test: `core/src/test/java/com/galacticodyssey/ui/actors/EquipmentSlotsActorTest.java`

- [ ] **Step 1: Write the test**

Tests the slot-matching logic (which item types are valid for which slots) without GL context.

```java
package com.galacticodyssey.ui.actors;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSlotsActorTest {

    @Test
    void getMatchingSlotForArmorItemReturnsCorrectSlot() {
        var helmet = new ArmorItem("h1", "Helmet", "", "", QualityTier.COMMON,
            1, 1, 1f, 10f, new java.util.EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.HELMET, 50f);
        assertEquals(EquipmentSlot.HELMET, EquipmentSlotsActor.getMatchingSlot(helmet));

        var chest = new ArmorItem("c1", "Vest", "", "", QualityTier.COMMON,
            2, 2, 3f, 20f, new java.util.EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.CHEST, 80f);
        assertEquals(EquipmentSlot.CHEST, EquipmentSlotsActor.getMatchingSlot(chest));
    }

    @Test
    void getMatchingSlotForWeaponReturnsPrimaryWeapon() {
        var weapon = new WeaponItem("w1", "Rifle", "", "", QualityTier.COMMON,
            3, 1, 4f,
            new com.galacticodyssey.combat.data.WeaponAssembly("frame1", null, null, new String[0], QualityTier.COMMON, false));
        assertEquals(EquipmentSlot.PRIMARY_WEAPON, EquipmentSlotsActor.getMatchingSlot(weapon));
    }

    @Test
    void getMatchingSlotForConsumableReturnsUtility1() {
        var item = new ConsumableItem("m1", "Medkit", "", "", QualityTier.COMMON,
            0.5f, 50f, "", 1f, 3);
        assertEquals(EquipmentSlot.UTILITY_1, EquipmentSlotsActor.getMatchingSlot(item));
    }

    @Test
    void getMatchingSlotForNonEquippableReturnsNull() {
        var junk = new JunkItem("j1", "Scrap", "", "", QualityTier.SALVAGED,
            1f, 5, new java.util.HashMap<>());
        assertNull(EquipmentSlotsActor.getMatchingSlot(junk));
    }

    @Test
    void slotLabelReturnsHumanReadableName() {
        assertEquals("Helmet", EquipmentSlotsActor.slotLabel(EquipmentSlot.HELMET));
        assertEquals("Primary", EquipmentSlotsActor.slotLabel(EquipmentSlot.PRIMARY_WEAPON));
        assertEquals("Utility 1", EquipmentSlotsActor.slotLabel(EquipmentSlot.UTILITY_1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.actors.EquipmentSlotsActorTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create EquipmentSlotsActor**

```java
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
            Table cell = slotCells.get(slot);
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
        if (item instanceof ArmorItem armor) {
            return armor.slotType;
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.actors.EquipmentSlotsActorTest" --info`
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/actors/EquipmentSlotsActor.java \
       core/src/test/java/com/galacticodyssey/ui/actors/EquipmentSlotsActorTest.java
git commit -m "feat(inventory): add EquipmentSlotsActor with body-layout and slot matching"
```

---

### Task 4: InventoryGridActor — the item grid

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/InventoryGridActor.java`
- Test: `core/src/test/java/com/galacticodyssey/ui/actors/InventoryGridActorTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ui.actors;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.items.ConsumableItem;
import com.galacticodyssey.equipment.items.Item;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryGridActorTest {

    @Test
    void cellIndexConversion() {
        assertEquals(0, InventoryGridActor.cellIndex(0, 0, 10));
        assertEquals(15, InventoryGridActor.cellIndex(5, 1, 10));
        assertEquals(9, InventoryGridActor.cellIndex(9, 0, 10));
    }

    @Test
    void gridCoordsFromCellIndex() {
        int[] coords = InventoryGridActor.cellCoords(15, 10);
        assertEquals(5, coords[0]);
        assertEquals(1, coords[1]);
    }

    @Test
    void isLeftPanel() {
        assertTrue(InventoryGridActor.isLeftPanel(0, 10));
        assertTrue(InventoryGridActor.isLeftPanel(4, 10));
        assertFalse(InventoryGridActor.isLeftPanel(5, 10));
        assertFalse(InventoryGridActor.isLeftPanel(9, 10));
    }

    @Test
    void buildCellDataFromInventory() {
        InventoryComponent inv = new InventoryComponent(10, 6, 100f);
        Item medkit = new ConsumableItem("medkit", "Medkit", "", "", QualityTier.COMMON,
            0.5f, 50f, "", 1f, 5);
        inv.tryAdd(medkit);

        Item found = inv.getItemAt(0, 0);
        assertNotNull(found);
        assertEquals("medkit", found.id);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.actors.InventoryGridActorTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create InventoryGridActor**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.items.Item;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class InventoryGridActor extends Table implements Disposable {

    public static final float CELL_SIZE = 56f;
    private static final float CELL_PAD = 2f;

    private final Skin skin;
    private final int startCol;
    private final int endCol;
    private Texture emptyCellTexture;
    private final Map<Integer, Table> cellTables = new java.util.HashMap<>();
    private final Map<Integer, Label> cellLabels = new java.util.HashMap<>();
    private Consumer<Item> onItemClicked;
    private Consumer<Item> onItemRightClicked;
    private Consumer<Item> onItemHovered;

    public InventoryGridActor(Skin skin, int startCol, int endCol) {
        this.skin = skin;
        this.startCol = startCol;
        this.endCol = endCol;
    }

    public void initialize() {
        Pixmap pix = new Pixmap((int) CELL_SIZE, (int) CELL_SIZE, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0.12f, 0.12f, 0.15f, 0.7f));
        pix.fill();
        pix.setColor(new Color(0.25f, 0.25f, 0.3f, 1f));
        pix.drawRectangle(0, 0, (int) CELL_SIZE, (int) CELL_SIZE);
        emptyCellTexture = new Texture(pix);
        pix.dispose();
    }

    public void setOnItemClicked(Consumer<Item> callback) { this.onItemClicked = callback; }
    public void setOnItemRightClicked(Consumer<Item> callback) { this.onItemRightClicked = callback; }
    public void setOnItemHovered(Consumer<Item> callback) { this.onItemHovered = callback; }

    public void refresh(InventoryComponent inv) {
        clear();
        cellTables.clear();
        cellLabels.clear();

        int cols = endCol - startCol;
        IdentityHashMap<Item, int[]> anchors = new IdentityHashMap<>();
        for (int x = 0; x < inv.gridWidth; x++) {
            for (int y = 0; y < inv.gridHeight; y++) {
                Item item = inv.getItemAt(x, y);
                if (item != null && !anchors.containsKey(item)) {
                    anchors.put(item, new int[]{x, y});
                }
            }
        }

        for (int row = 0; row < inv.gridHeight; row++) {
            for (int col = startCol; col < endCol; col++) {
                int idx = cellIndex(col, row, inv.gridWidth);
                Item item = inv.getItemAt(col, row);
                int[] anchor = item != null ? anchors.get(item) : null;
                boolean isAnchor = anchor != null && anchor[0] == col && anchor[1] == row;

                Table cell = new Table();
                cell.setBackground(new TextureRegionDrawable(new TextureRegion(emptyCellTexture)));

                Label label = new Label("", skin, "body");
                label.setFontScale(0.55f);

                if (item != null && isAnchor) {
                    label.setText(item.name.length() > 7 ? item.name.substring(0, 7) : item.name);
                    label.setColor(ItemDetailPanel.getQualityColor(item.qualityTier));
                    if (item.stackable && item.currentStack > 1) {
                        Label stackLabel = new Label("x" + item.currentStack, skin, "body");
                        stackLabel.setFontScale(0.5f);
                        stackLabel.setColor(Color.LIGHT_GRAY);
                        cell.add(label).center().row();
                        cell.add(stackLabel).right();
                    } else {
                        cell.add(label).center();
                    }

                    final Item capturedItem = item;
                    cell.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            if (onItemClicked != null) onItemClicked.accept(capturedItem);
                        }
                        @Override
                        public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                            super.enter(event, x, y, pointer, fromActor);
                            if (pointer == -1 && onItemHovered != null) onItemHovered.accept(capturedItem);
                        }
                    });
                    cell.addListener(new ClickListener(com.badlogic.gdx.Input.Buttons.RIGHT) {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            if (onItemRightClicked != null) onItemRightClicked.accept(capturedItem);
                        }
                    });
                } else if (item != null) {
                    cell.setColor(new Color(0.15f, 0.15f, 0.18f, 0.5f));
                }

                cellTables.put(idx, cell);
                cellLabels.put(idx, label);
                add(cell).size(CELL_SIZE).pad(CELL_PAD);
            }
            row();
        }
    }

    public static int cellIndex(int x, int y, int gridWidth) {
        return y * gridWidth + x;
    }

    public static int[] cellCoords(int index, int gridWidth) {
        return new int[]{index % gridWidth, index / gridWidth};
    }

    public static boolean isLeftPanel(int col, int gridWidth) {
        return col < gridWidth / 2;
    }

    @Override
    public void dispose() {
        if (emptyCellTexture != null) emptyCellTexture.dispose();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.actors.InventoryGridActorTest" --info`
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/actors/InventoryGridActor.java \
       core/src/test/java/com/galacticodyssey/ui/actors/InventoryGridActorTest.java
git commit -m "feat(inventory): add InventoryGridActor with split-panel grid rendering"
```

---

### Task 5: InventoryScreenSystem — main controller with overlay, DnD, and input

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/systems/InventoryScreenSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ui/systems/InventoryScreenSystemTest.java`

- [ ] **Step 1: Write the test**

Tests the toggle state machine and event publishing logic without GL.

```java
package com.galacticodyssey.ui.systems;

import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ui.events.InventoryClosedEvent;
import com.galacticodyssey.ui.events.InventoryOpenedEvent;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InventoryScreenSystemTest {

    @Test
    void togglePublishesOpenAndCloseEvents() {
        EventBus bus = new EventBus();
        AtomicInteger openCount = new AtomicInteger(0);
        AtomicInteger closeCount = new AtomicInteger(0);
        bus.subscribe(InventoryOpenedEvent.class, e -> openCount.incrementAndGet());
        bus.subscribe(InventoryClosedEvent.class, e -> closeCount.incrementAndGet());

        InventoryScreenSystem system = new InventoryScreenSystem(bus, null);

        assertFalse(system.isOpen());

        system.toggle();
        assertTrue(system.isOpen());
        assertEquals(1, openCount.get());
        assertEquals(0, closeCount.get());

        system.toggle();
        assertFalse(system.isOpen());
        assertEquals(1, openCount.get());
        assertEquals(1, closeCount.get());
    }

    @Test
    void doubleOpenDoesNotPublishTwice() {
        EventBus bus = new EventBus();
        AtomicInteger openCount = new AtomicInteger(0);
        bus.subscribe(InventoryOpenedEvent.class, e -> openCount.incrementAndGet());

        InventoryScreenSystem system = new InventoryScreenSystem(bus, null);
        system.open();
        system.open();
        assertEquals(1, openCount.get());
    }

    @Test
    void closeWhenAlreadyClosedDoesNotPublish() {
        EventBus bus = new EventBus();
        AtomicInteger closeCount = new AtomicInteger(0);
        bus.subscribe(InventoryClosedEvent.class, e -> closeCount.incrementAndGet());

        InventoryScreenSystem system = new InventoryScreenSystem(bus, null);
        system.close();
        assertEquals(0, closeCount.get());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.systems.InventoryScreenSystemTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Create InventoryScreenSystem**

```java
package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.ui.actors.EquipmentSlotsActor;
import com.galacticodyssey.ui.actors.InventoryGridActor;
import com.galacticodyssey.ui.actors.ItemDetailPanel;
import com.galacticodyssey.ui.events.InventoryClosedEvent;
import com.galacticodyssey.ui.events.InventoryOpenedEvent;

public class InventoryScreenSystem implements Disposable {

    private final EventBus eventBus;
    private final Skin skin;
    private boolean open;

    private Stage stage;
    private Texture overlayTexture;
    private InventoryGridActor leftGrid;
    private InventoryGridActor rightGrid;
    private EquipmentSlotsActor equipmentSlots;
    private ItemDetailPanel detailPanel;
    private Label titleLabel;
    private Label weightLabel;

    private Engine engine;
    private EquipmentSystem equipmentSystem;
    private DragAndDrop dragAndDrop;

    public InventoryScreenSystem(EventBus eventBus, Skin skin) {
        this.eventBus = eventBus;
        this.skin = skin;

        eventBus.subscribe(EquipmentChangedEvent.class, this::onEquipmentChanged);
    }

    public boolean isOpen() { return open; }

    public void toggle() {
        if (open) close(); else open();
    }

    public void open() {
        if (open) return;
        open = true;
        eventBus.publish(new InventoryOpenedEvent());
    }

    public void close() {
        if (!open) return;
        open = false;
        eventBus.publish(new InventoryClosedEvent());
    }

    public void initialize(Engine engine, EquipmentSystem equipmentSystem) {
        this.engine = engine;
        this.equipmentSystem = equipmentSystem;

        stage = new Stage(new ScreenViewport());
        dragAndDrop = new DragAndDrop();

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0f, 0f, 0f, 0.6f));
        pix.fill();
        overlayTexture = new Texture(pix);
        pix.dispose();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        root.pad(20);

        // Top bar
        Table topBar = new Table();
        titleLabel = new Label("INVENTORY", skin, "title");
        weightLabel = new Label("Weight: 0 / 0 kg", skin, "body");
        topBar.add(titleLabel).expandX().left();
        topBar.add(weightLabel).right();
        root.add(topBar).fillX().padBottom(12).colspan(3).row();

        // Left grid (columns 0-4 of a 10-col grid)
        leftGrid = new InventoryGridActor(skin, 0, 5);
        leftGrid.initialize();

        // Center equipment
        equipmentSlots = new EquipmentSlotsActor(skin);
        equipmentSlots.initialize();

        // Right grid (columns 5-9)
        rightGrid = new InventoryGridActor(skin, 5, 10);
        rightGrid.initialize();

        root.add(leftGrid).top().expandY();
        root.add(equipmentSlots).center().padLeft(16).padRight(16);
        root.add(rightGrid).top().expandY();
        root.row();

        // Bottom detail panel
        detailPanel = new ItemDetailPanel(skin);
        detailPanel.initialize();
        root.add(detailPanel).colspan(3).fillX().padTop(12).bottom();

        stage.addActor(root);

        // Wire callbacks
        leftGrid.setOnItemClicked(this::onItemSelected);
        leftGrid.setOnItemRightClicked(this::onItemRightClicked);
        leftGrid.setOnItemHovered(this::onItemHovered);
        rightGrid.setOnItemClicked(this::onItemSelected);
        rightGrid.setOnItemRightClicked(this::onItemRightClicked);
        rightGrid.setOnItemHovered(this::onItemHovered);
        equipmentSlots.setOnSlotRightClicked(this::onSlotRightClicked);
    }

    public Stage getStage() { return stage; }

    public void render(float delta) {
        if (!open || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    public void refreshAll() {
        Entity player = getPlayerEntity();
        if (player == null) return;

        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        EquipmentSlotsComponent equip = player.getComponent(EquipmentSlotsComponent.class);
        if (inv != null) {
            leftGrid.refresh(inv);
            rightGrid.refresh(inv);
            weightLabel.setText(String.format("Weight: %.1f / %.0f kg",
                inv.getCurrentWeight(), inv.maxWeight));

            float ratio = inv.maxWeight > 0 ? inv.getCurrentWeight() / inv.maxWeight : 0;
            if (ratio > 0.85f) weightLabel.setColor(Color.RED);
            else if (ratio > 0.6f) weightLabel.setColor(Color.YELLOW);
            else weightLabel.setColor(Color.WHITE);
        }
        if (equip != null) {
            equipmentSlots.refresh(equip);
        }
    }

    private void onItemSelected(Item item) {
        detailPanel.showItem(item);
    }

    private void onItemHovered(Item item) {
        detailPanel.showItem(item);
    }

    private void onItemRightClicked(Item item) {
        Entity player = getPlayerEntity();
        if (player == null || equipmentSystem == null) return;

        EquipmentSlot slot = EquipmentSlotsActor.getMatchingSlot(item);
        if (slot == null) return;

        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        if (inv != null) inv.remove(item);
        equipmentSystem.equip(player, slot, item);
        refreshAll();
    }

    private void onSlotRightClicked(EquipmentSlot slot, Item item) {
        Entity player = getPlayerEntity();
        if (player == null || equipmentSystem == null) return;

        equipmentSystem.unequip(player, slot);
        refreshAll();
    }

    private void onEquipmentChanged(EquipmentChangedEvent event) {
        if (open) refreshAll();
    }

    private Entity getPlayerEntity() {
        if (engine == null) return null;
        ImmutableArray<Entity> players = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class).get());
        return players.size() > 0 ? players.first() : null;
    }

    @Override
    public void dispose() {
        if (stage != null) { stage.dispose(); stage = null; }
        if (overlayTexture != null) { overlayTexture.dispose(); overlayTexture = null; }
        if (leftGrid != null) { leftGrid.dispose(); leftGrid = null; }
        if (rightGrid != null) { rightGrid.dispose(); rightGrid = null; }
        if (equipmentSlots != null) { equipmentSlots.dispose(); equipmentSlots = null; }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.systems.InventoryScreenSystemTest" --info`
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/systems/InventoryScreenSystem.java \
       core/src/test/java/com/galacticodyssey/ui/systems/InventoryScreenSystemTest.java
git commit -m "feat(inventory): add InventoryScreenSystem with toggle, event publishing, and full UI"
```

---

### Task 6: Rebind TAB → inventory, T → target cycle

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java:149-156`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerInputTargetCycleTest {

    @Test
    void tKeyCodesMatchExpected() {
        // T key should handle both target lock and next-target
        assertEquals(48, Input.Keys.T);
        // TAB should no longer be in PlayerInputSystem
        assertEquals(61, Input.Keys.TAB);
    }
}
```

- [ ] **Step 2: Modify PlayerInputSystem to remove TAB binding and merge target-cycle onto T**

In `PlayerInputSystem.java`, replace lines 149-156 (the T and TAB key handlers):

**Old code (lines 149-156):**
```java
            if (keycode == Input.Keys.T) {
                targetLockPressed = true;
                return true;
            }
            if (keycode == Input.Keys.TAB) {
                nextTargetPressed = true;
                return true;
            }
```

**New code:**
```java
            if (keycode == Input.Keys.T) {
                targetLockPressed = true;
                nextTargetPressed = true;
                return true;
            }
```

This makes T dual-purpose: sets both `targetLockPressed` and `nextTargetPressed` each frame it's pressed. Downstream systems (CombatInputSystem, flight input) already consume these independently.

- [ ] **Step 3: Run existing tests**

Run: `./gradlew :core:test --info`
Expected: All existing tests still pass.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java \
       core/src/test/java/com/galacticodyssey/player/systems/PlayerInputTargetCycleTest.java
git commit -m "feat(inventory): rebind TAB from target-cycle, merge target-cycle onto T key"
```

---

### Task 7: Wire InventoryScreenSystem into GameScreen

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

This is the integration task — adds inventory screen construction, TAB input handling, InputMultiplexer management, rendering, resizing, and disposal.

- [ ] **Step 1: Add import and field**

At the top of GameScreen.java, add the import (after line 75, near the other system imports):

```java
import com.galacticodyssey.ui.systems.InventoryScreenSystem;
```

Add field after `hackingOverlay` field (after line 137):

```java
    private InventoryScreenSystem inventoryScreenSystem;
    private boolean inInventory;
```

- [ ] **Step 2: Build inventory system in initializeWorld()**

After `buildHackingSystem();` (line 252), add:

```java
        buildInventorySystem();
```

Add the method after `buildHackingSystem()` (after line 515):

```java
    private void buildInventorySystem() {
        EventBus eventBus = gameWorld.getEventBus();
        inventoryScreenSystem = new InventoryScreenSystem(eventBus, game.getSkin());
        inventoryScreenSystem.initialize(gameWorld.getEngine(), gameWorld.getEquipmentSystem());

        eventBus.subscribe(com.galacticodyssey.ui.events.InventoryOpenedEvent.class, event -> {
            inInventory = true;
            Gdx.input.setCursorCatched(false);
            gameWorld.getPlayerInputSystem().setEnabled(false);
            inputMultiplexer.clear();
            inputMultiplexer.addProcessor(new InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == Input.Keys.TAB || keycode == Input.Keys.ESCAPE) {
                        inventoryScreenSystem.close();
                        return true;
                    }
                    return false;
                }
            });
            inputMultiplexer.addProcessor(inventoryScreenSystem.getStage());
            inventoryScreenSystem.refreshAll();
        });

        eventBus.subscribe(com.galacticodyssey.ui.events.InventoryClosedEvent.class, event -> {
            inInventory = false;
            Gdx.input.setCursorCatched(true);
            gameWorld.getPlayerInputSystem().setEnabled(true);
            setupInput();
        });
    }
```

- [ ] **Step 3: Add TAB key to the escape handler in setupInput()**

Modify the `escapeHandler` in `setupInput()` (lines 261-269). Replace:

```java
        InputAdapter escapeHandler = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    togglePause();
                    return true;
                }
                return false;
            }
        };
```

With:

```java
        InputAdapter escapeHandler = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    togglePause();
                    return true;
                }
                if (keycode == Input.Keys.TAB && !paused && !inDialog && !inInventory) {
                    inventoryScreenSystem.toggle();
                    return true;
                }
                return false;
            }
        };
```

- [ ] **Step 4: Add rendering call**

In `render()`, after the hackingOverlay render block (after line 936), add:

```java
        if (inventoryScreenSystem != null) {
            inventoryScreenSystem.render(delta);
        }
```

- [ ] **Step 5: Add resize call**

In `resize()` (after line 1041), add:

```java
        if (inventoryScreenSystem != null) inventoryScreenSystem.resize(width, height);
```

- [ ] **Step 6: Add dispose call**

In `dispose()`, after the hackingOverlay disposal block (after line 1102), add:

```java
        if (inventoryScreenSystem != null) {
            inventoryScreenSystem.dispose();
            inventoryScreenSystem = null;
        }
```

- [ ] **Step 7: Expose EquipmentSystem from GameWorld**

Check if `GameWorld` has a `getEquipmentSystem()` method. If not, add one. In `GameWorld.java`, add:

```java
    public EquipmentSystem getEquipmentSystem() {
        return equipmentSystem;
    }
```

(The field `equipmentSystem` should already exist since it's added to the Engine.)

- [ ] **Step 8: Run full test suite**

Run: `./gradlew :core:test --info`
Expected: All tests pass.

- [ ] **Step 9: Commit**

```
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java \
       core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(inventory): wire InventoryScreenSystem into GameScreen with TAB toggle"
```

---

### Task 8: Integration test — full open/close/equip cycle

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/ui/InventoryScreenIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

```java
package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.ArmorItem;
import com.galacticodyssey.equipment.items.ConsumableItem;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.ui.events.InventoryClosedEvent;
import com.galacticodyssey.ui.events.InventoryOpenedEvent;
import com.galacticodyssey.ui.systems.InventoryScreenSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InventoryScreenIntegrationTest {

    private EventBus eventBus;
    private Engine engine;
    private EquipmentSystem equipmentSystem;
    private InventoryScreenSystem inventorySystem;
    private Entity player;

    @BeforeEach
    void setup() {
        eventBus = new EventBus();
        engine = new Engine();
        equipmentSystem = new EquipmentSystem(eventBus);
        engine.addSystem(equipmentSystem);

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new InventoryComponent(10, 6, 100f));
        player.add(new EquipmentSlotsComponent());
        engine.addEntity(player);

        inventorySystem = new InventoryScreenSystem(eventBus, null);
    }

    @Test
    void toggleCyclePublishesCorrectEvents() {
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        eventBus.subscribe(InventoryOpenedEvent.class, e -> opened.incrementAndGet());
        eventBus.subscribe(InventoryClosedEvent.class, e -> closed.incrementAndGet());

        inventorySystem.toggle();
        assertTrue(inventorySystem.isOpen());
        assertEquals(1, opened.get());

        inventorySystem.toggle();
        assertFalse(inventorySystem.isOpen());
        assertEquals(1, closed.get());
    }

    @Test
    void equipViaSystemMovesItemFromInventoryToSlot() {
        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        EquipmentSlotsComponent equip = player.getComponent(EquipmentSlotsComponent.class);

        ArmorItem helmet = new ArmorItem("helm1", "Iron Helmet", "", "", QualityTier.COMMON,
            1, 1, 2f, 15f, new EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.HELMET, 50f);
        assertTrue(inv.tryAdd(helmet));
        assertEquals(1, inv.getItemCount());

        inv.remove(helmet);
        assertTrue(equipmentSystem.equip(player, EquipmentSlot.HELMET, helmet));

        assertEquals(0, inv.getItemCount());
        assertSame(helmet, equip.getSlot(EquipmentSlot.HELMET));
    }

    @Test
    void unequipReturnsItemToInventory() {
        ArmorItem chest = new ArmorItem("chest1", "Vest", "", "", QualityTier.REFINED,
            2, 2, 5f, 25f, new EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.CHEST, 80f);
        equipmentSystem.equip(player, EquipmentSlot.CHEST, chest);

        EquipmentSlotsComponent equip = player.getComponent(EquipmentSlotsComponent.class);
        assertNotNull(equip.getSlot(EquipmentSlot.CHEST));

        equipmentSystem.unequip(player, EquipmentSlot.CHEST);
        assertNull(equip.getSlot(EquipmentSlot.CHEST));

        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        assertEquals(1, inv.getItemCount());
    }

    @Test
    void equipmentChangedEventFiresOnEquip() {
        AtomicInteger count = new AtomicInteger();
        eventBus.subscribe(EquipmentChangedEvent.class, e -> count.incrementAndGet());

        ConsumableItem medkit = new ConsumableItem("med1", "Medkit", "", "",
            QualityTier.COMMON, 0.5f, 50f, "", 1f, 3);
        equipmentSystem.equip(player, EquipmentSlot.UTILITY_1, medkit);
        assertEquals(1, count.get());
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ui.InventoryScreenIntegrationTest" --info`
Expected: PASS — all 4 tests green.

- [ ] **Step 3: Commit**

```
git add core/src/test/java/com/galacticodyssey/ui/InventoryScreenIntegrationTest.java
git commit -m "test(inventory): add integration test for inventory open/close/equip cycle"
```

---

### Task 9: Visual test — run the game and verify the inventory screen

**Files:** None (manual verification).

- [ ] **Step 1: Build and run**

Run: `./gradlew :desktop:run`

- [ ] **Step 2: Test TAB toggle**

Press TAB. Verify:
- Semi-transparent overlay appears over the game world
- Game world continues animating behind the overlay
- Cursor is visible (uncaught)
- Player cannot move (WASD disabled)
- "INVENTORY" title visible top-left
- Weight indicator visible top-right
- Left and right grid panels visible
- Center equipment slots visible with body layout (Helmet top, Chest middle, etc.)

Press TAB again. Verify:
- Overlay closes
- Cursor is caught again
- Player can move

Press ESC while inventory is open. Verify:
- Inventory closes (not the pause menu)

- [ ] **Step 3: Test right-click equip**

Give player test items (already present from world population or add test items in GameWorld creation). Open inventory, right-click an armor item. Verify:
- Item disappears from grid
- Appears in the matching equipment slot
- Weight stays the same (equipped items still count toward weight)

Right-click the equipped slot. Verify:
- Item returns to inventory grid
- Equipment slot shows "—" again

- [ ] **Step 4: Test item detail panel**

Hover over items in the grid. Verify:
- Bottom panel shows item name (colored by quality tier)
- Stats displayed correctly (armor rating, resistances, etc.)
- Weight shown

- [ ] **Step 5: Commit any fixes**

If any visual bugs are found, fix and commit with a descriptive message.
