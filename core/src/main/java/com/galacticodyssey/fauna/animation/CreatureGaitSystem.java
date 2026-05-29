package com.galacticodyssey.fauna.animation;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureAnimationComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;
import com.galacticodyssey.fauna.components.CreatureRenderComponent;
import com.galacticodyssey.fauna.rig.Bone;
import com.galacticodyssey.fauna.rig.CreatureRig;

public class CreatureGaitSystem extends IteratingSystem {

    private final ComponentMapper<CreatureAnimationComponent> animMapper =
        ComponentMapper.getFor(CreatureAnimationComponent.class);
    private final ComponentMapper<TransformComponent> txMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<CreatureComponent> creatureMapper =
        ComponentMapper.getFor(CreatureComponent.class);
    private final ComponentMapper<CreatureRenderComponent> renderMapper =
        ComponentMapper.getFor(CreatureRenderComponent.class);

    public CreatureGaitSystem(int priority) {
        super(Family.all(CreatureAnimationComponent.class, TransformComponent.class,
                         CreatureComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CreatureAnimationComponent anim = animMapper.get(entity);
        TransformComponent tx = txMapper.get(entity);
        CreatureComponent creature = creatureMapper.get(entity);

        if (anim.rig == null || anim.gaitController == null) return;

        anim.params.deltaTime = deltaTime;
        anim.params.elapsedTime += deltaTime;
        anim.params.position.set(tx.position);
        anim.params.sizeMultiplier = creature.spec.sizeMultiplier;

        anim.gaitController.update(anim.rig, anim.params);

        CreatureRenderComponent render = renderMapper.get(entity);
        if (render != null && render.skinnedInstance != null) {
            applyRigToModel(anim.rig, render.skinnedInstance);
        }
    }

    private void applyRigToModel(CreatureRig rig, ModelInstance instance) {
        for (int i = 0; i < rig.boneCount(); i++) {
            Bone bone = rig.getBone(i);
            Node node = instance.getNode(bone.name);
            if (node != null) {
                node.localTransform.set(bone.currentPose);
            }
        }
        instance.calculateTransforms();
    }
}
