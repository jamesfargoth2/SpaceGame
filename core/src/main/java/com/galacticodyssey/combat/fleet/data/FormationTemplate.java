package com.galacticodyssey.combat.fleet.data;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class FormationTemplate {
    public final String id;
    private final List<Vector3> slotOffsets;

    public FormationTemplate(String id, List<Vector3> slotOffsets) {
        this.id = id;
        this.slotOffsets = new ArrayList<>(slotOffsets);
    }

    public int slotCount() {
        return slotOffsets.size();
    }

    public Vector3 getSlotOffset(int index) {
        if (slotOffsets.isEmpty()) return new Vector3();
        return new Vector3(slotOffsets.get(index % slotOffsets.size()));
    }

    public static FormationTemplate line(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        float spacing = 50f;
        float halfWidth = (maxSlots - 1) * spacing * 0.5f;
        for (int i = 0; i < maxSlots; i++) {
            slots.add(new Vector3(i * spacing - halfWidth, 0f, 0f));
        }
        return new FormationTemplate("line", slots);
    }

    public static FormationTemplate wedge(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        slots.add(new Vector3(0f, 0f, 0f));
        float spacing = 60f;
        int row = 1;
        int placed = 1;
        while (placed < maxSlots) {
            float zBack = -row * spacing;
            int perRow = row + 1;
            float halfW = (perRow - 1) * spacing * 0.5f;
            for (int i = 0; i < perRow && placed < maxSlots; i++) {
                slots.add(new Vector3(i * spacing - halfW, 0f, zBack));
                placed++;
            }
            row++;
        }
        return new FormationTemplate("wedge", slots);
    }

    public static FormationTemplate box(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        float spacing = 50f;
        int cols = (int) Math.ceil(Math.sqrt(maxSlots));
        int rows = (int) Math.ceil((double) maxSlots / cols);
        float halfW = (cols - 1) * spacing * 0.5f;
        float halfD = (rows - 1) * spacing * 0.5f;
        int placed = 0;
        for (int r = 0; r < rows && placed < maxSlots; r++) {
            for (int c = 0; c < cols && placed < maxSlots; c++) {
                slots.add(new Vector3(c * spacing - halfW, 0f, r * spacing - halfD));
                placed++;
            }
        }
        return new FormationTemplate("box", slots);
    }

    public static FormationTemplate sphere(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        slots.add(new Vector3(0f, 0f, 0f));
        float radius = 80f;
        for (int i = 1; i < maxSlots; i++) {
            float phi = (float) (Math.acos(1 - 2.0 * i / maxSlots));
            float theta = (float) (Math.PI * (1 + Math.sqrt(5)) * i);
            float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
            float y = (float) (radius * Math.sin(phi) * Math.sin(theta));
            float z = (float) (radius * Math.cos(phi));
            slots.add(new Vector3(x, y, z));
        }
        return new FormationTemplate("sphere", slots);
    }

    public static FormationTemplate wall(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        float spacing = 45f;
        int cols = (int) Math.ceil(Math.sqrt(maxSlots));
        int rows = (int) Math.ceil((double) maxSlots / cols);
        float halfW = (cols - 1) * spacing * 0.5f;
        float halfH = (rows - 1) * spacing * 0.5f;
        int placed = 0;
        for (int r = 0; r < rows && placed < maxSlots; r++) {
            for (int c = 0; c < cols && placed < maxSlots; c++) {
                slots.add(new Vector3(c * spacing - halfW, r * spacing - halfH, 0f));
                placed++;
            }
        }
        return new FormationTemplate("wall", slots);
    }

    public static FormationTemplate scattered(int maxSlots, long seed) {
        List<Vector3> slots = new ArrayList<>();
        java.util.Random rng = new java.util.Random(seed);
        float range = 120f;
        for (int i = 0; i < maxSlots; i++) {
            slots.add(new Vector3(
                (rng.nextFloat() - 0.5f) * range * 2f,
                (rng.nextFloat() - 0.5f) * range * 0.5f,
                (rng.nextFloat() - 0.5f) * range * 2f
            ));
        }
        return new FormationTemplate("scattered", slots);
    }
}
