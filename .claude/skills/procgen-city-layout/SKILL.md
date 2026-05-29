---
name: procgen-city-layout
description: >
  Enforces procedural city layout generation using district zoning, street
  network growth (grid, organic, radial), landmark anchoring, city wall and
  gate placement, terrain conformance, block subdivision, lot assignment,
  and faction-influenced urban form for a libGDX 3D space game. Use this
  skill whenever writing or modifying: planetary city generation, settlement
  layout on colonised worlds, district zoning, road network algorithms,
  city block subdivision, landmark placement, urban density gradients,
  or any code that produces a top-down city footprint from a seed.
  Also triggers when adding slums, market districts, spaceport districts,
  defensive perimeters, or any large-scale inhabited ground structure.
---

# Procedural City Layout Generation

## City Types & Scale

```java
public enum CityType {
    OUTPOST,        // 2–8 buildings, single dirt road, tiny landing pad
    FRONTIER_TOWN,  // 8–30 buildings, grid + plaza, no walls
    COLONY,         // 30–100 buildings, 3–5 districts, possible wall
    REGIONAL_HUB,   // 100–400 buildings, radial avenues, walls + gates
    METROPOLIS      // 400–2000+ buildings, multi-layered districts, megastructure features
}

public enum CityForm {
    GRID,           // colonial / military: strict orthogonal blocks
    ORGANIC,        // medieval / old: curved streets grown outward from centre
    RADIAL,         // planned capital: avenues radiate from a central landmark
    LINEAR,         // trade route / river: city grows along a single spine
    SPRAWL          // unplanned: irregular growth around an early nucleus
}
```

---

## CityConfig

```java
public class CityConfig {
    public long       seed;
    public CityType   type;
    public CityForm   form;
    public FactionId  rulingFaction;      // drives aesthetic and district mix
    public float      radiusMetres;       // outer boundary radius
    public float      populationDensity;  // 0–1; affects building count and block size
    public boolean    hasDefensiveWall;
    public boolean    hasSpaceport;
    public TerrainMap terrain;            // optional height/water data for conformance
}
```

---

## Generation Pipeline

```
1. Anchor landmarks  → place centre, spaceport, temple, market plaza
2. Street skeleton   → grow primary road network from landmarks
3. Fill streets      → subdivide skeleton into secondary/tertiary roads
4. Block extraction  → identify enclosed polygons as city blocks
5. Lot subdivision   → split blocks into individual building lots
6. District zoning   → assign zone type to each block
7. Density gradient  → tighten lot sizes near centre, loosen at periphery
8. Wall & gates      → if fortified, trace perimeter and cut gate arches
9. Terrain conform   → adjust roads/lots to avoid water, cliffs, slopes > 30°
```

---

## Landmark Anchoring

```java
public class LandmarkPlacer {

    public Array<Landmark> place(CityConfig cfg, Random rng) {
        Array<Landmark> lm = new Array<>();

        // Civic centre always at or near geometric centre
        lm.add(new Landmark(LandmarkType.CIVIC_CENTRE,
                             jitter(Vector2.Zero, 0.05f * cfg.radiusMetres, rng)));

        if (cfg.hasSpaceport) {
            float angle = rng.nextFloat() * MathUtils.PI2;
            float dist  = cfg.radiusMetres * MathUtils.random(rng, 0.65f, 0.85f);
            lm.add(new Landmark(LandmarkType.SPACEPORT, polar(angle, dist)));
        }

        lm.add(new Landmark(LandmarkType.MARKET_PLAZA, marketPos(lm, cfg, rng)));
        lm.add(factionLandmark(cfg.rulingFaction, cfg, rng));
        return lm;
    }
}
```

---

## Street Network Growth

### Grid Form

```java
public class GridStreetBuilder {

    public Array<Street> build(CityConfig cfg, Random rng) {
        Array<Street> streets = new Array<>();
        float blockSize  = blockSizeFor(cfg.populationDensity);
        int   halfCols   = (int)(cfg.radiusMetres / blockSize);
        float gridAngle  = rng.nextFloat() * MathUtils.PI / 4f;

        for (int i = -halfCols; i <= halfCols; i += 3) {
            streets.add(makeLine(i * blockSize, -cfg.radiusMetres,
                                  i * blockSize,  cfg.radiusMetres,
                                  StreetTier.AVENUE, gridAngle));
            streets.add(makeLine(-cfg.radiusMetres, i * blockSize,
                                   cfg.radiusMetres, i * blockSize,
                                   StreetTier.AVENUE, gridAngle));
        }
        for (int i = -halfCols; i <= halfCols; i++) {
            StreetTier tier = (i % 3 == 0) ? StreetTier.AVENUE : StreetTier.STREET;
            streets.add(makeLine(i * blockSize, -cfg.radiusMetres,
                                  i * blockSize,  cfg.radiusMetres, tier, gridAngle));
            streets.add(makeLine(-cfg.radiusMetres, i * blockSize,
                                   cfg.radiusMetres, i * blockSize, tier, gridAngle));
        }
        clipToRadius(streets, cfg.radiusMetres);
        return streets;
    }

    private float blockSizeFor(float density) {
        return MathUtils.lerp(60f, 20f, density);
    }
}
```

