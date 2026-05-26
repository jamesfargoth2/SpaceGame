package com.galacticodyssey.server;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.galacticodyssey.core.GalacticOdyssey;

public class ServerLauncher {

    public static void main(String[] args) {
        var config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new GalacticOdyssey(), config);
    }
}
