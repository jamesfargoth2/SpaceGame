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
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

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
}
