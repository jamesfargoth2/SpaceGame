package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.CockpitGeometryBuilder;
import com.galacticodyssey.ship.components.CockpitRenderComponent;
import com.galacticodyssey.ui.events.CockpitHUDHideEvent;
import com.galacticodyssey.ui.events.CockpitHUDShowEvent;

/**
 * Renders the 3D cockpit interior mesh when the player is in piloting mode.
 * <p>
 * Subscribes to {@link CockpitHUDShowEvent} / {@link CockpitHUDHideEvent} to
 * create and dispose the cockpit model lazily. The {@link #render(ModelBatch, PerspectiveCamera)}
 * method must be called from {@code GameScreen} after the main scene has been drawn;
 * it clears the depth buffer first so the cockpit always renders on top of all other
 * geometry, then positions the cockpit model at the camera origin so it moves with
 * the player's viewpoint.
 * </p>
 * <p>
 * Priority 12 — runs after flight/physics systems and before HUD overlays.
 * </p>
 */
public class CockpitModelSystem extends EntitySystem implements Disposable {

    private final EventBus eventBus;

    private final ComponentMapper<PlayerStateComponent> stateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private final ComponentMapper<CockpitRenderComponent> cockpitMapper =
        ComponentMapper.getFor(CockpitRenderComponent.class);

    /** Entities with a player tag — kept for potential future per-frame queries. */
    private ImmutableArray<Entity> playerEntities;

    /** The ship entity that currently owns the active cockpit, or {@code null}. */
    private Entity activeShip;

    /** Whether a cockpit is currently mounted and should be rendered. */
    private boolean cockpitActive;

    /**
     * @param eventBus the shared event bus; subscriptions are registered immediately
     */
    public CockpitModelSystem(EventBus eventBus) {
        super(12);
        this.eventBus = eventBus;
        eventBus.subscribe(CockpitHUDShowEvent.class, this::onShow);
        eventBus.subscribe(CockpitHUDHideEvent.class, this::onHide);
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class, PlayerStateComponent.class).get());
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private void onShow(CockpitHUDShowEvent event) {
        activeShip = event.ship;
        cockpitActive = true;

        CockpitRenderComponent render = cockpitMapper.get(activeShip);
        if (render == null) {
            render = new CockpitRenderComponent();
            render.cockpitModel       = CockpitGeometryBuilder.build(CockpitGeometryBuilder.SizeClass.SMALL);
            render.cockpitInstance    = new ModelInstance(render.cockpitModel);
            render.cockpitEnvironment = createCockpitEnvironment();
            render.visible            = true;
            activeShip.add(render);
        } else {
            render.visible = true;
        }
    }

    private void onHide(CockpitHUDHideEvent event) {
        if (activeShip != null) {
            CockpitRenderComponent render = cockpitMapper.get(activeShip);
            if (render != null) {
                render.visible = false;
                if (render.cockpitModel != null) {
                    render.cockpitModel.dispose();
                    render.cockpitModel = null;
                }
                activeShip.remove(CockpitRenderComponent.class);
            }
        }
        cockpitActive = false;
        activeShip    = null;
    }

    // -------------------------------------------------------------------------
    // Environment
    // -------------------------------------------------------------------------

    private static Environment createCockpitEnvironment() {
        Environment env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.2f, 0.25f, 0.3f, 1f));
        env.add(new DirectionalLight().set(0.4f, 0.5f, 0.6f, 0f, -1f, -0.5f));
        return env;
    }

    // -------------------------------------------------------------------------
    // Render pass — called explicitly by GameScreen
    // -------------------------------------------------------------------------

    /**
     * Renders the cockpit mesh over the current frame.
     * <p>
     * Clears the depth buffer so the cockpit is drawn in front of all scene
     * geometry regardless of distance, then positions the mesh at the camera
     * origin (offset downward so the pilot's eye level lands naturally).
     * </p>
     *
     * @param modelBatch the shared model batch (may be in any state; will be
     *                   flushed via begin/end inside this call)
     * @param camera     the active perspective camera
     */
    public void render(ModelBatch modelBatch, PerspectiveCamera camera) {
        if (!cockpitActive || activeShip == null) return;

        final CockpitRenderComponent render = cockpitMapper.get(activeShip);
        if (render == null || !render.visible || render.cockpitInstance == null) return;

        // Draw cockpit on top of everything by clearing the depth buffer first.
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

        // Anchor cockpit to camera position, then drop slightly so the pilot sits at eye level.
        render.cockpitInstance.transform
            .setToTranslation(camera.position)
            .translate(0, -1.2f, 0);

        modelBatch.begin(camera);
        modelBatch.render(render.cockpitInstance, render.cockpitEnvironment);
        modelBatch.end();
    }

    // -------------------------------------------------------------------------
    // ECS update — rendering is driven by GameScreen via render()
    // -------------------------------------------------------------------------

    @Override
    public void update(float deltaTime) {
        // Intentionally empty: cockpit rendering happens in render(), not the ECS update loop.
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        if (activeShip != null) {
            final CockpitRenderComponent render = cockpitMapper.get(activeShip);
            if (render != null && render.cockpitModel != null) {
                render.cockpitModel.dispose();
                render.cockpitModel = null;
            }
        }
    }
}
