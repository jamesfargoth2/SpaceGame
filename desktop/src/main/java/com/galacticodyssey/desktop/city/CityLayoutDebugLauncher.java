package com.galacticodyssey.desktop.city;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Standalone launcher for the city layout top-down debug view. */
public final class CityLayoutDebugLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("City Layout Debug");
        config.setWindowedMode(1100, 900);
        new Lwjgl3Application(new CityLayoutDebugRenderer(), config);
    }
}
