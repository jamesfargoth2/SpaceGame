package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;

public class EncyclopediaScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float SIDEBAR_WIDTH = 250f;
    private static final float CONTENT_WIDTH = 680f;

    private final GalacticOdyssey game;
    private final Stage stage;
    private final StarfieldBackground starfield;
    private final OrthographicCamera backgroundCamera;
    private final AudioManager audio;
    private final Skin skin;

    private final Array<Section> sections = new Array<>();
    private final Array<TextButton> categoryButtons = new Array<>();
    private Table contentTable;
    private ScrollPane contentScrollPane;
    private int selectedIndex = -1;

    public EncyclopediaScreen(GalacticOdyssey game) {
        this.game = game;
        this.skin = game.getSkin();
        this.audio = game.getAudioManager();
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.backgroundCamera = new OrthographicCamera();

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();
        this.starfield = new StarfieldBackground(screenW, screenH);

        buildSections();
        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().padTop(20);

        Label title = new Label("ENCYCLOPEDIA", skin, "title");
        root.add(title).colspan(2).padBottom(20).row();

        Table sidebar = new Table();
        sidebar.top();
        for (int i = 0; i < sections.size; i++) {
            final int index = i;
            TextButton btn = new TextButton(sections.get(i).title, skin);
            btn.getLabel().setAlignment(Align.left);
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!btn.isDisabled()) {
                        audio.playSound("audio/sfx/ui_click.ogg");
                        selectSection(index);
                    }
                }

                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    super.enter(event, x, y, pointer, fromActor);
                    if (pointer == -1 && !btn.isDisabled()) {
                        audio.playSound("audio/sfx/ui_hover.ogg");
                    }
                }
            });
            categoryButtons.add(btn);
            sidebar.add(btn).width(SIDEBAR_WIDTH - 20).height(40).padBottom(6).row();
        }

        ScrollPane sidebarScroll = new ScrollPane(sidebar, skin);
        sidebarScroll.setFadeScrollBars(false);
        sidebarScroll.setScrollingDisabled(true, false);

        contentTable = new Table();
        contentTable.top().left().pad(15);

        contentScrollPane = new ScrollPane(contentTable, skin);
        contentScrollPane.setFadeScrollBars(false);
        contentScrollPane.setScrollingDisabled(true, false);

        root.add(sidebarScroll).width(SIDEBAR_WIDTH).expandY().fillY().top().padRight(10);
        root.add(contentScrollPane).width(CONTENT_WIDTH).expandY().fillY().top();
        root.row();

        Table bottomRow = new Table();
        TextButton backBtn = new TextButton("Back", skin);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                game.setScreen(new MainMenuScreen(game));
            }
        });
        bottomRow.add(backBtn).width(200).height(50).expandX().left();
        root.add(bottomRow).colspan(2).width(SIDEBAR_WIDTH + CONTENT_WIDTH + 10).padTop(15).padBottom(15).row();

        stage.addActor(root);

        selectSection(0);
    }

    private void selectSection(int index) {
        if (index == selectedIndex) return;
        selectedIndex = index;

        for (int i = 0; i < categoryButtons.size; i++) {
            categoryButtons.get(i).setDisabled(i == index);
        }

        contentTable.clearChildren();
        Section section = sections.get(index);

        Label sectionTitle = new Label(section.title, skin, "header");
        contentTable.add(sectionTitle).left().padBottom(15).width(CONTENT_WIDTH - 30).row();

        for (Entry entry : section.entries) {
            Label subheader = new Label(entry.subtitle, skin, "subheader");
            contentTable.add(subheader).left().padTop(10).padBottom(5).width(CONTENT_WIDTH - 30).row();

            Label body = new Label(entry.body, skin, "body");
            body.setWrap(true);
            body.setAlignment(Align.topLeft);
            contentTable.add(body).left().width(CONTENT_WIDTH - 30).padBottom(10).row();
        }

        contentScrollPane.setScrollY(0);
    }

    private void buildSections() {
        sections.add(buildCombatSection());
        sections.add(buildShipsSection());
        sections.add(buildPlayerSection());
        sections.add(buildEconomySection());
        sections.add(buildEquipmentSection());
        sections.add(buildUniverseSection());
    }

    private Section buildCombatSection() {
        Section s = new Section("Combat");
        s.entries.add(new Entry("Unified Damage Model",
            "Incoming damage is processed through multiple defensive layers. Shields absorb damage first, " +
            "followed by armor mitigation calculated per hit region, and finally hull integrity. Different " +
            "damage types interact uniquely with each layer, making weapon and armor choices strategically meaningful."));
        s.entries.add(new Entry("Weapons",
            "Weapons are modularly constructed from five components: a frame that determines the weapon class, " +
            "a barrel that affects range and spread, an ammunition type that sets damage characteristics, " +
            "optional modifications for special properties, and a material quality tier that scales overall " +
            "effectiveness. Weapons support semi-automatic, fully automatic, and burst firing modes with both " +
            "hitscan and projectile-based delivery."));
        s.entries.add(new Entry("Melee Combat",
            "Close-quarters combat uses a state-machine driven melee system supporting multiple attack " +
            "directions including overhead strikes, forward thrusts, and lateral sweeps. Defensive blocking " +
            "mechanics allow players to mitigate incoming melee damage when timed correctly."));
        s.entries.add(new Entry("Status Effects",
            "Combatants can be afflicted by status effects such as burning, EMP disruption, and freezing. " +
            "Effects stack in intensity, apply damage each tick, and expire after their duration. When an " +
            "effect ends, connected systems are notified to update visuals and gameplay state."));
        s.entries.add(new Entry("Squad Tactics",
            "NPCs operating in squads share threat information among members. When a squad member detects " +
            "a threat, the information propagates to allies. Squads that suffer heavy casualties can issue " +
            "coordinated retreat orders, causing surviving members to disengage and regroup."));
        return s;
    }

    private Section buildShipsSection() {
        Section s = new Section("Ships");
        s.entries.add(new Entry("Ship Flight",
            "Ships use six degrees of freedom flight physics, providing full control over pitch, yaw, roll, " +
            "forward thrust, and lateral strafing. Player input is translated into forces applied to the " +
            "ship's physics body, producing momentum-based flight that feels weighty and responsive."));
        s.entries.add(new Entry("Ship Interiors",
            "Ship interiors run in their own isolated physics simulation, separate from the main space " +
            "environment. This allows objects and characters inside a ship to behave naturally with local " +
            "gravity while the ship itself moves freely through space. Players can walk around inside their " +
            "ship during flight."));
        s.entries.add(new Entry("Ship Weapons",
            "Ships mount weapons on hardpoint slots distributed across the hull. Each weapon system tracks " +
            "heat accumulation independently. Sustained fire causes heat to build, and exceeding the thermal " +
            "limit triggers an overheat state that forces a cooldown period before the weapon can fire again."));
        s.entries.add(new Entry("Turret Tracking",
            "Ship turrets automatically rotate to track targeting solutions. The tracking system calculates " +
            "lead angles to predict where a moving target will be when the projectile arrives, keeping fire " +
            "accurate against maneuvering targets."));
        s.entries.add(new Entry("Point Defense",
            "Automated point defense systems detect and engage incoming projectiles. Using spatial " +
            "partitioning for efficient threat detection, these systems independently prioritize and " +
            "intercept missiles and torpedoes before they reach the ship."));
        return s;
    }

    private Section buildPlayerSection() {
        Section s = new Section("Player");
        s.entries.add(new Entry("Movement",
            "The player character uses a kinematic character controller supporting walking, sprinting, " +
            "crouching, and jumping. Ground detection is raycast-based with slope angle limits that prevent " +
            "climbing surfaces that are too steep. Movement forces and damping adjust based on whether the " +
            "player is grounded or airborne."));
        s.entries.add(new Entry("Camera",
            "The first-person camera features head bob synchronized to movement speed, weapon recoil " +
            "recovery curves, a landing dip effect when touching down from a fall, and smooth eye height " +
            "transitions when changing between standing, crouching, and other stances."));
        s.entries.add(new Entry("Weapon Handling",
            "Players can switch between weapons in their inventory, with each weapon having its own switch " +
            "time. Aiming down sights narrows the field of view and adjusts input sensitivity. Weapons " +
            "exhibit procedural idle sway that varies by movement state, and each weapon type has unique " +
            "recoil patterns that affect aim during sustained fire."));
        s.entries.add(new Entry("Interaction",
            "The interaction system detects nearby interactive objects such as ship doors, pilot seats, and " +
            "terminals. When entering a ship's pilot seat, the system handles the seamless transition from " +
            "on-foot first-person mode to ship piloting mode, switching physics bodies and camera systems."));
        return s;
    }

    private Section buildEconomySection() {
        Section s = new Section("Economy");
        s.entries.add(new Entry("Trading",
            "Station markets use supply and demand-driven pricing. The pricing system periodically " +
            "recalculates commodity prices based on local stock levels and consumption rates. Buying " +
            "goods raises prices while selling lowers them, creating natural trade route incentives " +
            "between stations with complementary economies."));
        s.entries.add(new Entry("Planetary Economy",
            "Each planet simulates production and consumption cycles based on its economic profile. " +
            "Planets produce goods based on their resources and industry, and consume goods their " +
            "population needs. This drives the flow of trade across the galaxy as stations reflect " +
            "their parent planet's economic output."));
        s.entries.add(new Entry("Commodities",
            "Tradeable goods are defined as data-driven commodity types, each with base pricing, " +
            "categorization, and supply and demand parameters. The commodity system supports the full " +
            "economic simulation from planetary production through station-level trading."));
        return s;
    }

    private Section buildEquipmentSection() {
        Section s = new Section("Equipment");
        s.entries.add(new Entry("Armor",
            "Armor pieces slot into specific hit regions on the player character. Each piece provides " +
            "damage resistance values that factor into the damage calculation when that body region is " +
            "struck. Equipping or removing armor immediately updates the character's defensive profile."));
        s.entries.add(new Entry("Loot Generation",
            "When enemies are defeated, loot is procedurally generated from archetype-based loot tables. " +
            "Generated items receive quality tiers and random modifiers that affect their statistics, " +
            "ensuring variety in the equipment players discover throughout the game."));
        s.entries.add(new Entry("Weapon Assembly",
            "Unique weapon instances are constructed by combining modular components. A frame provides " +
            "the base weapon type, a barrel affects range and projectile spread, ammunition determines " +
            "the damage profile, modifications add special properties, and material quality scales the " +
            "weapon's overall effectiveness. This system produces a wide variety of distinct weapons " +
            "from a manageable set of component definitions."));
        return s;
    }

    private Section buildUniverseSection() {
        Section s = new Section("Universe");
        s.entries.add(new Entry("Floating Origin",
            "The game uses a floating origin coordinate system to handle the vast scale of space. " +
            "Galaxy and sector positions use 64-bit double-precision coordinates for accuracy across " +
            "light-years of distance. The active local scene uses 32-bit floats for rendering " +
            "performance. The player always remains near the coordinate origin, with the entire " +
            "universe repositioned around them to prevent floating-point precision loss at large " +
            "distances."));
        s.entries.add(new Entry("Physics Simulation",
            "Physics are simulated using the Bullet physics engine with a fixed timestep of one " +
            "sixtieth of a second. The physics world handles origin rebasing events when the player " +
            "moves far enough from the current local origin, seamlessly shifting all physics bodies " +
            "to maintain precision without disrupting the simulation."));
        s.entries.add(new Entry("Galaxy Structure",
            "The galaxy is procedurally defined with configurable generation parameters. Star systems " +
            "contain stars classified by spectral type, planets with distinct biome profiles and " +
            "atmospheric properties, and stations that serve as hubs for trading and mission activity. " +
            "Each celestial body's characteristics influence gameplay from resource availability to " +
            "environmental hazards."));
        return s;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);

        starfield.update(delta);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        backgroundCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Batch batch = stage.getBatch();
        batch.setProjectionMatrix(backgroundCamera.combined);
        batch.begin();
        starfield.render(batch);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        starfield.resize(width, height);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        stage.dispose();
        starfield.dispose();
    }

    private static class Section {
        final String title;
        final Array<Entry> entries = new Array<>();

        Section(String title) {
            this.title = title;
        }
    }

    private static class Entry {
        final String subtitle;
        final String body;

        Entry(String subtitle, String body) {
            this.subtitle = subtitle;
            this.body = body;
        }
    }
}
