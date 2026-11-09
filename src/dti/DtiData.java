package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import dti.logic.Area;
import dti.logic.Cells;
import dti.logic.Items;
import dti.logic.Overview;
import dti.logic.ProjectRow;
import dti.logic.Support;
import dti.logic.TypeLadder;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/** The DTI data files (docs/data-formats.md), loaded once at game start, merged with other mods' rows. */
public final class DtiData {
    private DtiData() {}

    public static final String MOD_ID = "domain_terraforming";
    public static final String CONTROL = "dti_terraforming_control";
    private static final String DIR = "data/config/dti/";
    private static final Logger log = Global.getLogger(DtiData.class);

    /** A row of structures.csv. */
    public static final class Structure {
        public final String id, tier, tech, setting, orbitEntity;
        public final int orbitCount;
        public final Area area;
        /** Repeat mode (ADR 0010): after a step, it starts its next step instead of removing itself. */
        public final boolean repeat;
        /** Only in games without AoTD Vaults of Knowledge: with AoTD it never shows (the Lobster Hatchery, ADR 0014). */
        public final boolean noAotd;
        /** The item family that fits it (docs/features/special-items.md), or blank. An unknown family is ignored. */
        public final String item;

        Structure(Map<String, String> r) {
            id = r.get("id");
            tier = r.get("tier");
            tech = r.get("tech");
            setting = r.get("setting");
            orbitEntity = r.get("orbitEntity");
            orbitCount = r.get("orbitCount").isEmpty() ? 1 : Integer.parseInt(r.get("orbitCount"));
            area = Area.of(r.get("area"));
            repeat = Cells.flag(r.get("repeat"));
            noAotd = Cells.flag(r.get("noAotd"));
            String family = Items.column(r.get("item"), i -> Global.getSettings().getSpecialItemSpec(i) != null);
            if (family == null) log.warn("DTI: structures.csv row " + id + " names an unknown item family '" + r.get("item") + "' (a typo?); ignored");
            item = family == null ? "" : family;
        }
    }

    /** A row of projects.csv. */
    public static final class Project extends ProjectRow {
        public final Map<String, Integer> goods, aotdGoods;

        Project(Map<String, String> r, boolean aotd) {
            super(r);
            // The game cannot add a condition it does not know.
            if (!adds.isEmpty() && Global.getSettings().getMarketConditionSpec(adds) == null) throw new IllegalArgumentException("unknown condition '" + adds + "'");
            goods = knownGoods(id, Cells.goods(r.get("goods")));
            aotdGoods = aotd ? knownGoods(id, Cells.goods(r.get("aotdGoods"))) : Collections.<String, Integer>emptyMap();
        }
    }

    private static final Map<String, Structure> structures = new LinkedHashMap<>();
    private static final Map<String, Project> projects = new LinkedHashMap<>();
    private static final List<String> protectedConditions = new ArrayList<>();
    private static final Map<String, Set<String>> taught = new HashMap<>();
    private static Map<Support.By, Set<String>> suppressions = new EnumMap<>(Support.By.class);
    private static Set<String> changeable = Collections.emptySet();
    private static TypeLadder ladder;

    public static void load() throws Exception {
        boolean aotd = Unlocks.aotd();
        for (Map<String, String> r : rows("id", "structures.csv")) {
            try {
                Structure s = new Structure(r);
                structures.put(s.id, s);
                Items.addTaught(taught, s.tech, s.item);
            } catch (RuntimeException e) {
                log.error("DTI: bad row in structures.csv: " + r, e);
            }
        }
        List<String[]> lines = new ArrayList<>();
        for (Structure s : structures.values()) lines.add(new String[]{s.tech, s.tier, s.setting, s.item});
        Items.addLineTaught(taught, lines);
        // The ladder first: the project rows' types are checked against its archetypes.
        ladder = TypeLadder.fromRows(rows("type", "planet_types.csv"), rows("id", "type_rules.csv"),
                (r, e) -> log.error("DTI: bad row in " + (r.containsKey("order") ? "type_rules.csv: " : "planet_types.csv: ") + r, e));
        for (Map<String, String> r : rows("id", "projects.csv")) {
            try {
                if (!structures.containsKey(r.get("structure"))) throw new IllegalArgumentException("unknown structure");
                Project p = new Project(r, aotd);
                projects.put(r.get("id"), p);
                for (String u : p.unknownIds(c -> Global.getSettings().getMarketConditionSpec(c) != null, ladder::hasArchetype)) {
                    log.warn("DTI: projects.csv row " + p.id + " names an unknown id: " + u + " (a typo?)");
                }
            } catch (RuntimeException e) {
                log.error("DTI: bad row in projects.csv: " + r, e);
            }
        }
        for (Map<String, String> r : rows("condition", "protected_conditions.csv")) protectedConditions.add(r.get("condition"));
        suppressions = Support.lists(rows("condition", "suppressions.csv"), (r, e) -> log.error("DTI: bad row in suppressions.csv: " + r, e));
        for (Set<String> list : suppressions.values()) {
            for (String c : list) {
                // Another mod's condition that is not in this game: a typo, or a mod that is off.
                if (Global.getSettings().getMarketConditionSpec(c) == null) log.info("DTI: suppressions.csv names a condition this game does not have: " + c);
            }
        }
        changeable = Overview.changeable(projects.values(), suppressions.values());
        log.info("DTI: loaded " + structures.size() + " structures, " + projects.size() + " projects");
    }

