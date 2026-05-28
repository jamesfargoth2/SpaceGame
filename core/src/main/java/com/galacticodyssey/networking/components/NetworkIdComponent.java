package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;

public class NetworkIdComponent implements Component {
    public final int networkId;

    public NetworkIdComponent() {
        this.networkId = -1;
    }

    public NetworkIdComponent(int networkId) {
        this.networkId = networkId;
    }
}
