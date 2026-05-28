package com.galacticodyssey.ui.outfitter;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.modules.OutfitterValidator;
import com.galacticodyssey.ship.modules.ShipModuleData;
import com.galacticodyssey.ship.modules.ShipModuleRegistry;
import com.galacticodyssey.ship.modules.ShipModuleSlot;
import com.galacticodyssey.ship.modules.components.ShipCargoComponent;
import com.galacticodyssey.ship.modules.components.ShipLoadoutComponent;
import com.galacticodyssey.ship.modules.events.ModuleInstalledEvent;
import com.galacticodyssey.ship.modules.events.ModuleUninstalledEvent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ui.ManagedScreen;
import com.galacticodyssey.ui.events.OutfitterClosedEvent;
import com.galacticodyssey.ui.events.OutfitterOpenedEvent;

import java.util.ArrayList;

/**
 * Main controller for the ship outfitter overlay screen.
 *
 * <p>The constructor is safe to call without a GL context (for unit tests). All GL-dependent
 * work is deferred to {@link #initialize(Engine)}, which must only be called once a valid
 * GL context exists.</p>
 *
 * <p>Call {@link #open(Entity, boolean)} to show the outfitter for a ship entity.
 * Opening publishes {@link OutfitterOpenedEvent}; closing publishes
 * {@link OutfitterClosedEvent}.</p>
 */
public class OutfitterScreenSystem implements ManagedScreen {

    private static final String DEFAULT_SHIP_CLASS = "corvette_scout";

    // -------------------------------------------------------------------------
    // Tabs
    // -------------------------------------------------------------------------

    private enum Tab { WEAPONS, MODULES, COSMETICS }

    // -------------------------------------------------------------------------
    // Dependencies (injected via constructor, no GL needed)
    // -------------------------------------------------------------------------

    private final EventBus eventBus;
    private final Skin skin;
    private final ShipModuleRegistry registry;

    // -------------------------------------------------------------------------
    // GL-dependent state (created in initialize)
    // -------------------------------------------------------------------------

    private Stage stage;
    private Texture overlayTexture;

    // Child actors
    private OutfitterBudgetBar budgetBar;
    private OutfitterDetailPanel detailPanel;
    private OutfitterInventoryPanel inventoryPanel;
    private ShipSilhouetteActor silhouetteActor;

    // Tab buttons
    private TextButton weaponsTabBtn;
    private TextButton modulesTabBtn;
    private TextButton cosmeticsTabBtn;
    private Label shipNameLabel;
    private Label cosmeticsPlaceholder;
    private Table cosmeticsContainer;

    // -------------------------------------------------------------------------
    // Runtime state
    // -------------------------------------------------------------------------

    private Engine engine;
    private boolean open;
    private Tab activeTab = Tab.MODULES;

    private Entity shipEntity;
    private boolean stationMode;
    private ShipLoadoutComponent loadout;
    private ShipHardpointComponent hardpoints;
    private ShipCargoComponent cargo;
    private ShipModuleRegistry.SlotLayout slotLayout;

    private String selectedSlotId;
    private ShipModuleData selectedCandidate;

    // -------------------------------------------------------------------------
    // Constructor (GL-safe)
    // -------------------------------------------------------------------------

    /** Safe to construct without a GL context. */
    public OutfitterScreenSystem(EventBus eventBus, Skin skin, ShipModuleRegistry registry) {
        this.eventBus = eventBus;
        this.skin = skin;
        this.registry = registry;
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    @Override
    public String getDisplayName() { return "Outfitter"; }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public Stage getStage() { return stage; }

    // -------------------------------------------------------------------------
    // GL initialisation (call once, after GL context exists)
    // -------------------------------------------------------------------------

    /**
     * Builds the Scene2D stage, overlay, and all child actors.
     * Must only be called with a valid GL context.
     */
    public void initialize(Engine engine) {
        this.engine = engine;

        stage = new Stage(new ScreenViewport());

        // Semi-transparent overlay background
        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0f, 0f, 0f, 0.7f));
        pix.fill();
        overlayTexture = new Texture(pix);
        pix.dispose();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        root.pad(20);

