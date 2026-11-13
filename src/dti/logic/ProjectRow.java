package dti.logic;

import java.util.*;

/**
 * The pure part of a projects.csv row: what it works on, where it fits, and what it changes.
 * DtiData.Project adds the goods, which need the game to check.
 */
public class ProjectRow {
    /** A structure in repeat mode stops after this many steps, so rows that undo each other cannot loop. */
    public static final int MAX_STEPS = 10;

    public final String id, structure;
    /** Removed at the end; blank when the row only adds. */
    public final String condition;
    /** Added at the end; blank when the row only removes. */
    public final String adds;
    /** Also removed at the end, those the planet has; the row does not need them (the Atmospheric Siphon). */
    public final List<String> removes;
    /** Archetypes it runs on; empty = any. */
    public final Set<String> types;
    /** Conditions the planet must have. */
    public final List<String> needs;
    /** Conditions that hide the row, and hold a running project. */
    public final List<String> blockers;
    /** The cost mult of this step, or null for the structure's own from industries.csv. */
    public final Float cost;
    public final float days;
    public final Light.Star star;

    /** Parses a row (column name -> cell; a missing column reads blank). Throws on a bad cell. */
    public ProjectRow(Map<String, String> r) {
        id = cell(r, "id");
        structure = cell(r, "structure");
        condition = cell(r, "condition");
        adds = cell(r, "adds");
        if (condition.isEmpty() && adds.isEmpty()) throw new IllegalArgumentException("no condition and no adds");
        removes = Cells.list(r.get("removes"));
        types = new LinkedHashSet<>(Cells.list(r.get("types")));
        needs = Cells.list(r.get("needs"));
        blockers = Cells.list(r.get("blockers"));
        String c = cell(r, "cost");
        cost = c.isEmpty() ? null : Float.parseFloat(c);
        if (cost != null && !(cost > 0)) throw new IllegalArgumentException("bad cost '" + c + "'");
        star = Light.Star.of(r.get("star"));
        days = cell(r, "days").isEmpty() ? 0f : Float.parseFloat(cell(r, "days"));
    }

    private static String cell(Map<String, String> r, String column) {
        String v = r.get(column);
        return v == null ? "" : v.trim();
    }

    /**
     * The ids in {@code needs}, {@code blockers} and {@code removes} that are no known condition, and the archetypes in
     * {@code types} that no planet type has, as "needs x", "blockers x", "removes x", "types x": likely typos. The row
     * still loads; DtiData warns.
     */
    public List<String> unknownIds(java.util.function.Predicate<String> condition, java.util.function.Predicate<String> archetype) {
        List<String> out = new ArrayList<>();
        for (String c : needs) if (!condition.test(c)) out.add("needs " + c);
        for (String c : blockers) if (!condition.test(c)) out.add("blockers " + c);
        for (String c : removes) if (!condition.test(c)) out.add("removes " + c);
        for (String t : types) if (!archetype.test(t)) out.add("types " + t);
        return out;
    }

    /** The condition the row works on, for "one project per condition": its condition, else the one it adds. */
    public String subject() {
        return condition.isEmpty() ? adds : condition;
    }

    /**
     * This row and another cannot run on one colony at the same time: they work on the same condition (one project per
     * condition), one removes the condition the other works on (one atmosphere project at a time: the Atmospheric Siphon
     * and the Accretor, Scrubber and Vent), or both add a ladder marker (one type change at a time: the Comet Shepherd and
     * the Hydrosphere Extractor).
     */
    public boolean clashes(ProjectRow other, java.util.function.Predicate<String> marker) {
        return subject().equals(other.subject()) || removesSubjectOf(other) || other.removesSubjectOf(this)
                || !adds.isEmpty() && !other.adds.isEmpty() && marker.test(adds) && marker.test(other.adds);
    }

    /** Its {@code removes} column takes away the condition the other row works on. */
    public boolean removesSubjectOf(ProjectRow other) {
        return removes.contains(other.subject());
    }

    /** No row removes a condition: the structure only adds (the Climate Harmonizer, the water structures). */
    public static boolean onlyAdds(List<? extends ProjectRow> rows) {
        for (ProjectRow r : rows) {
            if (!r.condition.isEmpty()) return false;
        }
        return true;
    }

