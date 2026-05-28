package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.events.SOIChangedEvent;
import com.galacticodyssey.galaxy.KeplerianOrbitSystem.OrbitTickEvent;
import com.galacticodyssey.galaxy.KeplerOrbit;
import com.galacticodyssey.galaxy.OrbitalMechanics;

/**
 * Scene2D Table panel showing live orbital data for the piloted ship.
 *
 * Subscribe this panel to the EventBus after constructing it.  On every
 * {@link OrbitTickEvent} it re-derives the ship's orbit from its current
 * position+velocity and refreshes all label values.
 *
 * Layout: two-column key / value rows.  Add to any Stage via normal Scene2D.
 */
public final class OrbitHUDPanel extends Table {

    // ------------------------------------------------------------------ constants

    private static final float M_TO_KM = 1e-3f;

    // ------------------------------------------------------------------ fields

    private final Label periapsisValue;
    private final Label apoapsisValue;
    private final Label periodValue;
    private final Label eccentricityValue;
    private final Label dv1Value;
    private final Label dv2Value;
    private final Label transferTimeValue;
    private final Label stabilityValue;

    /** The ship entity whose PhysicsBodyComponent is sampled for state vectors. */
    private Entity shipEntity;

    /**
     * Primary body mass used for orbit derivation.  Set externally; updated when
     * the active StarSystem changes.
     */
    private float primaryMass = 2e30f;

    /**
     * Primary body radius used for impact-risk detection (metres).
     * Defaults to a solar radius; override as needed.
     */
    private float primaryRadius = 6.96e8f;

    /** Optional target orbit radius for Hohmann transfer display (metres).  Negative = hidden. */
    private float targetOrbitRadius = -1f;

    // ------------------------------------------------------------------ construction

    public OrbitHUDPanel(Skin skin) {
        super(skin);

        defaults().left().padRight(8f);

        periapsisValue    = newValue(skin);
        apoapsisValue     = newValue(skin);
        periodValue       = newValue(skin);
        eccentricityValue = newValue(skin);
        dv1Value          = newValue(skin);
        dv2Value          = newValue(skin);
        transferTimeValue = newValue(skin);
        stabilityValue    = newValue(skin);

        addRow(skin, "PERIAPSIS",  periapsisValue);
        addRow(skin, "APOAPSIS",   apoapsisValue);
        addRow(skin, "PERIOD",     periodValue);
        addRow(skin, "ECC",        eccentricityValue);
        addRow(skin, "STATUS",     stabilityValue);
        addRow(skin, "HOH Δv1",    dv1Value);
        addRow(skin, "HOH Δv2",    dv2Value);
        addRow(skin, "HOH TIME",   transferTimeValue);

        clearTransferDisplay();
    }

    // ------------------------------------------------------------------ EventBus wiring

    public void subscribeToEventBus(EventBus eventBus) {
        eventBus.subscribe(OrbitTickEvent.class, this::onOrbitTick);
        eventBus.subscribe(SOIChangedEvent.class, this::onSOIChanged);
    }

    // ------------------------------------------------------------------ public API

    public void setShipEntity(Entity entity) {
        this.shipEntity = entity;
    }

    public void setPrimaryMass(float massKg) {
        this.primaryMass = massKg;
    }

    public void setPrimaryRadius(float radiusM) {
        this.primaryRadius = radiusM;
    }

    /**
     * Set a target circular orbit radius (metres) to show Hohmann transfer data.
     * Pass a value <= 0 to hide the transfer readout.
     */
    public void setTargetOrbitRadius(float radiusM) {
        this.targetOrbitRadius = radiusM;
        if (radiusM <= 0f) {
            clearTransferDisplay();
        }
    }

    // ------------------------------------------------------------------ event handler

    private void onSOIChanged(SOIChangedEvent event) {
        if (event.entity != shipEntity) return;
        Entity newBody = event.newDominantBody;
        if (newBody == null) return;

        GravitySourceComponent gravity = newBody.getComponent(GravitySourceComponent.class);
        if (gravity != null) {
            primaryMass = gravity.mass;
        }

        OrbitalBodyComponent orbital = newBody.getComponent(OrbitalBodyComponent.class);
        if (orbital != null) {
            primaryRadius = orbital.bodyRadius;
        }
    }

    private void onOrbitTick(OrbitTickEvent event) {
        if (shipEntity == null) return;

        PhysicsBodyComponent phys = shipEntity.getComponent(PhysicsBodyComponent.class);
        if (phys == null || phys.body == null) return;

        com.badlogic.gdx.math.Vector3 bPos = phys.body.getWorldTransform().getTranslation(new Vector3());
        com.badlogic.gdx.math.Vector3 bVel = phys.body.getLinearVelocity();

        Vector3 pos = new Vector3(bPos.x, bPos.y, bPos.z);
        Vector3 vel = new Vector3(bVel.x, bVel.y, bVel.z);

        float r = pos.len();
        float v = vel.len();
        if (r < 1f) return; // not yet positioned

        KeplerOrbit orbit = OrbitalMechanics.fromStateVectors(pos, vel, primaryMass);
        final float GM = OrbitalMechanics.G * primaryMass;

        // ---- orbital parameters ----
        periapsisValue.setText(String.format("%.1f km", orbit.periapsis * M_TO_KM));
        apoapsisValue.setText(String.format("%.1f km", orbit.apoapsis  * M_TO_KM));
        periodValue.setText(formatPeriod(orbit.period));
        eccentricityValue.setText(String.format("%.4f", orbit.eccentricity));

        // ---- stability ----
        boolean stable = OrbitalMechanics.isStableOrbit(pos, vel, GM, primaryRadius);
        if (!stable) {
            float vEsc = OrbitalMechanics.escapeVelocity(GM, r);
            if (v >= vEsc) {
                stabilityValue.setText("ESCAPE TRAJECTORY");
            } else if (r <= primaryRadius * 1.1f) {
                stabilityValue.setText("IMPACT RISK");
            } else {
                stabilityValue.setText("DEGENERATE");
            }
        } else {
            stabilityValue.setText("STABLE");
        }

        // ---- Hohmann transfer ----
        if (targetOrbitRadius > 0f) {
            OrbitalMechanics.HohmannTransfer h = OrbitalMechanics.hohmann(GM, r, targetOrbitRadius);
            dv1Value.setText(String.format("%.1f m/s", h.deltaV1));
            dv2Value.setText(String.format("%.1f m/s", h.deltaV2));
            transferTimeValue.setText(formatPeriod(h.transferTime));
        }
    }

    // ------------------------------------------------------------------ helpers

    private void addRow(Skin skin, String keyText, Label valueLabel) {
        add(new Label(keyText, skin)).left();
        add(valueLabel).left();
        row();
    }

    private static Label newValue(Skin skin) {
        return new Label("---", skin);
    }

    private void clearTransferDisplay() {
        dv1Value.setText("---");
        dv2Value.setText("---");
        transferTimeValue.setText("---");
    }

    private static String formatPeriod(float seconds) {
        if (seconds >= Float.MAX_VALUE || Float.isInfinite(seconds) || Float.isNaN(seconds)) {
            return "∞";
        }
        int total = (int) seconds;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return String.format("%dh %02dm %02ds", h, m, s);
    }
}
