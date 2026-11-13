package dti.logic;

import java.util.*;

/**
 * The best plan for a planet: the fewest DTI steps to the lowest hazard, and with
 * the same hazard to the type with the best farmland. Each step
 * is a projects.csv row that the menu would offer, then the type ladder, and it lowers the hazard or changes
 * the type. For the Terraforming Survey and the planet file. A search by breadth, so the first plan that reaches a
 * state has the fewest steps.
 */
public final class Survey {
    public static final int MAX_STEPS = 8;
    /** A guard for modded data: the search stops here and keeps the best plan it found. */
    static final int MAX_STATES = 4000;

    private Survey() {}

    /** What the search asks the game. */
    public interface World {
        /** The rows the menu offers on a planet of this type with these conditions: at most one per structure. */
        List<? extends ProjectRow> offered(String type, Set<String> conditions);

        /** The ladder after a step that takes the conditions from before to after ({@link TypeLadder#change}). */
        TypeLadder.Result after(String type, Set<String> before, Set<String> after);

        /** A ladder marker (dti_hydrated): a row that adds one only changes the type. */
        boolean marker(String condition);

        /** The condition's hazard, for example 0.5 for +50%. */
        float hazard(String condition);

        /** The best farmland of the type (planet_types.csv): with the same hazard, the type with the better farmland wins. */
        Farmland farmlandBest(String type);
    }

    /** One step: the row, and the planet type after it. */
    public static final class Step {
        public final ProjectRow row;
        public final String type;

        Step(ProjectRow row, String type) {
            this.row = row;
            this.type = type;
        }
    }

    public static final class Plan {
        public final String type;
        public final Set<String> conditions;
        public final List<Step> steps;
        /** 1 for 100%. */
        public final float hazard;

        Plan(String type, Set<String> conditions, List<Step> steps, float hazard) {
            this.type = type;
            this.conditions = conditions;
            this.steps = steps;
            this.hazard = hazard;
        }
    }

    /** The hazard of a planet with these conditions: 100% plus each condition's own. */
    public static float hazard(Set<String> conditions, World world) {
        float h = 1f;
        for (String c : conditions) h += world.hazard(c);
        return h;
    }

    /** The plan to the lowest hazard, with the fewest steps; no steps when nothing lowers it. */
    public static Plan best(String type, Set<String> conditions, World world) {
        Plan start = new Plan(type, new LinkedHashSet<>(conditions), Collections.<Step>emptyList(), hazard(conditions, world));
        Plan best = start;
        Deque<Plan> queue = new ArrayDeque<>();
        queue.add(start);
        Set<String> seen = new HashSet<>();
        seen.add(key(type, conditions));
        while (!queue.isEmpty() && seen.size() < MAX_STATES) {
            Plan p = queue.poll();
            if (p.steps.size() >= MAX_STEPS) continue;
            for (ProjectRow row : world.offered(p.type, p.conditions)) {
                TypeLadder.Result r = world.after(p.type, p.conditions, row.after(p.conditions));
                boolean marker = !row.adds.isEmpty() && world.marker(row.adds);
                // The menu offers a marker row only when it changes the type; a marker no rule used goes at the end.
                if (marker && r.type.equals(p.type)) continue;
                Set<String> cs = new LinkedHashSet<>(r.conditions);
                if (marker) cs.remove(row.adds);
                float h = hazard(cs, world);
                // Only a step that lowers the hazard or changes the type: farmland, marine life and the extractors stay out.
                if (r.type.equals(p.type) && h >= p.hazard - 1e-4f) continue;
                if (!seen.add(key(r.type, cs))) continue;
                List<Step> steps = new ArrayList<>(p.steps);
                steps.add(new Step(row, r.type));
                Plan next = new Plan(r.type, cs, steps, h);
                if (better(next, best, world)) best = next;
                queue.add(next);
            }
        }
        return best;
    }

    /** Lower hazard; with the same hazard, a type with better farmland (Terran over Desert). */
    private static boolean better(Plan a, Plan b, World world) {
        if (Math.abs(a.hazard - b.hazard) > 1e-4f) return a.hazard < b.hazard;
        return world.farmlandBest(a.type).compareTo(world.farmlandBest(b.type)) > 0;
    }

    private static String key(String type, Set<String> conditions) {
        return type + new TreeSet<>(conditions);
    }
}
