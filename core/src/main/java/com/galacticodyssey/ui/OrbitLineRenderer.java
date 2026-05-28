package com.galacticodyssey.ui;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.TrajectoryComponent;

public class OrbitLineRenderer implements Disposable {

    private static final Color STABLE_COLOR = new Color(0f, 0.8f, 1f, 0.8f);
    private static final Color ESCAPE_COLOR = new Color(1f, 0.4f, 0.1f, 0.8f);
    private static final Color IMPACT_COLOR = new Color(1f, 1f, 0f, 0.8f);

    private static final ComponentMapper<TrajectoryComponent> trajectoryMapper =
        ComponentMapper.getFor(TrajectoryComponent.class);

    private final ShapeRenderer shapeRenderer;

    public OrbitLineRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    public void render(Entity shipEntity, Camera camera) {
        if (!trajectoryMapper.has(shipEntity)) return;
        TrajectoryComponent traj = trajectoryMapper.get(shipEntity);
        if (traj.predictedPath.size < 2) return;

        Color lineColor;
        if (!traj.isStable && traj.currentOrbit != null && traj.currentOrbit.eccentricity >= 1f) {
            lineColor = ESCAPE_COLOR;
        } else if (!traj.isStable) {
            lineColor = IMPACT_COLOR;
        } else {
            lineColor = STABLE_COLOR;
        }

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(lineColor);

        Array<Vector3> path = traj.predictedPath;
        for (int i = 0; i < path.size - 1; i++) {
            Vector3 a = path.get(i);
            Vector3 b = path.get(i + 1);
            shapeRenderer.line(a.x, a.y, a.z, b.x, b.y, b.z);
        }

        if (traj.isStable && path.size > 2) {
            Vector3 last = path.get(path.size - 1);
            Vector3 first = path.get(0);
            shapeRenderer.line(last.x, last.y, last.z, first.x, first.y, first.z);
        }

        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
