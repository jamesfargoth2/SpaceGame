// core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java
package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class DebugHudSystem extends EntitySystem implements Disposable {

    private final CoordinateManager coordinateManager;

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

    private boolean visible = true;

    private ImmutableArray<Entity> playerEntities;

    public DebugHudSystem(CoordinateManager coordinateManager) {
        super(10);
        this.coordinateManager = coordinateManager;
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

        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        table.pad(10);

        galaxyPosLabel = new Label("Galaxy: -", style);
        localPosLabel = new Label("Local: -", style);
        velocityLabel = new Label("Velocity: -", style);
        groundLabel = new Label("Ground: -", style);
        stateLabel = new Label("State: -", style);
        staminaLabel = new Label("Stamina: -", style);
        fpsLabel = new Label("FPS: -", style);

        table.add(fpsLabel).left().row();
        table.add(galaxyPosLabel).left().row();
        table.add(localPosLabel).left().row();
        table.add(velocityLabel).left().row();
        table.add(groundLabel).left().row();
        table.add(stateLabel).left().row();
        table.add(staminaLabel).left().row();

        stage.addActor(table);
    }

    @Override
    public void update(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            visible = !visible;
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
            else if (state.isSprinting) stateStr = "sprinting";
            else if (state.isCrouching) stateStr = "crouching";
            else stateStr = "walking";
            stateLabel.setText("State: " + stateStr);

            staminaLabel.setText(String.format("Stamina: %.0f / %.0f", state.currentStamina, state.maxStamina));
        }

        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        stage.act(deltaTime);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
    }
}
