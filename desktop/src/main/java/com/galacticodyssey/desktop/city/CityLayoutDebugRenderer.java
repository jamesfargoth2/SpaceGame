package com.galacticodyssey.desktop.city;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.CityLayoutGenerator;
import com.galacticodyssey.city.layout.CityRequest;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityGate;
import com.galacticodyssey.city.layout.model.CityLayout;
import com.galacticodyssey.city.layout.model.CityWall;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.Street;
import com.galacticodyssey.galaxy.faction.FactionEthos;

/** Top-down debug view of a generated CityLayout. Keys: SPACE reroll, UP/DOWN population, F faction. */
public class CityLayoutDebugRenderer extends ApplicationAdapter {
    private ShapeRenderer shapes;
    private OrthographicCamera cam;
    private CityLayoutGenerator gen;

    private long seed = 1L;
    private final int[] pops = {10, 400, 4000, 30000, 80000, 250000, 1_000_000};
    private int popIndex = 3;
    private final FactionEthos[] ethoses = FactionEthos.values();
    private int ethosIndex = 0;
    private CityLayout layout;

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        cam = new OrthographicCamera();
        CityDataRegistry reg = new CityDataRegistry();
        reg.loadFromClasspath();
        gen = new CityLayoutGenerator(reg);
        regenerate();
    }

    private void regenerate() {
        CityRequest r = new CityRequest();
        r.seed = seed;
        r.population = pops[popIndex];
        r.rulingEthos = ethoses[ethosIndex];
        r.factionId = "debug";
        layout = gen.generate(r);
        Gdx.graphics.setTitle("City: " + layout.name + " | " + layout.type + " | " + layout.form
                + " | pop=" + layout.population + " | lots=" + layout.lots.size());
    }

    private void handleInput() {
        boolean changed = false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) { seed++; changed = true; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) { popIndex = Math.min(pops.length - 1, popIndex + 1); changed = true; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) { popIndex = Math.max(0, popIndex - 1); changed = true; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) { ethosIndex = (ethosIndex + 1) % ethoses.length; changed = true; }
        if (changed) regenerate();
    }

    @Override
    public void render() {
        handleInput();
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float span = 0f;
        for (BuildingLot lot : layout.lots) {
            span = Math.max(span, Math.abs(lot.footprint.x) + lot.footprint.width);
            span = Math.max(span, Math.abs(lot.footprint.y) + lot.footprint.height);
        }
        span = Math.max(span, 60f) * 1.1f;
        float aspect = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        cam.viewportWidth = span * 2f * aspect;
        cam.viewportHeight = span * 2f;
        cam.position.set(0, 0, 0);
        cam.update();
        shapes.setProjectionMatrix(cam.combined);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (BuildingLot lot : layout.lots) {
            shapes.setColor(districtColor(lot.district));
            Rectangle f = lot.footprint;
            shapes.rect(f.x, f.y, f.width, f.height);
        }
        shapes.setColor(Color.YELLOW);
        for (Landmark lm : layout.landmarks) shapes.circle(lm.position.x, lm.position.y, span * 0.012f, 12);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.5f, 0.5f, 0.55f, 1f);
        for (Street s : layout.streets) shapes.line(s.start.x, s.start.y, s.end.x, s.end.y);

        CityWall wall = layout.wall;
        if (wall != null) {
            shapes.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i < wall.hull.size(); i++) {
                Vector2 a = wall.hull.get(i);
                Vector2 b = wall.hull.get((i + 1) % wall.hull.size());
                shapes.line(a.x, a.y, b.x, b.y);
            }
            shapes.setColor(Color.RED);
            for (CityGate g : wall.gates) shapes.circle(g.position.x, g.position.y, span * 0.015f, 10);
        }
        shapes.end();
    }

    private Color districtColor(DistrictType d) {
        switch (d) {
            case GOVERNMENT:  return new Color(0.85f, 0.85f, 0.95f, 1f);
            case COMMERCIAL:  return new Color(0.95f, 0.75f, 0.2f, 1f);
            case RESIDENTIAL: return new Color(0.3f, 0.7f, 0.3f, 1f);
            case INDUSTRIAL:  return new Color(0.6f, 0.45f, 0.3f, 1f);
            case SLUMS:       return new Color(0.4f, 0.3f, 0.3f, 1f);
            case SPACEPORT:   return new Color(0.3f, 0.6f, 0.9f, 1f);
            case RELIGIOUS:   return new Color(0.8f, 0.5f, 0.9f, 1f);
            case GARDEN:      return new Color(0.2f, 0.55f, 0.25f, 1f);
            case MILITARY:    return new Color(0.7f, 0.25f, 0.25f, 1f);
            default:          return Color.DARK_GRAY;
        }
    }

    @Override
    public void dispose() {
        if (shapes != null) shapes.dispose();
    }
}
