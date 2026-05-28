package com.galacticodyssey.ui;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.data.VehicleDefinition;
import com.galacticodyssey.data.VehicleRegistry;
import com.galacticodyssey.planet.terrain.VehicleBayService;
import com.galacticodyssey.planet.terrain.events.VehicleDeployedEvent;
import com.galacticodyssey.planet.terrain.events.VehicleRetrievedEvent;
import com.galacticodyssey.ship.components.VehicleBayComponent;

import java.util.function.Supplier;

/**
 * Scene2D overlay that lists stored vehicles in the ship's vehicle bay and
 * provides a "Deploy" button for each row.
 *
 * <p>Usage: add to a Stage at full size, call {@link #show()} when the player
 * enters the bay zone, {@link #hide()} on close. The panel subscribes to
 * {@link VehicleDeployedEvent} and {@link VehicleRetrievedEvent} to keep the
 * list in sync.</p>
 */
public class VehicleBayPanel extends Table implements Disposable {

    private static final ComponentMapper<VehicleBayComponent> BAY_M =
        ComponentMapper.getFor(VehicleBayComponent.class);

    private final Skin skin;
    private final VehicleRegistry vehicleRegistry;
    private final VehicleBayService vehicleBayService;
    private final Supplier<Entity> shipSupplier;
    private final EventBus eventBus;

    private Table listContainer;
    private Texture bgTexture;

    // Retained so we can unsubscribe on dispose
    private final EventBus.EventListener<VehicleDeployedEvent> deployedListener;
    private final EventBus.EventListener<VehicleRetrievedEvent> retrievedListener;

    /**
     * @param skin              Scene2D Skin used for Label / TextButton styles.
     * @param vehicleRegistry   Registry to look up {@link VehicleDefinition}s by id.
     * @param vehicleBayService Service that handles deploy/retrieve logic.
     * @param shipSupplier      Returns the current player ship entity; may return null if not set.
     * @param eventBus          Central event bus for bay change notifications.
     */
    public VehicleBayPanel(Skin skin, VehicleRegistry vehicleRegistry,
                            VehicleBayService vehicleBayService,
                            Supplier<Entity> shipSupplier,
                            EventBus eventBus) {
        this.skin = skin;
        this.vehicleRegistry = vehicleRegistry;
        this.vehicleBayService = vehicleBayService;
        this.shipSupplier = shipSupplier;
        this.eventBus = eventBus;

        // Dark translucent background
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.04f, 0.05f, 0.09f, 0.93f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(16);
        setVisible(false);

        // Subscribe so the list rebuilds automatically on external deploy/retrieve
        deployedListener  = e -> rebuild();
        retrievedListener = e -> rebuild();
        eventBus.subscribe(VehicleDeployedEvent.class,  deployedListener);
        eventBus.subscribe(VehicleRetrievedEvent.class, retrievedListener);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Make the panel visible and rebuild the vehicle list immediately. */
    public void show() {
        setVisible(true);
        rebuild();
    }

    /** Hide the panel without destroying it. */
    public void hide() {
        setVisible(false);
    }

    /**
     * Rebuild the displayed vehicle list from the ship's current
     * {@link VehicleBayComponent}. Safe to call at any time.
     */
    public void rebuild() {
        clearChildren();

        Entity ship = shipSupplier.get();
        VehicleBayComponent bay = (ship != null) ? BAY_M.get(ship) : null;

        // --- Header ---
        Table header = new Table();
        Label title = new Label("VEHICLE BAY", skin, "header");
        title.setColor(0f, 0.9f, 1f, 1f);
        header.add(title).expandX().left();

        TextButton closeBtn = new TextButton("[ESC] Close", skin, "default");
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });
        header.add(closeBtn).right();
        add(header).expandX().fillX().padBottom(8).row();

        // --- Capacity summary ---
        if (bay != null) {
            int used = usedSlots(bay);
            Label capacityLabel = new Label(
                "Capacity: " + used + " / " + bay.capacity + " slots", skin, "slot-meta");
            capacityLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
            add(capacityLabel).left().padBottom(10).row();
        } else {
            Label noBayLabel = new Label("No vehicle bay available.", skin, "slot-meta");
            noBayLabel.setColor(0.7f, 0.3f, 0.3f, 1f);
            add(noBayLabel).left().padBottom(10).row();
        }

        // --- Vehicle rows ---
        listContainer = new Table();
        if (bay != null && !bay.storedVehicleIds.isEmpty()) {
            for (String vehicleId : bay.storedVehicleIds) {
                addVehicleRow(ship, vehicleId);
            }
        } else {
            Label emptyLabel = new Label("No vehicles stored.", skin, "slot-meta");
            emptyLabel.setColor(0.5f, 0.5f, 0.5f, 1f);
            listContainer.add(emptyLabel).expandX().left().pad(8).row();
        }

        ScrollPane scrollPane = new ScrollPane(listContainer, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).expand().fill();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void addVehicleRow(Entity ship, String vehicleId) {
        VehicleDefinition def = vehicleRegistry.get(vehicleId);

        Table row = new Table();
        row.pad(8);

        // Vehicle name
        String displayName = (def != null) ? def.displayName : vehicleId;
        Label nameLabel = new Label(displayName, skin, "slot-name");
        nameLabel.setColor(0.9f, 0.85f, 0.3f, 1f);
        row.add(nameLabel).left().minWidth(140);

        // Stats summary (HP / Armor / Weapon damage)
        if (def != null) {
            String stats = "HP:" + (int) def.maxHP
                + "  Armor:" + (int) def.armorValue;
            if (def.weapon != null) {
                stats += "  Dmg:" + (int) def.weapon.damage;
            }
            Label statsLabel = new Label(stats, skin, "slot-meta");
            statsLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
            row.add(statsLabel).left().expandX().padLeft(10);

            // Slots cost
            Label slotsLabel = new Label(def.baySlots + " slot(s)", skin, "slot-meta");
            slotsLabel.setColor(0.4f, 0.8f, 0.4f, 1f);
            row.add(slotsLabel).right().padRight(12);
        }

        // Deploy button
        TextButton deployBtn = new TextButton("Deploy", skin, "default");
        deployBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                vehicleBayService.deploy(ship, vehicleId);
                // The VehicleDeployedEvent subscription will trigger rebuild()
            }
        });
        row.add(deployBtn).right();

        listContainer.add(row).expandX().fillX().row();
    }

    /** Counts total used bay slots (mirrors VehicleBayService logic). */
    private int usedSlots(VehicleBayComponent bay) {
        int slots = 0;
        for (String id : bay.storedVehicleIds) {
            VehicleDefinition def = vehicleRegistry.get(id);
            slots += (def != null ? Math.max(1, def.baySlots) : 1);
        }
        return slots;
    }

    // -------------------------------------------------------------------------
    // Disposable
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        eventBus.unsubscribe(VehicleDeployedEvent.class,  deployedListener);
        eventBus.unsubscribe(VehicleRetrievedEvent.class, retrievedListener);
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
