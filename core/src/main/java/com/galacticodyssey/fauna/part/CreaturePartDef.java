package com.galacticodyssey.fauna.part;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.geometry.PartGeometrySpec;

import java.util.ArrayList;
import java.util.List;

/** A mesh-bearing node in the socket graph: its type, sockets, geometry, scale band, and eligibility. */
public final class CreaturePartDef {
    public String id;
    public PartType partType;
    public final List<Socket> sockets = new ArrayList<>();
    public PartGeometrySpec geometry = new PartGeometrySpec();
    public float minScale = 1f;
    public float maxScale = 1f;
    /** Body plans allowed to use this part. Empty = usable by any plan. */
    public final List<BodyPlan> bodyPlans = new ArrayList<>();

    public Socket findSocket(String socketId) {
        for (Socket s : sockets) if (s.id.equals(socketId)) return s;
        return null;
    }
}
