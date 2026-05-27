package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;

public class TransformComponent implements Component {
    public final Vector3 position = new Vector3();
    public final Quaternion rotation = new Quaternion();

    public TransformSnapshot takeSnapshot(double originOffsetX, double originOffsetY, double originOffsetZ) {
        return new TransformSnapshot(
            position.x + originOffsetX,
            position.y + originOffsetY,
            position.z + originOffsetZ,
            rotation.x, rotation.y, rotation.z, rotation.w
        );
    }

    public void restoreFromSnapshot(TransformSnapshot snapshot,
                                    double originOffsetX, double originOffsetY, double originOffsetZ) {
        position.set(
            (float)(snapshot.galaxyX - originOffsetX),
            (float)(snapshot.galaxyY - originOffsetY),
            (float)(snapshot.galaxyZ - originOffsetZ)
        );
        rotation.set(snapshot.rotX, snapshot.rotY, snapshot.rotZ, snapshot.rotW);
    }
}
