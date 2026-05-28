package com.galacticodyssey.networking.components;

import com.badlogic.ashley.core.Component;

public class AuthorityComponent implements Component {
    public enum Owner {
        SERVER,
        ZONE_SERVER,
        LOCAL_PREDICTED
    }

    public Owner owner = Owner.SERVER;
    public String ownerZoneId;

    public AuthorityComponent() {}
}
