package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.data.GameSession;
import com.galacticodyssey.galaxy.GalaxyConfig;
import com.galacticodyssey.galaxy.GalaxyManager;
import com.galacticodyssey.galaxy.GalaxyRegion;
import com.galacticodyssey.galaxy.LuminosityClass;
import com.galacticodyssey.galaxy.NebulaRegion;
import com.galacticodyssey.galaxy.StarPosition;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.galaxy.StarSystemCache;

import java.util.Locale;

/**
 * Full-screen galaxy and sector map. Opens from GameScreen via M key.
 * Pan: drag. Zoom: scroll wheel. Select system: left-click a star.
 * Close: M or ESC, or the header button.
 */
public class GalaxyMapScreen implements Screen {

    // Virtual canvas dimensions matching other screens
    private static final float VW = 1280f;
    private static final float VH = 720f;

    // Layout in virtual pixels (y-up, origin at bottom-left)
    private static final float HEADER_H = 50f;
    private static final float STATUS_H = 28f;
    private static final float INFO_W   = 310f;
    private static final float MAP_X    = 0f;
    private static final float MAP_Y    = STATUS_H;
    private static final float MAP_W    = VW - INFO_W;
    private static final float MAP_H    = VH - HEADER_H - STATUS_H;

    // Zoom: virtual pixels per light-year
    private static final float ZOOM_MIN    = 0.0005f;
    private static final float ZOOM_MAX    = 25f;
    private static final float ZOOM_FACTOR = 1.2f;

    // Star rendering
    private static final float STAR_R_MIN = 1.2f;
    private static final float STAR_R_MAX = 4.5f;

    // Click vs drag threshold (screen pixels squared)
    private static final float DRAG_THRESHOLD_SQ = 36f;

    // Picking tolerance in virtual pixels
    private static final float PICK_RADIUS_VPX = 9f;

    // IMF-weighted spectral colours (deterministic, not generated per star)
    private static final Color C_M = new Color(1.00f, 0.30f, 0.10f, 1f);
    private static final Color C_K = new Color(1.00f, 0.65f, 0.25f, 1f);
    private static final Color C_G = new Color(1.00f, 0.95f, 0.55f, 1f);
    private static final Color C_F = new Color(1.00f, 1.00f, 0.85f, 1f);
    private static final Color C_A = new Color(0.95f, 0.95f, 1.00f, 1f);
    private static final Color C_B = new Color(0.70f, 0.80f, 1.00f, 1f);
    private static final Color C_O = new Color(0.60f, 0.70f, 1.00f, 1f);

    // ─── core refs ────────────────────────────────────────────────────────────
    private final GalacticOdyssey game;
    private final Screen returnTo;
    private final FitViewport viewport;
    private final Stage stage;
    private final Skin skin;
    private final ShapeRenderer shapes = new ShapeRenderer();

    private final GalaxyManager galaxy;
    private final GalaxyConfig  config;
    private final StarSystemCache systemCache;
    private final StarPosition playerPosition; // starting galaxy-space origin (may be null)

    private final Array<Texture> ownedTextures = new Array<>();

    // ─── view state ───────────────────────────────────────────────────────────
    private double viewX, viewY;  // galaxy-space LY at map centre
    private float  zoom;          // virtual pixels per light-year

    // ─── drag state ───────────────────────────────────────────────────────────
    private boolean dragging;
    private float   dragStartSX, dragStartSY;   // screen px at drag start
    private double  viewXAtDrag, viewYAtDrag;

    // ─── selection state ──────────────────────────────────────────────────────
    private StarPosition selectedStar;
    private StarSystem   selectedSystem;

    // ─── mutable UI labels ────────────────────────────────────────────────────
    private Label infoTitle;
    private Label valTemp, valLum, valMass, valAge, valHz, valOrbits;
    private Label statusRegion, statusCoords, statusZoom;
    private TextButton setCourseBtn;

    // ─────────────────────────────────────────────────────────────────────────

