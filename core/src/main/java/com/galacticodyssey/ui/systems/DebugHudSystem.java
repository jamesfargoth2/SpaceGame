// core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java
package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.core.events.InteractionPromptEvent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class DebugHudSystem extends EntitySystem implements Disposable {

    private final CoordinateManager coordinateManager;

    private String pendingPrompt = "";
    private boolean promptVisible = false;

    private SpriteBatch batch;
    private Stage stage;
    private BitmapFont font;
    private Label galaxyPosLabel;
    private Label localPosLabel;
    private Label velocityLabel;
    private Label groundLabel;
    private Label stateLabel;
    private Label staminaLabel;
    private Label fpsLabel;
    private Label modeLabel;
    private Label shipSpeedLabel;
    private Label weaponLabel;
    private Label leanLabel;
    private Label promptLabel;
    private Label terrainDebugLabel;
    private Table debugTable;
    private BitmapFont promptFont;

    private boolean visible = true;

    private ImmutableArray<Entity> playerEntities;

    public DebugHudSystem(CoordinateManager coordinateManager, EventBus eventBus) {
        super(10);
        this.coordinateManager = coordinateManager;
        eventBus.subscribe(InteractionPromptEvent.class, e -> {
            pendingPrompt = e.promptText;
            promptVisible = e.visible;
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class, TransformComponent.class, MovementStateComponent.class).get());
    }

    public void initialize() {
        batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport(), batch);
        font = new BitmapFont();
        font.setColor(Color.WHITE);

        Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);

        debugTable = new Table();
        debugTable.top().left();
        debugTable.setFillParent(true);
        debugTable.pad(10);

        galaxyPosLabel = new Label("Galaxy: -", style);
        localPosLabel = new Label("Local: -", style);
        velocityLabel = new Label("Velocity: -", style);
        groundLabel = new Label("Ground: -", style);
        stateLabel = new Label("State: -", style);
        staminaLabel = new Label("Stamina: -", style);
        fpsLabel = new Label("FPS: -", style);
        modeLabel = new Label("Mode: -", style);
        shipSpeedLabel = new Label("Ship: -", style);

        debugTable.add(fpsLabel).left().row();
        debugTable.add(modeLabel).left().row();
        debugTable.add(shipSpeedLabel).left().row();
        debugTable.add(galaxyPosLabel).left().row();
        debugTable.add(localPosLabel).left().row();
        debugTable.add(velocityLabel).left().row();
        debugTable.add(groundLabel).left().row();
        debugTable.add(stateLabel).left().row();
        weaponLabel = new Label("Weapon: -", style);
        leanLabel = new Label("Lean: -", style);
        terrainDebugLabel = new Label("Terrain: -", style);
        debugTable.add(staminaLabel).left().row();
        debugTable.add(weaponLabel).left().row();
        debugTable.add(leanLabel).left().row();
        debugTable.add(terrainDebugLabel).left().row();

        stage.addActor(debugTable);

        promptFont = new BitmapFont();
        promptFont.getData().setScale(1.5f);
        promptLabel = new Label("", new Label.LabelStyle(promptFont, Color.YELLOW));
        Table promptTable = new Table();
        promptTable.setFillParent(true);
        promptTable.center().bottom();
        promptTable.padBottom(120);
        promptTable.add(promptLabel).center();
        stage.addActor(promptTable);
    }

    @Override
    public void update(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            visible = !visible;
            if (debugTable != null) debugTable.setVisible(visible);
        }

        if (promptLabel != null) {
            promptLabel.setText(promptVisible ? pendingPrompt : "");
        }

        if (!visible) return;

        if (playerEntities.size() > 0) {
            Entity player = playerEntities.first();
            TransformComponent transform = player.getComponent(TransformComponent.class);
            MovementStateComponent state = player.getComponent(MovementStateComponent.class);

            double[] galaxy = coordinateManager.toGalaxySpace(transform.position);
            galaxyPosLabel.setText(String.format("Galaxy: %.2f, %.2f, %.2f", galaxy[0], galaxy[1], galaxy[2]));
            localPosLabel.setText(String.format("Local: %.2f, %.2f, %.2f",
                transform.position.x, transform.position.y, transform.position.z));
            velocityLabel.setText(String.format("Velocity: %.2f m/s", state.currentSpeed));
            groundLabel.setText("Ground: " + state.isGrounded);

            String stateStr;
            if (!state.isGrounded) stateStr = "airborne";
            else if (state.isExhausted) stateStr = "exhausted";
            else if (state.isSprinting) stateStr = "sprinting";
            else if (state.isCrouching) stateStr = "crouching";
            else stateStr = "walking";
            if (state.slopeAngle > 1f) stateStr += String.format(" (slope %.0f°)", state.slopeAngle);
            stateLabel.setText("State: " + stateStr);

            staminaLabel.setText(String.format("Stamina: %.0f / %.0f%s",
                state.currentStamina, state.maxStamina, state.isExhausted ? " [EXHAUSTED]" : ""));

            WeaponInventoryComponent inventory = player.getComponent(WeaponInventoryComponent.class);
            RangedWeaponComponent ranged = player.getComponent(RangedWeaponComponent.class);
            if (inventory != null && ranged != null && !inventory.isActiveSlotMelee()) {
                String slot = inventory.getActiveSlot().name();
                weaponLabel.setText(String.format("Weapon [%s]: %s %d/%d",
                    slot, ranged.firingMode, ranged.currentAmmo, ranged.magSize));
            } else if (inventory != null) {
                weaponLabel.setText("Weapon [MELEE]");
            }

            FPSCameraComponent cam = player.getComponent(FPSCameraComponent.class);
            if (cam != null && Math.abs(cam.leanAngle) > 0.1f) {
                leanLabel.setText(String.format("Lean: %.1f°", cam.leanAngle));
            } else {
                leanLabel.setText("Lean: -");
            }

            PlayerStateComponent playerState = player.getComponent(PlayerStateComponent.class);
            if (playerState != null) {
                modeLabel.setText("Mode: " + playerState.currentMode);
                if (playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING
                    && playerState.currentShip != null) {
                    PhysicsBodyComponent shipPhys = playerState.currentShip.getComponent(PhysicsBodyComponent.class);
                    if (shipPhys != null && shipPhys.body != null) {
                        float speed = shipPhys.body.getLinearVelocity().len();
                        shipSpeedLabel.setText(String.format("Ship Speed: %.1f m/s", speed));
                    }
                } else {
                    shipSpeedLabel.setText("Ship: -");
                }
            }
        }

        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());
    }

    public void setTerrainDebug(String text) {
        if (terrainDebugLabel != null) terrainDebugLabel.setText(text);
    }

    public void render(float delta) {
        if (stage == null) return;
        stage.act(delta);
        stage.draw();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
        if (promptFont != null) promptFont.dispose();
    }
}
