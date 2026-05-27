package com.galacticodyssey.ship.flooding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.water.Compartment;
import com.galacticodyssey.ship.flooding.components.ShipFloodingComponent;
import com.galacticodyssey.water.events.CapsizeEvent;
import com.galacticodyssey.water.events.FloodingStartedEvent;
import com.galacticodyssey.water.events.StabilityWarningEvent;

/**
 * Scene2D HUD overlay that displays flooding status and stability
 * warnings to the player.
 *
 * <h3>Display Elements</h3>
 * <ul>
 *   <li>Per-compartment flood level with status indicator (DRY, FLOODING,
 *       BREACH, SEALED)</li>
 *   <li>Stability warning banner that escalates through
 *       CAUTION / WARNING / CRITICAL levels with colour-coded text</li>
 *   <li>Roll and pitch angle readout</li>
 *   <li>GZ (metacentric height) loss indicator</li>
 *   <li>Total water mass summary</li>
 *   <li>CAPSIZE flashing alert when the ship has capsized</li>
 * </ul>
 *
 * <p>The HUD panel appears in the top-right corner when the player's
 * ship has an active {@link ShipFloodingComponent} with non-zero water
 * levels or an active hull breach. It hides when the ship is dry.</p>
 *
 * <p>Uses procedurally generated textures for the panel background so
 * it works without external skin assets during early development.
 * Subscribes to flooding events via the event bus for trigger-based
 * alerts.</p>
 *
 * <p>Priority 20 -- runs late so all simulation data is settled.</p>
 */
public class FloodingHudSystem extends EntitySystem implements Disposable {

    public static final int PRIORITY = 50;

    // -- Warning level colours --
    private static final Color COLOR_NORMAL   = new Color(0.2f, 0.8f, 0.2f, 1f);
    private static final Color COLOR_CAUTION  = new Color(1f, 0.9f, 0.1f, 1f);
    private static final Color COLOR_WARNING  = new Color(1f, 0.5f, 0.0f, 1f);
    private static final Color COLOR_CRITICAL = new Color(1f, 0.1f, 0.1f, 1f);

    /** How quickly the warning banner flashes at critical level (Hz). */
    private static final float FLASH_FREQUENCY = 2.0f;

    /** Roll angle thresholds for warning level determination (degrees). */
    private static final float CAUTION_ROLL  = 5f;
    private static final float WARNING_ROLL  = 15f;
    private static final float CRITICAL_ROLL = 35f;

    /** GZ loss thresholds for warning level determination (metres). */
    private static final float CAUTION_GZ_LOSS  = 0.1f;
    private static final float WARNING_GZ_LOSS  = 0.3f;
    private static final float CRITICAL_GZ_LOSS = 0.6f;

    /** Maximum number of compartment labels to pre-allocate. */
    private static final int MAX_COMPARTMENTS = 6;

