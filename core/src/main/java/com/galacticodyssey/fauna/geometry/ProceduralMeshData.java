package com.galacticodyssey.fauna.geometry;

public final class ProceduralMeshData {
    public final float[] positions;
    public final float[] normals;
    public final short[] indices;

    public ProceduralMeshData(float[] positions, float[] normals, short[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
    }

    public int positionCount() { return positions.length / 3; }

    public float maxZ() { float m = -Float.MAX_VALUE; for (int i = 2; i < positions.length; i += 3) m = Math.max(m, positions[i]); return m; }
    public float minZ() { float m =  Float.MAX_VALUE; for (int i = 2; i < positions.length; i += 3) m = Math.min(m, positions[i]); return m; }
    public float maxAbsXY() {
        float m = 0f;
        for (int i = 0; i < positions.length; i += 3) { m = Math.max(m, Math.abs(positions[i])); m = Math.max(m, Math.abs(positions[i+1])); }
        return m;
    }
}
