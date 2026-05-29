---
name: procgen-ship-hull-geometry
description: >
  Enforces procedural ship hull mesh construction using two generator paths —
  LOFTED (cross-section profiles extruded along a spine spline, for organic
  and modular ships) and FACETED (platonic-primitive section assembly, for
  geometric/crystalline ships) — including cross-section shape libraries,
  spine curve generation, hull section breaks, nacelle/wing/fin placement,
  sensor array attachment, docking collar siting, panel edge highlighting,
  and vertex-color baking (no UV maps) for a libGDX 3D deferred-PBR space
  game. Use this skill whenever writing or modifying: hull mesh generation,
  ship silhouette construction, cross-section profile systems, spine-based
  extrusion, section panel layout, hull feature placement (nacelles, turret
  foundations, fin geometry), vertex-color material assignment, or any code
  that produces the 3D geometry of a ship hull from a seed. Also triggers
  when adding a new faction generator path, a new hull silhouette archetype,
  or modifying how section breaks and panel lines are placed on ship hulls.
  Read together with procgen-ship-faction-aesthetics which provides the
  per-faction cross-section and spine profiles that feed into this system.
---

# Procedural Ship Hull Geometry

## Critical Rendering Constraint

> Vertex colors are the ONLY material channel. Stride-11 layout:
> `[x, y, z, nx, ny, nz, r, g, b, a, emissive]`
> There are **NO UV coordinates and NO textures**. All surface detail
> (panel lines, faction markings, wear) must be baked as vertex-color
> modulation at mesh build time. The deferred-PBR pipeline reads the
> `r/g/b` channels as albedo and `emissive` as emission intensity.

---

## Hull Generator Paths

```java
public enum HullGeneratorPath {
    LOFTED,    // Cross-section profiles swept along a spine — organic, modular ships
    FACETED,   // Platonic primitive sections assembled in a graph — geometric, monolith ships
    CRYSTAL,   // Recursive crystalline spire growth — zeeLee faction only
}
```

---

## HullGeometryConfig

```java
public class HullGeometryConfig {
    public long               seed;
    public HullGeneratorPath  path;
    public HullClass          hullClass;
    public HullStyleId        styleId;        // from procgen-ship-faction-aesthetics
    public float              lengthMetres;   // spine length
    public float              beamMetres;     // max cross-section width
    public float              massKg;         // affects section proportions
    // Lofted-specific
    public int                spineSegments;  // 6–24 depending on class
    public CrossSectionProfile noseProfile;
    public CrossSectionProfile midProfile;
    public CrossSectionProfile tailProfile;
    // Faceted-specific
    public int                sectionCount;   // number of detached blocks
    public float              sectionGap;     // metres between sections
}
```

---

## Path A: Lofted Hull Generator

### Cross-Section Profile Library

```java
public enum CrossSectionProfile {
    CIRCLE,          // smooth fuselage — civilian, Federation
    ELLIPSE_WIDE,    // flattened oval — atmospheric ships, carriers
    ELLIPSE_TALL,    // vertical oval — Vaun blade profile
    DIAMOND,         // four-sided lozenge — fighters, Vaun
    ROUNDED_RECT,    // soft box — freighters, modular
    HEXAGON,         // six-sided — Zul-Kiri, industrial
    DOUBLE_HULL,     // catamaran — twin-boom ships, mining barges
    ASYMMETRIC_WEDGE,// one flat face + one angled — Vaun warships
}

public class CrossSection {
    public CrossSectionProfile profile;
    public float width;
    public float height;
    public float twist;     // rotation around spine axis (degrees)
    public float taper;     // 0 = same as adjacent; negative = necked; positive = flared
}
```

### Spine Curve

```java
public class SpineCurve {

    /**
     * Generate a Catmull-Rom spine from nose to tail.
     * Most ships are straight (fighters, destroyers); larger ships may have
     * a subtle dorsal arc (cruisers) or a pronounced forward rake (Vaun).
     */
    public Array<Vector3> buildControlPoints(HullGeometryConfig cfg, Random rng) {
        Array<Vector3> pts = new Array<>();
        float L = cfg.lengthMetres;

        pts.add(new Vector3(0, 0, 0));                  // nose

        // Vaun ships rake the bow upward
        boolean vaunRake = cfg.styleId.name().startsWith("VAUN");
        float dorsalArc  = vaunRake ? 0.08f : MathUtils.random(rng, 0f, 0.03f);

        // Interior control points — subtle arc for large ships
        int midPts = Math.max(2, cfg.spineSegments / 4);
        for (int i = 1; i <= midPts; i++) {
            float t = i / (float)(midPts + 1);
            float y = dorsalArc * L * MathUtils.sin(t * MathUtils.PI); // arc up and back
            pts.add(new Vector3(0, y, t * L));
        }

        pts.add(new Vector3(0, 0, L));                  // tail
        return pts;
    }

    public Array<Vector3> sampleCatmullRom(Array<Vector3> ctrl, int samples) {
        Array<Vector3> result = new Array<>();
        for (int i = 0; i < samples; i++) {
            float t   = i / (float)(samples - 1);
            result.add(CatmullRom.point(ctrl, t));
        }
        return result;
    }
}
```

