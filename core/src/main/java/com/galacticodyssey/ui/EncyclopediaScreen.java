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
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.GalacticOdyssey;

public class EncyclopediaScreen implements Screen {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private static final String[] CATEGORIES = {
        "Ships", "Weapons", "Factions", "Star Systems", "Biomes", "Economy", "Lore"
    };

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

        buildUi();
    }

    private void buildUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(20);

        Label title = new Label("ENCYCLOPEDIA", skin, "title");
        root.add(title).colspan(2).padBottom(20).row();

        // left: category list
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

        // right: detail panel
        detailPanel = new Table();
        detailPanel.top().left();
        ScrollPane detailScroll = new ScrollPane(detailPanel, skin);
        detailScroll.setFadeScrollBars(false);
        root.add(detailScroll).expand().fill().row();

        // back button
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
        Label.LabelStyle bodyStyle = skin.get("body", Label.LabelStyle.class);
        detailPanel.add(new Label(selectedCategory, skin, "title")).left().padBottom(12).row();
        detailPanel.add(new Label("Content for " + selectedCategory + " coming soon.", bodyStyle))
            .left().padBottom(8).row();
    }

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
