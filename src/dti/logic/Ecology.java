package dti.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Ecology events during long projects: the rows of ecology_events.csv and the timing. */
public final class Ecology {
    /** Running days to a project's first event, and from one event to the next: at most one a year. */
    public static final float FIRST_MIN = 120f, FIRST_SPAN = 245f, NEXT_MIN = 365f, NEXT_SPAN = 180f;
    /** With no answer in this many days, the default choice applies. */
    public static final float ANSWER_DAYS = 30f;
    /**
     * Experience: after this many finished projects of a structure in a save, its
     * events are good twice as often, disasters half as often, and every credit cost is 25% lower. With or without AoTD.
     */
    public static final int EXPERIENCE = 3;
    public static final float EXPERIENCED_GOOD = 2f, EXPERIENCED_DISASTER = 0.5f, EXPERIENCED_COST = 0.75f;

    /** The sector memory key that counts the finished projects of a structure (ProjectStructure.finishProject). */
    public static String doneKey(String structureId) {
        return "$dti_done_" + structureId;
    }

    public static boolean experienced(int finished) {
        return finished >= EXPERIENCE;
    }

    public enum Kind {
        GOOD, BAD, DISASTER;

        static Kind of(String cell) {
            return valueOf(cell.trim().toUpperCase(Locale.ROOT));
        }
    }

    private Ecology() {
    }

    /** A choice: progress in percent of the project's days (signed), the credits it costs, recent unrest points. */
    public static final class Choice {
        public final String label;
        public final int progress, credits, unrest;

        Choice(String label, String progress, String credits, String unrest, boolean needsLabel) {
            this.label = label;
            this.progress = number(progress);
            this.credits = number(credits);
            this.unrest = number(unrest);
            if (needsLabel && label.isEmpty() || this.credits < 0 || this.unrest < 0) throw new IllegalArgumentException("bad choice '" + label + "'");
        }

        /** The credits it costs; less with the line's research. */
        public int cost(boolean experienced) {
            return experienced ? Math.round(credits * EXPERIENCED_COST / 1000f) * 1000 : credits;
        }

        /** For a button, short: "100,000 cr, +10% progress"; "-5% progress"; "-1 stability". */
        public String buttonEffects(boolean experienced) {
            List<String> parts = new ArrayList<>();
            if (cost(experienced) > 0) parts.add(String.format(Locale.US, "%,d cr", cost(experienced)));
            if (progress != 0) parts.add((progress > 0 ? "+" : "") + progress + "% progress");
            if (unrest > 0) parts.add("-" + unrest + " stability");
            return parts.isEmpty() ? "no change" : String.join(", ", parts);
        }

        /** "15,000 credits, progress +10%"; "stability -1 as recent unrest"; "no change". */
        public String effects(boolean experienced) {
            StringBuilder s = new StringBuilder();
            int cost = cost(experienced);
            if (cost > 0) s.append(String.format(Locale.US, "%,d credits", cost));
            if (progress != 0) s.append(s.length() > 0 ? ", " : "").append("progress ").append(progress > 0 ? "+" : "").append(progress).append('%');
            if (unrest > 0) s.append(s.length() > 0 ? ", " : "").append("stability -").append(unrest).append(" as recent unrest");
            return s.length() == 0 ? "no change" : s.toString();
        }

        private static int number(String cell) {
            return cell == null || cell.trim().isEmpty() ? 0 : Integer.parseInt(cell.trim());
        }
    }

    /** A row: the structures it fits (blank: every project), its weight in the draw, and one or two choices. */
    public static final class Event {
        public final String id, name, text;
        public final Kind kind;
        public final List<String> structures;
        public final int weight;
        public final Choice a, b;

        public Event(Map<String, String> r) {
            id = r.get("id");
            name = r.get("name");
            text = r.get("text");
            kind = Kind.of(r.get("kind"));
            structures = Cells.list(r.get("structures"));
            weight = Integer.parseInt(r.get("weight").trim());
            b = r.get("b").trim().isEmpty() ? null : new Choice(r.get("b"), r.get("bProgress"), r.get("bCredits"), r.get("bUnrest"), true);
            // One choice: the event just happens, with no button (EcologyIntel.happen), so it needs no button text.
            a = new Choice(r.get("a"), r.get("aProgress"), r.get("aCredits"), r.get("aUnrest"), b != null);
            if (id.isEmpty() || name.isEmpty() || text.isEmpty() || weight <= 0) throw new IllegalArgumentException("bad event '" + id + "'");
        }

        /** Its weight in the draw, with or without the line's research. */
        public float weight(boolean experienced) {
            if (!experienced || kind == Kind.BAD) return weight;
            return weight * (kind == Kind.GOOD ? EXPERIENCED_GOOD : EXPERIENCED_DISASTER);
        }

        public boolean fits(String structureId) {
            return structures.isEmpty() || structures.contains(structureId);
        }

        /** With no answer: the second choice, or the only one. */
        public Choice fallback() {
            return b != null ? b : a;
        }
    }

    public static float firstGap(Random r) {
        return FIRST_MIN + r.nextFloat() * FIRST_SPAN;
    }

    public static float nextGap(Random r) {
        return NEXT_MIN + r.nextFloat() * NEXT_SPAN;
    }

    /** A random event that fits the structure, by weight (with or without the line's research); null when none fits. */
    public static Event pick(List<Event> events, String structureId, boolean experienced, Random r) {
        float total = 0f;
        Event last = null;
        for (Event e : events) {
            if (!e.fits(structureId)) continue;
            total += e.weight(experienced);
            last = e;
        }
        if (last == null) return null;
        float roll = r.nextFloat() * total;
        for (Event e : events) {
            if (!e.fits(structureId)) continue;
            roll -= e.weight(experienced);
            if (roll < 0f) return e;
        }
        // Rounding: the last one that fits.
        return last;
    }
}