### Section Extrusion

```java
public class LoftedHullBuilder {

    /**
     * Build hull geometry by sweeping cross-sections along the spine.
     * Each spine sample gets a cross-section; adjacent sections share edges.
     */
    public MeshBuilder extrude(HullGeometryConfig cfg, Array<Vector3> spine,
                                HullStyle style, Random rng) {
        MeshBuilder mb = new MeshBuilder();
        int segs = spine.size;

        for (int i = 0; i < segs; i++) {
            float t     = i / (float)(segs - 1);
            // Interpolate cross-section from nose → mid → tail profiles
            CrossSection cs = interpolateCrossSection(
                cfg.noseProfile, cfg.midProfile, cfg.tailProfile, t, cfg, rng);

            // Apply taper curve: widest at ~35% length, narrower at nose and tail
            float taperFactor = taperCurve(t, cfg.hullClass);
            cs.width  *= taperFactor;
            cs.height *= taperFactor;

            Array<Vector3> ring   = buildRing(spine.get(i), cs, spineNormal(spine, i));
            Color ringColor       = style.hullColorAt(t, rng);  // vertex colour
            float ringEmissive    = style.emissiveAt(t);

            for (Vector3 v : ring) {
                mb.vertex(v, ringNormal(v, spine.get(i)), ringColor, ringEmissive);
            }

            if (i > 0) mb.stitchRings(segs, ring.size); // connect to previous ring
        }

        mb.closeCap(0, true);    // nose cap
        mb.closeCap(segs - 1, false); // tail cap
        return mb;
    }

    private float taperCurve(float t, HullClass cls) {
        // Nose taper: narrow then widen; tail taper: narrow from ~60% onward
        float nose = MathUtils.clamp(t / 0.15f, 0f, 1f);    // 0→1 in first 15%
        float tail = MathUtils.clamp((1f - t) / 0.30f, 0f, 1f); // 1→0 in last 30%
        return Math.min(nose, tail) * 0.3f + 0.7f;           // 70–100% of max beam
    }

    private CrossSection interpolateCrossSection(CrossSectionProfile nose,
                                                   CrossSectionProfile mid,
                                                   CrossSectionProfile tail,
                                                   float t, HullGeometryConfig cfg,
                                                   Random rng) {
        // Blend profiles: 0–0.2 = nose, 0.2–0.7 = mid, 0.7–1.0 = tail
        CrossSectionProfile active = t < 0.2f ? nose : (t < 0.7f ? mid : tail);
        CrossSection cs = new CrossSection();
        cs.profile = active;
        cs.width   = cfg.beamMetres;
        cs.height  = cfg.beamMetres * heightRatioFor(active);
        return cs;
    }

    private float heightRatioFor(CrossSectionProfile p) {
        switch (p) {
            case ELLIPSE_WIDE:    return 0.55f;
            case ELLIPSE_TALL:    return 1.60f;
            case DIAMOND:         return 0.80f;
            case ASYMMETRIC_WEDGE:return 0.70f;
            default:              return 1.00f;
        }
    }
}
```

### Section Breaks (Panel Lines)

```java
public class SectionBreakPlacer {

    /**
     * Insert hard edge rings at faction-defined intervals along the hull.
     * These become the seams between armour sections, visible as dark lines
     * in the vertex-colour data (slightly darker albedo along the ring).
     */
    public void addSectionBreaks(MeshBuilder mb, Array<Vector3> spine,
                                  HullStyle style, HullClass cls) {
        int breakCount = sectionBreakCount(cls);
        for (int i = 1; i < breakCount; i++) {
            float t = i / (float) breakCount;
            int   spineIdx = (int)(t * (spine.size - 1));

            // Darken vertex ring at this t to create a visible seam
            mb.darkenRing(spineIdx, style.sectionBreakDarken); // 0.7–0.9 albedo mult
            mb.addCreaseEdge(spineIdx);  // hard normal for sharp edge rendering
        }
    }

    private int sectionBreakCount(HullClass cls) {
        switch (cls) {
            case FIGHTER:    return 2;
            case CORVETTE:   return 3;
            case FRIGATE:    return 4;
            case DESTROYER:  return 5;
            case CRUISER:    return 7;
            case BATTLESHIP: return 10;
            default:         return 4;
        }
    }
}
```

