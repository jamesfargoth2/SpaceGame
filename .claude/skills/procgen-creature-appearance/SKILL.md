---
name: procgen-creature-appearance
description: >
  Enforces procedural creature skin and surface appearance for the Galactic
  Odyssey fauna system (Cycle C), building on top of the Cycle A socket-graph
  CreatureSpec. Covers surface coverage type selection (scales, fur, feathers,
  chitin carapace, membrane, slime coat, crystalline plates), pattern overlay
  generation (striped, spotted, banded, mottled, iridescent, counter-shaded),
  biome-correlated base colour derivation from planet colorSeed and biome
  temperature, special surface features (bioluminescent patches, chromatophore
  bands, warning colouration, camouflage), and vertex-color baking into the
  CreatureMeshBuilder stride-11 format (no UV maps) for a libGDX 3D space game.
  Use this skill whenever writing or modifying: creature skin shader parameters,
  surface coverage material assignment, colour pattern generation, bioluminescent
  feature placement, biome-correlated creature coloration, or any code that
  makes creatures look visually distinct from each other and consistent with
  their planet of origin. Reads CreatureSpec.colorSeed and CreatureSpec.biome
  produced by Cycle A (procgen-creature-generation is not a listed skill —
  the fauna package lives at com.galacticodyssey.fauna).
---

# Procedural Creature Appearance (Cycle C)

## Integration with Cycle A

Cycle A produces a `CreatureSpec` containing:
- `colorSeed` — seeded from the planet's biome palette
- `archetype` — BIPEDAL / QUADRUPED / HEXAPOD / SERPENTINE
- `massKg` — drives scale; heavier creatures get thicker skin coverage
- `socketGraph` — the part topology used by `CreatureAssembler`

Cycle C reads `colorSeed` and `biome` and writes surface parameters back
into `CreatureSpec.appearance` before `CreatureMeshBuilder` runs.

---

## Rendering Constraint

Same as ships: **stride-11, vertex colours only, no UV maps.**
```
[x, y, z,  nx, ny, nz,  r, g, b, a,  emissive]
```
All skin detail — patterns, markings, bioluminescence — is baked as
vertex-colour modulation at `CreatureMeshBuilder` time.

---

## Surface Coverage Types

```java
public enum SkinCoverage {
    SCALES_FINE,       // small overlapping reptilian scales; high frequency geometry
    SCALES_ARMOURED,   // large plate-like scales; low frequency, pronounced ridges
    FUR_SHORT,         // mammalian short pelt; vertex displacement + colour noise
    FUR_LONG,          // longer, directional fur; needs fin-like geometry strips
    FEATHERS,          // avian; overlapping quill geometry on body + limbs
    CHITIN_PLATES,     // insectoid; hard segmented plates over soft membranes
    MEMBRANE_SMOOTH,   // amphibian/aquatic; wet sheen, very smooth
    SLIME_COAT,        // glistening surface; high specularity bake
    CRYSTALLINE,       // mineral-growth surface; faceted vertex geometry
    BARKLIKE,          // woody alien biology; creviced, brown/grey
    FUNGAL_CRUST,      // cap/gill surface features; spore pits
}
```

---

## CreatureAppearanceSpec

```java
public class CreatureAppearanceSpec {
    public SkinCoverage    coverage;
    public PatternType     pattern;
    public Color           baseColour;
    public Color           patternColour;
    public Color           accentColour;       // eyes, claws, bioluminescent organs
    public float           patternScale;       // UV-space frequency (faked via position)
    public float           patternContrast;    // 0 = subtle; 1 = stark
    public boolean         isBioluminescent;
    public BioLumZone[]    lumZones;           // which body regions glow
    public boolean         isChromatophore;    // colour-shifting ability
    public boolean         hasWarningColour;   // aposematic; red/yellow/black
    public boolean         isCamouflaged;      // base colour matches biome ground
    public float           shineLevel;         // 0 = matte; 1 = wet sheen
}
```

---

## Coverage Selection

