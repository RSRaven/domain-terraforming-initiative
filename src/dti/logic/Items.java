package dti.logic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The DTI special items (docs/features/special-items.md): 12 families in two tiers, ids
 * {@code dti_item_<family>_worn} and {@code dti_item_<family>_pristine}. Worn gives +25% speed only; Pristine gives
 * +50% speed and its second effect: 50% fewer basic goods, or for the deposits family 50% more yield (2026-10-26).
 */
public final class Items {
    private Items() {}

    public static final String PREFIX = "dti_item_", WORN = "_worn", PRISTINE = "_pristine";
    /** The Lithic Catalyst: more yield instead of fewer goods (decided 2026-10-25). */
    public static final String YIELD_FAMILY = "lithic_catalyst";

    public static String id(String family, boolean pristine) {
        return PREFIX + family + (pristine ? PRISTINE : WORN);
    }

    /** The family slug of a DTI item id; null for any other id, and for null. */
    public static String family(String itemId) {
        if (itemId == null || !itemId.startsWith(PREFIX)) return null;
        String rest = itemId.substring(PREFIX.length());
        for (String tier : new String[]{WORN, PRISTINE}) {
            if (rest.endsWith(tier) && rest.length() > tier.length()) return rest.substring(0, rest.length() - tier.length());
        }
        return null;
    }

    public static boolean pristine(String itemId) {
        return family(itemId) != null && itemId.endsWith(PRISTINE);
    }

    /** The speed an item adds: Worn 0.25, Pristine 0.5; 0 for no item or another mod's item. */
    public static float bonus(String itemId) {
        if (family(itemId) == null) return 0f;
        return pristine(itemId) ? 0.5f : 0.25f;
    }

    /** The deposits family gives more yield instead of fewer goods. */
    public static boolean yields(String itemId) {
        return YIELD_FAMILY.equals(family(itemId));
    }

    /** The multiplier for the project's basic goods (the goods column, not aotdGoods): Pristine 0.5, else 1. */
    public static float goodsMult(String itemId) {
        return pristine(itemId) && !yields(itemId) ? 0.5f : 1f;
    }

    /** A structure's yield with this item: +50% for a Pristine item of the deposits family, else the same. */
    public static float yield(float base, String itemId) {
        return pristine(itemId) && yields(itemId) ? base * 1.5f : base;
    }

    /**
     * The item after its project or its repeat-mode step ends (decided 2026-10-25): with the work done, a Pristine item
     * becomes Worn and a Worn one is used up (null). With nothing changed it comes back as it was. Another mod's item
     * never wears.
     */
    public static String afterEnd(String itemId, boolean workDone) {
        String family = family(itemId);
        if (!workDone || family == null) return itemId;
        return pristine(itemId) ? id(family, false) : null;
    }

    /**
     * The item column of structures.csv: blank, or a family whose two tiers exist ({@code known} tells an item id the
     * game knows). Null for an unknown family: the caller warns and ignores it.
     */
    public static String column(String cell, Predicate<String> known) {
        if (cell.isEmpty()) return "";
        return known.test(id(cell, false)) && known.test(id(cell, true)) ? cell : null;
    }

    /**
     * The AoTD workshop (special-items.md, Sources): a tech teaches both tiers of the item family of every structure it
     * unlocks. Adds one structures.csv row, its tech and item cells, to the map tech -> item ids; a blank cell adds nothing.
     */
    public static void addTaught(Map<String, Set<String>> byTech, String tech, String family) {
        if (tech.isEmpty() || family.isEmpty()) return;
        Set<String> ids = byTech.computeIfAbsent(tech, t -> new LinkedHashSet<>());
        ids.add(id(family, false));
        ids.add(id(family, true));
    }

    /**
     * research-gating.md (2026-11-06): a tier 2 tech needs a Worn item of its structure for research, so every tech of
     * that structure's line (the same setting) teaches that item too, and the workshop can make it first. Today this adds
     * only the Fusion Ignition Core to Solar Concentration. Rows: tech, tier, setting, item family.
     */
    public static void addLineTaught(Map<String, Set<String>> byTech, List<String[]> rows) {
        for (String[] two : rows) {
            if (!"2".equals(two[1])) continue;
            for (String[] r : rows) {
                if (r[2].equals(two[2])) addTaught(byTech, r[0], two[3]);
            }
        }
    }
}
