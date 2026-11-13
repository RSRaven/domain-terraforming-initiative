package dti.logic;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The planet type ladder: archetypes plus ordered rules.
 * Data comes from planet_types.csv and type_rules.csv; the game is asked through {@link Env}.
 */
public final class TypeLadder {
    public static final int MAX_STEPS = 10;

    /** One row of planet_types.csv. A blank archetype counts as blocked. */
    public static final class TypeRow {
        public final String type, archetype;
        public final boolean blocked;
        /** The farmland a planet of this type gets at least on arrival, and at most. */
        public final Farmland farmlandFloor, farmlandBest;

        public TypeRow(String type, String archetype, boolean blocked) {
            this(type, archetype, blocked, Farmland.NONE, Farmland.NONE);
        }

        public TypeRow(String type, String archetype, boolean blocked, Farmland farmlandFloor, Farmland farmlandBest) {
            this.type = type;
            this.archetype = archetype;
            this.blocked = blocked;
            this.farmlandFloor = farmlandFloor;
            this.farmlandBest = farmlandBest;
        }
    }

    /** One row of type_rules.csv. */
    public static final class Rule {
        public final String id;
        final int order;
        final Set<String> from, all, any, none;
        final String to, setting;
        final List<String> add, remove;

        public Rule(String id, int order, Collection<String> from, Collection<String> all, Collection<String> any,
                    Collection<String> none, String to, Collection<String> add, Collection<String> remove, String setting) {
            this.id = id;
            this.order = order;
            this.from = new HashSet<>(from);
            this.all = new HashSet<>(all);
            this.any = new HashSet<>(any);
            this.none = new HashSet<>(none);
            this.to = to;
            this.add = new ArrayList<>(add);
            this.remove = new ArrayList<>(remove);
            this.setting = setting == null || setting.trim().isEmpty() ? null : setting.trim();
        }
    }

    /** What the ladder asks the game. */
    public static final class Env {
        final Predicate<String> loaded, settingOn, protectedCondition;
        final Function<String, String> categoryOf;
        final boolean blockUnknownTypes;

        /**
         * @param loaded             the planet type has generation data
         * @param categoryOf         the planet type's game category, or null
         * @param settingOn          a DTI boolean setting is on
         * @param protectedCondition DTI never removes this condition
         */
        public Env(Predicate<String> loaded, Function<String, String> categoryOf, Predicate<String> settingOn,
                   Predicate<String> protectedCondition, boolean blockUnknownTypes) {
            this.loaded = loaded;
            this.categoryOf = categoryOf;
            this.settingOn = settingOn;
            this.protectedCondition = protectedCondition;
            this.blockUnknownTypes = blockUnknownTypes;
        }
    }

    public static final class Result {
        public final String type;
        public final Set<String> conditions;
        /** Ids of the rules that fired, in order. */
        public final List<String> rules;
        /** True when the run stopped at {@link #MAX_STEPS} with a rule still matching. */
        public final boolean capped;

        Result(String type, Set<String> conditions, List<String> rules, boolean capped) {
            this.type = type;
            this.conditions = conditions;
            this.rules = rules;
            this.capped = capped;
        }
    }

    private final Map<String, TypeRow> types = new HashMap<>();
    private final List<Rule> rules;

    public TypeLadder(Collection<TypeRow> typeRows, Collection<Rule> ruleRows) {
        for (TypeRow row : typeRows) types.put(row.type, row);
        rules = new ArrayList<>(ruleRows);
        rules.sort(Comparator.comparingInt(r -> r.order));
    }

    /**
     * Builds the ladder from the rows of planet_types.csv and type_rules.csv (column name -> cell).
     * A planet_types.csv row with a bad farmland level, or a type_rules.csv row with a bad order, goes to {@code bad} and is skipped.
     */
    public static TypeLadder fromRows(Collection<Map<String, String>> typeRows, Collection<Map<String, String>> ruleRows,
                                      BiConsumer<Map<String, String>, RuntimeException> bad) {
        List<TypeRow> types = new ArrayList<>();
        for (Map<String, String> r : typeRows) {
            try {
                types.add(new TypeRow(r.get("type").trim(), cell(r, "archetype"), "true".equalsIgnoreCase(cell(r, "blocked")),
                        Farmland.of(r.get("farmlandFloor")), Farmland.of(r.get("farmlandBest"))));
            } catch (RuntimeException e) {
                bad.accept(r, e);
            }
        }
        List<Rule> rules = new ArrayList<>();
        for (Map<String, String> r : ruleRows) {
            try {
                rules.add(new Rule(r.get("id").trim(), Integer.parseInt(cell(r, "order")), Cells.list(r.get("from")),
                        Cells.list(r.get("all")), Cells.list(r.get("any")), Cells.list(r.get("none")), cell(r, "to"),
                        Cells.list(r.get("add")), Cells.list(r.get("remove")), r.get("setting")));
            } catch (RuntimeException e) {
                bad.accept(r, e);
            }
        }
        return new TypeLadder(types, rules);
    }

