package com.galacticodyssey.core.scene;

import com.badlogic.ashley.core.Component;

/**
 * Marks an entity (player, active ship) that survives scene swaps. On transition it is
 * re-tagged to the destination scene instead of being unloaded with the source scene.
 */
public class PersistentSceneMemberComponent implements Component {
}
