package com.galacticodyssey.ship;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
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
 *       intermediate rings (linear lerp of half-extents and superellipse exponent).</li>
 *   <li>Orient each ring via a Frenet-like frame (tangent × world-up → right → up).</li>
 *   <li>Connect rings into triangle strips (quad rows).</li>
 *   <li>Fan-cap the nose and tail rings.</li>
 *   <li>Assign per-vertex color from the {@link ShipColorPalette} using a panel-inset
 *       pattern; mark the tail cap vertex as fully emissive (engine glow).</li>
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

    /** Number of vertices around each cross-section ring. */
    private static final int RING_VERTEX_COUNT = 24;

    /** Interpolated rings inserted between each pair of control cross-sections. */
    private static final int INTERPOLATION_RINGS = 4;

    /**
     * Fractional scale reduction applied to every third vertex on alternating
     * rings to create a subtle panel-inset surface detail.
     */
    private static final float PANEL_INSET = 0.015f;

    /** Floats per vertex: position(3) + normal(3) + color(4) + emissive(1). */
    public static final int VERTEX_STRIDE = 11;

    // -------------------------------------------------------------------------

    /**
     * Generates a complete hull mesh for the given blueprint.
     *
     * @param blueprint seed + size-class parameters; same seed always produces
     *                  the same geometry
     * @return immutable {@link HullGeometry} result
     */
    public HullGeometry generate(ShipBlueprint blueprint) {
        Random rng = new Random(blueprint.seed + 1);
        ShipColorPalette palette = new ShipColorPalette(blueprint.seed);

        SpineCurve spine = generateSpine(blueprint, rng);
        List<CrossSection> sections = generateCrossSections(blueprint, rng);
        List<Float> tValues = generateTValues(sections.size());

        // Build all vertex rings by interpolating between adjacent control sections
        List<float[]> allRings    = new ArrayList<>();
        List<Vector3> ringCenters  = new ArrayList<>();
        List<Vector3> ringTangents = new ArrayList<>();

        for (int i = 0; i < sections.size() - 1; i++) {
            CrossSection csA = sections.get(i);
            CrossSection csB = sections.get(i + 1);
            float tA = tValues.get(i);
            float tB = tValues.get(i + 1);

            for (int j = 0; j <= INTERPOLATION_RINGS; j++) {
                // Skip the first ring on all but the very first segment to avoid duplication
                if (i > 0 && j == 0) continue;

                float frac = (float) j / INTERPOLATION_RINGS;
                float t    = MathUtils.lerp(tA, tB, frac);

                float w = MathUtils.lerp(csA.getHalfWidth(),  csB.getHalfWidth(),  frac);
                float h = MathUtils.lerp(csA.getHalfHeight(), csB.getHalfHeight(), frac);
                float e = MathUtils.lerp(csA.getExponent(),   csB.getExponent(),   frac);

                CrossSection interpolated = new CrossSection(w, h, e);
                allRings.add(flattenRing(interpolated.generateRing(RING_VERTEX_COUNT)));
                ringCenters.add(spine.evaluate(t));
                ringTangents.add(spine.tangent(t));
            }
        }

        int ringCount    = allRings.size();
        int vertsPerRing = RING_VERTEX_COUNT;
        int totalVerts   = ringCount * vertsPerRing + 2; // +2 for nose + tail cap vertices

        float[] vertices = new float[totalVerts * VERTEX_STRIDE];
        List<Vector3> hardpointList = new ArrayList<>();

        BoundingBox bbox = new BoundingBox();
        bbox.inf();

        // --- Hull rings ---
        for (int r = 0; r < ringCount; r++) {
            float[]  ring    = allRings.get(r);
            Vector3  center  = ringCenters.get(r);
            Vector3  tangent = ringTangents.get(r);

            // Build an orthonormal frame (right, realUp) perpendicular to the tangent
            Vector3 up = new Vector3(0, 1, 0);
            if (Math.abs(tangent.dot(up)) > 0.99f) up.set(1, 0, 0);
            Vector3 right  = new Vector3(tangent).crs(up).nor();
            Vector3 realUp = new Vector3(right).crs(tangent).nor();

            boolean panelInset = (r % 2 == 0);

            for (int v = 0; v < vertsPerRing; v++) {
                float localX = ring[v * 2];
                float localY = ring[v * 2 + 1];

                // Panel-inset: scale inward every third vertex on alternating rings
                float insetScale = (panelInset && (v % 3 != 0)) ? (1f - PANEL_INSET) : 1f;
                localX *= insetScale;
                localY *= insetScale;

                float worldX = center.x + right.x * localX + realUp.x * localY;
                float worldY = center.y + right.y * localX + realUp.y * localY;
                float worldZ = center.z + right.z * localX + realUp.z * localY;

                // Outward normal approximated by the local radial direction
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
                vertices[idx + 10] = 0f; // unlit

                bbox.ext(worldX, worldY, worldZ);
            }
        }

        // --- Nose cap vertex (facing backward along tangent) ---
        Vector3 noseCenter = ringCenters.get(0);
        Vector3 noseTan    = ringTangents.get(0);
        int noseIdx = ringCount * vertsPerRing * VERTEX_STRIDE;
        vertices[noseIdx]     = noseCenter.x;
        vertices[noseIdx + 1] = noseCenter.y;
        vertices[noseIdx + 2] = noseCenter.z;
        vertices[noseIdx + 3] = -noseTan.x;
        vertices[noseIdx + 4] = -noseTan.y;
        vertices[noseIdx + 5] = -noseTan.z;
        vertices[noseIdx + 6] = palette.baseColor.r;
        vertices[noseIdx + 7] = palette.baseColor.g;
        vertices[noseIdx + 8] = palette.baseColor.b;
        vertices[noseIdx + 9] = 1f;
        vertices[noseIdx + 10] = 0f;

        // --- Tail cap vertex (emissive engine glow) ---
        Vector3 tailCenter = ringCenters.get(ringCount - 1);
        Vector3 tailTan    = ringTangents.get(ringCount - 1);
        int tailIdx = (ringCount * vertsPerRing + 1) * VERTEX_STRIDE;
        vertices[tailIdx]     = tailCenter.x;
        vertices[tailIdx + 1] = tailCenter.y;
        vertices[tailIdx + 2] = tailCenter.z;
        vertices[tailIdx + 3] = tailTan.x;
        vertices[tailIdx + 4] = tailTan.y;
        vertices[tailIdx + 5] = tailTan.z;
        vertices[tailIdx + 6] = palette.engineGlowColor.r;
        vertices[tailIdx + 7] = palette.engineGlowColor.g;
        vertices[tailIdx + 8] = palette.engineGlowColor.b;
        vertices[tailIdx + 9] = 1f;
        vertices[tailIdx + 10] = 1f; // fully emissive

        // --- Index buffer ---
        int hullTriangles = (ringCount - 1) * vertsPerRing * 2;
        int capTriangles  = vertsPerRing * 2;
        short[] indices = new short[(hullTriangles + capTriangles) * 3];
        int ii = 0;

        // Hull quad-strip
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

        // Nose fan cap (first ring)
        short noseVertIdx = (short)(ringCount * vertsPerRing);
        for (int v = 0; v < vertsPerRing; v++) {
            int v2 = (v + 1) % vertsPerRing;
            indices[ii++] = noseVertIdx;
            indices[ii++] = (short) v2;
            indices[ii++] = (short) v;
        }

        // Tail fan cap (last ring)
        short tailVertIdx    = (short)(ringCount * vertsPerRing + 1);
        int   lastRingStart  = (ringCount - 1) * vertsPerRing;
        for (int v = 0; v < vertsPerRing; v++) {
            int v2 = (v + 1) % vertsPerRing;
            indices[ii++] = tailVertIdx;
            indices[ii++] = (short)(lastRingStart + v);
            indices[ii++] = (short)(lastRingStart + v2);
        }

        // Single mid-spine hardpoint
        hardpointList.add(new Vector3(spine.evaluate(0.4f)));

        return new HullGeometry(vertices, indices, bbox,
            hardpointList.toArray(new Vector3[0]), VERTEX_STRIDE);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SpineCurve generateSpine(ShipBlueprint blueprint, Random rng) {
        float len       = blueprint.spineLength;
        float ctrlOffset = len * 0.3f;
        float yVariation = len * 0.05f;
        Vector3 p0 = new Vector3(0, 0, 0);
        Vector3 p1 = new Vector3(0, rng.nextFloat() * yVariation, -ctrlOffset);
        Vector3 p2 = new Vector3(0, rng.nextFloat() * yVariation, -(len - ctrlOffset));
        Vector3 p3 = new Vector3(0, 0, -len);
        return new SpineCurve(p0, p1, p2, p3);
    }

    private List<CrossSection> generateCrossSections(ShipBlueprint blueprint, Random rng) {
        List<CrossSection> sections = new ArrayList<>();
        int count = blueprint.crossSectionCount;

        for (int i = 0; i < count; i++) {
            float frac = (float) i / (count - 1);

            // Shape envelope: narrow at nose, full at mid-body, taper slightly at tail
            float envelope;
            if      (frac < 0.15f) envelope = frac / 0.15f * 0.3f;
            else if (frac < 0.60f) envelope = 0.3f + (frac - 0.15f) / 0.45f * 0.7f;
            else                   envelope = 1f   - (frac - 0.60f) / 0.40f * 0.4f;

            float w   = blueprint.maxWidth  * envelope * (0.85f + rng.nextFloat() * 0.15f);
            float h   = blueprint.maxHeight * envelope * (0.85f + rng.nextFloat() * 0.15f);
            float exp = 2.0f + rng.nextFloat() * 1.5f;
            sections.add(new CrossSection(Math.max(0.1f, w), Math.max(0.1f, h), exp));
        }
        return sections;
    }

    private List<Float> generateTValues(int count) {
        List<Float> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add((float) i / (count - 1));
        }
        return values;
    }

    /** Converts a 2-D ring ({@code float[n][2]}) into a flat {@code float[n*2]} array. */
    private float[] flattenRing(float[][] ring) {
        float[] flat = new float[ring.length * 2];
        for (int i = 0; i < ring.length; i++) {
            flat[i * 2]     = ring[i][0];
            flat[i * 2 + 1] = ring[i][1];
        }
        return flat;
    }
}
