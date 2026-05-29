package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.data.DistrictMixDef;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Splits each block into building lots by iterative binary subdivision along the long axis. */
public final class LotSubdivider {
    private LotSubdivider() {}

    public static List<BuildingLot> subdivide(List<CityBlock> blocks, CityDataRegistry reg, long citySeed) {
        List<BuildingLot> lots = new ArrayList<>();
        long domain = SeedDeriver.forId(citySeed, 0x107D1D1DL);
        long blockIndex = 0;
        for (CityBlock block : blocks) {
            Random rng = new Random(SeedDeriver.forId(domain, blockIndex++));
            DistrictMixDef mix = reg.districtMix(block.district);
            splitBlock(block, mix, rng, lots);
        }
        return lots;
    }

    private static void splitBlock(CityBlock block, DistrictMixDef mix, Random rng, List<BuildingLot> out) {
        Deque<Rectangle> queue = new ArrayDeque<>();
        queue.add(new Rectangle(block.footprint));
        while (!queue.isEmpty()) {
            Rectangle cell = queue.removeFirst();
            float area = cell.width * cell.height;
            if (area <= mix.minLot || rng.nextFloat() < stopProbability(area, mix.maxLot)) {
                out.add(new BuildingLot(cell, block.district));
                continue;
            }
            boolean splitW = cell.width >= cell.height;
            float f = RngUtil.range(rng, 0.4f, 0.6f);
            if (splitW) {
                float w1 = cell.width * f;
                queue.add(new Rectangle(cell.x, cell.y, w1, cell.height));
                queue.add(new Rectangle(cell.x + w1, cell.y, cell.width - w1, cell.height));
            } else {
                float h1 = cell.height * f;
                queue.add(new Rectangle(cell.x, cell.y, cell.width, h1));
                queue.add(new Rectangle(cell.x, cell.y + h1, cell.width, cell.height - h1));
            }
        }
    }

    private static float stopProbability(float area, float maxLot) {
        return MathUtils.clamp(1f - (area / maxLot), 0f, 0.85f);
    }
}
