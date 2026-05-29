package com.galacticodyssey.ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.galaxy.faction.FactionData;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@link HullStyle} archetypes and the faction/ethos → style bindings.
 *
 * <p>Resolution order for a faction: explicit {@code factionId → styleId} map,
 * then {@code ethos → styleId} fallback, then {@link HullStyle#defaultStyle()}.
 *
 * <p>Two overloads are provided for each load operation:
 * <ul>
 *   <li>{@code loadX(String path)} – production path that reads from {@link Gdx#files}.</li>
 *   <li>{@code loadX(JsonValue root)} – test-friendly overload that accepts a pre-parsed root.</li>
 * </ul>
 */
public class HullStyleRegistry {

    private final Map<String, HullStyle> styles = new HashMap<>();
    private final Map<String, String> ethosToStyle = new HashMap<>();
    private final Map<String, String> factionToStyle = new HashMap<>();

    // -------------------------------------------------------------------------
    // Style loading
    // -------------------------------------------------------------------------

    /** Loads style definitions from an internal asset path. */
    public void loadStyles(String path) {
        loadStyles(new JsonReader().parse(Gdx.files.internal(path)));
    }

    /** Parses style definitions from a pre-parsed {@link JsonValue} array root. */
    public void loadStyles(JsonValue root) {
        for (JsonValue e = root.child; e != null; e = e.next) {
            HullStyle s = new HullStyle(
                e.getString("id"),
                GeneratorType.valueOf(e.getString("generatorType", "LOFTED")),
                e.getFloat("sectionExponentMin", 2.2f),
                e.getFloat("sectionExponentMax", 4.0f),
                e.getFloat("aspectBiasMin", 0.7f),
                e.getFloat("aspectBiasMax", 1.3f),
                e.getFloat("spineCurvature", 1.0f),
                e.getFloat("panelInsetScale", 0.015f),
                readColors(e.get("baseColors")),
                readColors(e.get("accentColors")),
                readColors(e.get("glowColors")),
                e.getBoolean("ageless", false));
            styles.put(s.id, s);
        }
    }

    private static float[][] readColors(JsonValue arr) {
        if (arr == null || arr.size == 0) {
            return new float[][]{{0.5f, 0.5f, 0.5f}};
        }
        float[][] out = new float[arr.size][];
        int i = 0;
        for (JsonValue c = arr.child; c != null; c = c.next, i++) {
            out[i] = new float[]{c.getFloat(0), c.getFloat(1), c.getFloat(2)};
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Ethos map loading
    // -------------------------------------------------------------------------

    /** Loads the ethos→styleId map from an internal asset path. */
    public void loadEthosMap(String path) {
        loadEthosMap(new JsonReader().parse(Gdx.files.internal(path)));
    }

    /** Parses the ethos→styleId map from a pre-parsed {@link JsonValue} object root. */
    public void loadEthosMap(JsonValue root) {
        for (JsonValue e = root.child; e != null; e = e.next) {
            ethosToStyle.put(e.name, e.asString());
        }
    }

    // -------------------------------------------------------------------------
    // Faction map loading
    // -------------------------------------------------------------------------

    /** Loads the factionId→styleId map from an internal asset path. */
    public void loadFactionMap(String path) {
        loadFactionMap(new JsonReader().parse(Gdx.files.internal(path)));
    }

    /** Parses the factionId→styleId map from a pre-parsed {@link JsonValue} object root. */
    public void loadFactionMap(JsonValue root) {
        for (JsonValue e = root.child; e != null; e = e.next) {
            factionToStyle.put(e.name, e.asString());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the style with the given id, or {@link HullStyle#defaultStyle()} if not found.
     */
    public HullStyle get(String id) {
        HullStyle s = styles.get(id);
        return s != null ? s : HullStyle.defaultStyle();
    }

    /**
     * Resolves the best-matching style for a faction.
     *
     * <p>Priority:
     * <ol>
     *   <li>{@code faction.styleId} explicit override (if non-null and loaded)</li>
     *   <li>Explicit {@code factionId → styleId} map entry</li>
     *   <li>{@code ethos → styleId} fallback map</li>
     *   <li>{@link HullStyle#defaultStyle()}</li>
     * </ol>
     */
    public HullStyle resolve(FactionData faction) {
        if (faction == null) return HullStyle.defaultStyle();

        if (faction.styleId != null) {
            HullStyle s = styles.get(faction.styleId);
            if (s != null) return s;
        }

        String byId = factionToStyle.get(faction.id);
        if (byId != null && styles.containsKey(byId)) return styles.get(byId);

        String byEthos = ethosToStyle.get(faction.ethos.name());
        if (byEthos != null && styles.containsKey(byEthos)) return styles.get(byEthos);

        return HullStyle.defaultStyle();
    }
}