```java
public class SkinCoverageSelector {

    /**
     * Select surface coverage from archetype + mass + planet biome.
     * Heavy creatures prefer armoured or scaly; warm biomes push toward
     * smooth membranes; cold biomes push toward fur or thick scales.
     */
    public SkinCoverage select(CreatureSpec spec, Biome biome, Random rng) {
        float temperatureHint = biomeTemperature(biome);  // -1 cold, 0 temperate, 1 hot

        switch (spec.archetype) {
            case BIPEDAL:
                return selectBipedal(temperatureHint, spec.massKg, rng);
            case QUADRUPED:
                return selectQuadruped(temperatureHint, spec.massKg, rng);
            case HEXAPOD:
                // Hexapods lean insectoid — chitin dominant
                return rng.nextFloat() < 0.65f ? SkinCoverage.CHITIN_PLATES
                     : (temperatureHint > 0.3f ? SkinCoverage.SCALES_FINE
                                               : SkinCoverage.MEMBRANE_SMOOTH);
            case SERPENTINE:
                return rng.nextFloat() < 0.70f ? SkinCoverage.SCALES_FINE
                     : SkinCoverage.MEMBRANE_SMOOTH;
            default:
                return SkinCoverage.SCALES_FINE;
        }
    }

    private SkinCoverage selectBipedal(float temp, float massKg, Random rng) {
        if (biome == Biome.ALIEN_CRYSTAL)   return SkinCoverage.CRYSTALLINE;
        if (biome == Biome.ALIEN_FUNGAL)    return SkinCoverage.FUNGAL_CRUST;
        if (temp < -0.5f) {
            return rng.nextFloat() < 0.6f ? SkinCoverage.FUR_LONG : SkinCoverage.FUR_SHORT;
        }
        if (temp > 0.6f) {
            return massKg > 300f ? SkinCoverage.SCALES_ARMOURED : SkinCoverage.SCALES_FINE;
        }
        // Temperate: mix
        float r = rng.nextFloat();
        if (r < 0.30f) return SkinCoverage.SCALES_FINE;
        if (r < 0.55f) return SkinCoverage.FUR_SHORT;
        if (r < 0.70f) return SkinCoverage.FEATHERS;
        if (r < 0.85f) return SkinCoverage.MEMBRANE_SMOOTH;
        return SkinCoverage.SCALES_ARMOURED;
    }

    private float biomeTemperature(Biome b) {
        return switch (b) {
            case GLACIER, TUNDRA                         -> -1.0f;
            case BOREAL_FOREST, ALPINE                   -> -0.5f;
            case TEMPERATE_DECIDUOUS, TEMPERATE_RAINFOREST,
                 GRASSLAND, SHRUBLAND, COLD_DESERT       ->  0.0f;
            case SAVANNA, TROPICAL_SEASONAL, MANGROVE    ->  0.5f;
            case TROPICAL_RAINFOREST, HOT_DESERT,
                 ALIEN_LAVA_FIELDS                       ->  1.0f;
            default                                      ->  0.0f;
        };
    }
}
```

---

## Colour Derivation