    public GalaxyMapScreen(GalacticOdyssey game, GameSession session, Screen returnTo) {
        if (session.galaxy == null) throw new IllegalStateException("Galaxy not yet generated");

        this.game     = game;
        this.returnTo = returnTo;
        this.skin     = game.getSkin();
        this.viewport = new FitViewport(VW, VH);
        this.stage    = new Stage(viewport);

        this.galaxy       = session.galaxy;
        this.config       = galaxy.getConfig();
        this.systemCache  = new StarSystemCache(session.seed, 256);
        this.playerPosition = session.startingStarPosition;

        // Centre on starting system; fall back to galaxy origin
        viewX = (playerPosition != null) ? playerPosition.x : 0.0;
        viewY = (playerPosition != null) ? playerPosition.y : 0.0;

        // Initial zoom: show full galaxy diameter with a little margin
        zoom = MathUtils.clamp(MAP_W / (config.radiusLY * 2.6f), ZOOM_MIN, ZOOM_MAX);

        galaxy.updateView(viewX, viewY, viewRadiusLY());
        buildUi();
        updateStatusBar();
    }

    // ─── UI construction ──────────────────────────────────────────────────────

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);

        root.add(buildHeader()).fillX().height(HEADER_H).row();

        Table mainRow = new Table();
        // Transparent placeholder — ShapeRenderer draws the map canvas underneath
        mainRow.add(new Actor()).expand().fill();
        mainRow.add(buildInfoPanel()).width(INFO_W).fillY().top();
        root.add(mainRow).expand().fill().row();

        root.add(buildStatusBar()).fillX().height(STATUS_H);
        stage.addActor(root);
    }

    private Table buildHeader() {
        Table h = new Table();
        h.setBackground(new TextureRegionDrawable(new TextureRegion(color1x1(0.05f, 0.05f, 0.12f, 1f))));

        TextButton back = new TextButton("< Back to Game", skin);
        back.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                game.getAudioManager().playSound("audio/sfx/ui_click.ogg");
                game.setScreen(returnTo);
            }
        });
        h.add(back).width(180).height(36).padLeft(8);
        h.add(new Label("GALAXY MAP", skin, "title")).expandX().center();
        Label hint = new Label("[M] Close", skin);
        hint.setColor(Color.GRAY);
        h.add(hint).padRight(12);
        return h;
    }

    private Table buildInfoPanel() {
        Table p = new Table();
        p.setBackground(new TextureRegionDrawable(new TextureRegion(color1x1(0.04f, 0.04f, 0.10f, 1f))));
        p.top().pad(12);

        Label panelHeader = new Label("SELECTED SYSTEM", skin);
        panelHeader.setWrap(true);
        panelHeader.setAlignment(Align.center);
        p.add(panelHeader).colspan(2).expandX().fillX().center().padBottom(8).row();

        infoTitle = new Label("Click a star\nfor system info", skin);
        infoTitle.setColor(Color.GRAY);
        infoTitle.setWrap(true);
        infoTitle.setAlignment(Align.center);
        p.add(infoTitle).colspan(2).expandX().fillX().center().padBottom(10).row();

        valTemp   = infoRow(p, "Temp:");
        valLum    = infoRow(p, "Lum:");
        valMass   = infoRow(p, "Mass:");
        valAge    = infoRow(p, "Age:");
        valHz     = infoRow(p, "Hab Zone:");
        valOrbits = infoRow(p, "Orbits:");

        p.row().padTop(16);
        setCourseBtn = new TextButton("Set Course", skin);
        setCourseBtn.setDisabled(true);
        setCourseBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (!setCourseBtn.isDisabled() && selectedStar != null) {
                    game.getAudioManager().playSound("audio/sfx/ui_click.ogg");
                    // Navigation destination wired here when jump system is implemented
                }
            }
        });
        p.add(setCourseBtn).colspan(2).fillX().height(40);
        return p;
    }

    /** Adds a key/value row to the table and returns the mutable value Label. */
    private Label infoRow(Table t, String key) {
        t.row().padBottom(3);
        Label k = new Label(key, skin);
        k.setColor(Color.LIGHT_GRAY);
        t.add(k).width(80).left().padRight(6);
        Label v = new Label("-", skin);
        v.setColor(new Color(0.7f, 0.9f, 1f, 1f));
        v.setEllipsis(true);
        t.add(v).expandX().fillX().left();
        return v;
    }

    private Table buildStatusBar() {
        Table s = new Table();
        s.setBackground(new TextureRegionDrawable(new TextureRegion(color1x1(0.03f, 0.03f, 0.08f, 1f))));
        statusRegion = new Label("", skin);
        statusCoords = new Label("", skin);
        statusZoom   = new Label("", skin);
        for (Label l : new Label[]{ statusRegion, statusCoords, statusZoom }) l.setColor(Color.LIGHT_GRAY);
        statusRegion.setEllipsis(true);
        statusCoords.setEllipsis(true);
        statusZoom.setEllipsis(true);
        s.add(statusRegion).expandX().fillX().left().padLeft(8);
        s.add(statusCoords).expandX().fillX().center();
        s.add(statusZoom).expandX().fillX().right().padRight(8);
        return s;
    }

    /** Creates a 1×1 pixel Texture of the given RGBA colour (owned, disposed with screen). */
    private Texture color1x1(float r, float g, float b, float a) {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(r, g, b, a);
        p.fill();
        Texture t = new Texture(p);
        p.dispose();
        ownedTextures.add(t);
        return t;
    }

    // ─── status / info panel update ───────────────────────────────────────────

    private void updateStatusBar() {
        GalaxyRegion region = galaxy.getRegion(viewX, viewY);
        statusRegion.setText("Region: " + region.name().replace("_", " "));
        statusCoords.setText(String.format(Locale.US, "%.0f, %.0f LY", viewX, viewY));
        float widthLY = MAP_W / zoom;
        if (widthLY >= 1000f)
            statusZoom.setText(String.format(Locale.US, "View: %.0f kLY", widthLY / 1000f));
        else
            statusZoom.setText(String.format(Locale.US, "View: %.1f LY", widthLY));
    }

    private void updateInfoPanel() {
        if (selectedSystem == null) {
            infoTitle.setText("Click a star\nfor system info");
            infoTitle.setColor(Color.GRAY);
            valTemp.setText("-"); valLum.setText("-"); valMass.setText("-");
            valAge.setText("-"); valHz.setText("-"); valOrbits.setText("-");
            setCourseBtn.setDisabled(true);
            return;
        }
        StarSystem s = selectedSystem;
        infoTitle.setText(s.spectralClass.name() + " " + luminosityLabel(s.luminosityClass));
        infoTitle.setColor(new Color(0.85f, 0.90f, 1f, 1f));
        valTemp.setText(String.format(Locale.US, "%,d K",           (int) s.temperature));
        valLum .setText(String.format(Locale.US, "%.3f LSol",       s.luminosity));
        valMass.setText(String.format(Locale.US, "%.2f MSol",       s.mass));
        valAge .setText(String.format(Locale.US, "%.1f Gyr",        s.age));
        valHz  .setText(String.format(Locale.US, "%.1f - %.1f AU",  s.habZoneInner, s.habZoneOuter));
        valOrbits.setText(String.valueOf(s.orbits.size()));
        setCourseBtn.setDisabled(false);
    }

    private String luminosityLabel(LuminosityClass lc) {
        switch (lc) {
            case MAIN_SEQUENCE: return "V";
            case GIANT:         return "III";
            case SUPERGIANT:    return "I";
            case WHITE_DWARF:   return "WD";
            default:            return lc.name();
        }
    }

    // ─── map rendering ────────────────────────────────────────────────────────

    private void renderMap() {
        shapes.setProjectionMatrix(stage.getCamera().combined);

        // Map background
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.01f, 0.01f, 0.04f, 1f);
        shapes.rect(MAP_X, MAP_Y, MAP_W, MAP_H);
        shapes.end();

        // Nebulae — large semi-transparent blobs anchored to star clusters
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (NebulaRegion neb : galaxy.getNebulae()) {
            float nx = galToStageX(neb.centreX);
            float ny = galToStageY(neb.centreY);
            float nr = (float)(neb.radiusLY * zoom);
            if (nr < 2f || outsideMapRect(nx, ny, nr)) continue;
            Color c = neb.colour;
            shapes.setColor(c.r, c.g, c.b, 0.07f);
            shapes.circle(nx, ny, Math.max(nr, 8f), 28);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Stars
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (StarPosition star : galaxy.getLoadedStars()) {
            float sx = galToStageX(star.x);
            float sy = galToStageY(star.y);
            if (!inMapRect(sx, sy)) continue;

            float r = MathUtils.clamp(STAR_R_MIN + star.localDensity * 0.8f, STAR_R_MIN, STAR_R_MAX);

            if (star == selectedStar) {
                shapes.setColor(1f, 1f, 0.2f, 1f);
                shapes.circle(sx, sy, r + 3.5f, 12);
            }
            if (star == playerPosition) {
                shapes.setColor(0.1f, 1f, 0.45f, 0.65f);
                shapes.circle(sx, sy, r + 5.5f, 16);
            }
            shapes.setColor(spectralColor(star.uniqueId));
            shapes.circle(sx, sy, r, 8);
        }
        if (playerPosition == null) {
            float px = galToStageX(0), py = galToStageY(0);
            if (inMapRect(px, py)) drawCrosshair(px, py);
        }
        shapes.end();

        // Map border line
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.2f, 0.45f, 1f);
        shapes.rect(MAP_X, MAP_Y, MAP_W, MAP_H);
        shapes.end();
    }

    private void drawCrosshair(float x, float y) {
        shapes.setColor(0.1f, 1f, 0.45f, 0.9f);
        float s = 7f;
        shapes.rectLine(x - s, y, x + s, y, 1.5f);
        shapes.rectLine(x, y - s, x, y + s, 1.5f);
    }

    // ─── coordinate helpers ───────────────────────────────────────────────────

    /** Half-width of map in LY, with loading margin. */
    private float viewRadiusLY() {
        return (MAP_W / 2f) / zoom * 1.6f;
    }

    private float galToStageX(double wx) {
        return MAP_X + MAP_W * 0.5f + (float)((wx - viewX) * zoom);
    }

    private float galToStageY(double wy) {
        return MAP_Y + MAP_H * 0.5f + (float)((wy - viewY) * zoom);
    }

    private double stageToGalX(float stageX) {
        return viewX + (stageX - (MAP_X + MAP_W * 0.5f)) / zoom;
    }

    private double stageToGalY(float stageY) {
        return viewY + (stageY - (MAP_Y + MAP_H * 0.5f)) / zoom;
    }

    private boolean inMapRect(float sx, float sy) {
        return sx >= MAP_X && sx <= MAP_X + MAP_W && sy >= MAP_Y && sy <= MAP_Y + MAP_H;
    }

    private boolean outsideMapRect(float cx, float cy, float r) {
        return cx + r < MAP_X || cx - r > MAP_X + MAP_W
            || cy + r < MAP_Y || cy - r > MAP_Y + MAP_H;
    }

    /** Converts raw screen coords (0,0 top-left) to stage virtual coords. */
    private Vector2 screenToStage(int sx, int sy) {
        return viewport.unproject(new Vector2(sx, sy));
    }

    private boolean screenInMapArea(int sx, int sy) {
        Vector2 v = screenToStage(sx, sy);
        return inMapRect(v.x, v.y);
    }

    // ─── spectral colour (deterministic, no StarSystem generation) ────────────

    /**
     * Returns a colour for a star based solely on its uniqueId, sampled according
     * to the real initial mass function (76% M, 12% K, 7.6% G, 3% F, 0.6% A…).
     */
    private Color spectralColor(long id) {
        float roll = ((id ^ (id >>> 32)) & 0x7FFFL) / 32767f;
        if (roll < 0.760f) return C_M;
        if (roll < 0.880f) return C_K;
        if (roll < 0.956f) return C_G;
        if (roll < 0.986f) return C_F;
        if (roll < 0.992f) return C_A;
        if (roll < 0.999f) return C_B;
        return C_O;
    }

    // ─── star picking ─────────────────────────────────────────────────────────

    private StarPosition pickStar(int screenX, int screenY) {
        Vector2 sp = screenToStage(screenX, screenY);
        if (!inMapRect(sp.x, sp.y)) return null;

        double wx = stageToGalX(sp.x);
        double wy = stageToGalY(sp.y);
        double pickR   = PICK_RADIUS_VPX / zoom;  // tolerance in LY
        double pickRSq = pickR * pickR;

        StarPosition best = null;
        double bestDSq = pickRSq;
        for (StarPosition star : galaxy.getLoadedStars()) {
            double dx = star.x - wx, dy = star.y - wy;
            double dSq = dx * dx + dy * dy;
            if (dSq < bestDSq) { bestDSq = dSq; best = star; }
        }
        return best;
    }

    // ─── scissor test ─────────────────────────────────────────────────────────

    private void setMapScissor(boolean enable) {
        if (!enable) {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            return;
        }
        float sw = viewport.getScreenWidth();
        float sh = viewport.getScreenHeight();
        if (sw == 0 || sh == 0) return;
        float scaleX = sw / VW;
        float scaleY = sh / VH;
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(
            viewport.getScreenX() + (int)(MAP_X * scaleX),
            viewport.getScreenY() + (int)(MAP_Y * scaleY),
            (int)(MAP_W * scaleX),
            (int)(MAP_H * scaleY));
    }

    // ─── input ────────────────────────────────────────────────────────────────

    private InputAdapter buildMapInput() {
        return new InputAdapter() {

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.M || keycode == Input.Keys.ESCAPE) {
                    game.setScreen(returnTo);
                    return true;
                }
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (!screenInMapArea(Gdx.input.getX(), Gdx.input.getY())) return false;
                float factor = amountY > 0 ? (1f / ZOOM_FACTOR) : ZOOM_FACTOR;
                zoom = MathUtils.clamp(zoom * factor, ZOOM_MIN, ZOOM_MAX);
                galaxy.updateView(viewX, viewY, viewRadiusLY());
                updateStatusBar();
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT || !screenInMapArea(screenX, screenY)) return false;
                dragging       = true;
                dragStartSX    = screenX;
                dragStartSY    = screenY;
                viewXAtDrag    = viewX;
                viewYAtDrag    = viewY;
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT || !dragging) return false;
                dragging = false;
                float dx = screenX - dragStartSX;
                float dy = screenY - dragStartSY;
                if (dx * dx + dy * dy < DRAG_THRESHOLD_SQ) {
                    // Treat as click — attempt star selection
                    StarPosition picked = pickStar(screenX, screenY);
                    if (picked != selectedStar) {
                        selectedStar = picked;
                        if (picked != null) {
                            GalaxyRegion region = galaxy.getRegion(picked.x, picked.y);
                            selectedSystem = systemCache.get(picked, region);
                        } else {
                            selectedSystem = null;
                        }
                        updateInfoPanel();
                    }
                }
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (!dragging) return false;
                float sw = viewport.getScreenWidth();
                float sh = viewport.getScreenHeight();
                if (sw == 0 || sh == 0) return true;
                // Convert screen-pixel delta → virtual-pixel delta, then → galaxy LY
                float vdx =  (screenX - dragStartSX) * (VW / sw);
                float vdy = -(screenY - dragStartSY) * (VH / sh); // screen Y is inverted
                viewX = viewXAtDrag - vdx / zoom;
                viewY = viewYAtDrag - vdy / zoom;
                galaxy.updateView(viewX, viewY, viewRadiusLY());
                updateStatusBar();
                return true;
            }
        };
    }

    // ─── Screen lifecycle ─────────────────────────────────────────────────────

    @Override
    public void show() {
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(stage);           // UI buttons first
        mux.addProcessor(buildMapInput()); // pan/zoom/click on map
        Gdx.input.setInputProcessor(mux);
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        // Draw galaxy map under the stage (scissored to map canvas area)
        setMapScissor(true);
        renderMap();
        setMapScissor(false);

        // Stage draws UI panels on top
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { dispose(); }

    @Override
    public void dispose() {
        stage.dispose();
        shapes.dispose();
        for (Texture t : ownedTextures) t.dispose();
        ownedTextures.clear();
    }
}