    /**
     * The conditions after this row ends: its condition and its removes removed, its adds added. Its blockers are gone
     * too: a row waits until they are, so it never ends with one.
     */
    public Set<String> after(Set<String> conditions) {
        Set<String> out = new LinkedHashSet<>(conditions);
        if (!condition.isEmpty()) out.remove(condition);
        out.removeAll(removes);
        out.removeAll(blockers);
        if (!adds.isEmpty()) out.add(adds);
        return out;
    }

    /** Why a row does not fit, with the condition or archetype it is about. */
    public static final class Misfit {
        public enum Kind {
            /** Its condition is gone (or suppressed, in the menu). */
            CONDITION_GONE,
            /** The planet already has what it adds. */
            ADDS_PRESENT,
            /** A condition it needs is gone. */
            NEEDS_GONE,
            /** The planet's archetype is not in its types (what = the archetype, or blank for a blocked type). */
            TYPE,
            /** It adds farmland above the best level of the planet's type, with mild climate one more (what = that best level's condition, or blank for none). */
            FARMLAND_BEST,
            /** A blocker is there: hidden in the menu; a running project waits. */
            BLOCKED
        }

        public final Kind kind;
        public final String what;

        Misfit(Kind kind, String what) {
            this.kind = kind;
            this.what = what;
        }
    }

    /**
     * Null when the row fits a planet with these conditions and this archetype (null for a blocked type), else why
     * not. A blocker comes last, so BLOCKED means everything else fits.
     * <p>
     * Levels ({@link #LEVELS}): a row that adds a farmland or a marine life level does not fit a planet that has that
     * level or a better one. Farmland: nor a planet whose type cannot have it ({@link Farmland#top}).
     *
     * @param suppressed   conditions that count as gone for the row's own condition (the menu); empty for a running project
     * @param farmlandBest the best farmland of the planet's type (planet_types.csv), before mild climate
     */
    public Misfit misfit(Set<String> conditions, Set<String> suppressed, String archetype, Farmland farmlandBest) {
        if (!condition.isEmpty() && (!conditions.contains(condition) || suppressed.contains(condition))) {
            return new Misfit(Misfit.Kind.CONDITION_GONE, condition);
        }
        if (!adds.isEmpty() && conditions.contains(adds)) return new Misfit(Misfit.Kind.ADDS_PRESENT, adds);
        String better = better(adds, conditions);
        if (better != null) return new Misfit(Misfit.Kind.ADDS_PRESENT, better);
        Farmland level = Farmland.ofCondition(adds);
        for (String n : needs) {
            if (!conditions.contains(n)) return new Misfit(Misfit.Kind.NEEDS_GONE, n);
        }
        if (!types.isEmpty() && (archetype == null || !types.contains(archetype))) {
            return new Misfit(Misfit.Kind.TYPE, archetype == null ? "" : archetype);
        }
        Farmland top = Farmland.top(farmlandBest, conditions.contains("mild_climate"));
        if (level != null && level.compareTo(top) > 0) return new Misfit(Misfit.Kind.FARMLAND_BEST, top == Farmland.NONE ? "" : top.condition());
        for (String b : blockers) {
            if (conditions.contains(b)) return new Misfit(Misfit.Kind.BLOCKED, b);
        }
        return null;
    }

    /** Conditions in ordered levels, worst to best, by the name of their message line: farmland, marine life. */
    static final Map<String, List<String>> LEVELS = new LinkedHashMap<>();

    static {
        LEVELS.put("Farmland", Arrays.asList(Farmland.POOR.condition(), Farmland.ADEQUATE.condition(), Farmland.RICH.condition(), Farmland.BOUNTIFUL.condition()));
        LEVELS.put("Marine life", FoodBuildings.MARINE);
    }

    /** True for a condition of one of the {@link #LEVELS}: an end message gives it a level line, not a "removed" or "added" line. */
    public static boolean level(String condition) {
        for (List<String> levels : LEVELS.values()) {
            if (levels.contains(condition)) return true;
        }
        return false;
    }

