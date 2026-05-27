package com.galacticodyssey.planet;

import com.badlogic.gdx.math.Vector3;

public final class CaveRoom {
    public final int id;
    public final Vector3 position;
    public final float radius;
    public final float height;
    public final CaveRoomType type;
    public final int depthLayer;

    public CaveRoom(int id, Vector3 position, float radius, float height, CaveRoomType type, int depthLayer) {
        this.id = id;
        this.position = new Vector3(position);
        this.radius = radius;
        this.height = height;
        this.type = type;
        this.depthLayer = depthLayer;
    }
}
