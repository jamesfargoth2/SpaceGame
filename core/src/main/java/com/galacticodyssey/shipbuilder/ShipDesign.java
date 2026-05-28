package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;

import java.util.*;

public class ShipDesign {
    public String designId;
    public String name;
    public ShipSizeClass sizeClass;
    public final HullDesign hull = new HullDesign();
    public final List<RoomDesign> rooms = new ArrayList<>();
    public final Map<String, ModuleAssignment> modules = new LinkedHashMap<>();
    public final DesignMetadata metadata = new DesignMetadata();

    public ShipDesign() {
        this.designId = UUID.randomUUID().toString();
        this.name = "Untitled Ship";
        this.sizeClass = ShipSizeClass.SMALL;
    }

    public ShipDesign(ShipSizeClass sizeClass) {
        this();
        this.sizeClass = sizeClass;
    }

    public static ShipDesign fromSeed(long seed, ShipSizeClass sizeClass) {
        ShipBlueprint bp = new ShipBlueprint(seed, sizeClass);
        ShipDesign design = new ShipDesign(sizeClass);
        design.hull.spinePoints.add(new Vector3(0, 0, 0));
        design.hull.spinePoints.add(new Vector3(0, bp.maxHeight * 0.15f, bp.spineLength * 0.33f));
        design.hull.spinePoints.add(new Vector3(0, bp.maxHeight * 0.05f, bp.spineLength * 0.66f));
        design.hull.spinePoints.add(new Vector3(0, 0, bp.spineLength));

        int csCount = bp.crossSectionCount;
        for (int i = 0; i < csCount; i++) {
            float t = (float) i / (csCount - 1);
            float envelope = computeEnvelope(t);
            design.hull.addCrossSection(new CrossSectionDef(
                t, bp.maxWidth * envelope, bp.maxHeight * envelope, 2.5f
            ));
        }

        for (int w = 0; w < bp.wingPairs; w++) {
            float spineT = 0.35f + w * 0.08f;
            design.hull.addAppendage(new AppendageDef(
                AppendageDef.AppendageType.SWEPT_WING, spineT, AppendageDef.Side.BOTH, 1f
            ));
        }
        for (int e = 0; e < bp.enginePodCount; e++) {
            float spineT = 0.8f + e * 0.05f;
            design.hull.addAppendage(new AppendageDef(
                AppendageDef.AppendageType.ENGINE_POD, spineT, AppendageDef.Side.BOTH, 1f
            ));
        }

        return design;
    }

    private static float computeEnvelope(float t) {
        if (t < 0.15f) return t / 0.15f;
        if (t < 0.65f) return 1f;
        return 1f - ((t - 0.65f) / 0.35f) * 0.6f;
    }

    public ShipBlueprint toBlueprint() {
        return new ShipBlueprint(
            sizeClass,
            hull.estimateSpineLength(),
            hull.crossSections.size(),
            hull.estimateMaxWidth(),
            hull.estimateMaxHeight(),
            hull.countWingPairs(),
            hull.countEnginePods()
        );
    }

    public void addRoom(RoomDesign room) {
        rooms.add(room);
        metadata.lastModified = System.currentTimeMillis();
    }

    public void removeRoom(int index) {
        rooms.remove(index);
        metadata.lastModified = System.currentTimeMillis();
    }

    public void setModule(String hardpointId, ModuleAssignment assignment) {
        modules.put(hardpointId, assignment);
        metadata.lastModified = System.currentTimeMillis();
    }

    public void clearModule(String hardpointId) {
        modules.remove(hardpointId);
        metadata.lastModified = System.currentTimeMillis();
    }

    public int totalRoomVolume() {
        int total = 0;
        for (RoomDesign r : rooms) total += r.volume();
        return total;
    }
}
