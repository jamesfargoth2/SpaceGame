package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.Entity;

/**
 * Default implementation that always returns skill level 0.
 * Used as a placeholder until the RPG stat / character-progression
 * system is wired in.
 */
public class DefaultSkillProvider implements SkillProvider {

    @Override
    public int getSkillLevel(Entity entity, String skillName) {
        return 0;
    }
}
