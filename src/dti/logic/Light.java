package dti.logic;

import java.util.Collection;

/**
 * Light for the Solar Concentrator and the Stellar Mirrors (docs/features/hazard-set.md). Pure: the caller passes plain
 * values from the system.
 */
public final class Light {
    private Light() {}

    /**
     * The system has a real star to focus: not a nebula (the game keeps a hidden star there as the centre), not deep
     * space, and one of its stars is not a black hole. A black hole gives no light to focus; a neutron star does.
     *
     * @param blackHoles one entry per star the system has (primary, secondary, tertiary): true for a black hole
     */
    public static boolean realStar(boolean nebula, boolean deepSpace, Collection<Boolean> blackHoles) {
        return !nebula && !deepSpace && blackHoles.contains(false);
    }

    /** The star column of projects.csv (ADR 0013): where a project row is offered. */
    public enum Star {
        /** Blank: any system. */
        ANY,
        /** "real": only where the system has a real star. */
        REAL,
        /** "none": only where it has no real star. */
        NONE;

        /** Blank, "real" or "none". Anything else throws. */
        public static Star of(String cell) {
            String v = cell == null ? "" : cell.trim();
            if (v.isEmpty()) return ANY;
            if (v.equals("real")) return REAL;
            if (v.equals("none")) return NONE;
            throw new IllegalArgumentException("unknown star '" + v + "'");
        }
    }

    /** The build menu offers a project row with this star rule in a system with or without a real star. */
    public static boolean offers(Star star, boolean realStar) {
        return star == Star.ANY || (star == Star.REAL) == realStar;
    }
}
