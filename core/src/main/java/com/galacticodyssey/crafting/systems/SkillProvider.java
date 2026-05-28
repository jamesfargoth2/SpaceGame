package com.galacticodyssey.crafting.systems;

import com.badlogic.ashley.core.Entity;

/**
 * Abstraction for querying an entity's skill level by name.
 * Decouples the refining system from the RPG stat system so that
 * skill lookups can be swapped, stubbed, or wired to a future
 * character-progression module without changing crafting code.
 */
public interface SkillProvider {

    /**
     * Returns the skill level of the given entity for the named skill.
     *
     * @param entity    the entity whose skill is being queried
     * @param skillName the canonical skill name (e.g. "engineering")
     * @return the entity's level in that skill, or 0 if unknown
     */
    int getSkillLevel(Entity entity, String skillName);
}
