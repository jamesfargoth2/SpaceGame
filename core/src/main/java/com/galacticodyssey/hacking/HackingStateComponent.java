package com.galacticodyssey.hacking;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class HackingStateComponent implements Component {
    public Entity currentTarget = null;
    public HackingController controller = null;
    public boolean isRemoteHack = false;
}
