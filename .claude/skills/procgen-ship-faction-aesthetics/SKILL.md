---
name: procgen-ship-faction-aesthetics
description: >
  Enforces per-faction ship visual language for the five lore factions
  (Federation, Vaun, Zul-Kiri, Null-System, zeeLee) plus generic fallbacks,
  covering hull silhouette archetypes, signature feature geometry (Vaun spinal
  lance, Federation shield rings, Zul-Kiri mismatched panels, Null-System
  geometric separations, zeeLee crystal spires), cross-section and spine
  profiles, hardpoint socket layouts, nacelle types, fin geometry, panel-line
  rhythm, and faction-specific vertex-color palettes for a libGDX 3D
  deferred-PBR space game with vertex-color-only rendering (no UV maps).
  Use this skill whenever writing or modifying: faction HullStyle definitions,
  ship silhouette selection per faction, signature feature placement, faction
  colour palettes for ships, hardpoint socket layouts, nacelle geometry rules,
  or any code that makes ships from different factions visually distinguishable.
  Read together with procgen-ship-hull-geometry which implements the generator
  paths this skill parameterises. Also triggers when adding a new faction,
  creating a flagship or named ship for a faction, or adjusting any faction's
  ship aesthetic direction.
---

# Procedural Ship Faction Aesthetics

## The Five Lore Factions — Design Intent

| Faction | Silhouette Feel | Generator Path | Key Tell |
|---|---|---|---|
| **Federation** | Modular, symmetric, co-operative | LOFTED | Visible shield ring mounts; boxy module clusters at midships |
| **Vaun** | Brutalist blade; aggressive forward rake | LOFTED | Spinal lance protrudes from nose; asymmetric wedge cross-section |
| **Zul-Kiri** | Frankenstein assembled; mismatched sections | LOFTED (mixed) | Colour mismatch between sections; visible weld seams; irregular profile |
| **Null-System** | Monolith geometric; cold and minimal | FACETED | Sections separated by visible gaps; no curves anywhere |
| **zeeLee** | Crystal growth; organic and alien | CRYSTAL | Asymmetric spire branches; iridescent vertex colour |

---

## HullStyle Data Structure

```java
public class HullStyle {
    public HullStyleId        id;
    public HullGeneratorPath  generatorPath;

    // Cross-section profiles for lofted path
    public CrossSectionProfile noseProfile;
    public CrossSectionProfile midProfile;
    public CrossSectionProfile tailProfile;

    // Spine curve parameters
    public float              dorsalArcFactor;     // 0 = straight; 0.08 = Vaun rake
    public boolean            allowSpineKink;      // Zul-Kiri only

    // Appendages
    public int                nacelleCount;        // 0, 2, or 4
    public float              nacelleScale;        // relative to beam
    public NacelleShape       nacelleShape;
    public boolean            hasFins;
    public float              finScale;
    public float              sensorDomeScale;

    // Signature feature
    public SignatureFeature   signatureFeature;    // SPINAL_LANCE, SHIELD_RINGS, etc.

    // Hardpoints
    public Array<HardpointSocket> hardpointSockets; // {spineT, angle, size}

    // Vertex colour palette
    public Color              primaryAlbedo;
    public Color              secondaryAlbedo;
    public Color              accentAlbedo;
    public MarkingType        markingType;
    public float[]            ringPositions;       // for RING_BAND marking type
    public float              stripeAngle;         // for LONGITUDINAL_STRIPE
    public float              stripeHalfWidth;
    public float              stripeOpacity;

    // Engine
    public Color              engineGlowColor;
    public float              engineGlowIntensity;
    public Color              runningLightColor;

    // Wear
    public float              sectionBreakDarken;  // 0.7–0.95
    public boolean            ageless;             // no wear applied (Null, zeeLee)
}
```

---

## HullStyle Registry