        // ----- Top row: category tabs + ship name -----
        Table topBar = new Table();
        weaponsTabBtn = new TextButton("WEAPONS", skin);
        modulesTabBtn = new TextButton("MODULES", skin);
        cosmeticsTabBtn = new TextButton("COSMETICS", skin);
        shipNameLabel = new Label("Ship Outfitter", skin, "title");

        weaponsTabBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { switchTab(Tab.WEAPONS); }
        });
        modulesTabBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { switchTab(Tab.MODULES); }
        });
        cosmeticsTabBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { switchTab(Tab.COSMETICS); }
        });

        topBar.add(weaponsTabBtn).padRight(6);
        topBar.add(modulesTabBtn).padRight(6);
        topBar.add(cosmeticsTabBtn).padRight(16);
        topBar.add(shipNameLabel).expandX().right();
        root.add(topBar).fillX().padBottom(12).colspan(3).row();

        // ----- Middle row: 3-column split -----

        // Left column: inventory panel
        inventoryPanel = new OutfitterInventoryPanel(skin);
        inventoryPanel.initialize(false);

        // Centre column: ship silhouette
        silhouetteActor = new ShipSilhouetteActor();
        silhouetteActor.initialize();

        // Right column: detail panel
        detailPanel = new OutfitterDetailPanel(skin);
        detailPanel.initialize();

        // Cosmetics placeholder (shown only on COSMETICS tab)
        cosmeticsContainer = new Table();
        cosmeticsPlaceholder = new Label("Cosmetics coming soon...", skin);
        cosmeticsPlaceholder.setColor(Color.GRAY);
        cosmeticsContainer.add(cosmeticsPlaceholder).center().expand();

        root.add(inventoryPanel).width(220).fillY().top();
        root.add(silhouetteActor).expand().fill().padLeft(8).padRight(8);
        root.add(detailPanel).width(240).fillY().top();
        root.row();

        // ----- Bottom row: budget bar -----
        budgetBar = new OutfitterBudgetBar(skin);
        budgetBar.initialize();
        root.add(budgetBar).colspan(3).fillX().padTop(8);

        stage.addActor(root);

        // ----- Wire callbacks between panels -----
        wireCallbacks();

        // ----- Wire keyboard input -----
        wireKeyboardInput();
    }

    // -------------------------------------------------------------------------
    // Callback wiring
    // -------------------------------------------------------------------------

    private void wireCallbacks() {
        // Slot clicked on the silhouette
        silhouetteActor.setOnSlotClicked(this::onSlotClicked);

        // Module selected in inventory
        inventoryPanel.setOnModuleSelected(this::onModuleSelected);

        // Weapon selected in inventory
        inventoryPanel.setOnWeaponSelected(this::onWeaponSelected);

        // Double-click module in inventory: quick install
        inventoryPanel.setOnModuleDoubleClicked(this::onModuleDoubleClicked);

        // Detail panel action buttons
        detailPanel.setCallbacks(
            this::onInstallClicked,
            this::onBuyInstallClicked,
            this::onUninstallClicked,
            this::onSellClicked
        );
    }

    private void wireKeyboardInput() {
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                switch (keycode) {
                    case Input.Keys.ESCAPE:
                        close();
                        return true;
                    case Input.Keys.NUM_1:
                        switchTab(Tab.WEAPONS);
                        return true;
                    case Input.Keys.NUM_2:
                        switchTab(Tab.MODULES);
                        return true;
                    case Input.Keys.NUM_3:
                        switchTab(Tab.COSMETICS);
                        return true;
                    case Input.Keys.TAB:
                        inventoryPanel.toggleTab();
                        refreshInventory();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Open / Close / Toggle
    // -------------------------------------------------------------------------

    /** Toggles between open and closed. If opening, uses the last ship entity and station mode. */
    public void toggle() {
        if (open) close(); else if (shipEntity != null) open(shipEntity, stationMode);
    }

    @Override
    public void open() {
        if (shipEntity != null) open(shipEntity, stationMode);
    }

    public void open(Entity shipEntity, boolean stationMode) {
        if (open) return;
        this.shipEntity = shipEntity;
        this.stationMode = stationMode;

        // Fetch components
        loadout = shipEntity.getComponent(ShipLoadoutComponent.class);
        hardpoints = shipEntity.getComponent(ShipHardpointComponent.class);
        cargo = shipEntity.getComponent(ShipCargoComponent.class);

        // Look up slot layout from registry (fall back to default class)
        slotLayout = registry.getSlotLayout(DEFAULT_SHIP_CLASS);

        // Set data on silhouette
        silhouetteActor.setData(slotLayout, loadout, hardpoints);

        // Re-initialize inventory panel with current station mode
        inventoryPanel.clearChildren();
        inventoryPanel.initialize(stationMode);
        wireInventoryCallbacks();

        // Publish event and set state
        open = true;
        eventBus.publish(new OutfitterOpenedEvent(stationMode));

        // Clear previous selection
        clearSelection();

        // Switch to default tab and refresh everything
        switchTab(Tab.MODULES);
        refreshAll();
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        clearSelection();
        eventBus.publish(new OutfitterClosedEvent());
    }

    // -------------------------------------------------------------------------
    // Tab switching
    // -------------------------------------------------------------------------

    private void switchTab(Tab tab) {
        activeTab = tab;
        clearSelection();

        // Update tab button colors
        weaponsTabBtn.setColor(tab == Tab.WEAPONS ? Color.WHITE : Color.GRAY);
        modulesTabBtn.setColor(tab == Tab.MODULES ? Color.WHITE : Color.GRAY);
        cosmeticsTabBtn.setColor(tab == Tab.COSMETICS ? Color.WHITE : Color.GRAY);

        switch (tab) {
            case WEAPONS:
                silhouetteActor.setShowWeaponSlots(true);
                silhouetteActor.setShowModuleSlots(false);
                inventoryPanel.setVisible(true);
                detailPanel.setVisible(true);
                cosmeticsContainer.setVisible(false);
                refreshWeaponsInventory();
                break;

            case MODULES:
                silhouetteActor.setShowWeaponSlots(false);
                silhouetteActor.setShowModuleSlots(true);
                inventoryPanel.setVisible(true);
                detailPanel.setVisible(true);
                cosmeticsContainer.setVisible(false);
                refreshModulesInventory();
                break;

            case COSMETICS:
                silhouetteActor.setShowWeaponSlots(false);
                silhouetteActor.setShowModuleSlots(false);
                inventoryPanel.setVisible(false);
                detailPanel.setVisible(false);
                cosmeticsContainer.setVisible(true);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Slot selection flow
    // -------------------------------------------------------------------------

    private void onSlotClicked(String slotId) {
        selectedSlotId = slotId;
        selectedCandidate = null;
        silhouetteActor.setSelectedSlotId(slotId);

        if (activeTab == Tab.MODULES && loadout != null) {
            ShipModuleSlot slot = loadout.getSlot(slotId);
            if (slot != null) {
                detailPanel.showSlot(slot, stationMode);
                refreshModulesInventory(); // Filters inventory to compatible items
            }
        }
        // Weapon slot selection could be handled similarly in the future
    }

    private void onModuleSelected(ShipModuleData module) {
        selectedCandidate = module;

        if (selectedSlotId == null || loadout == null) {
            detailPanel.showCurrentModule(module);
            return;
        }

        ShipModuleSlot slot = loadout.getSlot(selectedSlotId);
        if (slot == null) return;

        // Show comparison in detail panel
        detailPanel.showCandidate(module, slot.installedModule);

        // Validate install
        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(shipEntity, selectedSlotId, module);
        detailPanel.setInstallEnabled(result.allowed);

        // Determine source of module
        boolean fromStation = !inventoryPanel.isShowingCargo();
        detailPanel.updateButtons(slot, module, stationMode, fromStation);
    }

    private void onWeaponSelected(ShipWeaponData weapon) {
        // Weapon mounting is a future extension; for now just log selection
        selectedCandidate = null;
    }

    private void onModuleDoubleClicked(ShipModuleData module) {
        // Quick-install: select the module and immediately try to install
        if (selectedSlotId == null) return;
        selectedCandidate = module;
        onInstallClicked();
    }

    // -------------------------------------------------------------------------
    // Action button handlers
    // -------------------------------------------------------------------------

    /** Install from cargo: move candidate into slot, old module goes to cargo. */
    private void onInstallClicked() {
        if (selectedSlotId == null || selectedCandidate == null || loadout == null) return;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(
            shipEntity, selectedSlotId, selectedCandidate);
        if (!result.allowed) return;

        ShipModuleSlot slot = loadout.getSlot(selectedSlotId);
        if (slot == null) return;

        ShipModuleData previousModule = slot.installedModule;

        // Remove candidate from cargo
        if (cargo != null) {
            cargo.removeModule(selectedCandidate);
        }

        // Move old module to cargo (if not empty)
        if (previousModule != null && cargo != null) {
            cargo.addModule(previousModule);
        }

        // Install new module
        slot.installedModule = selectedCandidate;

        // Publish event
        eventBus.publish(new ModuleInstalledEvent(shipEntity, selectedSlotId,
            selectedCandidate, previousModule));

        // Refresh UI
        selectedCandidate = null;
        refreshAll();

        // Re-show the slot after refresh
        if (slot != null) {
            detailPanel.showSlot(slot, stationMode);
        }
    }

    /** Buy from station and install: placeholder for station purchasing logic. */
    private void onBuyInstallClicked() {
        if (selectedSlotId == null || selectedCandidate == null || loadout == null) return;

        OutfitterValidator.Result result = OutfitterValidator.canInstallModule(
            shipEntity, selectedSlotId, selectedCandidate);
        if (!result.allowed) return;

        ShipModuleSlot slot = loadout.getSlot(selectedSlotId);
        if (slot == null) return;

        ShipModuleData previousModule = slot.installedModule;

        // Move old module to cargo (if not empty)
        if (previousModule != null && cargo != null) {
            cargo.addModule(previousModule);
        }

        // Install new module (station purchase — module is not removed from cargo
        // since it comes from station stock)
        slot.installedModule = selectedCandidate;

        // Publish event
        eventBus.publish(new ModuleInstalledEvent(shipEntity, selectedSlotId,
            selectedCandidate, previousModule));

        // Refresh UI
        selectedCandidate = null;
        refreshAll();

        if (slot != null) {
            detailPanel.showSlot(slot, stationMode);
        }
    }

    /** Uninstall: move module from slot to cargo. */
    private void onUninstallClicked() {
        if (selectedSlotId == null || loadout == null) return;

        OutfitterValidator.Result result = OutfitterValidator.canUninstallModule(shipEntity, selectedSlotId);
        if (!result.allowed) return;

        ShipModuleSlot slot = loadout.getSlot(selectedSlotId);
        if (slot == null || slot.isEmpty()) return;

        ShipModuleData removed = slot.installedModule;

        // Move to cargo
        if (cargo != null) {
            cargo.addModule(removed);
        }

        // Clear slot
        slot.installedModule = null;

        // Publish event
        eventBus.publish(new ModuleUninstalledEvent(shipEntity, selectedSlotId, removed));

        // Refresh UI
        selectedCandidate = null;
        refreshAll();
        detailPanel.showSlot(slot, stationMode);
    }

    /** Sell installed module at station: placeholder for station economy. */
    private void onSellClicked() {
        if (selectedSlotId == null || loadout == null || !stationMode) return;

        ShipModuleSlot slot = loadout.getSlot(selectedSlotId);
        if (slot == null || slot.isEmpty() || slot.mandatory) return;

        ShipModuleData sold = slot.installedModule;

        // Clear slot (module is sold, not moved to cargo)
        slot.installedModule = null;

        // Publish uninstalled event (selling involves removal)
        eventBus.publish(new ModuleUninstalledEvent(shipEntity, selectedSlotId, sold));

        // Refresh UI
        selectedCandidate = null;
        refreshAll();
        detailPanel.showSlot(slot, stationMode);
    }

    // -------------------------------------------------------------------------
    // Refresh helpers
    // -------------------------------------------------------------------------

    /** Refreshes all UI panels from the current ship entity state. */
    private void refreshAll() {
        refreshBudgetBar();
        refreshInventory();

        // Update silhouette data in case loadout changed
        if (slotLayout != null) {
            silhouetteActor.setData(slotLayout, loadout, hardpoints);
        }
    }

    private void refreshBudgetBar() {
        if (loadout == null) return;
        float powerDraw = loadout.getTotalPowerDraw();
        float powerGen = loadout.getTotalPowerGeneration();
        float mass = loadout.getTotalModuleMass();
        float maxMass = loadout.maxMass;
        int credits = 0; // TODO: wire to player credits when GameSession supports it
        budgetBar.update(powerDraw, powerGen, mass, maxMass, credits, stationMode);
    }

    private void refreshInventory() {
        switch (activeTab) {
            case MODULES:
                refreshModulesInventory();
                break;
            case WEAPONS:
                refreshWeaponsInventory();
                break;
            default:
                break;
        }
    }

    private void refreshModulesInventory() {
        if (cargo == null) return;
        ShipModuleSlot selectedSlot = (selectedSlotId != null && loadout != null)
            ? loadout.getSlot(selectedSlotId)
            : null;

        if (inventoryPanel.isShowingCargo()) {
            inventoryPanel.refreshModules(
                new ArrayList<>(cargo.storedModules), selectedSlot);
        } else {
            // Station mode: show all modules from the registry that fit the slot
            if (selectedSlot != null) {
                inventoryPanel.refreshModules(
                    registry.getModulesForSize(selectedSlot.size), selectedSlot);
            } else {
                inventoryPanel.refreshModules(
                    new ArrayList<>(cargo.storedModules), null);
            }
        }
    }

    private void refreshWeaponsInventory() {
        if (cargo == null) return;
        inventoryPanel.refreshWeapons(new ArrayList<>(cargo.storedWeapons), null);
    }

    // -------------------------------------------------------------------------
    // Selection helpers
    // -------------------------------------------------------------------------

    private void clearSelection() {
        selectedSlotId = null;
        selectedCandidate = null;
        if (silhouetteActor != null) silhouetteActor.setSelectedSlotId(null);
        if (detailPanel != null) detailPanel.clearAll();
    }

    /** Re-wires inventory callbacks after panel re-initialization. */
    private void wireInventoryCallbacks() {
        inventoryPanel.setOnModuleSelected(this::onModuleSelected);
        inventoryPanel.setOnWeaponSelected(this::onWeaponSelected);
        inventoryPanel.setOnModuleDoubleClicked(this::onModuleDoubleClicked);
    }

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
    // Disposable
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        if (stage != null)            { stage.dispose();            stage = null; }
        if (overlayTexture != null)   { overlayTexture.dispose();   overlayTexture = null; }
        if (silhouetteActor != null)  { silhouetteActor.dispose();  silhouetteActor = null; }
        if (budgetBar != null)        { budgetBar.dispose();        budgetBar = null; }
    }
}
