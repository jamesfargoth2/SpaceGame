package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

/**
 * Generates procedural cockpit interior meshes for each ship size class.
 * <p>
 * Uses libGDX {@link ModelBuilder}/{@link MeshPartBuilder} to produce a {@link Model}
 * consisting of hull panels (dark metallic) and a console surface (emissive cyan). The
 * forward face is left open as a viewport cutout.
 * </p>
 * <p>
 * Returned models are caller-owned and must be disposed when no longer needed.
 * </p>
 */
public final class CockpitGeometryBuilder {

    /** Ship size classes that determine cockpit dimensions. */
    public enum SizeClass {
        /** Fighter-class: 3m wide x 4m long x 2.5m tall. */
        SMALL,
        /** Bridge-class: 8m wide x 6m long x 3m tall. */
        MEDIUM,
        /** Command deck: 15m wide x 10m long x 4m tall. */
        LARGE
    }

    private CockpitGeometryBuilder() {}

    /**
     * Builds and returns a cockpit model for the given size class.
     *
     * @param sizeClass the size class to build
     * @return a new {@link Model}; caller must dispose
     */
    public static Model build(SizeClass sizeClass) {
        switch (sizeClass) {
            case SMALL:  return buildSmall();
            case MEDIUM: return buildMedium();
            case LARGE:  return buildLarge();
            default:     return buildSmall();
        }
    }

    private static Model buildSmall() {
        return buildCockpit(3f, 4f, 2.5f, 0.6f);
    }

    private static Model buildMedium() {
        return buildCockpit(8f, 6f, 3f, 0.7f);
    }

    private static Model buildLarge() {
        return buildCockpit(15f, 10f, 4f, 0.8f);
    }