```java
public class HullStyleRegistry {

    private static final Map<HullStyleId, HullStyle> STYLES = new HashMap<>();

    static {
        register(buildFederationStyle());
        register(buildVaunStyle());
        register(buildZulKiriStyle());
        register(buildNullSystemStyle());
        register(buildZeeLeeStyle());
        register(buildPirateStyle());
        register(buildFrontierStyle());
        register(buildMerchantStyle());
    }

    public static HullStyle get(HullStyleId id) {
        return STYLES.getOrDefault(id, STYLES.get(HullStyleId.MERCHANT));
    }

    /** Return the canonical style for a given faction + hull class. */
    public static HullStyleId styleFor(FactionId faction, HullClass cls, Random rng) {
        switch (faction) {
            case FEDERATION:   return HullStyleId.FEDERATION;
            case VAUN:         return HullStyleId.VAUN;
            case ZUL_KIRI:     return HullStyleId.ZUL_KIRI;
            case NULL_SYSTEM:  return HullStyleId.NULL_SYSTEM;
            case ZELEE:        return HullStyleId.ZELEE;
            case PIRATE:       return rng.nextFloat() < 0.6f ? HullStyleId.PIRATE : HullStyleId.FRONTIER;
            default:           return shipRoleStyle(cls, rng);
        }
    }

    private static HullStyleId shipRoleStyle(HullClass cls, Random rng) {
        if (cls == HullClass.FREIGHTER || cls == HullClass.TANKER)
            return HullStyleId.MERCHANT;
        return rng.nextFloat() < 0.5f ? HullStyleId.FRONTIER : HullStyleId.MERCHANT;
    }
}
```

---

## Federation Style

```java
private static HullStyle buildFederationStyle() {
    HullStyle s             = new HullStyle();
    s.id                    = HullStyleId.FEDERATION;
    s.generatorPath         = HullGeneratorPath.LOFTED;

    // Profile: smooth round fuselage, modular box midships
    s.noseProfile           = CrossSectionProfile.ELLIPSE_WIDE;
    s.midProfile            = CrossSectionProfile.ROUNDED_RECT;  // boxy module cluster
    s.tailProfile           = CrossSectionProfile.CIRCLE;

    // Spine: very slight dorsal arc only on large ships
    s.dorsalArcFactor       = 0.01f;
    s.allowSpineKink        = false;

    // Twin symmetric nacelles, medium scale
    s.nacelleCount          = 2;
    s.nacelleScale          = 0.22f;
    s.nacelleShape          = NacelleShape.CYLINDRICAL_FLAT_CAP;
    s.hasFins               = false;
    s.sensorDomeScale       = 0.12f;

    // Signature: shield ring mounts banded around hull at t=0.35 and t=0.55
    s.signatureFeature      = SignatureFeature.SHIELD_RINGS;
    s.ringPositions         = new float[]{ 0.35f, 0.55f };

    // Hardpoints: lateral pairs, symmetric
    s.hardpointSockets      = Array.with(
        new HardpointSocket(0.25f, MathUtils.PI / 2f,   HardpointSize.MEDIUM),  // port
        new HardpointSocket(0.25f, -MathUtils.PI / 2f,  HardpointSize.MEDIUM),  // starboard
        new HardpointSocket(0.45f, MathUtils.PI / 2f,   HardpointSize.LARGE),
        new HardpointSocket(0.45f, -MathUtils.PI / 2f,  HardpointSize.LARGE),
        new HardpointSocket(0.10f, 0f,                  HardpointSize.SMALL)   // dorsal nose
    );

    // Colours: white/light grey primary, blue-grey accent
    s.primaryAlbedo         = Color.valueOf("D8DDE8");
    s.secondaryAlbedo       = Color.valueOf("8898B0");
    s.accentAlbedo          = Color.valueOf("4477CC");
    s.markingType           = MarkingType.RING_BAND;
    s.stripeOpacity         = 0.80f;

    s.engineGlowColor       = Color.valueOf("88AAFF");
    s.engineGlowIntensity   = 0.7f;
    s.runningLightColor     = Color.valueOf("FFFFFF");
    s.sectionBreakDarken    = 0.82f;
    s.ageless               = false;
    return s;
}
```

---

## Vaun Style