---

## Path B: Faceted Hull Generator (Null-System / geometric factions)

```java
public class FacetedHullBuilder {

    /**
     * Assemble a ship from a linear chain of platonic-primitive sections.
     * Null-System ships use this path; sections are slightly separated
     * to reveal structural connectors between them.
     */
    public Array<HullSection> buildSections(HullGeometryConfig cfg, Random rng) {
        Array<HullSection> sections = new Array<>();
        float z = 0f;

        // Nose section: always a pointed primitive (pyramid, cone, obelisk)
        sections.add(buildSection(SectionPrimitive.OBELISK,
                                   cfg.beamMetres * 0.6f, cfg.beamMetres * 1.5f,
                                   new Vector3(0, 0, z)));
        z += cfg.beamMetres * 1.5f + cfg.sectionGap;

        // Middle sections: cube, prism, or hex column
        int midCount = cfg.sectionCount - 2;
        for (int i = 0; i < midCount; i++) {
            SectionPrimitive prim = rollMidPrimitive(rng);
            float len = cfg.lengthMetres / cfg.sectionCount
                      * MathUtils.random(rng, 0.7f, 1.3f);
            sections.add(buildSection(prim, cfg.beamMetres, len,
                                       new Vector3(0, 0, z)));
            z += len + cfg.sectionGap;
        }

        // Tail section: truncated prism (engine housing)
        sections.add(buildSection(SectionPrimitive.TRUNCATED_PRISM,
                                   cfg.beamMetres * 0.8f, cfg.beamMetres * 1.2f,
                                   new Vector3(0, 0, z)));
        return sections;
    }

    private SectionPrimitive rollMidPrimitive(Random rng) {
        float r = rng.nextFloat();
        if (r < 0.40f) return SectionPrimitive.CUBE;
        if (r < 0.70f) return SectionPrimitive.HEX_PRISM;
        if (r < 0.85f) return SectionPrimitive.OCTAHEDRON;
        return SectionPrimitive.RHOMBOHEDRON;
    }

    private HullSection buildSection(SectionPrimitive prim, float beam, float length,
                                      Vector3 origin) {
        HullSection s   = new HullSection();
        s.primitive     = prim;
        s.beam          = beam;
        s.length        = length;
        s.origin        = origin;
        s.mesh          = PrimitiveMeshFactory.build(prim, beam, length);
        return s;
    }
}

public enum SectionPrimitive {
    CUBE, HEX_PRISM, OCTAHEDRON, RHOMBOHEDRON,
    OBELISK, TRUNCATED_PRISM, BIPYRAMID
}
```

---

## Path C: Crystal Hull Generator (zeeLee faction)

```java
public class CrystalHullBuilder {

    /**
     * Grow a crystalline ship by recursively spawning spire branches from
     * a central spine crystal. zeeLee ships have no symmetric profile —
     * they grow asymmetrically like a mineral formation.
     */
    public Array<CrystalSpire> grow(HullGeometryConfig cfg, Random rng) {
        Array<CrystalSpire> spires = new Array<>();

        // Root spire: the main body
        CrystalSpire root = growSpire(
            Vector3.Zero, new Vector3(0, 0, 1), cfg.lengthMetres, 0, rng);
        spires.add(root);

        // Branch spires from junction points on root
        int branchCount = 3 + rng.nextInt(5);
        for (int i = 0; i < branchCount; i++) {
            float   t          = MathUtils.random(rng, 0.2f, 0.8f);
            Vector3 junctionPt = sampleSpire(root, t);
            Vector3 branchDir  = randomBranchDir(root.direction, rng);
            float   branchLen  = cfg.lengthMetres * MathUtils.random(rng, 0.15f, 0.45f);
            spires.add(growSpire(junctionPt, branchDir, branchLen, 1, rng));
        }
        return spires;
    }

    private CrystalSpire growSpire(Vector3 origin, Vector3 dir, float length,
                                    int depth, Random rng) {
        CrystalSpire spire = new CrystalSpire();
        spire.origin    = origin.cpy();
        spire.direction = dir.nor().cpy();
        spire.length    = length;
        // Facet count: 4–8 sides, thinner at tip
        spire.facets    = 4 + rng.nextInt(5);
        spire.baseDiam  = length * MathUtils.random(rng, 0.08f, 0.18f);
        spire.tipDiam   = spire.baseDiam * MathUtils.random(rng, 0.0f, 0.15f);
        return spire;
    }

    private Vector3 randomBranchDir(Vector3 parentDir, Random rng) {
        // Branch diverges 20–70 degrees from parent direction
        float pitch = MathUtils.random(rng, 20f, 70f) * MathUtils.degreesToRadians;
        float yaw   = rng.nextFloat() * MathUtils.PI2;
        Vector3 perp = parentDir.crs(new Vector3(0, 1, 0)).nor();
        return parentDir.cpy().rotate(perp, pitch)
                         .rotate(parentDir, yaw)
                         .nor();
    }
}
```

