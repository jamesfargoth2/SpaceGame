// core/src/main/java/com/galacticodyssey/ui/CockpitHUDSystem.java
package com.galacticodyssey.ui;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerTargetComponent;
import com.galacticodyssey.ship.components.FuelTankComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipThermalComponent;
import com.galacticodyssey.ship.power.PowerStateComponent;
import com.galacticodyssey.ship.events.ReentryHeatingEvent;
import com.galacticodyssey.ship.events.StallWarningEvent;
import com.galacticodyssey.ship.weapons.components.WeaponGroupComponent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * Scene2D HUD system that renders flight instruments while the player is piloting a ship.
 * Must call {@link #initialize()} after the GL context is ready; never creates GL resources
 * in the constructor.
 */
public class CockpitHUDSystem extends EntitySystem implements Disposable {

    private static final float ALERT_DURATION = 3f;

    private final EventBus eventBus;

    // GL resources — created in initialize()
    private SpriteBatch batch;
    private Stage stage;
    private BitmapFont font;
    private Label.LabelStyle styleWhite;
    private Label.LabelStyle styleOrange;
    private Label.LabelStyle styleRed;
    private Label.LabelStyle styleCyan;
    private Label.LabelStyle styleDarkGray;

    // Instrument labels
    private Label speedLabel;
    private Label altLabel;
    private Label throttleLabel;
    private Label reticleLabel;
    private Label targetLockLabel;
    private Label targetInfoLabel;
    private Label fuelLabel;
    private Label heatLabel;
    private Label powerLabel;
    private Label capacitorLabel;
    private Label faLabel;
    private Label boostLabel;
    private Label alertLabel;
    private Label[] weaponGroupLabels;

    private OrbitHUDPanel orbitHUDPanel;

    // State
    private boolean visible = false;
    private Entity shipEntity;
    private float stallWarningTimer = 0f;
    private float reentryTimer = 0f;

    // Ashley family for player entity lookup
    private ImmutableArray<Entity> playerEntities;

    public CockpitHUDSystem(EventBus eventBus) {
        super(20);
        this.eventBus = eventBus;

        eventBus.subscribe(CockpitHUDShowEvent.class, this::onShow);
        eventBus.subscribe(CockpitHUDHideEvent.class, this::onHide);
        eventBus.subscribe(StallWarningEvent.class, this::onStallWarning);
        eventBus.subscribe(ReentryHeatingEvent.class, this::onReentryHeating);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(
                Family.all(PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    /**
     * Create all GL resources.  Must be called from the render thread after the GL context exists.
     */
    public void initialize() {
        batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport(), batch);
        font = new BitmapFont();

        styleWhite    = new Label.LabelStyle(font, Color.WHITE);
        styleOrange   = new Label.LabelStyle(font, Color.ORANGE);
        styleRed      = new Label.LabelStyle(font, Color.RED);
        styleCyan     = new Label.LabelStyle(font, Color.CYAN);
        styleDarkGray = new Label.LabelStyle(font, Color.DARK_GRAY);

        Skin defaultSkin = new Skin();
        defaultSkin.add("default", styleWhite, Label.LabelStyle.class);
        orbitHUDPanel = new OrbitHUDPanel(defaultSkin);
        orbitHUDPanel.subscribeToEventBus(eventBus);

        buildLayout();
        applyVisibility();
    }

    /** Update the stage viewport when the screen is resized. */
    public void resize(int width, int height) {
        if (stage == null) return;
        stage.getViewport().update(width, height, true);
    }

    /** Returns the underlying Scene2D Stage (may be null before {@link #initialize()} is called). */
    public Stage getStage() {
        return stage;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(float deltaTime) {
        if (stage == null) return;

        // Decrement alert timers
        if (stallWarningTimer > 0f) {
            stallWarningTimer -= deltaTime;
            if (stallWarningTimer <= 0f && reentryTimer <= 0f) {
                alertLabel.setText("");
                alertLabel.setVisible(false);
            }
        }
        if (reentryTimer > 0f) {
            reentryTimer -= deltaTime;
            if (reentryTimer <= 0f && stallWarningTimer <= 0f) {
                alertLabel.setText("");
                alertLabel.setVisible(false);
            }
        }

        stage.act(deltaTime);

        if (!visible || shipEntity == null) return;

        refreshInstruments();
    }

    public void render(float delta) {
        if (stage == null) return;
        stage.draw();
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        speedLabel      = new Label("SPEED: 0 m/s", styleWhite);
        altLabel        = new Label("ALT: ---", styleWhite);
        throttleLabel   = new Label("THR: 0%", styleWhite);
        reticleLabel    = new Label("[ + ]", styleWhite);
        targetLockLabel = new Label("", styleWhite);
        targetInfoLabel = new Label("", styleWhite);
        fuelLabel       = new Label("FUEL: ---", styleWhite);
        heatLabel       = new Label("HEAT: 0%", styleWhite);
        powerLabel      = new Label("PWR: ---", styleWhite);
        capacitorLabel  = new Label("CAP: ---", styleWhite);
        faLabel         = new Label("FA: ON", styleCyan);
        boostLabel      = new Label("BOOST: 100%", styleWhite);
        alertLabel      = new Label("", styleRed);

        weaponGroupLabels = new Label[4];
        for (int i = 0; i < 4; i++) {
            weaponGroupLabels[i] = new Label("[" + (i + 1) + "]", styleDarkGray);
        }

        alertLabel.setVisible(false);

        /*
         * 3×3 root table layout:
         *
         *  [top-left]     [top-center]   [top-right (empty)]
         *  [mid-left]     [center]       [mid-right (empty)]
         *  [btm-left]     [btm-center]   [btm-right]
         */
        Table root = new Table();
        root.setFillParent(true);
        root.pad(12f);

        // --- Top row ---
        // top-left: speed + altitude panel
        Table speedPanel = new Table();
        speedPanel.add(speedLabel).left().row();
        speedPanel.add(altLabel).left();
        root.add(speedPanel).top().left().expand();

        // top-center: alert label
        root.add(alertLabel).top().center().expand();

        // top-right: empty
        root.add().top().right().expand();
        root.row();

        // --- Middle row ---
        // mid-left: throttle gauge
        root.add(throttleLabel).left().top().expand();

        // center: reticle + target lock
        Table centerPanel = new Table();
        centerPanel.add(reticleLabel).center().row();
        centerPanel.add(targetLockLabel).center().row();
        centerPanel.add(targetInfoLabel).center();
        root.add(centerPanel).center().expand();

        // mid-right: empty
        root.add().right().top().expand();
        root.row();

        // --- Bottom row ---
        // bottom-left: fuel + heat + power
        Table btmLeft = new Table();
        btmLeft.add(fuelLabel).left().row();
        btmLeft.add(heatLabel).left().row();
        btmLeft.add(powerLabel).left().row();
        btmLeft.add(capacitorLabel).left().row();
        btmLeft.add(faLabel).left().row();
        btmLeft.add(boostLabel).left();
        root.add(btmLeft).bottom().left().expand();

        // bottom-center: empty
        root.add().bottom().center().expand();

        // bottom-right: weapon groups
        Table weaponPanel = new Table();
        weaponPanel.defaults().padLeft(4f);
        for (Label wl : weaponGroupLabels) {
            weaponPanel.add(wl);
        }
        root.add(weaponPanel).bottom().right().expand();

        stage.addActor(root);

        // Orbit HUD panel — anchored to the bottom-center of the screen
        orbitHUDPanel.setPosition(8f, 8f);
        stage.addActor(orbitHUDPanel);
    }

    // ------------------------------------------------------------------ instrument refresh

    private void refreshInstruments() {
        // --- Speed (from ship physics body) ---
        PhysicsBodyComponent phys = shipEntity.getComponent(PhysicsBodyComponent.class);
        if (phys != null && phys.body != null) {
            float speed = phys.body.getLinearVelocity().len();
            speedLabel.setText(String.format("SPEED: %.0f m/s", speed));
        } else {
            speedLabel.setText("SPEED: 0 m/s");
        }

        // --- Throttle ---
        ShipFlightComponent flight = shipEntity.getComponent(ShipFlightComponent.class);
        if (flight != null) {
            throttleLabel.setText(String.format("THR: %.0f%%", flight.currentThrottle * 100f));
            faLabel.setText(flight.flightAssistEnabled ? "FA: ON" : "FA: OFF");
            faLabel.setStyle(flight.flightAssistEnabled ? styleCyan : styleOrange);
            float boostPct = (flight.boostMaxEnergy > 0f)
                ? (flight.boostEnergy / flight.boostMaxEnergy * 100f) : 0f;
            String boostState = flight.boostTimer > 0f ? " (BOOSTING)"
                : (flight.boostCooldownTimer > 0f ? " (CD)" : "");
            boostLabel.setText(String.format("BOOST: %.0f%%%s", boostPct, boostState));
            boostLabel.setStyle(flight.boostTimer > 0f ? styleCyan : styleWhite);
        } else {
            throttleLabel.setText("THR: ---");
            faLabel.setText("FA: ---");
            boostLabel.setText("BOOST: ---");
        }

        // --- Fuel ---
        FuelTankComponent fuel = shipEntity.getComponent(FuelTankComponent.class);
        if (fuel != null) {
            fuelLabel.setText(String.format("FUEL: %.0f / %.0f", fuel.currentMass, fuel.maxMass));
        } else {
            fuelLabel.setText("FUEL: ---");
        }

        // --- Heat ---
        ShipThermalComponent thermal = shipEntity.getComponent(ShipThermalComponent.class);
        if (thermal != null) {
            float pct = (thermal.maxHeat > 0f) ? (thermal.currentHeat / thermal.maxHeat * 100f) : 0f;
            heatLabel.setText(String.format("HEAT: %.0f%%", pct));
            if (pct >= 80f) {
                heatLabel.setStyle(styleRed);
            } else if (pct >= 50f) {
                heatLabel.setStyle(styleOrange);
            } else {
                heatLabel.setStyle(styleWhite);
            }
        } else {
            heatLabel.setText("HEAT: ---");
            heatLabel.setStyle(styleWhite);
        }

        // --- Power ---
        PowerStateComponent power = shipEntity.getComponent(PowerStateComponent.class);
        if (power != null) {
            float reactorPct = (power.reactorBaseOutput > 0f)
                    ? (power.reactorCurrentOutput / power.reactorBaseOutput * 100f) : 0f;
            powerLabel.setText(String.format("PWR: %.0f/%.0f kW (%.0f%%)",
                    power.reactorCurrentOutput, power.reactorBaseOutput, reactorPct));
            if (power.totalDemand > power.totalSupply) {
                powerLabel.setStyle(styleRed);
            } else if (reactorPct < 80f) {
                powerLabel.setStyle(styleOrange);
            } else {
                powerLabel.setStyle(styleWhite);
            }

            float capPct = (power.capacitorCapacity > 0f)
                    ? (power.capacitorCharge / power.capacitorCapacity * 100f) : 0f;
            capacitorLabel.setText(String.format("CAP: %.0f%% | BAT: %.0f%%",
                    capPct,
                    (power.batteryCapacity > 0f)
                            ? (power.batteryCharge / power.batteryCapacity * 100f) : 0f));
            if (capPct < 20f) {
                capacitorLabel.setStyle(styleOrange);
            } else {
                capacitorLabel.setStyle(styleWhite);
            }
        } else {
            powerLabel.setText("PWR: ---");
            powerLabel.setStyle(styleWhite);
            capacitorLabel.setText("CAP: ---");
            capacitorLabel.setStyle(styleWhite);
        }

        // --- Weapon groups ---
        WeaponGroupComponent wg = shipEntity.getComponent(WeaponGroupComponent.class);
        int activeGroup = (wg != null) ? wg.activeGroup : -1;
        for (int i = 0; i < 4; i++) {
            weaponGroupLabels[i].setStyle(i == activeGroup ? styleCyan : styleDarkGray);
        }

        // --- Target info (from player entity) ---
        if (playerEntities != null && playerEntities.size() > 0) {
            Entity player = playerEntities.first();
            PlayerTargetComponent target = player.getComponent(PlayerTargetComponent.class);
            if (target != null && target.lockedTarget != null) {
                targetLockLabel.setText("[TARGET LOCKED]");
                targetInfoLabel.setText("TGT: " + target.lockedTarget.hashCode());
            } else {
                targetLockLabel.setText("");
                targetInfoLabel.setText("");
            }
        } else {
            targetLockLabel.setText("");
            targetInfoLabel.setText("");
        }
    }

    // ------------------------------------------------------------------ event handlers

    private void onShow(CockpitHUDShowEvent event) {
        shipEntity = event.ship;
        visible = true;
        if (orbitHUDPanel != null) orbitHUDPanel.setShipEntity(shipEntity);
        applyVisibility();
    }

    private void onHide(CockpitHUDHideEvent event) {
        shipEntity = null;
        visible = false;
        if (orbitHUDPanel != null) orbitHUDPanel.setShipEntity(null);
        applyVisibility();
    }

    private void onStallWarning(StallWarningEvent event) {
        if (alertLabel == null) return;
        alertLabel.setText("STALL WARNING");
        alertLabel.setStyle(styleRed);
        alertLabel.setVisible(true);
        stallWarningTimer = ALERT_DURATION;
    }

    private void onReentryHeating(ReentryHeatingEvent event) {
        if (alertLabel == null) return;
        alertLabel.setText("REENTRY HEATING");
        alertLabel.setStyle(styleRed);
        alertLabel.setVisible(true);
        reentryTimer = ALERT_DURATION;
    }

    // ------------------------------------------------------------------ helpers

    private void applyVisibility() {
        if (stage == null) return;
        stage.getRoot().setVisible(visible);
    }

    // ------------------------------------------------------------------ dispose

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (font != null) {
            font.dispose();
            font = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
    }
}
