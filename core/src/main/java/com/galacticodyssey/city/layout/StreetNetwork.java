package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.Street;

import java.util.ArrayList;
import java.util.List;

/** Output of the street builder: the road segments plus the rectangular blocks they bound. */
public final class StreetNetwork {
    public final List<Street> streets = new ArrayList<>();
    public final List<CityBlock> blocks = new ArrayList<>();
}