---

## Appendage Placement

```java
public class HullAppendagePlacer {

    /**
     * Place nacelles, wings, fins, sensor arrays, and docking collars
     * on the generated hull. Positions are expressed as t-values along
     * the spine and angular offsets around the hull cross-section.
     */
    public void place(GeneratedHull hull, HullGeometryConfig cfg,
                       HullStyle style, Random rng) {

        // Nacelles: always at t=0.6–0.8 (aft of centre), lateral ±90°
        int nacelles = style.nacelleCount;
        for (int n = 0; n < nacelles; n++) {
            float angle = (n / (float) nacelles) * MathUtils.PI2;
            float t     = MathUtils.random(rng, 0.62f, 0.78f);
            Vector3 pos = hullSurfaceAt(hull, t, angle);
            hull.appendages.add(new Appendage(AppendageType.NACELLE, pos, angle,
                                               style.nacelleScale, style));
        }

        // Fins/wings: at t=0.5–0.7, ±90° or ±180° (ventral/dorsal)
        if (style.hasFins) {
            for (float angle : new float[]{ 0f, MathUtils.PI }) {
                float t = MathUtils.random(rng, 0.50f, 0.65f);
                hull.appendages.add(new Appendage(AppendageType.FIN,
                    hullSurfaceAt(hull, t, angle), angle, style.finScale, style));
            }
        }

        // Sensor dome: always at t=0.05–0.15 (near nose), top (angle=0)
        hull.appendages.add(new Appendage(AppendageType.SENSOR_DOME,
            hullSurfaceAt(hull, MathUtils.random(rng, 0.05f, 0.15f), 0f),
            0f, style.sensorDomeScale, style));

        // Docking collar: at t=0.4–0.6, top or ventral
        if (cfg.hullClass.ordinal() >= HullClass.CORVETTE.ordinal()) {
            hull.appendages.add(new Appendage(AppendageType.DOCKING_COLLAR,
                hullSurfaceAt(hull, MathUtils.random(rng, 0.4f, 0.6f), 0f),
                0f, 1.0f, style));
        }

        // Turret foundations: placed on hull surface at hardpoint positions
        // (actual weapon models placed by procgen-ship-faction-aesthetics)
        for (HardpointSocket sock : style.hardpointSockets) {
            Vector3 pos = hullSurfaceAt(hull, sock.spineT, sock.angle);
            hull.hardpoints.add(new PlacedHardpoint(pos, sock));
        }
    }
}
```

---

## Vertex Color Baking