    /**
     * The end message lines, one for each of the {@link #LEVELS} that changed: "Farmland: Rich -> Poor",
     * "Marine life: Sparse added", "Marine life: Thriving removed".
     */
    public static List<String> levelLines(Set<String> before, Set<String> after) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : LEVELS.entrySet()) {
            String b = best(e.getValue(), before), a = best(e.getValue(), after);
            if (Objects.equals(a, b)) continue;
            String line = e.getKey() + ": ";
            if (a == null) out.add(line + label(b) + " removed");
            else out.add(line + (b == null ? label(a) + " added" : label(b) + " -> " + label(a)));
        }
        return out;
    }

    /** The best condition of these levels that the planet has, or null. */
    private static String best(List<String> levels, Set<String> conditions) {
        for (int i = levels.size() - 1; i >= 0; i--) {
            if (conditions.contains(levels.get(i))) return levels.get(i);
        }
        return null;
    }

    /** "farmland_poor" -> "Poor", "dti_marine_sparse" -> "Sparse". */
    private static String label(String condition) {
        String w = condition.substring(condition.lastIndexOf('_') + 1);
        return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    }

    /** The best condition the planet has of a level above the one this adds ({@link #LEVELS}), or null: a level never goes down. */
    static String better(String adds, Set<String> conditions) {
        for (List<String> levels : LEVELS.values()) {
            int at = levels.indexOf(adds);
            if (at < 0) continue;
            for (int i = levels.size() - 1; i > at; i--) {
                if (conditions.contains(levels.get(i))) return levels.get(i);
            }
        }
        return null;
    }

    /** The first row that fits, in row order, or null. */
    public static <R extends ProjectRow> R first(List<R> rows, Set<String> conditions, Set<String> suppressed, String archetype, Farmland farmlandBest) {
        for (R r : rows) {
            if (r.misfit(conditions, suppressed, archetype, farmlandBest) == null) return r;
        }
        return null;
    }

    /**
     * The row a building starts when its build is done, or the next step in repeat mode: the first row that fits, else the
     * first that only a blocker holds (the project starts and waits for the blocker to go), else null.
     */
    public static <R extends ProjectRow> R firstOrBlocked(List<R> rows, Set<String> conditions, Set<String> suppressed, String archetype, Farmland farmlandBest) {
        R fits = first(rows, conditions, suppressed, archetype, farmlandBest);
        if (fits != null) return fits;
        for (R r : rows) {
            Misfit m = r.misfit(conditions, suppressed, archetype, farmlandBest);
            if (m.kind == Misfit.Kind.BLOCKED) return r;
        }
        return null;
    }

    /**
     * Repeat mode: the row a structure starts when its step ends, done or no longer fitting
     * ({@link #firstOrBlocked}), or null: no step is left, or this was step {@link #MAX_STEPS}, and it removes itself.
     */
    public static <R extends ProjectRow> R next(List<R> rows, int step, Set<String> conditions, Set<String> suppressed, String archetype, Farmland farmlandBest) {
        return step < MAX_STEPS ? firstOrBlocked(rows, conditions, suppressed, archetype, farmlandBest) : null;
    }

    /**
     * The steps a structure in repeat mode does, in order: the first row that fits, then the first that fits the
     * conditions after it, and so on, up to {@link #MAX_STEPS}. A step that only a blocker holds counts: it starts and
     * waits ({@link #firstOrBlocked}). The archetype stays the same.
     */
    // ponytail: no type ladder between steps; the 2b repeat structures (Soil Conditioner, Reef Seeder) change no type.
    public static <R extends ProjectRow> List<R> chain(List<R> rows, Set<String> conditions, Set<String> suppressed, String archetype, Farmland farmlandBest) {
        List<R> out = new ArrayList<>();
        Set<String> cs = new LinkedHashSet<>(conditions);
        for (R r = firstOrBlocked(rows, cs, suppressed, archetype, farmlandBest); r != null && out.size() < MAX_STEPS; r = firstOrBlocked(rows, cs, suppressed, archetype, farmlandBest)) {
            out.add(r);
            cs = r.after(cs);
        }
        return out;
    }
}
