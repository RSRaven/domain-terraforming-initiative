package dti.logic;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The support structures of milestone 4, the pure part: the domes and their three looks (docs/features/domed-cities.md),
 * the City Agrav Grid, the Impact Defense Grid, Hydroponic Farms, and the meteor rule (docs/features/impact-defense-grid.md).
 */
public final class Support {
    private Support() {}

    public static final String WATER = "water_surface", METEORS = "meteor_impacts", THIN = "thin_atmosphere", NO_AIR = "no_atmosphere";

    /** Who suppresses a condition (the by column of suppressions.csv): a look of the domes, or a grid. */
    public enum By {
        DOMED, SKY, SEAFLOOR, AGRAV, IMPACT;

        /** "domed", "sky", "seafloor", "agrav", "impact", in any case. Anything else throws. */
        public static By of(String cell) {
            String v = cell == null ? "" : cell.trim();
            for (By b : values()) {
                if (b.name().equalsIgnoreCase(v)) return b;
            }
            throw new IllegalArgumentException("unknown suppressor '" + v + "'");
        }

        /** The City Agrav Grid works only while the domes stand. */
        public boolean needsDomes() {
            return this == AGRAV;
        }
    }

    /** The look of the domes: Sky Cities on a gas giant, Seafloor Cities on a world with a water surface, else Domed Cities. */
    public static By look(boolean gasGiant, boolean water) {
        return gasGiant ? By.SKY : water ? By.SEAFLOOR : By.DOMED;
    }

    /**
     * The rows of suppressions.csv (condition, by) as one list per suppressor. A bad row goes to onBad and is skipped:
     * an unknown suppressor, or the water surface, which aquaculture, marine life and the type ladder need.
     */
    public static Map<By, Set<String>> lists(List<Map<String, String>> rows, BiConsumer<Map<String, String>, RuntimeException> onBad) {
        Map<By, Set<String>> out = new EnumMap<>(By.class);
        for (By b : By.values()) out.put(b, new LinkedHashSet<String>());
        for (Map<String, String> r : rows) {
            try {
                String condition = r.get("condition") == null ? "" : r.get("condition").trim();
                if (condition.equals(WATER)) throw new IllegalArgumentException("the water surface is never suppressed");
                for (String by : Cells.list(r.get("by"))) By.of(by);
                for (String by : Cells.list(r.get("by"))) out.get(By.of(by)).add(condition);
            } catch (RuntimeException e) {
                onBad.accept(r, e);
            }
        }
        return out;
    }

    /** What a suppressor takes away on this colony: the conditions of its list that the colony has. */
    public static Set<String> targets(Set<String> list, Set<String> conditions) {
        Set<String> out = new LinkedHashSet<>();
        for (String c : conditions) {
            if (list.contains(c)) out.add(c);
        }
        return out;
    }

    /** The build menu offers a suppressor when the colony has a condition of its list that nothing else suppresses. */
    public static boolean offered(Set<String> list, Set<String> conditions, Set<String> suppressed) {
        for (String c : targets(list, conditions)) {
            if (!suppressed.contains(c)) return true;
        }
        return false;
    }

    /** The meteor rule: when a DTI project ends, meteor impacts go for good if the world has neither thin nor no atmosphere. */
    public static boolean meteorsBurnUp(Set<String> conditions) {
        return conditions.contains(METEORS) && !conditions.contains(THIN) && !conditions.contains(NO_AIR);
    }

    /** Hydroponic Farms are offered only on a colony with no farmland and no water surface. */
    public static boolean hydroponicsFit(Set<String> conditions) {
        return Farmland.present(conditions) == Farmland.NONE && !conditions.contains(WATER);
    }

    /** The food of Hydroponic Farms: the colony size -2, never below 0. */
    public static int hydroponicFood(int size) {
        return Math.max(0, size - 2);
    }
}