```java
private static HullStyle buildVaunStyle() {
    HullStyle s             = new HullStyle();
    s.id                    = HullStyleId.VAUN;
    s.generatorPath         = HullGeneratorPath.LOFTED;

    // Profile: blade-like wedge that tapers to a point at the nose
    s.noseProfile           = CrossSectionProfile.DIAMOND;
    s.midProfile            = CrossSectionProfile.ASYMMETRIC_WEDGE; // flat ventral, angled dorsal
    s.tailProfile           = CrossSectionProfile.ELLIPSE_TALL;

    // Aggressive forward dorsal rake
    s.dorsalArcFactor       = 0.08f;
    s.allowSpineKink        = false;

    // No conventional nacelles — engine banks are integrated into the tail section
    s.nacelleCount          = 0;
    s.nacelleScale          = 0f;
    s.nacelleShape          = NacelleShape.INTEGRATED;
    s.hasFins               = true;    // large swept-back ventral fins
    s.finScale              = 0.45f;
    s.sensorDomeScale       = 0.08f;   // minimised; Vaun distrust sensors

    // Signature: SPINAL_LANCE — a protruding spire at t=-0.05 (ahead of nose)
    s.signatureFeature      = SignatureFeature.SPINAL_LANCE;

    // Hardpoints: dorsal spine mounts, forward-biased
    s.hardpointSockets      = Array.with(
        new HardpointSocket(0.15f, 0f,  HardpointSize.LARGE),   // forward dorsal
        new HardpointSocket(0.30f, 0f,  HardpointSize.LARGE),
        new HardpointSocket(0.50f, 0f,  HardpointSize.MEDIUM),
        new HardpointSocket(0.20f, MathUtils.PI, HardpointSize.MEDIUM), // ventral
        new HardpointSocket(0.35f, MathUtils.PI, HardpointSize.SMALL)
    );

    // Colours: dark charcoal primary, blood-red accent
    s.primaryAlbedo         = Color.valueOf("252830");
    s.secondaryAlbedo       = Color.valueOf("1A1C22");
    s.accentAlbedo          = Color.valueOf("990011");
    s.markingType           = MarkingType.LONGITUDINAL_STRIPE;
    s.stripeAngle           = 0f;           // dorsal stripe
    s.stripeHalfWidth       = 0.08f;
    s.stripeOpacity         = 0.70f;

    s.engineGlowColor       = Color.valueOf("FF3311");
    s.engineGlowIntensity   = 1.0f;
    s.runningLightColor     = Color.valueOf("FF0000");
    s.sectionBreakDarken    = 0.72f;       // deep, harsh seams
    s.ageless               = false;
    return s;
}
```

---

## Zul-Kiri Style

