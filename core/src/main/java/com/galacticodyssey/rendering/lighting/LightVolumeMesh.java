package com.galacticodyssey.rendering.lighting;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

public class LightVolumeMesh implements Disposable {

    private final Mesh sphereMesh;

    public LightVolumeMesh(int segments, int rings) {
        int vertexCount = (segments + 1) * (rings + 1);
        int indexCount = segments * rings * 6;

        float[] vertices = new float[vertexCount * 3];
        short[] indices = new short[indexCount];

        int vi = 0;
        for (int r = 0; r <= rings; r++) {
            float phi = MathUtils.PI * r / rings;
            float sinPhi = MathUtils.sin(phi);
            float cosPhi = MathUtils.cos(phi);
            for (int s = 0; s <= segments; s++) {
                float theta = MathUtils.PI2 * s / segments;
                vertices[vi++] = sinPhi * MathUtils.cos(theta);
                vertices[vi++] = cosPhi;
                vertices[vi++] = sinPhi * MathUtils.sin(theta);
            }
        }

        int ii = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                int curr = r * (segments + 1) + s;
                int next = curr + segments + 1;
                indices[ii++] = (short) curr;
                indices[ii++] = (short) next;
                indices[ii++] = (short) (curr + 1);
                indices[ii++] = (short) (curr + 1);
                indices[ii++] = (short) next;
                indices[ii++] = (short) (next + 1);
            }
        }

        sphereMesh = new Mesh(true, vertexCount, indexCount,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"));
        sphereMesh.setVertices(vertices);
        sphereMesh.setIndices(indices);
    }

    public void render(ShaderProgram shader) {
        sphereMesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        sphereMesh.dispose();
    }
}
