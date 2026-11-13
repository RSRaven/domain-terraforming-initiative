package dti.logic;

import dti.logic.ProjectClock.State;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rows of the "DTI Projects" Intel entry: which rows a filter shows and in what
 * order. Fed plain values, so it runs without the game.
 */
public final class Overview {
    private Overview() {}

    /**
     * The state filter. PROBLEMS: stopped, going back and waiting together. STOPPED and GOING_BACK lost their buttons in
     * 0.4.0 (the line shows the state); they stay only so a save that chose one still loads, and ProjectsIntel turns
     * them into PROBLEMS.
     */
    public enum StateFilter { ALL, RUNNING, STOPPED, GOING_BACK, PROBLEMS }

    /** The group filter: the content area. */
    public enum Group { ALL, TERRAFORMING, COLONY }

    /** END: closest to its end first. */
    public enum Sort { END, COLONY, SYSTEM }

    /** One running project. The UI fills the display fields; the filter and the sort read the final ones. */
    public static final class Row {
        public final String colony, system, structure;
        public final Area area;
        public final State state;
        /** Days left at the current speed, for the sort only; infinite when it does not move forward. */
        public final float daysLeft;
        public String marketId, industryId, work, shortGoods;
        public int percent;

        public Row(String colony, String system, String structure, Area area, State state, float daysLeft) {
            this.colony = colony;
            this.system = system;
            this.structure = structure;
            this.area = area;
            this.state = state;
            this.daysLeft = daysLeft;
        }
    }

    /**
     * Days of work left at the current speed ({@link ProjectClock#speed}: the AI core and the item count), or infinite
     * when the project does not move forward (stopped, going back, waiting, paused).
     */
    public static float daysLeft(float progress, float totalDays, State state, String coreId, float itemBonus, ProjectClock.Rules r) {
        float speed = ProjectClock.speed(state, coreId, itemBonus, r);
        return speed > 0f ? (totalDays - progress) / speed : Float.POSITIVE_INFINITY;
    }

    /** ALL shows every state, a paused one too. */
    public static boolean shows(StateFilter f, State s) {
        switch (f) {
            case RUNNING:
                return s == State.RUNNING;
            case PROBLEMS:
                return s == State.STOPPED || s == State.GOING_BACK || s == State.WAITING;
            default:
                return true;
        }
    }

    public static boolean shows(Group g, Area a) {
        return g == Group.ALL || g == Group.TERRAFORMING && a == Area.TERRAFORMING || g == Group.COLONY && a == Area.COLONY;
    }

    /** The line under the entry's name in the Intel list: "5 projects, 1 with problems", problems as {@link StateFilter#PROBLEMS}. */
    public static String summary(List<Row> rows) {
        if (rows.isEmpty()) return "No projects";
        int problems = 0;
        for (Row r : rows) if (shows(StateFilter.PROBLEMS, r.state)) problems++;
        String n = rows.size() == 1 ? "1 project" : rows.size() + " projects";
        return problems == 0 ? n : n + ", " + problems + " with problems";
    }

    /**
     * The only condition icons on a colony line: what a DTI building can work on. A
     * project's condition, what it adds and removes, and what a support building cancels.
     */
    public static Set<String> changeable(Collection<? extends ProjectRow> projects, Collection<Set<String>> cancelled) {
        Set<String> out = new HashSet<>();
        for (ProjectRow p : projects) {
            out.add(p.condition);
            out.add(p.adds);
            out.addAll(p.removes);
        }
        for (Set<String> s : cancelled) out.addAll(s);
        out.remove("");
        return out;
    }

    /**
     * The shown rows in boxes, one per colony: the colonies in the order of their first
     * row, each with its rows in order. Two colonies with one name stay apart: the key is the market id.
     */
    public static Map<String, List<Row>> byColony(List<Row> sorted) {
        Map<String, List<Row>> out = new LinkedHashMap<>();
        for (Row r : sorted) out.computeIfAbsent(r.marketId, k -> new ArrayList<>()).add(r);
        return out;
    }

    /** The rows the filters show, in the sort's order. Ties: by colony, then structure (by days left first for a name sort). */
    public static List<Row> view(List<Row> rows, StateFilter f, Group g, Sort s) {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (shows(f, r.state) && shows(g, r.area)) out.add(r);
        }
        Comparator<Row> end = (a, b) -> Float.compare(a.daysLeft, b.daysLeft);
        Comparator<Row> colony = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.colony, b.colony);
        Comparator<Row> structure = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.structure, b.structure);
        Comparator<Row> system = (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.system, b.system);
        if (s == Sort.COLONY) out.sort(colony.thenComparing(end).thenComparing(structure));
        else if (s == Sort.SYSTEM) out.sort(system.thenComparing(colony).thenComparing(end).thenComparing(structure));
        else out.sort(end.thenComparing(colony).thenComparing(structure));
        return out;
    }
}