```java
private static HullStyle buildZulKiriStyle() {
    HullStyle s             = new HullStyle();
    s.id                    = HullStyleId.ZUL_KIRI;
    s.generatorPath         = HullGeneratorPath.LOFTED;

    // Profile: irregular — each section is sampled from a random pool
    // The ZulKiriHullBuilder overrides section profiles per-segment
    s.noseProfile           = CrossSectionProfile.HEXAGON;
    s.midProfile            = CrossSectionProfile.ROUNDED_RECT;
    s.tailProfile           = CrossSectionProfile.ELLIPSE_WIDE;

    // Spine has a random kink at one point (salvaged sections don't align perfectly)
    s.dorsalArcFactor       = 0.02f;
    s.allowSpineKink        = true;  // one random kink, ±8° in pitch

    // 4 asymmetrically placed nacelles (salvaged from different ships)
    s.nacelleCount          = 4;
    s.nacelleScale          = 0.18f;
    s.nacelleShape          = NacelleShape.MIXED;  // each nacelle picks random shape
    s.hasFins               = rng_placeholder_true(); // ~50% have fins
    s.finScale              = 0.25f;
    s.sensorDomeScale       = 0.15f;

    // Signature: MISMATCHED_PANELS — section break colours intentionally clash
    s.signatureFeature      = SignatureFeature.MISMATCHED_PANELS;

    // Hardpoints: haphazard, many small/medium, few large
    s.hardpointSockets      = buildZulKiriHardpoints(); // random distribution, 6–12

    // Colours: no unified palette — each section uses a different hue
    // The ZulKiriColorBaker overrides per-section with random salvage colours
    s.primaryAlbedo         = Color.valueOf("7A6B50");  // fallback only
    s.secondaryAlbedo       = Color.valueOf("445566");
    s.accentAlbedo          = Color.valueOf("CC8833");
    s.markingType           = MarkingType.NONE;         // too messy for coherent markings

    s.engineGlowColor       = Color.valueOf("FFAA33");
    s.engineGlowIntensity   = 0.8f;
    s.runningLightColor     = Color.valueOf("FFFF00");
    s.sectionBreakDarken    = 0.65f;      // very obvious weld seams
    s.ageless               = false;
    return s;
}

/** Zul-Kiri ships bake each hull section in a different salvage colour. */
public class ZulKiriColorBaker {

    public void bakePerSection(MeshBuilder mb, int sectionCount, long seed) {
        Random rng = new Random(seed);
        // Each section gets its own random colour, from a "salvage palette"
        for (int sec = 0; sec < sectionCount; sec++) {
            Color sectionColor = salvageColor(rng);
            float secT0 = sec / (float) sectionCount;
            float secT1 = (sec + 1f) / sectionCount;
            mb.paintVertexRange(secT0, secT1, sectionColor);
        }
    }

    private Color salvageColor(Random rng) {
        // Pick from a curated set of believable salvage colours
        Color[] palette = {
            Color.valueOf("7A6B50"), Color.valueOf("445566"),
            Color.valueOf("335544"), Color.valueOf("663322"),
            Color.valueOf("4455AA"), Color.valueOf("887766"),
            Color.valueOf("556644"), Color.valueOf("774433"),
        };
        return palette[rng.nextInt(palette.length)].cpy()
            .mul(MathUtils.random(rng, 0.85f, 1.15f));
    }
}
```

---

## Null-System Style

```java
private static HullStyle buildNullSystemStyle() {
    HullStyle s             = new HullStyle();
    s.id                    = HullStyleId.NULL_SYSTEM;
    s.generatorPath         = HullGeneratorPath.FACETED;

    // Faceted path — no curved cross-sections
    s.noseProfile           = null; // unused; faceted path uses SectionPrimitive
    s.midProfile            = null;
    s.tailProfile           = null;
    s.dorsalArcFactor       = 0f;   // perfectly straight spine
    s.allowSpineKink        = false;

    // No nacelles — propulsion is internal; manifests as a uniform glow at tail
    s.nacelleCount          = 0;
    s.nacelleShape          = NacelleShape.NONE;
    s.hasFins               = false;
    s.sensorDomeScale       = 0f;   // no visible sensors

    // Signature: GEOMETRIC_SEPARATION — gaps between all hull sections (2–4 m)
    s.signatureFeature      = SignatureFeature.GEOMETRIC_SEPARATION;

    // Hardpoints: flush recessed slots, perfectly aligned to section faces
    s.hardpointSockets      = buildNullHardpoints(); // grid-aligned, symmetric

    // Colours: near-black, monochromatic, zero variation between ships
    s.primaryAlbedo         = Color.valueOf("111318");
    s.secondaryAlbedo       = Color.valueOf("080A0D");
    s.accentAlbedo          = Color.valueOf("334455"); // subtle only
    s.markingType           = MarkingType.NONE;        // no markings ever

    s.engineGlowColor       = Color.valueOf("6688FF");
    s.engineGlowIntensity   = 0.4f;  // subdued
    s.runningLightColor     = Color.valueOf("334455");
    s.sectionBreakDarken    = 1.0f;  // sections fully separate; no seam needed
    s.ageless               = true;  // Null-System ships don't accumulate wear
    return s;
}
```

---

## zeeLee Style

