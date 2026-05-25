package com.galacticodyssey.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.galacticodyssey.core.GalacticOdyssey;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class DesktopLauncher {

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Galactic Odyssey");
        config.setForegroundFPS(60);

        applyDisplayPreferences(config);

        new Lwjgl3Application(new GalacticOdyssey(), config);
    }

    private static void applyDisplayPreferences(Lwjgl3ApplicationConfiguration config) {
        Properties props = loadPreferencesFile();
        if (props == null) {
            config.setWindowedMode(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            config.useVsync(true);
            return;
        }

        String displayMode = props.getProperty("displayMode", "WINDOWED");
        int width = getIntProperty(props, "resolutionWidth", DEFAULT_WIDTH);
        int height = getIntProperty(props, "resolutionHeight", DEFAULT_HEIGHT);
        boolean vsync = Boolean.parseBoolean(props.getProperty("vsync", "true"));

        config.useVsync(vsync);

        switch (displayMode) {
            case "FULLSCREEN":
                config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
                break;
            case "BORDERLESS":
                config.setDecorated(false);
                var desktop = Lwjgl3ApplicationConfiguration.getDisplayMode();
                config.setWindowedMode(desktop.width, desktop.height);
                break;
            default:
                config.setWindowedMode(width, height);
                break;
        }
    }

    private static Properties loadPreferencesFile() {
        String userHome = System.getProperty("user.home");
        File prefsFile = new File(userHome, ".prefs" + File.separator + "GalacticOdyssey");
        if (!prefsFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(prefsFile)) {
            Properties props = new Properties();
            props.load(fis);
            return props;
        } catch (Exception e) {
            return null;
        }
    }

    private static int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
