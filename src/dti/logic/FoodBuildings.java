package dti.logic;

import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Farming and Aquaculture when a project changes the world: no swap. A world that becomes
 * water loses Farming and its chain; a world that stops being water loses Aquaculture and its chain. A land world that
 * loses all its farmland loses Farming.
 */
public final class FoodBuildings {
    private FoodBuildings() {}

    public static final String FARMING = "farming", AQUACULTURE = "aquaculture";

    /** The marine life conditions, worst to best. */
    public static final List<String> MARINE = Arrays.asList("dti_marine_sparse", "dti_marine_thriving", "dti_marine_abundant");

    /** The flat food a marine life condition adds to Aquaculture and to each building of its chain: +1, +2, +3; 0 for any other condition. */
    public static int marineFood(String condition) {
        return MARINE.indexOf(condition) + 1;
    }

    /** The food industry whose chain a type change removes: Farming when the world becomes water, Aquaculture when it stops; else null. */
    public static String lost(boolean wasWater, boolean isWater) {
        return wasWater == isWater ? null : isWater ? FARMING : AQUACULTURE;
    }

    /**
     * A world that is not water and loses all its farmland loses vanilla Farming, which then makes no food.
     * Only Farming itself: AoTD's Monoculture Plots, Subsidised Farming and Artisanal Farming need farmland
     * to be built, but make food with none (a flat supply), so they stay.
     */
    public static boolean farmlandLost(boolean isWater, Farmland before, Farmland after) {
        return !isWater && before != Farmland.NONE && after == Farmland.NONE;
    }

    /**
     * The seed and every industry linked to it by an upgrade or a downgrade, in either direction, also through others
     * (AoTD: Farming downgrades to Monoculture Plots, Subsidised and Artisanal Farming downgrade to Farming).
     *
     * @param upgrade   industry id -> its upgrade id, or null
     * @param downgrade industry id -> its downgrade id, or null
     */
    public static Set<String> chain(String seed, Map<String, String> upgrade, Map<String, String> downgrade) {
        Set<String> out = new LinkedHashSet<>();
        out.add(seed);
        for (boolean grew = true; grew; ) {
            grew = false;
            for (Map<String, String> links : Arrays.asList(upgrade, downgrade)) {
                for (Map.Entry<String, String> e : links.entrySet()) {
                    if (e.getValue() == null) continue;
                    if (out.contains(e.getKey())) grew |= out.add(e.getValue());
                    if (out.contains(e.getValue())) grew |= out.add(e.getKey());
                }
            }
        }
        return out;
    }
}