```java
private static HullStyle buildZeeLeeStyle() {
    HullStyle s             = new HullStyle();
    s.id                    = HullStyleId.ZELEE;
    s.generatorPath         = HullGeneratorPath.CRYSTAL;

    // Crystal path — no conventional profiles
    s.noseProfile           = null;
    s.midProfile            = null;
    s.tailProfile           = null;
    s.dorsalArcFactor       = 0f;
    s.allowSpineKink        = false;

    // Appendages are crystal branches; handled by CrystalHullBuilder
    s.nacelleCount          = 0;
    s.nacelleShape          = NacelleShape.CRYSTAL_CLUSTER;
    s.hasFins               = false;
    s.sensorDomeScale       = 0f;

    // Signature: CRYSTAL_SPIRE_BRANCHES — asymmetric growth pattern
    s.signatureFeature      = SignatureFeature.CRYSTAL_SPIRE_BRANCHES;

    // Hardpoints: located at crystal junction points; not pre-defined
    s.hardpointSockets      = new Array<>(); // populated by CrystalHullBuilder

    // Colours: iridescent — vertex colour shifts along hue from tip to root
    // ZeeLeeColorBaker handles the gradient; these are hints only
    s.primaryAlbedo         = Color.valueOf("B0E8FF");  // pale cyan base
    s.secondaryAlbedo       = Color.valueOf("FFB0E8");  // pale magenta for tips
    s.accentAlbedo          = Color.valueOf("EEFFB0");  // yellow-green highlights
    s.markingType           = MarkingType.IRIDESCENT_GRADIENT;

    s.engineGlowColor       = Color.valueOf("AADDFF");
    s.engineGlowIntensity   = 1.2f;  // very bright — crystals glow
    s.runningLightColor     = Color.valueOf("FFFFFF");
    s.sectionBreakDarken    = 0.9f;
    s.ageless               = true;  // zeeLee ships don't accumulate grime
    return s;
}

/** zeeLee ships use an iridescent per-vertex hue gradient. */
public class ZeeLeeColorBaker {

    public void bakeIridescent(MeshBuilder mb, HullStyle style, long seed) {
        Random rng = new Random(seed);
        float hueShift = rng.nextFloat() * 360f; // random starting hue per ship

        for (int vi = 0; vi < mb.vertexCount(); vi++) {
            float t    = mb.spineT(vi);       // 0 = base, 1 = tip
            // Hue cycles from base colour to tip colour across the spire
            float hue  = (hueShift + t * 180f) % 360f;
            float sat  = 0.5f + 0.3f * (float) Math.sin(t * MathUtils.PI * 3f);
            float val  = 0.85f + 0.1f * (float) Math.cos(t * MathUtils.PI * 5f);
            mb.setVertexColor(vi, Color.fromHSV(hue, sat, val));
            // Tips of spires emit light
            if (t > 0.85f)
                mb.setVertexEmissive(vi, (t - 0.85f) / 0.15f * style.engineGlowIntensity);
        }
    }
}
```

---

## Generic / Fallback Styles

```java
private static HullStyle buildPirateStyle() {
    HullStyle s          = new HullStyle();
    s.id                 = HullStyleId.PIRATE;
    s.generatorPath      = HullGeneratorPath.LOFTED;
    s.noseProfile        = CrossSectionProfile.CIRCLE;
    s.midProfile         = CrossSectionProfile.ROUNDED_RECT;
    s.tailProfile        = CrossSectionProfile.CIRCLE;
    s.dorsalArcFactor    = 0.02f;
    s.allowSpineKink     = false;
    s.nacelleCount       = 2;
    s.nacelleScale       = 0.20f;
    s.nacelleShape       = NacelleShape.CYLINDRICAL_OPEN_END;
    s.hasFins            = false;
    s.sensorDomeScale    = 0.10f;
    s.signatureFeature   = SignatureFeature.NONE;
    s.hardpointSockets   = buildStandardHardpoints(6);
    // Dirty, weathered, mismatched
    s.primaryAlbedo      = Color.valueOf("4A3D2A");
    s.secondaryAlbedo    = Color.valueOf("2A2D33");
    s.accentAlbedo       = Color.valueOf("CC4400");
    s.markingType        = MarkingType.NONE;
    s.engineGlowColor    = Color.valueOf("FF6622");
    s.engineGlowIntensity = 0.85f;
    s.runningLightColor  = Color.valueOf("FF4400");
    s.sectionBreakDarken = 0.70f;
    s.ageless            = false;
    return s;
}
```

---

## Signature Feature Placement

