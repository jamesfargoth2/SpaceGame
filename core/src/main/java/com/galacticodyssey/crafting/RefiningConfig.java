package com.galacticodyssey.crafting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Tunable parameters for the refining yield calculation.
 * Values can be loaded from {@code data/crafting/refining_config.json}
 * at runtime, or constructed directly for testing.
 *
 * <p>Yield formula: {@code floor(baseQuantity * (1.0 + skillLevel * yieldBonusPerLevel))},
 * with a floor guarantee that yield never drops below the base quantity.</p>
 */
public class RefiningConfig {

    /** Fractional yield bonus added per skill level (e.g. 0.005 = +0.5% per level). */
    public float yieldBonusPerLevel = 0.005f;

    /** The skill name used for yield bonus lookups (e.g. "engineering"). */
    public String yieldSkillName = "engineering";

    /** Default constructor with built-in defaults. */
    public RefiningConfig() {
    }

    /**
     * Constructs a config with explicit values.
     *
     * @param yieldBonusPerLevel fractional bonus per skill level
     * @param yieldSkillName     skill name for yield lookups
     */
    public RefiningConfig(float yieldBonusPerLevel, String yieldSkillName) {
        this.yieldBonusPerLevel = yieldBonusPerLevel;
        this.yieldSkillName = yieldSkillName;
    }

    /**
     * Calculates the adjusted output quantity based on the base recipe
     * quantity and the crafter's skill level.
     *
     * @param baseQuantity the recipe's base output quantity
     * @param skillLevel   the crafter's level in the yield skill
     * @return the adjusted quantity, always at least {@code baseQuantity}
     */
    public int calculateYield(int baseQuantity, int skillLevel) {
        float multiplier = 1.0f + skillLevel * yieldBonusPerLevel;
        return Math.max(baseQuantity, (int) Math.floor(baseQuantity * multiplier));
    }

    /**
     * Loads a {@link RefiningConfig} from the JSON data file at
     * {@code data/crafting/refining_config.json}. Requires a live
     * libGDX file-handle context (i.e. a running application).
     *
     * @return a populated RefiningConfig
     */
    public static RefiningConfig loadFromFile() {
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/crafting/refining_config.json"));
        RefiningConfig config = new RefiningConfig();
        config.yieldBonusPerLevel = root.getFloat("yieldBonusPerLevel", 0.005f);
        config.yieldSkillName = root.getString("yieldSkillName", "engineering");
        return config;
    }
}
