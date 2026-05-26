package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class UiFactory {

    private static final Color CYAN = new Color(0f, 0.9f, 1f, 1f);
    private static final Color BORDER_DEFAULT = new Color(0f, 0.6f, 0.8f, 1f);
    private static final Color BG_DEFAULT = new Color(0f, 0f, 0f, 0.6f);
    private static final Color BG_HOVER = new Color(0f, 0f, 0f, 0.7f);
    private static final Color BG_PRESS = new Color(0f, 0f, 0f, 0.8f);

    public static Skin createSkin() {
        Skin skin = new Skin();

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(
            Gdx.files.internal("fonts/Orbitron-Regular.ttf")
        );

        FreeTypeFontParameter titleParam = new FreeTypeFontParameter();
        titleParam.size = 48;
        titleParam.color = Color.WHITE;
        titleParam.borderWidth = 1;
        titleParam.borderColor = new Color(0f, 0.4f, 0.6f, 0.3f);
        BitmapFont titleFont = generator.generateFont(titleParam);
        skin.add("title", titleFont);

        FreeTypeFontParameter buttonParam = new FreeTypeFontParameter();
        buttonParam.size = 22;
        buttonParam.color = Color.WHITE;
        BitmapFont buttonFont = generator.generateFont(buttonParam);
        skin.add("button", buttonFont);

        FreeTypeFontParameter versionParam = new FreeTypeFontParameter();
        versionParam.size = 14;
        versionParam.color = new Color(1f, 1f, 1f, 0.5f);
        BitmapFont versionFont = generator.generateFont(versionParam);
        skin.add("version", versionFont);

        FreeTypeFontParameter headerParam = new FreeTypeFontParameter();
        headerParam.size = 26;
        headerParam.color = CYAN;
        BitmapFont headerFont = generator.generateFont(headerParam);
        skin.add("header", headerFont);

        FreeTypeFontParameter settingParam = new FreeTypeFontParameter();
        settingParam.size = 18;
        settingParam.color = Color.WHITE;
        BitmapFont settingFont = generator.generateFont(settingParam);
        skin.add("setting", settingFont);

        generator.dispose();

        NinePatchDrawable buttonDefault = createButtonDrawable(BG_DEFAULT, BORDER_DEFAULT);
        NinePatchDrawable buttonHover = createButtonDrawable(BG_HOVER, CYAN);
        NinePatchDrawable buttonPress = createButtonDrawable(BG_PRESS, CYAN);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = buttonFont;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.overFontColor = CYAN.cpy();
        buttonStyle.downFontColor = CYAN.cpy();
        buttonStyle.disabledFontColor = new Color(1f, 1f, 1f, 0.3f);
        buttonStyle.up = buttonDefault;
        buttonStyle.over = buttonHover;
        buttonStyle.down = buttonPress;
        buttonStyle.disabled = buttonDefault;
        skin.add("default", buttonStyle);

        Label.LabelStyle titleStyle = new Label.LabelStyle();
        titleStyle.font = titleFont;
        titleStyle.fontColor = Color.WHITE;
        skin.add("title", titleStyle);

        Label.LabelStyle versionStyle = new Label.LabelStyle();
        versionStyle.font = versionFont;
        versionStyle.fontColor = new Color(1f, 1f, 1f, 0.5f);
        skin.add("version", versionStyle);

        Label.LabelStyle headerStyle = new Label.LabelStyle();
        headerStyle.font = headerFont;
        headerStyle.fontColor = CYAN.cpy();
        skin.add("header", headerStyle);

        Label.LabelStyle settingStyle = new Label.LabelStyle();
        settingStyle.font = settingFont;
        settingStyle.fontColor = Color.WHITE;
        skin.add("setting", settingStyle);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = createSliderBackground();
        sliderStyle.knob = createSliderKnob();
        skin.add("default-horizontal", sliderStyle);

        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = createCheckboxDrawable(false);
        checkBoxStyle.checkboxOn = createCheckboxDrawable(true);
        checkBoxStyle.font = settingFont;
        checkBoxStyle.fontColor = Color.WHITE;
        skin.add("default", checkBoxStyle);

        NinePatchDrawable selectBg = createButtonDrawable(BG_DEFAULT, BORDER_DEFAULT);
        NinePatchDrawable selectBgOver = createButtonDrawable(BG_HOVER, CYAN);

        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = settingFont;
        listStyle.fontColorSelected = CYAN.cpy();
        listStyle.fontColorUnselected = Color.WHITE;
        listStyle.selection = createButtonDrawable(new Color(0f, 0.3f, 0.4f, 0.8f), CYAN);
        listStyle.over = createButtonDrawable(BG_HOVER, BORDER_DEFAULT);
        listStyle.background = createButtonDrawable(BG_PRESS, BORDER_DEFAULT);
        skin.add("default", listStyle);

        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        skin.add("default", scrollPaneStyle);

        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = settingFont;
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.overFontColor = CYAN.cpy();
        selectBoxStyle.background = selectBg;
        selectBoxStyle.backgroundOver = selectBgOver;
        selectBoxStyle.listStyle = listStyle;
        selectBoxStyle.scrollStyle = scrollPaneStyle;
        skin.add("default", selectBoxStyle);

        return skin;
    }

    private static NinePatchDrawable createButtonDrawable(Color bgColor, Color borderColor) {
        int size = 12;
        int border = 1;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(borderColor);
        pixmap.fill();
        pixmap.setColor(bgColor);
        pixmap.fillRectangle(border, border, size - 2 * border, size - 2 * border);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        NinePatch ninePatch = new NinePatch(new TextureRegion(texture), 3, 3, 3, 3);
        return new NinePatchDrawable(ninePatch);
    }

    private static Drawable createSliderBackground() {
        Pixmap pixmap = new Pixmap(20, 4, Pixmap.Format.RGBA8888);
        pixmap.setColor(BG_DEFAULT);
        pixmap.fill();
        pixmap.setColor(BORDER_DEFAULT);
        pixmap.drawRectangle(0, 0, 20, 4);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        NinePatch ninePatch = new NinePatch(new TextureRegion(texture), 2, 2, 0, 0);
        NinePatchDrawable drawable = new NinePatchDrawable(ninePatch);
        drawable.setMinHeight(4);
        return drawable;
    }

    private static Drawable createSliderKnob() {
        int knobSize = 16;
        Pixmap pixmap = new Pixmap(knobSize, knobSize, Pixmap.Format.RGBA8888);
        pixmap.setColor(BORDER_DEFAULT);
        pixmap.fill();
        pixmap.setColor(BG_DEFAULT);
        pixmap.fillRectangle(2, 2, knobSize - 4, knobSize - 4);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }

    private static Drawable createCheckboxDrawable(boolean checked) {
        int size = 20;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(BORDER_DEFAULT);
        pixmap.fill();
        if (checked) {
            pixmap.setColor(new Color(0f, 0.6f, 0.8f, 0.8f));
            pixmap.fillRectangle(2, 2, size - 4, size - 4);
        } else {
            pixmap.setColor(BG_DEFAULT);
            pixmap.fillRectangle(2, 2, size - 4, size - 4);
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return new TextureRegionDrawable(new TextureRegion(texture));
    }
}
