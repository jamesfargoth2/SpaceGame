package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.events.CharacterLevelUpEvent;
import com.galacticodyssey.player.events.PerkAvailableEvent;
import com.galacticodyssey.player.events.PerkSelectedEvent;
import com.galacticodyssey.player.events.SkillLevelUpEvent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkNodeDef;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.stats.SkillProgress;
import com.galacticodyssey.player.systems.PerkSystem;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;

/** Character sheet: skill levels, point allocation, and per-skill perk trees. */
public class CharacterScreen implements ManagedScreen {

    private final Stage stage;
    private final Skin skin;
    private boolean open;

    private Engine engine;
    private RealTimeSkillSystem skillSystem;
    private PerkSystem perkSystem;
    private PerkRegistry perkRegistry;

    private final Table root = new Table();
    private final Table body = new Table();

    public CharacterScreen(EventBus eventBus, Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        root.setFillParent(true);
        root.top();
        ScrollPane scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).expand().fill().pad(20);
        stage.addActor(root);

        eventBus.subscribe(CharacterLevelUpEvent.class, e -> refreshIfOpen());
        eventBus.subscribe(SkillLevelUpEvent.class, e -> refreshIfOpen());
        eventBus.subscribe(PerkAvailableEvent.class, e -> refreshIfOpen());
        eventBus.subscribe(PerkSelectedEvent.class, e -> refreshIfOpen());
    }

    public void initialize(Engine engine, RealTimeSkillSystem skillSystem,
                           PerkSystem perkSystem, PerkRegistry perkRegistry) {
        this.engine = engine;
        this.skillSystem = skillSystem;
        this.perkSystem = perkSystem;
        this.perkRegistry = perkRegistry;
    }

    private Entity player() {
        var arr = engine.getEntitiesFor(Family.all(PlayerStatsComponent.class).get());
        return arr.size() > 0 ? arr.first() : null;
    }

    private void refreshIfOpen() {
        if (open && engine != null) rebuild();
    }

    private void rebuild() {
        body.clear();
        Entity p = player();
        if (p == null) return;
        PlayerStatsComponent stats = p.getComponent(PlayerStatsComponent.class);

        body.add(new Label("Character Level " + stats.characterLevel
            + "   XP: " + (int) stats.totalXP, skin)).left().padBottom(4).row();
        body.add(new Label("Unspent skill points: " + stats.unspentPoints
            + "    Perk picks: " + stats.unspentPerkPicks, skin)).left().padBottom(12).row();

        body.add(new Label("Real-Time Skills", skin)).left().padBottom(6).row();
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            SkillProgress prog = stats.realTimeSkills.get(skill);
            Table rowT = new Table();
            rowT.add(new Label(skill.name(), skin)).width(160).left();
            rowT.add(new Label("Lv " + prog.level, skin)).width(60).left();
            ProgressBar bar = new ProgressBar(0f, thresholdFor(prog.level), 1f, false, skin);
            bar.setValue(prog.xp);
            rowT.add(bar).width(200);
            body.add(rowT).left().padBottom(2).row();
        }

        body.add(new Label("Point Skills", skin)).left().padTop(12).padBottom(6).row();
        for (PointSkill skill : PointSkill.values()) {
            int lvl = stats.pointSkills.get(skill, 0);
            Table rowT = new Table();
            rowT.add(new Label(skill.name(), skin)).width(160).left();
            rowT.add(new Label("Lv " + lvl, skin)).width(60).left();
            TextButton plus = new TextButton("+", skin);
            boolean canSpend = stats.unspentPoints > 0 && lvl < RealTimeSkillSystem.MAX_SKILL_LEVEL;
            plus.setDisabled(!canSpend);
            plus.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) {
                    if (skillSystem.spendPoint(p, skill)) rebuild();
                }
            });
            rowT.add(plus).width(40);
            body.add(rowT).left().padBottom(2).row();
        }

        body.add(new Label("Perk Trees (permanent)", skin)).left().padTop(12).padBottom(6).row();
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            var nodes = perkRegistry.getTree(skill);
            if (nodes.size == 0) continue;
            body.add(new Label(skill.name(), skin)).left().padTop(6).row();
            for (PerkNodeDef node : nodes) {
                Table rowT = new Table();
                boolean owned = stats.perks.contains(node.id, false);
                boolean selectable = !owned && stats.unspentPerkPicks > 0
                    && perkRegistry.canSelect(stats, node.id);
                String status = owned ? "[owned] " : (selectable ? "" : "[locked] ");
                rowT.add(new Label("  T" + node.tier + " " + status + node.name
                    + " - " + node.description, skin)).left().width(420);
                if (selectable) {
                    TextButton take = new TextButton("Select", skin);
                    take.addListener(new ChangeListener() {
                        @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) {
                            if (perkSystem.selectPerk(p, node.id)) rebuild();
                        }
                    });
                    rowT.add(take).width(80);
                }
                body.add(rowT).left().padBottom(1).row();
            }
        }
    }

    private static float thresholdFor(int level) {
        return 100f + level * level * 2f;
    }

    @Override public String getDisplayName() { return "Character"; }

    @Override public void open() {
        open = true;
        rebuild();
    }

    @Override public void close() { open = false; }
    @Override public boolean isOpen() { return open; }
    @Override public Stage getStage() { return stage; }

    @Override public void render(float delta) {
        if (!open) return;
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() { stage.dispose(); }
}