```java
public class CreatureColourDeriver {

    /**
     * Derive base colour from planet colorSeed + biome.
     * Creatures are ecologically coherent — their colour is a biologically
     * plausible variation on their biome's palette, not random.
     */
    public Color deriveBase(long colorSeed, Biome biome, boolean isCamouflaged,
                             boolean hasWarningColour, Random rng) {
        Color biomeBase = biomeGroundColour(biome);

        if (hasWarningColour) {
            // Aposematic: bright yellow, orange, or red — often dark-banded
            return warningPalette(rng);
        }

        if (isCamouflaged) {
            // Very close to biome ground colour with slight noise
            Color c = biomeBase.cpy();
            c.r += MathUtils.random(rng, -0.08f, 0.08f);
            c.g += MathUtils.random(rng, -0.08f, 0.08f);
            c.b += MathUtils.random(rng, -0.08f, 0.08f);
            return clamp(c);
        }

        // Standard: biome-tinted but with biological variance
        // Use colorSeed to pick a hue offset within the biome palette range
        Random colourRng = new Random(colorSeed);
        float hueShift = MathUtils.random(colourRng, -25f, 25f);
        float[] hsv = toHSV(biomeBase);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = MathUtils.clamp(hsv[1] + MathUtils.random(colourRng, -0.15f, 0.15f), 0.1f, 0.9f);
        hsv[2] = MathUtils.clamp(hsv[2] + MathUtils.random(colourRng, -0.15f, 0.15f), 0.15f, 0.95f);
        return Color.fromHSV(hsv[0], hsv[1], hsv[2]);
    }

    private Color warningPalette(Random rng) {
        Color[] warnings = {
            Color.valueOf("FFD700"),  // yellow
            Color.valueOf("FF6600"),  // orange
            Color.valueOf("CC0000"),  // red
            Color.valueOf("FF00AA"),  // magenta (alien)
        };
        return warnings[rng.nextInt(warnings.length)];
    }

    private Color biomeGroundColour(Biome b) {
        return switch (b) {
            case TROPICAL_RAINFOREST -> Color.valueOf("1A5C1A");
            case SAVANNA             -> Color.valueOf("A89040");
            case HOT_DESERT          -> Color.valueOf("D4B870");
            case TEMPERATE_DECIDUOUS -> Color.valueOf("3A7A25");
            case GRASSLAND           -> Color.valueOf("80A830");
            case BOREAL_FOREST       -> Color.valueOf("2A5020");
            case TUNDRA              -> Color.valueOf("8C8C7A");
            case GLACIER             -> Color.valueOf("D8E8F0");
            case ALIEN_FUNGAL        -> Color.valueOf("7A1A8A");
            case ALIEN_CRYSTAL       -> Color.valueOf("90D8E0");
            case ALIEN_LAVA_FIELDS   -> Color.valueOf("3A0A05");
            default                  -> Color.valueOf("607050");
        };
    }
}
```

---

## Pattern Types & Generation

```java
public enum PatternType {
    SOLID,               // no pattern; single base colour
    STRIPED_LONGITUDINAL,// stripes along body axis (like a tiger)
    STRIPED_TRANSVERSE,  // bands around the body (like a banded snake)
    SPOTTED_REGULAR,     // evenly distributed spots (leopard)
    SPOTTED_RANDOM,      // irregular blotch pattern
    MOTTLED,             // irregular marbling of two colours
    GRADIENT_DORSAL,     // darker dorsally, lighter ventrally (counter-shading)
    GRADIENT_HEAD,       // colour fades along body from head to tail
    IRIDESCENT,          // hue shifts with viewing angle (baked as gradient)
    OCELLI,              // eye-spot patterns (for threat display)
    BANDED_LIMBS,        // limbs banded, body solid
    ALIEN_CIRCUIT,       // geometric veins or circuit-like tracery
    ALIEN_BLOOM,         // flower-like radial patches (alien biology)
}

public class PatternBaker {

    /**
     * Apply colour pattern to the creature mesh via vertex colour.
     * Position-based pattern: we use the vertex's position relative to
     * the creature bounding box to compute pattern membership.
     */
    public void bake(MeshBuilder mb, CreatureAppearanceSpec app, Random rng) {
        Vector3 bbMin = mb.boundingBoxMin();
        Vector3 bbMax = mb.boundingBoxMax();
        Vector3 bbSize = bbMax.cpy().sub(bbMin);

        for (int vi = 0; vi < mb.vertexCount(); vi++) {
            Vector3 pos = mb.vertexPos(vi);
            // Normalised [0,1] position within creature bounding box
            float tx = (pos.x - bbMin.x) / bbSize.x; // lateral
            float ty = (pos.y - bbMin.y) / bbSize.y; // dorsal-ventral
            float tz = (pos.z - bbMin.z) / bbSize.z; // head-tail

            Color c = computePatternColor(app, tx, ty, tz, rng);
            mb.setVertexColor(vi, c);
        }
    }

    private Color computePatternColor(CreatureAppearanceSpec app,
                                       float tx, float ty, float tz, Random rng) {
        switch (app.pattern) {

            case STRIPED_LONGITUDINAL: {
                // Stripes along body (tz) axis
                float stripe = MathUtils.sin(tz * MathUtils.PI2 * app.patternScale * 6f);
                return stripe > 0 ? app.baseColour : app.patternColour;
            }

            case STRIPED_TRANSVERSE: {
                // Bands around the body (tx/ty angular)
                float band = MathUtils.sin(tz * MathUtils.PI * app.patternScale * 8f);
                return band > (1f - app.patternContrast) ? app.patternColour : app.baseColour;
            }

            case SPOTTED_REGULAR: {
                // Grid of spots in tx/tz space
                float gx = tx * app.patternScale * 5f;
                float gz = tz * app.patternScale * 8f;
                float distToSpot = (float) Math.sqrt(
                    Math.pow(gx - Math.round(gx), 2) +
                    Math.pow(gz - Math.round(gz), 2));
                return distToSpot < 0.25f * app.patternContrast
                    ? app.patternColour : app.baseColour;
            }

            case GRADIENT_DORSAL: {
                // Counter-shading: dark dorsal (ty=1), light ventral (ty=0)
                return app.baseColour.cpy().lerp(
                    app.patternColour, ty * app.patternContrast);
            }

            case GRADIENT_HEAD: {
                // Fade along body from head (tz=1) to tail (tz=0)
                return app.baseColour.cpy().lerp(app.patternColour, tz * app.patternContrast);
            }

            case IRIDESCENT: {
                // Hue shifts along body axis — baked gradient
                float[] hsv = toHSV(app.baseColour);
                hsv[0] = (hsv[0] + tz * 120f) % 360f;
                return Color.fromHSV(hsv[0], hsv[1], hsv[2]);
            }

            case ALIEN_CIRCUIT: {
                // Vein-like geometric pattern baked from domain-warped noise
                float noise = domainWarpedNoise(tx, ty, tz, app.patternScale);
                return noise > 0.75f ? app.accentColour : app.baseColour;
            }

            case MOTTLED: {
                float noise = simplex3(tx * 4f, ty * 4f, tz * 4f);
                return noise > 0.5f - app.patternContrast * 0.5f
                    ? app.patternColour : app.baseColour;
            }

            default:
                return app.baseColour;
        }
    }
}
```