    private static List<Map<String, String>> rows(String idColumn, String file) throws Exception {
        JSONArray array = Global.getSettings().getMergedSpreadsheetDataForMod(idColumn, DIR + file, MOD_ID);
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            if (o.optString(idColumn, "").trim().isEmpty()) continue;
            // A column that a row does not have (an older file from another mod) reads as blank.
            Map<String, String> row = new HashMap<String, String>() {
                @Override
                public String get(Object key) {
                    String v = super.get(key);
                    return v == null ? "" : v;
                }
            };
            Iterator<?> keys = o.keys();
            while (keys.hasNext()) {
                String k = (String) keys.next();
                row.put(k, o.optString(k, "").trim());
            }
            out.add(row);
        }
        return out;
    }

    private static Map<String, Integer> knownGoods(String projectId, Map<String, Integer> goods) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : goods.entrySet()) {
            if (Global.getSettings().getCommoditySpec(e.getKey()) == null) {
                log.error("DTI: project " + projectId + " names an unknown commodity '" + e.getKey() + "'; skipped");
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    public static Structure structure(String id) {
        return structures.get(id);
    }

    public static Collection<Structure> structures() {
        return structures.values();
    }

    /** A DTI building that works on a condition: every structure but the Control (the Labor Camps too). */
    public static boolean isProjectStructure(String industryId) {
        return structures.containsKey(industryId) && !CONTROL.equals(industryId);
    }

    public static Project project(String id) {
        return projects.get(id);
    }

    /** The structure's rows, in row order. */
    public static List<Project> projectsOf(String structureId) {
        List<Project> out = new ArrayList<>();
        for (Project p : projects.values()) {
            if (p.structure.equals(structureId)) out.add(p);
        }
        return out;
    }

    /** The project this structure would run on this colony: its first row that fits ({@link ProjectRow#misfit}). */
    public static Project target(String structureId, MarketAPI market) {
        return ProjectRow.first(projectsOf(structureId), Terraform.conditionIds(market), Terraform.suppressed(market), Terraform.archetype(market), Terraform.farmlandBest(market));
    }

    /** The project a building starts when its build is done, or the next step in repeat mode: {@link #target}, else a row that only a blocker holds; it then waits. */
    public static Project startTarget(String structureId, MarketAPI market) {
        return ProjectRow.firstOrBlocked(projectsOf(structureId), Terraform.conditionIds(market), Terraform.suppressed(market), Terraform.archetype(market), Terraform.farmlandBest(market));
    }

    /** Repeat mode: the project a structure starts when its step ends, done or no longer fitting ({@link ProjectRow#next}), or null. */
    public static Project nextStep(String structureId, int step, MarketAPI market) {
        return ProjectRow.next(projectsOf(structureId), step, Terraform.conditionIds(market), Terraform.suppressed(market), Terraform.archetype(market), Terraform.farmlandBest(market));
    }

    /**
     * The tech of this structure's line: the tech of the tier 2 structure that has the same setting (the Stellar Shade:
     * Stellar Arrays), or null. Once researched, it cuts the upkeep of the whole line (research-gating.md, 2026-11-05).
     */
    public static String lineTech(String structureId) {
        Structure s = structures.get(structureId);
        if (s == null || s.setting.isEmpty()) return null;
        for (Structure t : structures.values()) {
            if ("2".equals(t.tier) && t.setting.equals(s.setting) && !t.noAotd) return t.tech;
        }
        return null;
    }

    /** The item ids a tech teaches the AoTD workshop (Items.addTaught); empty for none. */
    public static Set<String> taught(String tech) {
        Set<String> ids = taught.get(tech);
        return ids == null ? Collections.<String>emptySet() : ids;
    }

    /** The conditions this suppressor takes away while it works (suppressions.csv). */
    public static Set<String> suppression(Support.By by) {
        Set<String> list = suppressions.get(by);
        return list == null ? Collections.<String>emptySet() : list;
    }

    /** A condition some DTI building can work on (Overview.changeable). */
    public static boolean changeable(String condition) {
        return changeable.contains(condition);
    }

    public static boolean isProtected(String condition) {
        for (String p : protectedConditions) {
            if (p.endsWith("*") ? condition.startsWith(p.substring(0, p.length() - 1)) : condition.equals(p)) return true;
        }
        return false;
    }

    public static TypeLadder ladder() {
        return ladder;
    }
}