### Organic Form (L-System Growth)

```java
public class OrganicStreetBuilder {

    public Array<Street> build(CityConfig cfg, Array<Landmark> landmarks, Random rng) {
        Array<Street> streets = new Array<>();
        Array<GrowthAgent> agents = new Array<>();

        for (Landmark lm : landmarks) {
            int rays = 3 + rng.nextInt(3);
            for (int r = 0; r < rays; r++) {
                float angle = r * (MathUtils.PI2 / rays) + rng.nextFloat() * 0.3f;
                agents.add(new GrowthAgent(lm.position.cpy(), angle, StreetTier.AVENUE));
            }
        }

        int maxSteps = 200;
        for (int step = 0; step < maxSteps && !agents.isEmpty(); step++) {
            Array<GrowthAgent> next = new Array<>();
            for (GrowthAgent ag : agents) {
                if (ag.position.len() > cfg.radiusMetres) continue;
                if (ag.stepCount > ag.maxSteps) continue;
                steerTowardLandmark(ag, landmarks);
                ag.angle += MathUtils.random(rng, -0.25f, 0.25f);
                float segLen = segLengthFor(ag.tier);
                Vector2 end  = ag.position.cpy().add(
                    MathUtils.cos(ag.angle) * segLen,
                    MathUtils.sin(ag.angle) * segLen);
                if (!tooCloseToExisting(streets, ag.position, end, 4f)) {
                    streets.add(new Street(ag.position.cpy(), end.cpy(), ag.tier));
                    if (rng.nextFloat() < 0.2f) {
                        next.add(new GrowthAgent(end.cpy(),
                            ag.angle + MathUtils.PI / 2f + rng.nextFloat() * 0.5f - 0.25f,
                            StreetTier.STREET));
                    }
                }
                ag.position.set(end);
                ag.stepCount++;
                next.add(ag);
            }
            agents = next;
        }
        return streets;
    }
}
```

### Radial Form

```java
public class RadialStreetBuilder {

    public Array<Street> build(CityConfig cfg, Random rng) {
        Array<Street> streets = new Array<>();
        int   rings  = (int)(cfg.radiusMetres / 40f) + 1;
        int   spokes = 6 + rng.nextInt(4);

        for (int r = 1; r <= rings; r++) {
            float radius = r * (cfg.radiusMetres / rings);
            StreetTier t = (r == 1 || r == rings) ? StreetTier.AVENUE : StreetTier.STREET;
            addRing(streets, radius, t);
        }
        for (int s = 0; s < spokes; s++) {
            float angle = s * (MathUtils.PI2 / spokes);
            streets.add(new Street(Vector2.Zero,
                                    polar(angle, cfg.radiusMetres),
                                    StreetTier.AVENUE));
        }
        return streets;
    }
}
```

---

## District Zoning

```java
public enum DistrictType {
    CIVIC, RESIDENTIAL, COMMERCIAL, INDUSTRIAL, SPACEPORT,
    MILITARY, SLUM, RELIGIOUS, GARDEN, UNKNOWN
}

public class DistrictZoner {

    public void zone(Array<CityBlock> blocks, Array<Landmark> landmarks,
                     CityConfig cfg, Random rng) {
        for (CityBlock block : blocks) {
            float dist = block.centroid().dst(Vector2.Zero);
            float t    = dist / cfg.radiusMetres;

            Landmark adjacent = nearestLandmark(block, landmarks, 10f);
            if (adjacent != null) {
                block.districtType = landmarkZone(adjacent.type);
                continue;
            }
            block.districtType = rollDistrictByDepth(t, cfg.rulingFaction, rng);
        }
    }

    private DistrictType rollDistrictByDepth(float t, FactionId faction, Random rng) {
        if (t < 0.15f) return DistrictType.CIVIC;
        if (t < 0.30f) {
            float r = rng.nextFloat();
            if (r < 0.4f) return DistrictType.COMMERCIAL;
            if (r < 0.7f) return DistrictType.RESIDENTIAL;
            return DistrictType.RELIGIOUS;
        }
        if (t < 0.55f) {
            float r = rng.nextFloat();
            if (r < 0.35f) return DistrictType.RESIDENTIAL;
            if (r < 0.60f) return DistrictType.COMMERCIAL;
            if (r < 0.75f) return DistrictType.INDUSTRIAL;
            return DistrictType.GARDEN;
        }
        if (t < 0.80f) {
            float r = rng.nextFloat();
            if (r < 0.40f) return DistrictType.INDUSTRIAL;
            if (r < 0.65f) return DistrictType.RESIDENTIAL;
            if (r < 0.80f) return DistrictType.SLUM;
            return DistrictType.MILITARY;
        }
        float r = rng.nextFloat();
        if (r < 0.45f) return DistrictType.SLUM;
        if (r < 0.70f) return DistrictType.INDUSTRIAL;
        return DistrictType.MILITARY;
    }
}
```

