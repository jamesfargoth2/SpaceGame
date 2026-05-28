package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.shipbuilder.events.EnterDrydockEvent;
import com.galacticodyssey.shipbuilder.events.ExitDrydockEvent;
import com.galacticodyssey.shipbuilder.events.ShipDesignCommittedEvent;

public class DrydockScreen implements Screen {
    private final Game game;
    private final Screen previousScreen;
    private final EventBus eventBus;
    private final ShipDesign design;
    private final DrydockScene scene;
    private final BuilderPhaseController phaseController;
    private final ShipDesignValidator validator;
    private final BlueprintRegistry blueprintRegistry;
    private final BuildCostCalculator costCalculator;

    private PerspectiveCamera camera;
    private Stage uiStage;
    private InputMultiplexer inputMultiplexer;

    public DrydockScreen(Game game, Screen previousScreen, EventBus eventBus,
                         ShipDesign design, BlueprintRegistry blueprintRegistry) {
        this.game = game;
        this.previousScreen = previousScreen;
        this.eventBus = eventBus;
        this.design = design;
        this.validator = new ShipDesignValidator();
        this.phaseController = new BuilderPhaseController(design, validator, eventBus);
        this.scene = new DrydockScene();
        this.blueprintRegistry = blueprintRegistry;
        this.costCalculator = new BuildCostCalculator();
    }

    @Override
    public void show() {
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 5, 20);
        camera.lookAt(0, 0, 0);
        camera.near = 0.1f;
        camera.far = 500f;
        camera.update();

        uiStage = new Stage(new ScreenViewport());
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(uiStage);
        Gdx.input.setInputProcessor(inputMultiplexer);

        scene.markMeshDirty();
        eventBus.publish(new EnterDrydockEvent(design));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        scene.updateMesh(design);
        scene.render(camera);

        uiStage.act(delta);
        uiStage.draw();
    }

    public void commitBuild() {
        eventBus.publish(new ShipDesignCommittedEvent(design));
        exitDrydock(true);
    }

    public void cancelBuild() {
        exitDrydock(false);
    }

    private void exitDrydock(boolean committed) {
        eventBus.publish(new ExitDrydockEvent(committed));
        game.setScreen(previousScreen);
    }

    public ShipDesign getDesign() { return design; }
    public BuilderPhaseController getPhaseController() { return phaseController; }
    public BlueprintRegistry getBlueprintRegistry() { return blueprintRegistry; }
    public BuildCostCalculator getCostCalculator() { return costCalculator; }
    public PerspectiveCamera getCamera() { return camera; }
    public Stage getUiStage() { return uiStage; }
    public InputMultiplexer getInputMultiplexer() { return inputMultiplexer; }
    public DrydockScene getScene() { return scene; }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        uiStage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        scene.dispose();
        uiStage.dispose();
    }
}
