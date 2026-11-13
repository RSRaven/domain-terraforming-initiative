package dti.logic;

import java.util.Set;

/** A farmland level, for the farmlandFloor and farmlandBest columns of planet_types.csv. */
public enum Farmland {
    NONE, POOR, ADEQUATE, RICH, BOUNTIFUL;

    /** Blank or "none", "poor", "adequate", "rich", "bountiful", in any case. Anything else throws. */
    public static Farmland of(String cell) {
        String v = cell == null ? "" : cell.trim();
        if (v.isEmpty()) return NONE;
        for (Farmland f : values()) {
            if (f.name().equalsIgnoreCase(v)) return f;
        }
        throw new IllegalArgumentException("unknown farmland level '" + v + "'");
    }

    /** The vanilla condition id (farmland_poor ...), or null for none. */
    public String condition() {
        return this == NONE ? null : "farmland_" + name().toLowerCase();
    }

    /** The level of a farmland condition id, or null for any other condition. */
    public static Farmland ofCondition(String id) {
        for (Farmland f : values()) {
            if (f != NONE && f.condition().equals(id)) return f;
        }
        return null;
    }

    /** The planet's farmland: its best farmland condition, or none. */
    public static Farmland present(Set<String> conditions) {
        Farmland out = NONE;
        for (Farmland f : values()) {
            if (conditions.contains(f.condition())) out = f;
        }
        return out;
    }

    /**
     * The farmland after the ladder changes the planet type: at
     * least the new type's floor, at most its best level. Mild climate raises a best level that is not none by one step,
     * up to bountiful; it never raises the floor. A drying step (the ladder used dti_dried) has no floor: the farmland
     * stays, at most the best level. From a water world, the caller passes {@link #fromMarine} as {@code now}.
     */
    public static Farmland afterTypeChange(Farmland now, Farmland floor, Farmland best, boolean mildClimate, boolean dried) {
        Farmland top = top(best, mildClimate);
        Farmland f = !dried && now.compareTo(floor) < 0 ? floor : now;
        return f.compareTo(top) > 0 ? top : f;
    }

    /**
     * The soil a drained sea leaves: the marine life's food bonus becomes the farmland's.
     * No marine life (+0) gives adequate, sparse (+1) rich, thriving (+2) bountiful; abundant (+3) also bountiful, the best there is.
     */
    public static Farmland fromMarine(int food) {
        return values()[Math.min(ADEQUATE.ordinal() + food, BOUNTIFUL.ordinal())];
    }

    /** The best farmland a planet can have: its type's best level, one step more with mild climate (never up from none). */
    public static Farmland top(Farmland best, boolean mildClimate) {
        return mildClimate && best != NONE && best != BOUNTIFUL ? values()[best.ordinal() + 1] : best;
    }
}