---

## Bioluminescent Zone Placement

```java
public enum BioLumZone {
    HEAD_CREST,      // crest or head frill
    DORSAL_STRIPE,   // stripe along the dorsal surface
    TAIL_TIP,        // tip of tail
    FLANK_PATCHES,   // irregular patches on flanks
    LIMB_BANDS,      // rings on limbs
    UNDERBELLY,      // ventral surface
    EYE_RINGS,       // rings around eyes
    THROAT_SAC,      // inflatable display sac
}

public class BioluminescenceBaker {

    /**
     * Bake emissive vertex data for bioluminescent zones.
     * High-emissive patches at night glow; zero emissive elsewhere.
     */
    public void bake(MeshBuilder mb, CreatureAppearanceSpec app,
                      Color lumColour, float intensity) {
        if (!app.isBioluminescent) return;

        for (BioLumZone zone : app.lumZones) {
            Predicate<Integer> inZone = vertexInZonePredicate(mb, zone);
            for (int vi = 0; vi < mb.vertexCount(); vi++) {
                if (inZone.test(vi)) {
                    mb.blendVertexColor(vi, lumColour, 0.6f);
                    mb.setVertexEmissive(vi, intensity);
                }
            }
        }
    }

    private Predicate<Integer> vertexInZonePredicate(MeshBuilder mb, BioLumZone zone) {
        Vector3 bbMin = mb.boundingBoxMin();
        Vector3 bbMax = mb.boundingBoxMax();
        float height = bbMax.y - bbMin.y;
        float length = bbMax.z - bbMin.z;

        return vi -> {
            Vector3 p = mb.vertexPos(vi);
            float ty = (p.y - bbMin.y) / height;  // 0=bottom, 1=top
            float tz = (p.z - bbMin.z) / length;  // 0=tail, 1=head
            float tx = Math.abs(p.x) / ((bbMax.x - bbMin.x) * 0.5f); // 0=centre, 1=side

            return switch (zone) {
                case HEAD_CREST      -> tz > 0.80f && ty > 0.70f;
                case DORSAL_STRIPE   -> ty > 0.75f && tx < 0.20f;
                case TAIL_TIP        -> tz < 0.15f;
                case FLANK_PATCHES   -> tx > 0.70f;
                case LIMB_BANDS      -> false; // requires socket topology query
                case UNDERBELLY      -> ty < 0.20f;
                case EYE_RINGS       -> tz > 0.85f && tx > 0.30f && tx < 0.60f;
                case THROAT_SAC      -> tz > 0.75f && ty < 0.35f;
            };
        };
    }
}
```

