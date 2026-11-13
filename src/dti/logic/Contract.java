package dti.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numbers of an Eridani-Utopia contract. They grow
 * with the project's base days (projects.csv days: 300 to 750): the cargo from 5,000 to 15,000 units, the premium on top
 * of the goods' value from 100,000 to 250,000 credits. The work takes half the project's days.
 */
public final class Contract {
    public static final float SHORT = 300f, LONG = 750f;
    public static final int MIN_CARGO = 5000, MAX_CARGO = 15000, MIN_PREMIUM = 100000, MAX_PREMIUM = 250000;
    /** Each good's amount is a multiple of this. */
    public static final int STEP = 50;

    private Contract() {
    }

    /** 0 for a 300-day project, 1 for a 750-day one; shorter or longer stay at the ends. */
    private static float share(float baseDays) {
        return Math.max(0f, Math.min(1f, (baseDays - SHORT) / (LONG - SHORT)));
    }

    public static int cargo(float baseDays) {
        return Math.round(MIN_CARGO + share(baseDays) * (MAX_CARGO - MIN_CARGO));
    }

    public static int premium(float baseDays) {
        return Math.round((MIN_PREMIUM + share(baseDays) * (MAX_PREMIUM - MIN_PREMIUM)) / 1000f) * 1000;
    }

    /**
     * The reward: the goods' base value plus the premium, as a round number (912k becomes 900k, 1572k
     * becomes 1500k): down to 50,000 below a million, down to 100,000 from a million.
     */
    public static int reward(float goodsValue, int premium) {
        float total = goodsValue + premium;
        int step = total < 1000000f ? 50000 : 100000;
        return (int) (Math.floor(total / step) * step);
    }

    /** The cargo split over the goods by their monthly demand, each rounded to 50 and at least 50. */
    public static Map<String, Integer> goods(Map<String, Integer> demand, float baseDays) {
        int units = 0;
        for (int u : demand.values()) units += u;
        Map<String, Integer> out = new LinkedHashMap<>();
        if (units == 0) return out;
        float perUnit = cargo(baseDays) / (float) units;
        for (Map.Entry<String, Integer> e : demand.entrySet()) out.put(e.getKey(), Math.max(STEP, Math.round(perUnit * e.getValue() / STEP) * STEP));
        return out;
    }

    /** The work after the delivery: half the project's days (with the LunaLib time multiplier in them), at least 1. */
    public static int workDays(float projectDays) {
        return Math.max(1, Math.round(projectDays / 2f));
    }
}