    private static String cell(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.trim();
    }

    /** The type's row, or its category's row, or null when the type is blocked. */
    public TypeRow row(String type, Env env) {
        TypeRow row = types.get(type);
        if (row == null && !env.blockUnknownTypes) {
            String category = env.categoryOf.apply(type);
            if (category != null) row = types.get(category);
        }
        if (row == null || row.blocked || row.archetype.isEmpty()) return null;
        return row;
    }

    /** Some row of planet_types.csv has this archetype. */
    public boolean hasArchetype(String archetype) {
        for (TypeRow r : types.values()) if (r.archetype.equals(archetype)) return true;
        return false;
    }

    /** The type's archetype, or null when the type is blocked. */
    public String archetype(String type, Env env) {
        TypeRow row = row(type, env);
        return row == null ? null : row.archetype;
    }

    /** Applies the first matching rule again and again, up to {@link #MAX_STEPS} times. */
    public Result run(String type, Set<String> conditions, Env env) {
        String current = type;
        Set<String> cs = new LinkedHashSet<>(conditions);
        List<String> fired = new ArrayList<>();
        for (int step = 0; ; step++) {
            Rule rule = firstMatch(current, cs, env);
            if (rule == null) return new Result(current, cs, fired, false);
            if (step == MAX_STEPS) return new Result(current, cs, fired, true);
            current = rule.to;
            cs.addAll(rule.add);
            for (String c : rule.remove) {
                if (!env.protectedCondition.test(c)) cs.remove(c);
            }
            if (!env.protectedCondition.test(MILD_CLIMATE)) dropMildClimate(cs);
            fired.add(rule.id);
        }
    }

    /**
     * The type change a project causes: run the ladder on the conditions from the project start and on the
     * conditions now. If both runs give the same type, nothing changes ("before and after").
     */
    public Result change(String type, Set<String> before, Set<String> after, Env env) {
        Result b = run(type, before, env);
        Result a = run(type, after, env);
        if (a.type.equals(b.type)) return new Result(type, new LinkedHashSet<>(after), Collections.<String>emptyList(), false);
        return a;
    }

    /**
     * A marker for the ladder (dti_hydrated, dti_dried): some rule both reads it in its all column and removes it. A
     * project that adds a marker never leaves it: the rule that uses it removes it, and the project removes it when no
     * rule did. A real condition that rules only read (cold) is never a marker.
     */
    public boolean marker(String condition) {
        for (Rule r : rules) {
            if (r.all.contains(condition) && r.remove.contains(condition)) return true;
        }
        return false;
    }

    public static final String HABITABLE = "habitable", MILD_CLIMATE = "mild_climate";

    /**
     * Vanilla allows mild climate only with habitable (condition_gen_data.csv, requiresAll): when habitable goes, by a
     * ladder rule or by the extraction fallout ({@link Extraction#afterFallout}), mild climate goes too.
     */
    public static void dropMildClimate(Set<String> conditions) {
        if (!conditions.contains(HABITABLE)) conditions.remove(MILD_CLIMATE);
    }

    /**
     * Vanilla gives "habitable" to every planet of a habitable category that has none of its blockers
     * (condition_gen_data: the conditions whose requiresNotAny holds habitable). True when the planet lacks it,
     * for example after the console or another mod took it away together with a blocker that a project removed.
     */
    public static boolean missingHabitable(boolean habitableCategory, Set<String> conditions, Set<String> blockers) {
        return habitableCategory && !conditions.contains("habitable") && Collections.disjoint(conditions, blockers);
    }

    private Rule firstMatch(String type, Set<String> cs, Env env) {
        String arch = archetype(type, env);
        if (arch == null) return null;
        for (Rule r : rules) {
            if (!r.from.contains(arch) || !cs.containsAll(r.all)) continue;
            if (!r.any.isEmpty() && Collections.disjoint(r.any, cs)) continue;
            if (!Collections.disjoint(r.none, cs)) continue;
            if (r.setting != null && !env.settingOn.test(r.setting)) continue;
            if (!env.loaded.test(r.to) || arch.equals(archetype(r.to, env))) continue;
            return r;
        }
        return null;
    }
}