    /**
     * Core geometry builder.
     *
     * @param width            cockpit width in metres
     * @param length           cockpit depth in metres
     * @param height           cockpit height in metres
     * @param viewportFraction fraction of forward face left open as the viewport window
     */
    private static Model buildCockpit(float width, float length, float height, float viewportFraction) {
        final ModelBuilder builder = new ModelBuilder();
        final long attributes = Usage.Position | Usage.Normal | Usage.ColorUnpacked;

        // Dark metallic hull surfaces
        final Material hullMaterial = new Material(
            ColorAttribute.createDiffuse(0.15f, 0.15f, 0.18f, 1f),
            ColorAttribute.createSpecular(0.3f, 0.3f, 0.3f, 1f)
        );

        // Console surfaces with emissive cyan accent (UI palette: 0, 0.9, 1)
        final Material consoleMaterial = new Material(
            ColorAttribute.createDiffuse(0.05f, 0.08f, 0.1f, 1f),
            ColorAttribute.createEmissive(0f, 0.9f, 1f, 0.3f)
        );

        final float hw = width / 2f;
        final float hl = length / 2f;

        builder.begin();

        // --- Floor ---
        MeshPartBuilder floor = builder.part("floor", GL20.GL_TRIANGLES, attributes, hullMaterial);
        floor.rect(
            new Vector3(-hw, 0, -hl), new Vector3(-hw, 0, hl),
            new Vector3( hw, 0,  hl), new Vector3( hw, 0, -hl),
            new Vector3(0, 1, 0)
        );

        // --- Ceiling ---
        MeshPartBuilder ceiling = builder.part("ceiling", GL20.GL_TRIANGLES, attributes, hullMaterial);
        ceiling.rect(
            new Vector3(-hw, height,  hl), new Vector3(-hw, height, -hl),
            new Vector3( hw, height, -hl), new Vector3( hw, height,  hl),
            new Vector3(0, -1, 0)
        );

        // --- Left wall ---
        MeshPartBuilder leftWall = builder.part("leftWall", GL20.GL_TRIANGLES, attributes, hullMaterial);
        leftWall.rect(
            new Vector3(-hw, 0,      hl), new Vector3(-hw, 0,      -hl),
            new Vector3(-hw, height, -hl), new Vector3(-hw, height,  hl),
            new Vector3(1, 0, 0)
        );

        // --- Right wall ---
        MeshPartBuilder rightWall = builder.part("rightWall", GL20.GL_TRIANGLES, attributes, hullMaterial);
        rightWall.rect(
            new Vector3(hw, 0,      -hl), new Vector3(hw, 0,       hl),
            new Vector3(hw, height,  hl), new Vector3(hw, height, -hl),
            new Vector3(-1, 0, 0)
        );

        // --- Back wall (behind pilot) ---
        MeshPartBuilder backWall = builder.part("backWall", GL20.GL_TRIANGLES, attributes, hullMaterial);
        backWall.rect(
            new Vector3( hw, 0,      hl), new Vector3(-hw, 0,      hl),
            new Vector3(-hw, height, hl), new Vector3( hw, height, hl),
            new Vector3(0, 0, -1)
        );

        // --- Forward wall with viewport cutout ---
        // Only side/top/bottom border panels are emitted; the viewport opening has no geometry.
        final float viewportWidth  = width * viewportFraction;
        final float viewportHeight = height * viewportFraction;
        final float borderH = (width  - viewportWidth)  / 2f;
        final float borderV = (height - viewportHeight) / 2f;

        // Top strip above viewport
        MeshPartBuilder fwdTop = builder.part("fwdTop", GL20.GL_TRIANGLES, attributes, hullMaterial);
        fwdTop.rect(
            new Vector3(-hw, height - borderV, -hl), new Vector3( hw, height - borderV, -hl),
            new Vector3( hw, height,           -hl), new Vector3(-hw, height,           -hl),
            new Vector3(0, 0, 1)
        );

        // Bottom strip below viewport
        MeshPartBuilder fwdBot = builder.part("fwdBot", GL20.GL_TRIANGLES, attributes, hullMaterial);
        fwdBot.rect(
            new Vector3(-hw, 0,       -hl), new Vector3( hw, 0,       -hl),
            new Vector3( hw, borderV, -hl), new Vector3(-hw, borderV, -hl),
            new Vector3(0, 0, 1)
        );

        // Left strip beside viewport
        MeshPartBuilder fwdLeft = builder.part("fwdLeft", GL20.GL_TRIANGLES, attributes, hullMaterial);
        fwdLeft.rect(
            new Vector3(-hw,          borderV,        -hl), new Vector3(-hw + borderH, borderV,        -hl),
            new Vector3(-hw + borderH, height - borderV, -hl), new Vector3(-hw,          height - borderV, -hl),
            new Vector3(0, 0, 1)
        );

        // Right strip beside viewport
        MeshPartBuilder fwdRight = builder.part("fwdRight", GL20.GL_TRIANGLES, attributes, hullMaterial);
        fwdRight.rect(
            new Vector3(hw - borderH, borderV,          -hl), new Vector3(hw, borderV,          -hl),
            new Vector3(hw,           height - borderV, -hl), new Vector3(hw - borderH, height - borderV, -hl),
            new Vector3(0, 0, 1)
        );

        // --- Transparent windshield glass over the viewport cutout ---
        final Material glassMaterial = new Material(
            ColorAttribute.createDiffuse(0.6f, 0.75f, 0.85f, 0.12f),
            ColorAttribute.createSpecular(0.9f, 0.95f, 1f, 1f),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.12f)
        );

        float glassInset = 0.01f;
        MeshPartBuilder glass = builder.part("windshield", GL20.GL_TRIANGLES, attributes, glassMaterial);
        glass.rect(
            new Vector3(-hw + borderH, borderV,          -hl + glassInset),
            new Vector3( hw - borderH, borderV,          -hl + glassInset),
            new Vector3( hw - borderH, height - borderV, -hl + glassInset),
            new Vector3(-hw + borderH, height - borderV, -hl + glassInset),
            new Vector3(0, 0, 1)
        );

        // --- Console (angled panel in front of pilot seat) ---
        final float consoleDepth  = length * 0.2f;
        final float consoleHeight = height * 0.35f;
        MeshPartBuilder console = builder.part("console", GL20.GL_TRIANGLES, attributes, consoleMaterial);
        console.rect(
            new Vector3(-hw * 0.7f, consoleHeight,        -hl + consoleDepth),
            new Vector3( hw * 0.7f, consoleHeight,        -hl + consoleDepth),
            new Vector3( hw * 0.7f, consoleHeight * 0.5f, -hl),
            new Vector3(-hw * 0.7f, consoleHeight * 0.5f, -hl),
            new Vector3(0, 1, 0)
        );

        return builder.end();
    }
}
