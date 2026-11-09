package dti.logic;

import java.util.Collection;

/** Is a building a pollution source (docs/features/hazard-set.md)? Pure: the caller passes plain values from the colony. */
public final class Pollution {
    private Pollution() {}

    /**
     * Vanilla Heavy Industry adds pollution with any special item on a habitable world; AoTD's heavy industries skip
     * items tagged no_pollution (the Tenebrian Nanoforge).
     *
     * @param habitable     the colony has the habitable condition (vanilla checks only that)
     * @param heavyIndustry the building's plugin is vanilla HeavyIndustry or a subclass (Orbital Works, AoTD's heavy industries)
     * @param itemTags      the tags of the building's special item; null when it has none
     */
    public static boolean source(boolean habitable, boolean heavyIndustry, Collection<String> itemTags) {
        return habitable && heavyIndustry && itemTags != null && !itemTags.contains("no_pollution");
    }
}
