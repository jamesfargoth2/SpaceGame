package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;

import java.util.List;
import java.util.function.Consumer;

public class NpcPortraitActor extends Group implements Disposable {

    private static final float PORTRAIT_SIZE = 64f;
    private static final float HOVER_SCALE = 1.05f;

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Entity entity;
    private Texture portraitTexture;
    private Texture nameTagBgTexture;
    private boolean selected;

    public NpcPortraitActor(Entity entity, Skin skin, Consumer<Entity> onClick) {
        this.entity = entity;
        setTouchable(Touchable.enabled);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        NpcStatsComponent stats = STATS_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);

        Color speciesColor = getSpeciesColor(identity.species);

        // Portrait circle (placeholder)
        portraitTexture = createCircleTexture(32, speciesColor);
        Image portrait = new Image(new TextureRegion(portraitTexture));
        portrait.setSize(PORTRAIT_SIZE, PORTRAIT_SIZE);
        portrait.setPosition(0, 0);
        addActor(portrait);

        // Name tag below portrait
        Table nameTag = new Table();
        nameTag.setBackground(createSolidDrawable(new Color(0.04f, 0.05f, 0.09f, 0.8f)));
        nameTag.pad(4);

        String name = identity.name != null ? identity.name : "Unknown";
        Label nameLabel = new Label(name, skin, "slot-name");
        nameLabel.setColor(speciesColor);
        nameTag.add(nameLabel).left().row();

        String subtitle = (identity.species != null ? identity.species : "Unknown") +
            " · " + (identity.role != null ? identity.role.name() : "");
        Label subtitleLabel = new Label(subtitle, skin, "slot-meta");
        subtitleLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
        nameTag.add(subtitleLabel).left().row();

        if (rc != null && stats != null) {
            List<StatType> topStats = StatType.getTopN(stats, 2);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < topStats.size(); i++) {
                if (i > 0) sb.append(" · ");
                StatType st = topStats.get(i);
                sb.append(st.abbreviation).append(" ").append((int) st.getValue(stats));
            }
            Label statsLabel = new Label(sb.toString(), skin, "slot-meta");
            statsLabel.setColor(0.33f, 0.81f, 0.55f, 1f);
            nameTag.add(statsLabel).left();
        }

        nameTag.pack();
        nameTag.setPosition(PORTRAIT_SIZE / 2 - nameTag.getWidth() / 2, -nameTag.getHeight() - 6);
        addActor(nameTag);

        setSize(PORTRAIT_SIZE, PORTRAIT_SIZE);
        setOrigin(Align.center);

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClick.accept(entity);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer,
                              com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                if (!selected) {
                    addAction(Actions.scaleTo(HOVER_SCALE, HOVER_SCALE, 0.1f));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer,
                             com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                if (!selected) {
                    addAction(Actions.scaleTo(1f, 1f, 0.1f));
                }
            }
        });
    }

    public Entity getEntity() {
        return entity;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            addAction(Actions.scaleTo(HOVER_SCALE, HOVER_SCALE, 0.15f));
        } else {
            addAction(Actions.scaleTo(1f, 1f, 0.15f));
        }
    }

    private Texture createCircleTexture(int radius, Color color) {
        int size = radius * 2;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(color.r * 0.3f, color.g * 0.3f, color.b * 0.3f, 1f);
        pm.fillCircle(radius, radius, radius);
        pm.setColor(color);
        pm.drawCircle(radius, radius, radius - 1);
        pm.drawCircle(radius, radius, radius - 2);
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private TextureRegionDrawable createSolidDrawable(Color color) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(color);
        pm.fill();
        nameTagBgTexture = new Texture(pm);
        pm.dispose();
        return new TextureRegionDrawable(new TextureRegion(nameTagBgTexture));
    }

    private Color getSpeciesColor(String species) {
        if (species == null) return new Color(0.9f, 0.27f, 0.37f, 1f);
        return switch (species.toLowerCase()) {
            case "veloxi" -> new Color(0.2f, 1f, 0.47f, 1f);
            case "krethian" -> new Color(0.8f, 0.5f, 0.2f, 1f);
            default -> new Color(0.9f, 0.27f, 0.37f, 1f);
        };
    }

    @Override
    public void dispose() {
        if (portraitTexture != null) {
            portraitTexture.dispose();
            portraitTexture = null;
        }
        if (nameTagBgTexture != null) {
            nameTagBgTexture.dispose();
            nameTagBgTexture = null;
        }
    }
}
