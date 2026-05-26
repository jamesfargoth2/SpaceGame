package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;

import java.util.HashMap;
import java.util.Map;

public class PricingComponent implements Component {
    public final Map<String, Integer> prices = new HashMap<>();
    public float volatility;
}
