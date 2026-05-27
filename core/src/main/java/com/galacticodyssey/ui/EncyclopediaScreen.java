package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EncyclopediaScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final String[] CATEGORIES = {
        "Ships", "Weapons", "Factions", "Star Systems", "Biomes", "Economy", "Lore"
    };

    private static class Entry {
        final String name;
        final String stats;
        Entry(String name, String stats) { this.name = name; this.stats = stats; }
    }

    /** Rendered as a section header rather than a data row. */
    private static final class SectionEntry extends Entry {
        SectionEntry(String label) { super(label, ""); }
    }

    private final GalacticOdyssey game;
    private final Screen returnTo;
    private final Skin skin;
    private final AudioManager audio;
    private final Stage stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera backgroundCamera;
    private Texture overlayTexture;

    private String selectedCategory = CATEGORIES[0];
    private Table categoryButtons;
    private Table detailPanel;

    private final Map<String, List<Entry>> content = new LinkedHashMap<>();

    public EncyclopediaScreen(GalacticOdyssey game, Screen returnTo) {
        this.game = game;
        this.returnTo = returnTo;
        this.skin = game.getSkin();
        this.audio = game.getAudioManager();

        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        backgroundCamera = new OrthographicCamera();
        starfield = new StarfieldBackground(w, h);

        stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0.55f);
        pm.fill();
        overlayTexture = new Texture(pm);
        pm.dispose();

        loadContent();
        buildUi();
    }

    // -------------------------------------------------------------------------
    // Content loading
    // -------------------------------------------------------------------------

    private void loadContent() {
        JsonReader reader = new JsonReader();
        loadShips(reader);
        loadWeapons(reader);
        loadFactions(reader);
        loadStarSystems(reader);
        loadBiomes(reader);
        loadEconomy(reader);
        loadLore();
    }

    private void loadShips(JsonReader reader) {
        List<Entry> list = new ArrayList<>();
        JsonValue root = reader.parse(Gdx.files.internal("data/ships/ship_classes.json"));
        for (JsonValue e = root.child; e != null; e = e.next) {
            String name = e.getString("name");
            String size = e.getString("sizeClass");
            float mass = e.getFloat("mass");
            float thrust = e.getFloat("linearThrust");
            float fuel = e.getFloat("fuelCapacity");
            list.add(new Entry(
                name + "  [" + size + "]",
                fmt("Mass: %,.0f kg  ·  Thrust: %,.0f N  ·  Fuel: %,.0f L", mass, thrust, fuel)
            ));
        }
        content.put("Ships", list);
    }

    private void loadWeapons(JsonReader reader) {
        List<Entry> list = new ArrayList<>();

        list.add(new SectionEntry("Personal Weapons"));
        JsonValue framesRoot = reader.parse(Gdx.files.internal("data/weapons/frames.json"));
        for (JsonValue e = framesRoot.child; e != null; e = e.next) {
            String id = e.getString("id");
            String cat = e.getString("category");
            float dmg = e.getFloat("baseDamage");
            float rof = e.getFloat("baseFireRate");
            float range = e.getFloat("range", 100f);
            int mag = e.getInt("magSize");
            list.add(new Entry(
                formatId(id) + "  [" + cat + "]",
                fmt("Damage: %.0f  ·  Rate: %.1f/s  ·  Range: %.0fm  ·  Mag: %d", dmg, rof, range, mag)
            ));
        }

        list.add(new SectionEntry("Ship Weapons"));
        JsonValue swRoot = reader.parse(Gdx.files.internal("data/weapons/ship_weapons.json"));
        for (JsonValue e = swRoot.child; e != null; e = e.next) {
            String name = e.getString("name");
            String cat = e.getString("category");
            String dmgType = e.getString("damageType");
            float dmg = e.getFloat("damage");
            float rof = e.getFloat("fireRate");
            float range = e.getFloat("range");
            list.add(new Entry(
                name + "  [" + dmgType + "]",
                fmt("Damage: %.0f  ·  Rate: %.1f/s  ·  Range: %.0fm  ·  Type: %s", dmg, rof, range, cat)
            ));
        }
        content.put("Weapons", list);
    }

    private void loadFactions(JsonReader reader) {
        List<Entry> list = new ArrayList<>();
        JsonValue root = reader.parse(Gdx.files.internal("data/factions/faction_seeds.json"));
        for (JsonValue e = root.child; e != null; e = e.next) {
            String factionId = e.getString("factionId");
            float strength = e.getFloat("strength");
            float agg = e.getFloat("aggressiveness");
            list.add(new Entry(
                formatId(factionId),
                fmt("Strength: %.1f  ·  Aggressiveness: %.1f", strength, agg)
            ));
        }
        content.put("Factions", list);
    }

    private void loadStarSystems(JsonReader reader) {
        List<Entry> list = new ArrayList<>();

        JsonValue cfg = reader.parse(Gdx.files.internal("data/galaxy/galaxy_config.json"));
        list.add(new Entry(
            "Galaxy Overview",
            fmt("Type: %s  ·  Stars: %,d  ·  Radius: %.0f LY  ·  Arms: %d",
                cfg.getString("type"), cfg.getInt("targetStarCount"),
                cfg.getFloat("radiusLY"), cfg.getInt("armCount"))
        ));

        list.add(new SectionEntry("Planet Types"));
        JsonValue ptRoot = reader.parse(Gdx.files.internal("data/planet/planet_types.json"));
        for (JsonValue e = ptRoot.get("types").child; e != null; e = e.next) {
            list.add(new Entry(
                formatId(e.getString("id")),
                fmt("Radius: %.1f–%.1f R⊕  ·  Gravity: %.1f–%.1f g  ·  Moons: %d–%d  ·  Atmosphere: %s",
                    e.getFloat("radiusMin"), e.getFloat("radiusMax"),
                    e.getFloat("gravityMin"), e.getFloat("gravityMax"),
                    e.getInt("moonMin"), e.getInt("moonMax"),
                    e.getBoolean("hasAtmosphere") ? "Yes" : "No")
            ));
        }
        content.put("Star Systems", list);
    }

    private void loadBiomes(JsonReader reader) {
        List<Entry> list = new ArrayList<>();
        JsonValue root = reader.parse(Gdx.files.internal("data/planet/biome_profiles.json"));
        for (JsonValue e = root.get("profiles").child; e != null; e = e.next) {
            list.add(new Entry(
                formatId(e.getString("id")),
                fmt("Terrain amplitude: %.2f  ·  Ridge factor: %.1f",
                    e.getFloat("amplitude"), e.getFloat("ridgeMix"))
            ));
        }
        content.put("Biomes", list);
    }

    private void loadEconomy(JsonReader reader) {
        List<Entry> list = new ArrayList<>();
        JsonValue root = reader.parse(Gdx.files.internal("data/economy/commodities.json"));
        String lastTier = null;
        for (JsonValue e = root.child; e != null; e = e.next) {
            String tier = e.getString("tier");
            if (!tier.equals(lastTier)) {
                list.add(new SectionEntry(tier));
                lastTier = tier;
            }
            list.add(new Entry(
                e.getString("name"),
                fmt("[%s]  Base price: %d cr  ·  Mass: %.1f kg",
                    e.getString("category"), e.getInt("basePrice"), e.getFloat("mass"))
            ));
        }
        content.put("Economy", list);
    }

    private void loadLore() {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry("Archives", "Lore records are being compiled. Check back as the universe expands."));
        content.put("Lore", list);
    }

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(20);

        Label title = new Label("ENCYCLOPEDIA", skin, "title");
        root.add(title).colspan(2).padBottom(20).row();

        categoryButtons = new Table();
        categoryButtons.top().left();
        for (String cat : CATEGORIES) {
            TextButton btn = new TextButton(cat, skin);
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    audio.playSound("audio/sfx/ui_click.ogg");
                    selectedCategory = cat;
                    refreshDetail();
                }
            });
            categoryButtons.add(btn).width(220).height(44).padBottom(8).left().row();
        }
        ScrollPane categoryScroll = new ScrollPane(categoryButtons, skin);
        categoryScroll.setFadeScrollBars(false);
        root.add(categoryScroll).width(240).expandY().fillY().padRight(20).top();

        detailPanel = new Table();
        detailPanel.top().left();
        ScrollPane detailScroll = new ScrollPane(detailPanel, skin);
        detailScroll.setFadeScrollBars(false);
        root.add(detailScroll).expand().fill().row();

        TextButton backBtn = new TextButton("Back", skin);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                game.setScreen(returnTo != null ? returnTo : new MainMenuScreen(game));
            }
        });
        root.add(backBtn).colspan(2).width(200).height(44).padTop(16).right();

        stage.addActor(root);
        refreshDetail();
    }

    private void refreshDetail() {
        detailPanel.clear();
        Label.LabelStyle headerStyle = skin.get("header", Label.LabelStyle.class);
        Label.LabelStyle bodyStyle = skin.get("body", Label.LabelStyle.class);

        detailPanel.add(new Label(selectedCategory, skin, "title")).left().padBottom(16).row();

        List<Entry> entries = content.get(selectedCategory);
        if (entries == null || entries.isEmpty()) {
            detailPanel.add(new Label("No data available.", bodyStyle)).left().row();
            return;
        }

        for (Entry entry : entries) {
            if (entry instanceof SectionEntry) {
                detailPanel.add(new Label(entry.name, headerStyle)).left().padTop(14).padBottom(4).row();
                continue;
            }
            detailPanel.add(new Label(entry.name, headerStyle)).left().padTop(10).row();
            if (entry.stats != null && !entry.stats.isEmpty()) {
                Label statsLabel = new Label(entry.stats, bodyStyle);
                statsLabel.setWrap(true);
                detailPanel.add(statsLabel).left().padLeft(16).padBottom(2).width(680).row();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatId(String id) {
        if (id == null || id.isEmpty()) return id;
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.US));
        }
        return sb.toString();
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    // -------------------------------------------------------------------------
    // Screen lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        starfield.update(delta);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, screenW, screenH);

        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin();
        starfield.render(batch);
        batch.draw(overlayTexture, 0, 0, screenW, screenH);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        stage.dispose();
        if (overlayTexture != null) overlayTexture.dispose();
    }
}