```java
public class SignatureFeaturePlacer {

    public void place(GeneratedHull hull, HullStyle style, HullGeometryConfig cfg,
                       Random rng) {
        switch (style.signatureFeature) {

            case SPINAL_LANCE:
                // Extend a narrow spire forward from the nose (t=0)
                // Length: 15–30% of ship length
                float lanceLen = cfg.lengthMetres * MathUtils.random(rng, 0.15f, 0.30f);
                hull.signatureMesh = buildSpinalLance(hull.nosePos(), lanceLen, style);
                break;

            case SHIELD_RINGS:
                // Torus-shaped rings around the hull at each ringPosition
                for (float t : style.ringPositions) {
                    float radius = hull.beamAt(t) * 1.15f;  // slightly wider than hull
                    hull.signatureMeshes.add(buildShieldRing(
                        hull.spineAt(t), radius, style.accentAlbedo));
                }
                break;

            case MISMATCHED_PANELS:
                // No geometry added; handled by ZulKiriColorBaker at bake time
                break;

            case GEOMETRIC_SEPARATION:
                // No geometry added; handled by FacetedHullBuilder via sectionGap
                break;

            case CRYSTAL_SPIRE_BRANCHES:
                // Handled entirely by CrystalHullBuilder
                break;

            case NONE:
            default:
                break;
        }
    }

    private Mesh buildSpinalLance(Vector3 nosePos, float length, HullStyle style) {
        // Conical spire: base diam = 2% of lance length, tapers to sharp tip
        MeshBuilder mb = new MeshBuilder();
        float baseDiam = length * 0.02f;
        int facets     = 6;
        for (int f = 0; f < facets; f++) {
            float a0 = f * MathUtils.PI2 / facets;
            float a1 = (f + 1) * MathUtils.PI2 / facets;
            // Base ring → tip
            mb.triangle(
                nosePos.cpy().add(MathUtils.cos(a0) * baseDiam, MathUtils.sin(a0) * baseDiam, 0),
                nosePos.cpy().add(MathUtils.cos(a1) * baseDiam, MathUtils.sin(a1) * baseDiam, 0),
                nosePos.cpy().add(0, 0, -length) // tip forward of nose
            );
        }
        mb.setUniformColor(style.primaryAlbedo.cpy().mul(0.6f)); // darker than hull
        return mb.build();
    }
}
```

---

## Loading HullStyle from JSON (Data-Driven)

```java
// HullStyles should ultimately be loaded from data/ships/hull_styles.json
// Current state: registry is code-only; JSON loader not yet wired up.
// When wiring: use FactionData.styleId (String) to look up HullStyleId enum.
// Note: faction_seeds.json is STALE — the 5 lore factions are not yet
// authored as data. Until they are, use the hardcoded registry above.

public class HullStyleLoader {

    public static void loadFromJson(String jsonPath, HullStyleRegistry registry) {
        JsonValue root = new JsonReader().parse(Gdx.files.internal(jsonPath));
        for (JsonValue entry : root) {
            HullStyle style = parseStyle(entry);
            registry.register(style);
        }
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Applying Federation style to Vaun ships | Each faction has exactly one canonical style; use `HullStyleRegistry.styleFor()` |
| Symmetric nacelles on Zul-Kiri | Zul-Kiri nacelle positions randomised; `ZulKiriHullBuilder` overrides standard placement |
| Null-System ships with curves | Null-System must use FACETED path; LOFTED is wrong for them |
| zeeLee ships with uniform colour | zeeLee uses iridescent per-vertex gradient from `ZeeLeeColorBaker`; solid colours are wrong |
| Wear applied to Null/zeeLee ships | `ageless = true` means skip all grime/scorch baking |
| Signature features optional | Vaun **always** has a spinal lance; Federation **always** has shield ring mounts; these are non-negotiable faction tells |
| Using faction_seeds.json for lore factions | That file is stale; lore faction styles live in `HullStyleRegistry` code until a proper JSON schema is written |
| Pirate livery using faction palette | Pirate/independent ships use `PirateStyle` or `FrontierStyle`, never a lore-faction palette |
