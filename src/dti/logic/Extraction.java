package dti.logic;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Deep Core Extractor and the Volatiles Extractor: the yield while one works,
 * and the fallout when it is removed before its deposit is gone.
 */
public final class Extraction {
    private Extraction() {}

    public static final String POLLUTION = "pollution", TOXIC = "toxic_atmosphere", NO_ATMOSPHERE = "no_atmosphere";
    /** Toxic atmosphere's one-per-planet group in vanilla's condition_gen_data.csv ("atmosphere"), besides no atmosphere: toxic air takes their place. */
    public static final List<String> OTHER_AIR = Arrays.asList("thin_atmosphere", "dense_atmosphere");

    /**
     * What a vanilla Mining building gets from a deposit (ResourceDepositsCondition): the colony size, plus the good's
     * base modifier, plus the level's modifier; never below 0. A Pristine Lithic Catalyst adds 50% ({@link Items#yield}),
     * rounded to the nearest unit, a half up.
     */
    public static int yield(int size, int baseModifier, int levelModifier, String itemId) {
        int base = Math.max(0, size + baseModifier + levelModifier);
        return Math.round(Items.yield(base, itemId));
    }

    /**
     * The removal of an extractor poisons the colony, in any step: it is not DTI's own end (its last
     * step took the deposit away, or it ended with nothing changed), the colony has the owner it had at the start, and the
     * deposit is still there.
     */
    public static boolean fallout(boolean ownEnd, boolean ownerChanged, boolean depositLeft) {
        return !ownEnd && !ownerChanged && depositLeft;
    }

    /**
     * The conditions after the fallout: pollution, and toxic atmosphere in place of thin or dense atmosphere. Toxic air
     * also blocks habitable in vanilla, so habitable goes, and mild climate with it ({@link TypeLadder#dropMildClimate}).
     * A world with no atmosphere gets pollution only. The type ladder runs after this.
     */
    public static Set<String> afterFallout(Set<String> conditions) {
        Set<String> out = new LinkedHashSet<>(conditions);
        out.add(POLLUTION);
        if (out.contains(NO_ATMOSPHERE)) return out;
        out.removeAll(OTHER_AIR);
        out.add(TOXIC);
        out.remove(TypeLadder.HABITABLE);
        TypeLadder.dropMildClimate(out);
        return out;
    }
}
