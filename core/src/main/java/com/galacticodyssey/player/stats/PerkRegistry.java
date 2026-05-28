package com.galacticodyssey.player.stats;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.galacticodyssey.player.components.PlayerStatsComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Loads and indexes player perk-tree content. Effects are applied via
 * {@code applyModifiers} (data-driven) and {@code has} (named-id specials),
 * added in later tasks.
 */
public final class PerkRegistry {

    private final ObjectMap<String, PerkNodeDef> byId = new ObjectMap<>();
    private final ObjectMap<RealTimeSkill, PerkTree> trees = new ObjectMap<>();

    private PerkRegistry() {
        for (RealTimeSkill s : RealTimeSkill.values()) trees.put(s, new PerkTree(s));
    }

    /** Production loader (libGDX file backend required). */
    public static PerkRegistry fromFile(FileHandle handle) {
        return parse(handle.readString("UTF-8"));
    }

    /** Test/headless loader — reads from the JVM classpath, no GL context needed. */
    public static PerkRegistry fromClasspath(String resourcePath) {
        try (InputStream in = PerkRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("resource not found: " + resourcePath);
            try (Scanner scanner = new Scanner(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                scanner.useDelimiter("\\A");
                return parse(scanner.hasNext() ? scanner.next() : "");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to load perk trees: " + resourcePath, e);
        }
    }

    private static PerkRegistry parse(String jsonText) {
        PerkRegistry reg = new PerkRegistry();
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        // PerkNodeDef uses java.util.List<String> and java.util.List<PerkModifier>;
        // libGDX Json needs element type hints to deserialise these correctly.
        json.setElementType(PerkNodeDef.class, "prerequisitePerkIds", String.class);
        json.setElementType(PerkNodeDef.class, "modifiers", PerkModifier.class);
        PerkNodeDef[] nodes = json.fromJson(PerkNodeDef[].class, jsonText);
        if (nodes == null) return reg;
        for (PerkNodeDef node : nodes) {
            if (reg.byId.containsKey(node.id)) {
                throw new IllegalArgumentException("Duplicate perk id: " + node.id);
            }
            reg.byId.put(node.id, node);
            RealTimeSkill skill;
            try {
                skill = RealTimeSkill.valueOf(node.treeSkill);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "unknown treeSkill '" + node.treeSkill + "' on perk '" + node.id + "'", e);
            }
            reg.trees.get(skill).nodes.add(node);
        }
        return reg;
    }

    public PerkNodeDef get(String perkId) {
        return byId.get(perkId);
    }

    public Array<PerkNodeDef> getTree(RealTimeSkill skill) {
        return new Array<>(trees.get(skill).nodes);
    }

    /**
     * Folds every owned perk's modifiers for {@code target} onto {@code base}:
     * result = (base + sum(ADD values)) * product(MULT values).
     */
    public float applyModifiers(PlayerStatsComponent stats, PerkTarget target, float base) {
        float add = 0f;
        float mult = 1f;
        for (String perkId : stats.perks) {
            PerkNodeDef node = byId.get(perkId);
            if (node == null) continue;
            for (PerkModifier mod : node.modifiers) {
                if (mod.target != target) continue;
                if (mod.op == ModifierOp.ADD) add  += mod.value;
                else                          mult *= mod.value;
            }
        }
        return (base + add) * mult;
    }

    /** True if the player owns a perk carrying the given specialEffectId. */
    public boolean has(PlayerStatsComponent stats, String specialEffectId) {
        for (String perkId : stats.perks) {
            PerkNodeDef node = byId.get(perkId);
            if (node != null && specialEffectId.equals(node.specialEffectId)) return true;
        }
        return false;
    }

    /** True iff not already owned, skill-level gate met, and all prerequisite perks owned. */
    public boolean canSelect(PlayerStatsComponent stats, String perkId) {
        PerkNodeDef node = byId.get(perkId);
        if (node == null) return false;
        if (stats.perks.contains(perkId, false)) return false;
        RealTimeSkill skill = RealTimeSkill.valueOf(node.treeSkill);
        if (stats.realTimeSkills.get(skill).level < node.requiredSkillLevel) return false;
        for (String prereq : node.prerequisitePerkIds) {
            if (!stats.perks.contains(prereq, false)) return false;
        }
        return true;
    }
}