    private static final Family FAMILY = Family.all(ShipFloodingComponent.class).get();
    private static final ComponentMapper<ShipFloodingComponent> FLOOD_M =
            ComponentMapper.getFor(ShipFloodingComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> entities;

    // -- UI elements --
    private Stage stage;
    private BitmapFont font;
    private BitmapFont fontTitle;
    private Table rootTable;
    private Label warningLabel;
    private Label rollLabel;
    private Label pitchLabel;
    private Label floodSummaryLabel;
    private Label gzLossLabel;
    private final Label[] compartmentLabels = new Label[MAX_COMPARTMENTS];

    // -- Textures (procedurally generated, must be disposed) --
    private Texture panelBgTexture;

    // -- Flashing state --
    private float flashTimer;
    private boolean capsizeActive;

    private final EventBus.EventListener<CapsizeEvent> capsizeListener = this::onCapsize;
    private final EventBus.EventListener<FloodingStartedEvent> floodingStartedListener
            = this::onFloodingStarted;

    /** Recent flood alert text for brief display. */
    private String alertText;
    private float alertTimer;

    /**
     * Creates a new flooding HUD system. Call {@link #initialize()} after
     * the OpenGL context is available to create the Stage and UI elements.
     *
     * @param eventBus central event bus
     */
    public FloodingHudSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    /**
     * Initializes the Scene2D stage and UI elements. Must be called once
     * the GL context is available. Separated from the constructor so the
     * system can be tested without GL (per CLAUDE.md rule 5).
     */
    public void initialize() {
        stage = new Stage(new com.badlogic.gdx.utils.viewport.ScreenViewport());

        font = new BitmapFont();
        font.setColor(Color.WHITE);

        fontTitle = new BitmapFont();
        fontTitle.getData().setScale(1.3f);
        fontTitle.setColor(Color.WHITE);

        createTextures();
        buildUi();

        eventBus.subscribe(CapsizeEvent.class, capsizeListener);
        eventBus.subscribe(FloodingStartedEvent.class, floodingStartedListener);
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private void createTextures() {
        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(0f, 0f, 0f, 0.6f);
        px.fill();
        panelBgTexture = new Texture(px);
        px.dispose();
    }

    private void buildUi() {
        Label.LabelStyle titleStyle = new Label.LabelStyle(fontTitle, Color.WHITE);
        Label.LabelStyle normalStyle = new Label.LabelStyle(font, Color.WHITE);

        rootTable = new Table();
        rootTable.setFillParent(false);
        rootTable.top().right();
        rootTable.pad(10f);
        rootTable.setBackground(new TextureRegionDrawable(
                new TextureRegion(panelBgTexture)));

        // Title
        Label titleLabel = new Label("DAMAGE CONTROL", titleStyle);
        rootTable.add(titleLabel).colspan(2).padBottom(8f).row();

        // Warning banner
        warningLabel = new Label("", new Label.LabelStyle(font, COLOR_NORMAL));
        warningLabel.setAlignment(Align.center);
        rootTable.add(warningLabel).colspan(2).fillX().padBottom(6f).row();

        // Roll and pitch display
        Table angleTable = new Table();
        rollLabel = new Label("Roll: 0.0", normalStyle);
        pitchLabel = new Label("Pitch: 0.0", normalStyle);
        angleTable.add(rollLabel).padRight(12f);
        angleTable.add(pitchLabel);
        rootTable.add(angleTable).colspan(2).padBottom(4f).row();

        // GZ loss indicator
        gzLossLabel = new Label("GZ loss: 0.00 m", normalStyle);
        rootTable.add(gzLossLabel).colspan(2).padBottom(6f).row();

        // Per-compartment flood level labels
        for (int i = 0; i < MAX_COMPARTMENTS; i++) {
            compartmentLabels[i] = new Label("", new Label.LabelStyle(font, Color.WHITE));
            rootTable.add(compartmentLabels[i]).colspan(2).fillX()
                    .padBottom(2f).row();
        }

        // Total water summary
        floodSummaryLabel = new Label("Total water: 0 kg", normalStyle);
        rootTable.add(floodSummaryLabel).colspan(2).padTop(6f).row();

        rootTable.pack();
        rootTable.setVisible(false);

        stage.addActor(rootTable);
    }

    // ------------------------------------------------------------------
    // System lifecycle
    // ------------------------------------------------------------------

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
        eventBus.unsubscribe(CapsizeEvent.class, capsizeListener);
        eventBus.unsubscribe(FloodingStartedEvent.class, floodingStartedListener);
    }

    @Override
    public void update(float deltaTime) {
        if (stage == null || entities == null) return;

        // Find the first entity with active flooding
        Entity target = null;
        for (int i = 0, n = entities.size(); i < n; i++) {
            ShipFloodingComponent fc = FLOOD_M.get(entities.get(i));
            if (fc != null && (fc.totalFloodedMass > 0f || hasAnyBreach(fc))) {
                target = entities.get(i);
                break;
            }
        }

        if (target == null && !capsizeActive) {
            rootTable.setVisible(false);
            return;
        }
        if (target == null) return;

        rootTable.setVisible(true);
        ShipFloodingComponent flood = FLOOD_M.get(target);

        flashTimer += deltaTime;

        // Reposition panel to top-right corner
        if (Gdx.graphics != null) {
            float screenW = Gdx.graphics.getWidth();
            float screenH = Gdx.graphics.getHeight();
            rootTable.setPosition(
                    screenW - rootTable.getWidth() - 10f,
                    screenH - rootTable.getHeight() - 10f);
        }

        int warningLevel = computeWarningLevel(flood);
        updateWarningBanner(warningLevel);
        updateAngleDisplay(flood);
        updateGzDisplay(flood);
        updateCompartmentDisplay(flood);
        updateSummary(flood);

        // Alert flash timer
        if (alertTimer > 0f) {
            alertTimer -= deltaTime;
        }

        stage.act(deltaTime);
        stage.draw();
    }

    // ------------------------------------------------------------------
    // Warning level determination
    // ------------------------------------------------------------------

    /**
     * Determines the current warning level from roll angle and GZ loss.
     * <ul>
     *   <li>0 = Normal (flooding detected but stable)</li>
     *   <li>1 = Caution (minor list)</li>
     *   <li>2 = Warning (significant stability loss)</li>
     *   <li>3 = Critical (imminent capsize)</li>
     * </ul>
     */
    private int computeWarningLevel(ShipFloodingComponent flood) {
        float absRoll = Math.abs(flood.currentRollDeg);
        float gzLoss = flood.freeSurfaceGzLoss;

        if (flood.capsized || absRoll >= CRITICAL_ROLL || gzLoss >= CRITICAL_GZ_LOSS) return 3;
        if (absRoll >= WARNING_ROLL || gzLoss >= WARNING_GZ_LOSS) return 2;
        if (absRoll >= CAUTION_ROLL || gzLoss >= CAUTION_GZ_LOSS) return 1;
        return 0;
    }

    // ------------------------------------------------------------------
    // UI updates
    // ------------------------------------------------------------------

    private void updateWarningBanner(int warningLevel) {
        if (capsizeActive) {
            warningLabel.setText("*** CAPSIZED ***");
            boolean flashOn = (flashTimer * FLASH_FREQUENCY * 2f) % 2f < 1f;
            warningLabel.setColor(flashOn ? COLOR_CRITICAL : Color.WHITE);
            return;
        }

        switch (warningLevel) {
            case 0:
                warningLabel.setText("FLOODING DETECTED");
                warningLabel.setColor(COLOR_NORMAL);
                break;
            case 1:
                warningLabel.setText("CAUTION: LISTING");
                warningLabel.setColor(COLOR_CAUTION);
                break;
            case 2:
                warningLabel.setText("WARNING: STABILITY LOSS");
                warningLabel.setColor(COLOR_WARNING);
                break;
            case 3:
                boolean flash = (flashTimer * FLASH_FREQUENCY * 2f) % 2f < 1f;
                warningLabel.setText("CRITICAL: CAPSIZE IMMINENT");
                warningLabel.setColor(flash ? COLOR_CRITICAL : COLOR_WARNING);
                break;
            default:
                warningLabel.setText("");
                break;
        }
    }

    private void updateAngleDisplay(ShipFloodingComponent flood) {
        Color rollColor = getAngleColor(Math.abs(flood.currentRollDeg));
        Color pitchColor = getAngleColor(Math.abs(flood.currentPitchDeg));

        rollLabel.setText(String.format("Roll: %+.1f deg", flood.currentRollDeg));
        rollLabel.setColor(rollColor);

        pitchLabel.setText(String.format("Pitch: %+.1f deg", flood.currentPitchDeg));
        pitchLabel.setColor(pitchColor);
    }

    private void updateGzDisplay(ShipFloodingComponent flood) {
        gzLossLabel.setText(String.format("GZ loss: %.2f m", flood.freeSurfaceGzLoss));

        if (flood.freeSurfaceGzLoss >= CRITICAL_GZ_LOSS) {
            gzLossLabel.setColor(COLOR_CRITICAL);
        } else if (flood.freeSurfaceGzLoss >= WARNING_GZ_LOSS) {
            gzLossLabel.setColor(COLOR_WARNING);
        } else if (flood.freeSurfaceGzLoss >= CAUTION_GZ_LOSS) {
            gzLossLabel.setColor(COLOR_CAUTION);
        } else {
            gzLossLabel.setColor(COLOR_NORMAL);
        }
    }

    private void updateCompartmentDisplay(ShipFloodingComponent flood) {
        for (int i = 0; i < MAX_COMPARTMENTS; i++) {
            if (i < flood.compartments.size) {
                Compartment comp = flood.compartments.get(i);
                float pct = comp.fillFraction() * 100f;

                String status;
                if (comp.breachArea > 0f && !comp.sealed) {
                    status = "BREACH";
                } else if (comp.sealed && comp.waterVolume > 0f) {
                    status = "SEALED";
                } else if (pct > 0.1f) {
                    status = "FLOODING";
                } else {
                    status = "DRY";
                }

                String text = String.format("%-14s %5.1f%% [%s]",
                        formatId(comp.id), pct, status);
                compartmentLabels[i].setText(text);

                if ("BREACH".equals(status)) {
                    compartmentLabels[i].setColor(COLOR_CRITICAL);
                } else if (pct >= 75f) {
                    compartmentLabels[i].setColor(COLOR_CRITICAL);
                } else if (pct >= 40f) {
                    compartmentLabels[i].setColor(COLOR_WARNING);
                } else if (pct > 0.1f) {
                    compartmentLabels[i].setColor(COLOR_CAUTION);
                } else {
                    compartmentLabels[i].setColor(COLOR_NORMAL);
                }

                compartmentLabels[i].setVisible(true);
            } else {
                compartmentLabels[i].setVisible(false);
            }
        }
    }

    private void updateSummary(ShipFloodingComponent flood) {
        float massKg = flood.totalFloodedMass;
        String text;
        if (massKg >= 1000f) {
            text = String.format("Total water: %.1f t", massKg / 1000f);
        } else {
            text = String.format("Total water: %.0f kg", massKg);
        }
        floodSummaryLabel.setText(text);

        if (massKg > 50_000f) {
            floodSummaryLabel.setColor(COLOR_CRITICAL);
        } else if (massKg > 20_000f) {
            floodSummaryLabel.setColor(COLOR_WARNING);
        } else if (massKg > 5_000f) {
            floodSummaryLabel.setColor(COLOR_CAUTION);
        } else {
            floodSummaryLabel.setColor(COLOR_NORMAL);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Color getAngleColor(float absDeg) {
        if (absDeg >= CRITICAL_ROLL) return COLOR_CRITICAL;
        if (absDeg >= WARNING_ROLL)  return COLOR_WARNING;
        if (absDeg >= CAUTION_ROLL)  return COLOR_CAUTION;
        return COLOR_NORMAL;
    }

    /**
     * Formats a compartment ID for display: replaces underscores with spaces,
     * capitalises the first letter.
     */
    private String formatId(String id) {
        if (id == null || id.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private boolean hasAnyBreach(ShipFloodingComponent flood) {
        for (int i = 0, n = flood.compartments.size; i < n; i++) {
            if (flood.compartments.get(i).breachArea > 0f) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Event handlers
    // ------------------------------------------------------------------

    private void onCapsize(CapsizeEvent event) {
        capsizeActive = true;
    }

    private void onFloodingStarted(FloodingStartedEvent event) {
        String name = formatId(event.compartmentId);
        alertText = event.fromBreach ? "HULL BREACH: " + name : "FLOODING: " + name;
        alertTimer = 3.0f;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Updates the Stage viewport on window resize. */
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        if (stage != null) { stage.dispose(); stage = null; }
        if (font != null) { font.dispose(); font = null; }
        if (fontTitle != null) { fontTitle.dispose(); fontTitle = null; }
        if (panelBgTexture != null) { panelBgTexture.dispose(); panelBgTexture = null; }
    }
}
