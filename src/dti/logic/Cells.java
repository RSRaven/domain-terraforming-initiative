package dti.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsers for the cells of DTI data files (docs/data-formats.md). */
public final class Cells {
    private Cells() {}

    /** "cold, very_cold" -> [cold, very_cold]. Blank -> empty. */
    public static List<String> list(String cell) {
        if (cell == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String part : cell.split(",")) {
            part = part.trim();
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    /** "heavy_machinery:3,metals:2" -> ordered map. Blank -> empty. Throws on a bad pair. */
    public static Map<String, Integer> goods(String cell) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String pair : list(cell)) {
            String[] p = pair.split(":");
            if (p.length != 2 || p[0].trim().isEmpty()) throw new IllegalArgumentException("bad goods '" + cell + "'");
            int amount;
            try {
                amount = Integer.parseInt(p[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad goods '" + cell + "'");
            }
            if (amount <= 0 || out.put(p[0].trim(), amount) != null) throw new IllegalArgumentException("bad goods '" + cell + "'");
        }
        return out;
    }

    /** Blank -> false, "TRUE" (any case) -> true. Anything else throws. */
    public static boolean flag(String cell) {
        String v = cell == null ? "" : cell.trim();
        if (v.isEmpty()) return false;
        if (v.equalsIgnoreCase("true")) return true;
        throw new IllegalArgumentException("bad flag '" + v + "'");
    }

    /** A goods amount times a multiplier: rounded, at least 1 while the multiplier is above 0. */
    public static int scaled(int amount, float mult) {
        if (mult <= 0) return 0;
        return Math.max(1, Math.round(amount * mult));
    }
}