---

## Block Subdivision into Lots

```java
public class LotSubdivider {

    public Array<BuildingLot> subdivide(CityBlock block, DistrictType zone, Random rng) {
        Array<BuildingLot> lots = new Array<>();
        Array<Rect> queue       = new Array<>();
        queue.add(block.footprint);
        float minLotSize = minLotSizeFor(zone);
        float maxLotSize = maxLotSizeFor(zone);

        while (!queue.isEmpty()) {
            Rect cell = queue.removeIndex(0);
            float area = cell.width * cell.height;
            if (area <= minLotSize || rng.nextFloat() < stopProbability(area, maxLotSize)) {
                lots.add(new BuildingLot(cell, zone));
            } else {
                boolean splitW  = cell.width >= cell.height;
                float splitFrac = MathUtils.random(rng, 0.4f, 0.6f);
                if (splitW) {
                    float w1 = cell.width * splitFrac;
                    queue.add(new Rect(cell.x, cell.y, w1, cell.height));
                    queue.add(new Rect(cell.x + w1, cell.y, cell.width - w1, cell.height));
                } else {
                    float h1 = cell.height * splitFrac;
                    queue.add(new Rect(cell.x, cell.y, cell.width, h1));
                    queue.add(new Rect(cell.x, cell.y + h1, cell.width, cell.height - h1));
                }
            }
        }
        return lots;
    }

    private float minLotSizeFor(DistrictType zone) {
        switch (zone) {
            case CIVIC:      return 400f;
            case COMMERCIAL: return  80f;
            case INDUSTRIAL: return 200f;
            case SLUM:       return  20f;
            default:         return  60f;
        }
    }

    private float maxLotSizeFor(DistrictType zone) {
        switch (zone) {
            case CIVIC:      return 2000f;
            case INDUSTRIAL: return  800f;
            case SLUM:       return   60f;
            default:         return  250f;
        }
    }

    private float stopProbability(float area, float maxLotSize) {
        return MathUtils.clamp(1f - (area / maxLotSize), 0f, 0.85f);
    }
}
```

---

## Defensive Wall & Gates

```java
public class WallBuilder {

    public CityWall build(Array<CityBlock> blocks, CityConfig cfg,
                           Array<Street> streets, Random rng) {
        Array<Vector2> periphery = new Array<>();
        for (CityBlock b : blocks) {
            if (b.centroid().len() > cfg.radiusMetres * 0.75f)
                periphery.addAll(b.corners());
        }
        Array<Vector2> hull = GeometryUtils.convexHull(periphery);
        CityWall wall       = new CityWall(hull);
        wall.thicknessM     = wallThicknessFor(cfg.type);
        wall.heightM        = wallHeightFor(cfg.type);

        for (Street st : streets) {
            if (st.tier == StreetTier.AVENUE) {
                Vector2 intersection = lineHullIntersect(st, hull);
                if (intersection != null)
                    wall.gates.add(new CityGate(intersection, st.direction(), gateWidthFor(cfg.type)));
            }
        }
        return wall;
    }
}
```

---

## Terrain Conformance

```java
public class TerrainConformer {

    public void conform(Array<Street> streets, Array<BuildingLot> lots,
                         TerrainMap terrain) {
        streets.removeIf(st -> {
            float slope      = terrain.maxSlopeAlongLine(st.start, st.end);
            boolean water    = terrain.crossesWater(st.start, st.end);
            return slope > 0.577f || water;
        });
        lots.removeIf(lot -> terrain.fractionInaccessible(lot.footprint) > 0.40f);
    }
}
```

---

## Common Mistakes

| Mistake | Fix |
|---|---|
| Placing spaceport at city centre | Spaceport needs peripheral clearance; always place at 65–85% radius |
| Same block size everywhere | Use density gradient: small blocks near centre, large at periphery |
| District zoning ignores landmarks | Blocks adjacent to landmarks must inherit that landmark's zone type |
| No loops in street network | Pure spanning-tree streets feel dead; always add secondary cross streets |
| Wall gates don't align with avenues | Gates must be cut where avenues pierce the perimeter, not randomly |
| Lots subdivided identically | Use zone-specific min/max lot sizes; slum lots tiny, civic lots huge |
| Terrain not checked | Streets and lots must be purged that cross water or > 30° slopes |
