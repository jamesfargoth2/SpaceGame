package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.OrderedMap;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class ScreenTabManager implements Disposable {

    private final OrderedMap<String, ManagedScreen> screens = new OrderedMap<>();
    private final ScreenTabBar tabBar;
    private final Stage tabBarStage;
    private final Texture bgTexture;
    private String activeScreenName;

    public interface ScreenTransitionListener {
        void onScreenOpened(String name, ManagedScreen screen);
        void onAllScreensClosed();
    }

    private ScreenTransitionListener transitionListener;

    public ScreenTabManager(Skin skin) {
        tabBar = new ScreenTabBar(skin);
        tabBar.setTabSelectedListener(this::switchTo);

        tabBarStage = new Stage(new ScreenViewport());

        Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pix.setColor(new Color(0.02f, 0.03f, 0.06f, 0.85f));
        pix.fill();
        bgTexture = new Texture(pix);
        pix.dispose();

        Table root = new Table();
        root.setFillParent(true);
        root.top().left();

        Table barContainer = new Table();
        barContainer.setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));
        barContainer.add(tabBar).expandX().fillX();

        root.add(barContainer).expandX().fillX().row();
        root.add().expand().fill();

        tabBarStage.addActor(root);
    }

    public void setTransitionListener(ScreenTransitionListener listener) {
        this.transitionListener = listener;
    }

    public void register(String name, ManagedScreen screen) {
        screens.put(name, screen);
        tabBar.addTab(name, screen.getDisplayName());
    }

    public void unregister(String name) {
        ManagedScreen screen = screens.get(name);
        if (screen != null && screen.isOpen()) {
            screen.close();
        }
        screens.remove(name);
        tabBar.removeTab(name);
        if (name.equals(activeScreenName)) {
            activeScreenName = null;
        }
    }

    public void switchTo(String name) {
        if (name.equals(activeScreenName)) return;

        ManagedScreen target = screens.get(name);
        if (target == null) return;

        if (activeScreenName != null) {
            ManagedScreen current = screens.get(activeScreenName);
            if (current != null && current.isOpen()) {
                current.close();
            }
        }

        activeScreenName = name;
        target.open();
        tabBar.setActiveTab(name);

        if (transitionListener != null) {
            transitionListener.onScreenOpened(name, target);
        }
    }

    public void closeActive() {
        if (activeScreenName != null) {
            ManagedScreen current = screens.get(activeScreenName);
            if (current != null && current.isOpen()) {
                current.close();
            }
            activeScreenName = null;
            if (transitionListener != null) {
                transitionListener.onAllScreensClosed();
            }
        }
    }

    public boolean isAnyOpen() {
        return activeScreenName != null;
    }

    public String getActiveScreenName() {
        return activeScreenName;
    }

    public ManagedScreen getActiveScreen() {
        return activeScreenName != null ? screens.get(activeScreenName) : null;
    }

    public ManagedScreen getScreen(String name) {
        return screens.get(name);
    }

    public Stage getTabBarStage() {
        return tabBarStage;
    }

    public void render(float delta) {
        if (activeScreenName == null) return;

        ManagedScreen active = screens.get(activeScreenName);
        if (active != null && active.isOpen()) {
            active.render(delta);
        }

        tabBarStage.act(delta);
        tabBarStage.draw();
    }

    public void resize(int width, int height) {
        tabBarStage.getViewport().update(width, height, true);
        for (ManagedScreen screen : screens.values()) {
            screen.resize(width, height);
        }
    }

    public void notifyClosedExternally(String name) {
        if (name.equals(activeScreenName)) {
            activeScreenName = null;
            if (transitionListener != null) {
                transitionListener.onAllScreensClosed();
            }
        }
    }

    @Override
    public void dispose() {
        tabBarStage.dispose();
        bgTexture.dispose();
    }
}
