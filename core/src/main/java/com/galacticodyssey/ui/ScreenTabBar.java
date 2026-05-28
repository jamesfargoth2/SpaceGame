package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;

public class ScreenTabBar extends Table {

    private static final Color ACTIVE_COLOR = new Color(0f, 0.9f, 1f, 1f);
    private static final Color INACTIVE_COLOR = new Color(0.5f, 0.6f, 0.7f, 1f);

    private final Skin skin;
    private final Array<TabEntry> entries = new Array<>();

    public interface TabSelectedListener {
        void onTabSelected(String screenName);
    }

    private TabSelectedListener listener;

    public ScreenTabBar(Skin skin) {
        this.skin = skin;
        pad(6).padLeft(12).padRight(12);
        left();
    }

    public void setTabSelectedListener(TabSelectedListener listener) {
        this.listener = listener;
    }

    public void addTab(String screenName, String displayName) {
        TextButton button = new TextButton(displayName, skin, "small");
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (listener != null) listener.onTabSelected(screenName);
            }
        });

        entries.add(new TabEntry(screenName, button));
        rebuild();
    }

    public void removeTab(String screenName) {
        for (int i = 0; i < entries.size; i++) {
            if (entries.get(i).name.equals(screenName)) {
                entries.removeIndex(i);
                break;
            }
        }
        rebuild();
    }

    public void setActiveTab(String screenName) {
        for (int i = 0; i < entries.size; i++) {
            TabEntry entry = entries.get(i);
            boolean active = entry.name.equals(screenName);
            entry.button.getLabel().setColor(active ? ACTIVE_COLOR : INACTIVE_COLOR);
        }
    }

    private void rebuild() {
        clearChildren();
        for (int i = 0; i < entries.size; i++) {
            add(entries.get(i).button).padRight(4).minWidth(90).height(28);
        }
    }

    private static class TabEntry {
        final String name;
        final TextButton button;

        TabEntry(String name, TextButton button) {
            this.name = name;
            this.button = button;
        }
    }
}
