package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
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
import com.galacticodyssey.ui.ManagedScreen;
import com.galacticodyssey.ui.actors.EquipmentSlotsActor;
import com.galacticodyssey.ui.actors.InventoryGridActor;
import com.galacticodyssey.ui.actors.ItemDetailPanel;
import com.galacticodyssey.ui.events.InventoryClosedEvent;
import com.galacticodyssey.ui.events.InventoryOpenedEvent;

/** Main controller for the inventory overlay screen.
 *
 *  <p>The constructor is safe to call without a GL context (for unit tests). All GL-dependent
 *  work is deferred to {@link #initialize(Engine, EquipmentSystem)}, which must only be called
 *  once a valid GL context exists.</p>
 *
 *  <p>Call {@link #toggle()} to open or close the screen. Opening publishes
 *  {@link InventoryOpenedEvent}; closing publishes {@link InventoryClosedEvent}.</p> */
public class InventoryScreenSystem implements ManagedScreen {

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

    /** Safe to construct without a GL context. */
    public InventoryScreenSystem(EventBus eventBus, Skin skin) {
        this.eventBus = eventBus;
        this.skin = skin;

        eventBus.subscribe(EquipmentChangedEvent.class, this::onEquipmentChanged);
    }

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    @Override
    public String getDisplayName() { return "Inventory"; }

    @Override
    public boolean isOpen() { return open; }

    /** Toggles between open and closed. */
    public void toggle() {
        if (open) close(); else open();
    }

    @Override
    public void open() {
        if (open) return;
        open = true;
        eventBus.publish(new InventoryOpenedEvent());
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        eventBus.publish(new InventoryClosedEvent());
    }

    // -------------------------------------------------------------------------
    // GL initialisation (call once, after GL context exists)
    // -------------------------------------------------------------------------

    /** Builds the Scene2D stage, overlay, and all child actors.
     *  Must only be called with a valid GL context. */
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

        // Left grid (columns 0–4)
        leftGrid = new InventoryGridActor(skin, 0, 5);
        leftGrid.initialize();

        // Centre equipment slots
        equipmentSlots = new EquipmentSlotsActor(skin);
        equipmentSlots.initialize();

        // Right grid (columns 5–9)
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

    @Override
    public Stage getStage() { return stage; }

    // -------------------------------------------------------------------------
    // Per-frame update
    // -------------------------------------------------------------------------

    @Override
    public void render(float delta) {
        if (!open || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    // -------------------------------------------------------------------------
    // Content refresh
    // -------------------------------------------------------------------------

    /** Repopulates grids, equipment slots, and weight label from the player entity. */
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

            float ratio = inv.maxWeight > 0 ? inv.getCurrentWeight() / inv.maxWeight : 0f;
            if (ratio > 0.85f)      weightLabel.setColor(Color.RED);
            else if (ratio > 0.6f)  weightLabel.setColor(Color.YELLOW);
            else                    weightLabel.setColor(Color.WHITE);
        }

        if (equip != null) {
            equipmentSlots.refresh(equip);
        }
    }

    // -------------------------------------------------------------------------
    // Interaction callbacks
    // -------------------------------------------------------------------------

    private void onItemSelected(Item item) {
        detailPanel.showItem(item);
    }

    private void onItemHovered(Item item) {
        detailPanel.showItem(item);
    }

    /** Right-click on an inventory item: equip it into its matching slot. */
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

    /** Right-click on an equipment slot: unequip the item back to inventory. */
    private void onSlotRightClicked(EquipmentSlot slot, Item item) {
        Entity player = getPlayerEntity();
        if (player == null || equipmentSystem == null) return;

        equipmentSystem.unequip(player, slot);
        refreshAll();
    }

    private void onEquipmentChanged(EquipmentChangedEvent event) {
        if (open) refreshAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Entity getPlayerEntity() {
        if (engine == null) return null;
        ImmutableArray<Entity> players = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class).get());
        return players.size() > 0 ? players.first() : null;
    }

    // -------------------------------------------------------------------------
    // Disposable
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        if (stage != null)         { stage.dispose();          stage = null; }
        if (overlayTexture != null){ overlayTexture.dispose();  overlayTexture = null; }
        if (leftGrid != null)      { leftGrid.dispose();        leftGrid = null; }
        if (rightGrid != null)     { rightGrid.dispose();       rightGrid = null; }
        if (equipmentSlots != null){ equipmentSlots.dispose();  equipmentSlots = null; }
    }
}
