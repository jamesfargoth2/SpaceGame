package com.galacticodyssey.ship;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.galacticodyssey.galaxy.SeedDeriver;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a lofted triangle-mesh hull for a procedural spaceship.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Build a cubic Bezier {@link SpineCurve} (nose → tail).</li>
 *   <li>Place {@link CrossSection} control sections at evenly-spaced t values.</li>
 *   <li>Interpolate between adjacent sections with {@value #INTERPOLATION_RINGS}
 *       intermediate rings.</li>
 *   <li>Orient each ring via a Frenet-like frame.</li>
 *   <li>Append swept wing geometry (one pair per {@code blueprint.wingPairs}).</li>
 *   <li>Append engine nacelle geometry (one pod per {@code blueprint.enginePodCount}).</li>
 *   <li>Fan-cap all noses and tails; mark tail caps as emissive engine glow.</li>
 * </ol>
 *
 * <h3>Vertex layout</h3>
 * Stride = {@value #VERTEX_STRIDE} floats:
 * <pre>
 *   [0..2]  position  (x, y, z)
 *   [3..5]  normal    (nx, ny, nz)
 *   [6..9]  color     (r, g, b, a)
 *   [10]    emissive  scalar (0 = unlit, 1 = engine glow)
 * </pre>
 */
public class ShipHullGenerator {

    private static final int RING_VERTEX_COUNT = 24;
    private static final int INTERPOLATION_RINGS = 4;
    public static final int VERTEX_STRIDE = 11;

    // Sub-seed component IDs within a ship's SHIP_DOMAIN seed. Each must be
    // distinct so sibling generators (palette, hull, interior) derive
    // independent, domain-separated RNG streams from the same blueprint seed.
    public static final long SUBSEED_PALETTE  = 0;
    public static final long SUBSEED_HULL     = 1;
    public static final long SUBSEED_INTERIOR = 2;

    // Nacelle sub-hull resolution
    private static final int NACELLE_RING_VERTS = 12;
    private static final int NACELLE_RINGS      = 7;

    // -------------------------------------------------------------------------

    public HullGeometry generate(ShipBlueprint blueprint) {
        return generate(blueprint, HullStyle.defaultStyle());
    }

    public HullGeometry generate(ShipBlueprint blueprint, HullStyle style) {
        long shipDomain = SeedDeriver.shipDomain(blueprint.seed);
        Random rng = new Random(SeedDeriver.forId(shipDomain, SUBSEED_HULL));
        ShipColorPalette palette =
            new ShipColorPalette(SeedDeriver.forId(shipDomain, SUBSEED_PALETTE), style);

        SpineCurve spine = generateSpine(blueprint, style, rng);
        List<CrossSection> sections = generateCrossSections(blueprint, style, rng);
        List<Float> tValues = generateTValues(sections.size());

        // Build interpolated hull rings
        List<float[]> allRings     = new ArrayList<>();
        List<Vector3> ringCenters  = new ArrayList<>();
        List<Vector3> ringTangents = new ArrayList<>();

        for (int i = 0; i < sections.size() - 1; i++) {
            CrossSection csA = sections.get(i);
            CrossSection csB = sections.get(i + 1);
            float tA = tValues.get(i);
            float tB = tValues.get(i + 1);

            for (int j = 0; j <= INTERPOLATION_RINGS; j++) {
                if (i > 0 && j == 0) continue;
                float frac = (float) j / INTERPOLATION_RINGS;
                float t    = MathUtils.lerp(tA, tB, frac);
                float w = MathUtils.lerp(csA.getHalfWidth(),  csB.getHalfWidth(),  frac);
                float h = MathUtils.lerp(csA.getHalfHeight(), csB.getHalfHeight(), frac);
                float e = MathUtils.lerp(csA.getExponent(),   csB.getExponent(),   frac);
                allRings.add(flattenRing(new CrossSection(w, h, e).generateRing(RING_VERTEX_COUNT)));
                ringCenters.add(spine.evaluate(t));
                ringTangents.add(spine.tangent(t));
            }
        }

        int ringCount    = allRings.size();
        int vertsPerRing = RING_VERTEX_COUNT;
        int hullVertCount = ringCount * vertsPerRing + 2;

        BoundingBox bbox = new BoundingBox();
        bbox.inf();

        float[] hullVerts = buildHullVerts(allRings, ringCenters, ringTangents,
                                           ringCount, vertsPerRing, palette, bbox, style);
        short[] hullIdx   = buildHullIndices(ringCount, vertsPerRing);

        List<float[]> vertChunks = new ArrayList<>();
        List<short[]> idxChunks  = new ArrayList<>();
        vertChunks.add(hullVerts);
        idxChunks.add(hullIdx);
        int vertBase = hullVertCount;

        // Wings
        if (blueprint.wingPairs > 0) {
            float[] wv = buildWingVerts(blueprint, spine, palette, rng);
            short[] wi = buildWingIndices(blueprint, vertBase);
            vertChunks.add(wv);
            idxChunks.add(wi);
            updateBounds(bbox, wv);
            vertBase += wv.length / VERTEX_STRIDE;
        }

        // Nacelles
        if (blueprint.enginePodCount > 0) {
            float[] nv = buildNacelleVerts(blueprint, spine, palette, rng);
            short[] ni = buildNacelleIndices(blueprint, vertBase);
            vertChunks.add(nv);
            idxChunks.add(ni);
            updateBounds(bbox, nv);
            vertBase += nv.length / VERTEX_STRIDE;
        }

        List<Vector3> hardpoints = new ArrayList<>();
        hardpoints.add(new Vector3(spine.evaluate(0.4f)));

        return new HullGeometry(
            mergeFloatArrays(vertChunks),
            mergeShortArrays(idxChunks),
            bbox,
            hardpoints.toArray(new Vector3[0]),
            VERTEX_STRIDE);
    }

    // -------------------------------------------------------------------------
    // Main hull
    // -------------------------------------------------------------------------

    private float[] buildHullVerts(List<float[]> allRings, List<Vector3> ringCenters,
                                   List<Vector3> ringTangents, int ringCount, int vertsPerRing,
                                   ShipColorPalette palette, BoundingBox bbox, HullStyle style) {
        int totalVerts = ringCount * vertsPerRing + 2;
        float[] vertices = new float[totalVerts * VERTEX_STRIDE];

        for (int r = 0; r < ringCount; r++) {
            float[]  ring    = allRings.get(r);
            Vector3  center  = ringCenters.get(r);
            Vector3  tangent = ringTangents.get(r);

            Vector3 up = new Vector3(0, 1, 0);
            if (Math.abs(tangent.dot(up)) > 0.99f) up.set(1, 0, 0);
            Vector3 right  = new Vector3(tangent).crs(up).nor();
            Vector3 realUp = new Vector3(right).crs(tangent).nor();

            boolean panelInset = (r % 2 == 0);

            for (int v = 0; v < vertsPerRing; v++) {
                float localX = ring[v * 2];
                float localY = ring[v * 2 + 1];

                float insetScale = (panelInset && (v % 3 != 0)) ? (1f - style.panelInsetScale) : 1f;
                localX *= insetScale;
                localY *= insetScale;

                float worldX = center.x + right.x * localX + realUp.x * localY;
                float worldY = center.y + right.y * localX + realUp.y * localY;
                float worldZ = center.z + right.z * localX + realUp.z * localY;

                float nx = right.x * localX + realUp.x * localY;
                float ny = right.y * localX + realUp.y * localY;
                float nz = right.z * localX + realUp.z * localY;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nLen > 0.001f) { nx /= nLen; ny /= nLen; nz /= nLen; }

                int idx = (r * vertsPerRing + v) * VERTEX_STRIDE;
                vertices[idx]     = worldX;
                vertices[idx + 1] = worldY;
                vertices[idx + 2] = worldZ;
                vertices[idx + 3] = nx;
                vertices[idx + 4] = ny;
                vertices[idx + 5] = nz;

                float colorBlend = (panelInset && (v % 3 != 0)) ? 0.15f : 0f;
                vertices[idx + 6]  = MathUtils.lerp(palette.baseColor.r, palette.trimColor.r, colorBlend);
                vertices[idx + 7]  = MathUtils.lerp(palette.baseColor.g, palette.trimColor.g, colorBlend);
                vertices[idx + 8]  = MathUtils.lerp(palette.baseColor.b, palette.trimColor.b, colorBlend);
                vertices[idx + 9]  = 1f;
                vertices[idx + 10] = 0f;

                bbox.ext(worldX, worldY, worldZ);
            }
        }

        // Nose cap
        Vector3 noseCenter = ringCenters.get(0);
        Vector3 noseTan    = ringTangents.get(0);
        int noseIdx = ringCount * vertsPerRing * VERTEX_STRIDE;
        vertices[noseIdx]     = noseCenter.x;  vertices[noseIdx + 1] = noseCenter.y;
        vertices[noseIdx + 2] = noseCenter.z;  vertices[noseIdx + 3] = -noseTan.x;
        vertices[noseIdx + 4] = -noseTan.y;    vertices[noseIdx + 5] = -noseTan.z;
        vertices[noseIdx + 6] = palette.baseColor.r;
        vertices[noseIdx + 7] = palette.baseColor.g;
        vertices[noseIdx + 8] = palette.baseColor.b;
        vertices[noseIdx + 9] = 1f;            vertices[noseIdx + 10] = 0f;

        // Tail cap (emissive engine glow)
        Vector3 tailCenter = ringCenters.get(ringCount - 1);
        Vector3 tailTan    = ringTangents.get(ringCount - 1);
        int tailIdx = (ringCount * vertsPerRing + 1) * VERTEX_STRIDE;
        vertices[tailIdx]     = tailCenter.x;  vertices[tailIdx + 1] = tailCenter.y;
        vertices[tailIdx + 2] = tailCenter.z;  vertices[tailIdx + 3] = tailTan.x;
        vertices[tailIdx + 4] = tailTan.y;     vertices[tailIdx + 5] = tailTan.z;
        vertices[tailIdx + 6] = palette.engineGlowColor.r;
        vertices[tailIdx + 7] = palette.engineGlowColor.g;
        vertices[tailIdx + 8] = palette.engineGlowColor.b;
        vertices[tailIdx + 9] = 1f;            vertices[tailIdx + 10] = 1f;

        return vertices;
    }

    private short[] buildHullIndices(int ringCount, int vertsPerRing) {
        int hullTri = (ringCount - 1) * vertsPerRing * 2;
        int capTri  = vertsPerRing * 2;
        short[] indices = new short[(hullTri + capTri) * 3];
        int ii = 0;

        for (int r = 0; r < ringCount - 1; r++) {
            for (int v = 0; v < vertsPerRing; v++) {
                int v2 = (v + 1) % vertsPerRing;
                short a = (short)(r       * vertsPerRing + v);
                short b = (short)(r       * vertsPerRing + v2);
                short c = (short)((r + 1) * vertsPerRing + v);
                short d = (short)((r + 1) * vertsPerRing + v2);
                indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
            }
        }

        short noseCap = (short)(ringCount * vertsPerRing);
        for (int v = 0; v < vertsPerRing; v++) {
            int v2 = (v + 1) % vertsPerRing;
            indices[ii++] = noseCap; indices[ii++] = (short)v2; indices[ii++] = (short)v;
        }

        short tailCap  = (short)(ringCount * vertsPerRing + 1);
        int lastRingStart = (ringCount - 1) * vertsPerRing;
        for (int v = 0; v < vertsPerRing; v++) {
            int v2 = (v + 1) % vertsPerRing;
            indices[ii++] = tailCap;
            indices[ii++] = (short)(lastRingStart + v);
            indices[ii++] = (short)(lastRingStart + v2);
        }
        return indices;
    }

    // -------------------------------------------------------------------------
    // Wings
    // -------------------------------------------------------------------------
    //
    // Per wing: 8 vertices (4 top-face + 4 bottom-face), 4 triangles.
    // Per pair: 2 wings × 8 = 16 verts, 2 × 4 tris × 3 idx = 24 indices.

    private float[] buildWingVerts(ShipBlueprint blueprint, SpineCurve spine,
                                   ShipColorPalette palette, Random rng) {
        int pairs = blueprint.wingPairs;
        float[] verts = new float[pairs * 2 * 8 * VERTEX_STRIDE];
        int vi = 0;

        float hullLen = blueprint.spineLength;

        for (int wp = 0; wp < pairs; wp++) {
            float attachT = 0.35f + wp * 0.08f;
            Vector3 center  = spine.evaluate(attachT);
            Vector3 tangent = spine.tangent(attachT).nor();

            Vector3 worldUp = new Vector3(0, 1, 0);
            if (Math.abs(tangent.dot(worldUp)) > 0.99f) worldUp.set(1, 0, 0);
            Vector3 right  = new Vector3(tangent).crs(worldUp).nor();
            Vector3 realUp = new Vector3(right).crs(tangent).nor();

            float env      = computeEnvelope(attachT);
            float hw       = blueprint.maxWidth  * env;
            float hh       = blueprint.maxHeight * env;

            float span      = hw * (1.1f + rng.nextFloat() * 0.9f);
            float sweep     = hullLen * (0.08f + rng.nextFloat() * 0.07f);
            float chordRoot = hw * (0.35f + rng.nextFloat() * 0.2f);
            float chordTip  = chordRoot * (0.15f + rng.nextFloat() * 0.25f);
            float thick     = hh * 0.06f;
            float dihedral  = span * 0.05f;

            float wr = palette.baseColor.r * 0.88f;
            float wg = palette.baseColor.g * 0.88f;
            float wb = palette.baseColor.b * 0.88f;

            for (int side = -1; side <= 1; side += 2) {
                float s = side;
                // Four planform corners (centre-line coords)
                Vector3 RI  = new Vector3(center).mulAdd(right, s * hw);
                Vector3 RTE = new Vector3(RI).mulAdd(tangent, chordRoot);
                Vector3 TLE = new Vector3(RI)
                        .mulAdd(right,  s * span)
                        .mulAdd(tangent, sweep)
                        .mulAdd(realUp, -dihedral);
                Vector3 TTE = new Vector3(TLE).mulAdd(tangent, chordTip);

                // Top face (normal = +realUp)
                vi = putWingVert(verts, vi, RI,  realUp,  wr,       wg,       wb,       +thick, realUp);
                vi = putWingVert(verts, vi, TLE, realUp,  wr,       wg,       wb,       +thick, realUp);
                vi = putWingVert(verts, vi, TTE, realUp,  wr,       wg,       wb,       +thick, realUp);
                vi = putWingVert(verts, vi, RTE, realUp,  wr,       wg,       wb,       +thick, realUp);

                // Bottom face (normal = -realUp, slightly darker)
                Vector3 dn = new Vector3(realUp).scl(-1f);
                vi = putWingVert(verts, vi, RI,  dn, wr*0.78f, wg*0.78f, wb*0.78f, -thick, realUp);
                vi = putWingVert(verts, vi, RTE, dn, wr*0.78f, wg*0.78f, wb*0.78f, -thick, realUp);
                vi = putWingVert(verts, vi, TTE, dn, wr*0.78f, wg*0.78f, wb*0.78f, -thick, realUp);
                vi = putWingVert(verts, vi, TLE, dn, wr*0.78f, wg*0.78f, wb*0.78f, -thick, realUp);
            }
        }
        return verts;
    }

    private int putWingVert(float[] v, int vi, Vector3 pos, Vector3 norm,
                            float r, float g, float b, float thick, Vector3 thickDir) {
        v[vi++] = pos.x + thickDir.x * thick;
        v[vi++] = pos.y + thickDir.y * thick;
        v[vi++] = pos.z + thickDir.z * thick;
        v[vi++] = norm.x; v[vi++] = norm.y; v[vi++] = norm.z;
        v[vi++] = r;      v[vi++] = g;      v[vi++] = b;
        v[vi++] = 1f;     v[vi++] = 0f;
        return vi;
    }

    private short[] buildWingIndices(ShipBlueprint blueprint, int vertBase) {
        int pairs = blueprint.wingPairs;
        // 4 triangles × 3 = 12 indices per wing; 2 wings per pair
        short[] indices = new short[pairs * 2 * 12];
        int ii = 0;

        for (int wp = 0; wp < pairs; wp++) {
            for (int s = 0; s < 2; s++) {
                short b = (short)(vertBase + (wp * 2 + s) * 8);

                // Top face verts: b=RI, b+1=TLE, b+2=TTE, b+3=RTE
                // Both triangles consistent regardless of side (backface culling is off for ship mesh)
                indices[ii++] = b;         indices[ii++] = (short)(b+1); indices[ii++] = (short)(b+3);
                indices[ii++] = (short)(b+1); indices[ii++] = (short)(b+2); indices[ii++] = (short)(b+3);

                // Bottom face verts: b+4=RI, b+5=RTE, b+6=TTE, b+7=TLE
                indices[ii++] = (short)(b+4); indices[ii++] = (short)(b+7); indices[ii++] = (short)(b+5);
                indices[ii++] = (short)(b+7); indices[ii++] = (short)(b+6); indices[ii++] = (short)(b+5);
            }
        }
        return indices;
    }

    // -------------------------------------------------------------------------
    // Nacelles (engine pods)
    // -------------------------------------------------------------------------
    //
    // Per nacelle: NACELLE_RINGS rings × NACELLE_RING_VERTS + 2 cap verts.
    // Arranged as podCount pairs (left + right), stacked outward for multi-pod ships.

    private float[] buildNacelleVerts(ShipBlueprint blueprint, SpineCurve spine,
                                      ShipColorPalette palette, Random rng) {
        int pods = blueprint.enginePodCount;
        int vertsPerNacelle = NACELLE_RINGS * NACELLE_RING_VERTS + 2;
        float[] verts = new float[pods * 2 * vertsPerNacelle * VERTEX_STRIDE];
        int vi = 0;

        float podStartT = 0.52f;

        // Evaluate hull dimensions at attachment point
        float env = computeEnvelope(podStartT);
        float hwAtt = blueprint.maxWidth  * env;
        float hhAtt = blueprint.maxHeight * env;

        float podRadius  = blueprint.maxHeight * 0.14f;
        float firstOffset = hwAtt * 0.85f + podRadius;
        float podSpacing  = podRadius * 2.6f;

        Vector3 attachCenter  = spine.evaluate(podStartT + 0.22f);
        Vector3 attachTangent = spine.tangent(podStartT + 0.22f).nor();
        Vector3 worldUp = new Vector3(0, 1, 0);
        if (Math.abs(attachTangent.dot(worldUp)) > 0.99f) worldUp.set(1, 0, 0);
        Vector3 podRight = new Vector3(attachTangent).crs(worldUp).nor();

        for (int pod = 0; pod < pods; pod++) {
            float xOffset = firstOffset + pod * podSpacing;

            for (int side = -1; side <= 1; side += 2) {
                Vector3 nacelleShift = new Vector3(podRight).scl(side * xOffset);

                for (int r = 0; r < NACELLE_RINGS; r++) {
                    float ringFrac = (float) r / (NACELLE_RINGS - 1);
                    float ringT = Math.min(podStartT + ringFrac * 0.48f, 1.0f);

                    Vector3 mainCenter = spine.evaluate(ringT);
                    Vector3 ringCenter = new Vector3(mainCenter).add(nacelleShift);
                    Vector3 ringTan    = spine.tangent(ringT).nor();

                    float nacelleEnv = computeNacelleEnvelope(ringFrac);
                    float rw = podRadius * nacelleEnv;
                    float rh = podRadius * 0.8f * nacelleEnv;

                    Vector3 rUp = new Vector3(0, 1, 0);
                    if (Math.abs(ringTan.dot(rUp)) > 0.99f) rUp.set(1, 0, 0);
                    Vector3 rRight  = new Vector3(ringTan).crs(rUp).nor();
                    Vector3 rRealUp = new Vector3(rRight).crs(ringTan).nor();

                    float[][] ring2d = new CrossSection(rw, rh, 2.4f).generateRing(NACELLE_RING_VERTS);

                    float cr = palette.baseColor.r * 0.78f;
                    float cg = palette.baseColor.g * 0.78f;
                    float cb = palette.baseColor.b * 0.78f;

                    for (int v = 0; v < NACELLE_RING_VERTS; v++) {
                        float lx = ring2d[v][0];
                        float ly = ring2d[v][1];
                        float wx = ringCenter.x + rRight.x * lx + rRealUp.x * ly;
                        float wy = ringCenter.y + rRight.y * lx + rRealUp.y * ly;
                        float wz = ringCenter.z + rRight.z * lx + rRealUp.z * ly;
                        float nx = rRight.x * lx + rRealUp.x * ly;
                        float ny = rRight.y * lx + rRealUp.y * ly;
                        float nz = rRight.z * lx + rRealUp.z * ly;
                        float nLen = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
                        if (nLen > 0.001f) { nx /= nLen; ny /= nLen; nz /= nLen; }
                        verts[vi++] = wx;  verts[vi++] = wy;  verts[vi++] = wz;
                        verts[vi++] = nx;  verts[vi++] = ny;  verts[vi++] = nz;
                        verts[vi++] = cr;  verts[vi++] = cg;  verts[vi++] = cb;
                        verts[vi++] = 1f;  verts[vi++] = 0f;
                    }
                }

                // Nacelle nose cap
                Vector3 nacNose = new Vector3(spine.evaluate(podStartT)).add(nacelleShift);
                Vector3 nacNoseTan = spine.tangent(podStartT).nor();
                verts[vi++] = nacNose.x;     verts[vi++] = nacNose.y;     verts[vi++] = nacNose.z;
                verts[vi++] = -nacNoseTan.x; verts[vi++] = -nacNoseTan.y; verts[vi++] = -nacNoseTan.z;
                verts[vi++] = palette.baseColor.r * 0.78f;
                verts[vi++] = palette.baseColor.g * 0.78f;
                verts[vi++] = palette.baseColor.b * 0.78f;
                verts[vi++] = 1f; verts[vi++] = 0f;

                // Nacelle tail cap (emissive)
                float tailT = Math.min(podStartT + 0.48f, 1.0f);
                Vector3 nacTail = new Vector3(spine.evaluate(tailT)).add(nacelleShift);
                Vector3 nacTailTan = spine.tangent(tailT).nor();
                verts[vi++] = nacTail.x;     verts[vi++] = nacTail.y;     verts[vi++] = nacTail.z;
                verts[vi++] = nacTailTan.x;  verts[vi++] = nacTailTan.y;  verts[vi++] = nacTailTan.z;
                verts[vi++] = palette.engineGlowColor.r;
                verts[vi++] = palette.engineGlowColor.g;
                verts[vi++] = palette.engineGlowColor.b;
                verts[vi++] = 1f; verts[vi++] = 1f;
            }
        }
        return verts;
    }

    private short[] buildNacelleIndices(ShipBlueprint blueprint, int vertBase) {
        int pods = blueprint.enginePodCount;
        int nacelleCount = pods * 2;
        int rvc = NACELLE_RING_VERTS;
        int ringsCount = NACELLE_RINGS;
        int vertsPerNacelle = ringsCount * rvc + 2;
        int triPerNacelle = (ringsCount - 1) * rvc * 2 + rvc * 2;
        short[] indices = new short[nacelleCount * triPerNacelle * 3];
        int ii = 0;

        for (int n = 0; n < nacelleCount; n++) {
            int base = vertBase + n * vertsPerNacelle;

            // Ring strips
            for (int r = 0; r < ringsCount - 1; r++) {
                for (int v = 0; v < rvc; v++) {
                    int v2 = (v + 1) % rvc;
                    short a = (short)(base + r * rvc + v);
                    short b = (short)(base + r * rvc + v2);
                    short c = (short)(base + (r+1) * rvc + v);
                    short d = (short)(base + (r+1) * rvc + v2);
                    indices[ii++] = a; indices[ii++] = c; indices[ii++] = b;
                    indices[ii++] = b; indices[ii++] = c; indices[ii++] = d;
                }
            }

            // Nose fan cap
            short noseCap = (short)(base + ringsCount * rvc);
            for (int v = 0; v < rvc; v++) {
                int v2 = (v + 1) % rvc;
                indices[ii++] = noseCap;
                indices[ii++] = (short)(base + v2);
                indices[ii++] = (short)(base + v);
            }

            // Tail fan cap (emissive)
            short tailCap = (short)(base + ringsCount * rvc + 1);
            int lastRing  = base + (ringsCount - 1) * rvc;
            for (int v = 0; v < rvc; v++) {
                int v2 = (v + 1) % rvc;
                indices[ii++] = tailCap;
                indices[ii++] = (short)(lastRing + v);
                indices[ii++] = (short)(lastRing + v2);
            }
        }
        return indices;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Shape envelope: narrow at nose (t=0), full at mid-body (t≈0.6), slightly tapered at tail. */
    private static float computeEnvelope(float frac) {
        if      (frac < 0.15f) return frac / 0.15f * 0.3f;
        else if (frac < 0.60f) return 0.3f + (frac - 0.15f) / 0.45f * 0.7f;
        else                   return 1f   - (frac - 0.60f) / 0.40f * 0.4f;
    }

    /** Nacelle cross-section taper: zero at ends, full in the middle. */
    private static float computeNacelleEnvelope(float frac) {
        if      (frac < 0.18f) return frac / 0.18f;
        else if (frac < 0.72f) return 1.0f;
        else                   return 1f - (frac - 0.72f) / 0.28f;
    }

    private static void updateBounds(BoundingBox bbox, float[] verts) {
        for (int i = 0; i < verts.length; i += VERTEX_STRIDE) {
            bbox.ext(verts[i], verts[i+1], verts[i+2]);
        }
    }

    private static float[] mergeFloatArrays(List<float[]> chunks) {
        int total = 0;
        for (float[] c : chunks) total += c.length;
        float[] result = new float[total];
        int pos = 0;
        for (float[] c : chunks) { System.arraycopy(c, 0, result, pos, c.length); pos += c.length; }
        return result;
    }

    private static short[] mergeShortArrays(List<short[]> chunks) {
        int total = 0;
        for (short[] c : chunks) total += c.length;
        short[] result = new short[total];
        int pos = 0;
        for (short[] c : chunks) { System.arraycopy(c, 0, result, pos, c.length); pos += c.length; }
        return result;
    }

    private SpineCurve generateSpine(ShipBlueprint blueprint, HullStyle style, Random rng) {
        float len        = blueprint.spineLength;
        float ctrlOffset = len * 0.3f;
        float yVar       = len * 0.06f * style.spineCurvature;
        float xVar       = len * 0.04f * style.spineCurvature;
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(
            (rng.nextFloat() - 0.5f) * 2f * xVar,
            rng.nextFloat() * yVar,
            -ctrlOffset);
        Vector3 p2 = new Vector3(
            (rng.nextFloat() - 0.5f) * 2f * xVar,
            rng.nextFloat() * yVar,
            -(len - ctrlOffset));
        Vector3 p3 = new Vector3(0, 0, -len);
        return new SpineCurve(p0, p1, p2, p3);
    }

    private List<CrossSection> generateCrossSections(ShipBlueprint blueprint, HullStyle style, Random rng) {
        List<CrossSection> sections = new ArrayList<>();
        int count = blueprint.crossSectionCount;

        // Aspect ratio (height/width) biased by style
        float aspectBias = style.aspectBiasMin
                + rng.nextFloat() * (style.aspectBiasMax - style.aspectBiasMin);

        float expRange = style.sectionExponentMax - style.sectionExponentMin;

        for (int i = 0; i < count; i++) {
            float frac     = (float) i / (count - 1);
            float envelope = computeEnvelope(frac);

            float w   = blueprint.maxWidth  * envelope * (0.85f + rng.nextFloat() * 0.15f);
            float h   = blueprint.maxHeight * envelope * aspectBias * (0.85f + rng.nextFloat() * 0.15f);
            float exp = style.sectionExponentMin + rng.nextFloat() * expRange;
            sections.add(new CrossSection(Math.max(0.1f, w), Math.max(0.1f, h), exp));
        }
        return sections;
    }

    private List<Float> generateTValues(int count) {
        List<Float> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add((float) i / (count - 1));
        return values;
    }

    private float[] flattenRing(float[][] ring) {
        float[] flat = new float[ring.length * 2];
        for (int i = 0; i < ring.length; i++) {
            flat[i * 2]     = ring[i][0];
            flat[i * 2 + 1] = ring[i][1];
        }
        return flat;
    }
}