---

## Warning Colouration Logic

```java
public class WarningColourRule {

    /**
     * Warning colouration (aposematism) is ecologically coupled to venom/toxin.
     * Never assign warning colour to a non-venomous, non-toxic creature;
     * that would be Batesian mimicry, which must also be flagged.
     */
    public boolean shouldHaveWarningColour(CreatureSpec spec, Random rng) {
        boolean isPoisonous  = spec.hasVenom || spec.isToxic;
        boolean isMimic      = !isPoisonous && rng.nextFloat() < 0.05f; // rare mimics
        return isPoisonous || isMimic;
    }

    /** Aposematic patterns are always high-contrast: bright base, dark bands. */
    public CreatureAppearanceSpec applyWarningScheme(CreatureAppearanceSpec app) {
        app.patternType     = PatternType.STRIPED_TRANSVERSE;
        app.patternContrast = 0.95f;
        app.patternColour   = Color.valueOf("111111"); // dark bands
        app.shineLevel      = 0.6f; // slightly waxy/shiny surface
        return app;
    }
}
```

---

## Feature Geometry: Coverage-Specific Mesh Augmentation

```java
public class CoverageGeometryAugmenter {

    /**
     * Some coverage types require additional geometry beyond the base part mesh.
     * FUR_LONG and FEATHERS need fin-strips to simulate strand geometry.
     * CHITIN_PLATES add raised plate outlines.
     * CRYSTALLINE adds faceted ridges.
     */
    public void augment(MeshBuilder mb, SkinCoverage coverage,
                         CreatureAppearanceSpec app, Random rng) {
        switch (coverage) {
            case FUR_LONG:
                // Dorsal fin strips along spine — acts as hair volume proxy
                addFurStrips(mb, 8 + rng.nextInt(8), app.baseColour, rng);
                break;
            case FEATHERS:
                // Overlapping quill-shaped geometry on wing/arm sockets
                addFeatherOverlay(mb, app.patternColour, rng);
                break;
            case CHITIN_PLATES:
                // Extruded plate edge loops over main body segments
                addChitinPlateEdges(mb, app.accentColour);
                break;
            case CRYSTALLINE:
                // Small faceted spikes distributed over surface
                addCrystallineSpikes(mb, app.accentColour,
                                      4 + rng.nextInt(8), rng);
                break;
            case FUNGAL_CRUST:
                addMushroomNubs(mb, app.patternColour, rng);
                break;
        }
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Random colours unrelated to biome | Derive base colour from `biomeGroundColour(biome)` ± `colorSeed` hue shift; never pure random |
| Warning colour on non-venomous creatures | Check `spec.hasVenom || spec.isToxic` first; mimics are rare (5%) |
| Pattern baked with UVs | No UVs exist. Use normalised bounding-box position `(tx, ty, tz)` for all pattern math |
| Bioluminescence with no emissive channel | Emissive is stride-11 index 10; setting it to 0 everywhere makes glow invisible |
| Same pattern for all archetypes | Serpentines favour STRIPED_TRANSVERSE; hexapods favour SPOTTED_REGULAR; respect archetype |
| Coverage geometry added to all creatures | FUR_LONG strips and crystal spikes are optional augmentations; skip for SCALES_FINE/MEMBRANE_SMOOTH |
| Camouflage creatures with loud patterns | Camouflaged creatures use SOLID or MOTTLED only; never OCELLI or ALIEN_CIRCUIT |
