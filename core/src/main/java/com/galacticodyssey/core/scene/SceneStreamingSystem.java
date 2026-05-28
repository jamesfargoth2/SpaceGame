package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;

/**
 * Per-frame bridge between gameplay and the {@link SceneManager}: evaluates automatic
 * distance-based transitions (with hysteresis) and pumps the manager's transition controller.
 * Player position is pushed in via {@link #setPlayerPosition}. For Sub-project A it carries a
 * single DEEP_SPACE&lt;-&gt;ORBITAL trigger; explicit transitions (land/dock/board) arrive via
 * {@link SceneManager#requestTransition} from gameplay systems.
 */
public final class SceneStreamingSystem extends EntitySystem {

    private final SceneManager sceneManager;
    private final Vector3 playerPosition = new Vector3();

    private SceneDistanceTrigger orbitalTrigger;
    private final Vector3 orbitalBodyLocalPos = new Vector3();
    private double[] orbitalAnchor;
    private double[] deepSpaceAnchor;

    public SceneStreamingSystem(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void setPlayerPosition(Vector3 position) {
        playerPosition.set(position);
    }

    /** Configure the deep-space &lt;-&gt; orbital auto trigger. */
    public void configureOrbitalTrigger(SceneDistanceTrigger trigger, Vector3 bodyLocalPos,
                                        double[] orbitalAnchor, double[] deepSpaceAnchor) {
        this.orbitalTrigger = trigger;
        this.orbitalBodyLocalPos.set(bodyLocalPos);
        this.orbitalAnchor = orbitalAnchor;
        this.deepSpaceAnchor = deepSpaceAnchor;
    }

    @Override
    public void update(float deltaTime) {
        evaluateAutoTriggers();
        sceneManager.update(deltaTime);
    }

    private void evaluateAutoTriggers() {
        if (orbitalTrigger == null) return;
        if (!sceneManager.getController().isIdle()) return;
        Scene primary = sceneManager.getPrimaryScene();
        if (primary == null) return;

        boolean inside = primary.type == SceneType.ORBITAL;
        float distance = playerPosition.dst(orbitalBodyLocalPos);
        boolean shouldBeInside = orbitalTrigger.shouldBeInside(inside, distance);

        if (shouldBeInside && !inside) {
            sceneManager.requestTransition(new SceneTransitionRequest(SceneType.ORBITAL, orbitalAnchor));
        } else if (!shouldBeInside && inside) {
            sceneManager.requestTransition(new SceneTransitionRequest(SceneType.DEEP_SPACE, deepSpaceAnchor));
        }
    }
}
