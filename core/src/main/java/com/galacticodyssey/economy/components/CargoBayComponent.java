package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;

import java.util.HashMap;
import java.util.Map;

public class CargoBayComponent implements Component {
    public float capacity;
    public final Map<String, Integer> contents = new HashMap<>();
    public float usedVolume;
}