```java
public class HullVertexColorBaker {

    /**
     * All surface detail is encoded in vertex colors — no textures.
     * Bake: base faction albedo, panel break darkening, wear modulation,
     * faction markings (stripes/chevrons), emissive zones (engine glow, lights).
     */
    public void bake(MeshBuilder mb, HullStyle style, float conditionFactor,
                      Random rng) {

        // 1. Base albedo: faction primary colour with subtle noise
        for (int vi = 0; vi < mb.vertexCount(); vi++) {
            Color c = style.primaryAlbedo.cpy();
            c.mul(MathUtils.random(rng, 0.92f, 1.08f)); // subtle variation
            mb.setVertexColor(vi, c);
        }

        // 2. Faction stripe markings along spine
        bakeStripes(mb, style);

        // 3. Panel break seams: darken vertices within 0.05m of a section break ring
        bakePanelSeams(mb, style.sectionBreakDarken);

        // 4. Wear: grime, scorch, ablation darkening
        if (conditionFactor < 0.9f) {
            float wearStrength = 1f - conditionFactor;
            bakeWear(mb, wearStrength, rng);
        }

        // 5. Emissive zones: engine glow (tail), running lights, weapon emitters
        bakeEmissive(mb, style);
    }

    private void bakeStripes(MeshBuilder mb, HullStyle style) {
        if (style.markingType == MarkingType.NONE) return;

        for (int vi = 0; vi < mb.vertexCount(); vi++) {
            float t = mb.spineT(vi);          // 0–1 along spine
            float a = mb.hullAngle(vi);       // 0–2π around cross-section

            if (style.markingType == MarkingType.LONGITUDINAL_STRIPE) {
                // Stripe along specific angular band
                float dist = angularDist(a, style.stripeAngle);
                if (dist < style.stripeHalfWidth) {
                    mb.blendVertexColor(vi, style.accentAlbedo, style.stripeOpacity);
                }
            } else if (style.markingType == MarkingType.RING_BAND) {
                // Band at fixed t position (Federation shield ring mounts)
                for (float ringT : style.ringPositions) {
                    if (Math.abs(t - ringT) < 0.025f) {
                        mb.blendVertexColor(vi, style.accentAlbedo, 0.85f);
                    }
                }
            }
        }
    }

    private void bakeWear(MeshBuilder mb, float strength, Random rng) {
        // Grime darkens ventral surfaces (debris accumulation)
        // Scorch marks are localised patches of near-black
        for (int vi = 0; vi < mb.vertexCount(); vi++) {
            float a = mb.hullAngle(vi);
            // Ventral grime (bottom of ship, angle near π)
            float ventral = (float) Math.max(0, Math.cos(a - MathUtils.PI));
            float grime   = ventral * strength * 0.35f;
            mb.darkenVertex(vi, 1f - grime);
        }
        // Scorch patches: random Gaussian blobs
        int patches = (int)(strength * 5f);
        for (int p = 0; p < patches; p++) {
            float patchT = rng.nextFloat();
            float patchA = rng.nextFloat() * MathUtils.PI2;
            mb.applyGaussianDarken(patchT, patchA, 0.05f, 0.6f); // radius, max-darken
        }
    }

    private void bakeEmissive(MeshBuilder mb, HullStyle style) {
        // Engine glow: tail vertices at t > 0.85
        for (int vi = 0; vi < mb.vertexCount(); vi++) {
            float t = mb.spineT(vi);
            if (t > 0.85f) {
                float glow = MathUtils.clamp((t - 0.85f) / 0.15f, 0f, 1f);
                mb.setVertexEmissive(vi, glow * style.engineGlowIntensity);
                mb.blendVertexColor(vi, style.engineGlowColor, glow * 0.6f);
            }
        }
        // Running lights: small high-emissive patches at bow tips and wing tips
        for (Vector3 lightPos : mb.runningLightPositions()) {
            mb.applyEmissivePatch(lightPos, 0.3f, style.runningLightColor, 1.0f);
        }
    }
}
```

---

## LOD Strategy

```java
// LOD0: full geometry (< 80 m from camera)
// LOD1: 50% vertex reduction, no appendages except nacelles (80–300 m)
// LOD2: convex-hull impostor mesh (300–1500 m)
// LOD3: billboard sprite (> 1500 m)

public class ShipLODBuilder {

    public ShipLOD[] buildLODs(GeneratedHull lod0) {
        return new ShipLOD[]{
            buildLOD1(lod0, 80f),   // simplified hull + nacelles
            buildLOD2(lod0, 300f),  // convex-hull approximation
            buildLOD3(lod0, 1500f)  // billboard
        };
    }

    private ShipLOD buildLOD2(GeneratedHull lod0, float switchDist) {
        // Convex hull of all LOD0 vertices — cheap collision approx + visual stand-in
        Array<Vector3> pts = lod0.allVertexPositions();
        ShipLOD lod = new ShipLOD();
        lod.mesh       = ConvexHullMeshBuilder.build(pts);
        lod.switchDist = switchDist;
        // Tint to average albedo of LOD0
        lod.mesh.setUniformColor(lod0.averageAlbedo());
        return lod;
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Adding UV coordinates | This pipeline has NO UVs. All detail is vertex-color only. Stride is always 11. |
| Taper applied uniformly | Taper must follow `taperCurve(t)`: narrow nose, widen to max at ~35%, narrow again at tail |
| Same cross-section profile nose to tail | Interpolate nose/mid/tail profiles; never lock to one profile for the whole ship |
| Nacelles at the nose | Nacelles always aft of centre (t=0.62–0.78); only sensor domes go near the nose |
| Faceted sections touching each other | Null-System sections must have a visible gap (`sectionGap` > 0) revealing the connector |
| Crystal spires grown symmetrically | zeeLee hulls are asymmetric by design; symmetric crystal growth is wrong |
| Vertex color variation skipped | Every hull needs subtle per-vertex noise ±8% on base albedo to avoid flat-plastic look |
| Emissive only on engine meshes | Engine glow bleeds onto adjacent hull verts (t > 0.85); skip this and engines look bolted on |
| Section breaks as separate meshes | Section breaks are just darkened vertex rings, not separate geometry |
