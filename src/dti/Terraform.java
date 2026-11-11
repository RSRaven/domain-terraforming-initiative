package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.PlanetSpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue.ConstructionQueueItem;
import com.fs.starfarer.api.impl.campaign.econ.impl.Farming;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.procgen.ConditionGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.PlanetGenDataSpec;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import dti.industry.SupportStructure;
import dti.logic.Farmland;
import dti.logic.FoodBuildings;
import dti.logic.TypeLadder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Changes to the world: conditions, the planet type, objects in orbit, and messages. */
public final class Terraform {
    private Terraform() {}

    public static Set<String> conditionIds(MarketAPI market) {
        Set<String> ids = new LinkedHashSet<>();
        for (MarketConditionAPI c : market.getConditions()) ids.add(c.getId());
        return ids;
    }

    /**
     * The colony's conditions that something else suppresses (an item, a building of the base game or another mod). Not
     * those DTI's own support structures suppress: terraforming still works on them (domed-cities.md).
     */
    public static Set<String> suppressed(MarketAPI market) {
        Set<String> ids = new LinkedHashSet<>();
        for (MarketConditionAPI c : market.getConditions()) {
            if (market.isConditionSuppressed(c.getId())) ids.add(c.getId());
        }
        // ponytail: a condition that both DTI and something else suppress counts as DTI's; track the sources if a mod ever overlaps.
        ids.removeAll(SupportStructure.suppressedHere(market));
        return ids;
    }

    /**
     * The example market of AoTD's research screen and the Codex: it has no planet or station. A building there shows
     * everything it can do, not what it would do on one colony.
     */
    public static boolean example(MarketAPI market) {
        return market != null && market.getPrimaryEntity() == null;
    }

    /** The planet's archetype on the type ladder, or null for a station or a blocked type. */
    public static String archetype(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        return planet == null ? null : DtiData.ladder().archetype(planet.getTypeId(), ladderEnv());
    }

    /** The best farmland of the planet's type (planet_types.csv), before mild climate; none for a station or a blocked type. */
    public static Farmland farmlandBest(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        TypeLadder.TypeRow row = planet == null ? null : DtiData.ladder().row(planet.getTypeId(), ladderEnv());
        return row == null ? Farmland.NONE : row.farmlandBest;
    }

    /** The one place where DTI removes a condition. It refuses protected conditions. */
    public static boolean removeCondition(MarketAPI market, String id) {
        if (DtiData.isProtected(id)) {
            Global.getLogger(Terraform.class).warn("DTI: refused to remove protected condition " + id + " on " + market.getId());
            return false;
        }
        market.removeCondition(id);
        return true;
    }

    /** The one place where DTI adds a condition. It is marked surveyed, so the player sees it at once. */
    public static void addCondition(MarketAPI market, String id) {
        market.getSpecificCondition(market.addCondition(id)).setSurveyed(true);
    }

    public static String conditionName(String id) {
        MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(id);
        return spec == null ? id : spec.getName();
    }

    /** Hazard of a condition from the game's condition_gen_data.csv, for example 0.5 for +50%. */
    public static float hazard(String condition) {
        if (condition.isEmpty()) return 0f;
        Object spec = Global.getSettings().getSpec(ConditionGenDataSpec.class, condition, true);
        return spec instanceof ConditionGenDataSpec ? ((ConditionGenDataSpec) spec).getHazard() : 0f;
    }

    public static String typeName(String typeId) {
        for (PlanetSpecAPI spec : Global.getSettings().getAllPlanetSpecs()) {
            if (spec.getPlanetType().equals(typeId)) return spec.getName();
        }
        return typeId;
    }

    static TypeLadder.Env ladderEnv() {
        return new TypeLadder.Env(
                type -> Global.getSettings().getSpec(PlanetGenDataSpec.class, type, true) != null,
                type -> {
                    Object spec = Global.getSettings().getSpec(PlanetGenDataSpec.class, type, true);
                    return spec instanceof PlanetGenDataSpec ? ((PlanetGenDataSpec) spec).getCategory() : null;
                },
                DtiSettings::on,
                DtiData::isProtected,
                DtiSettings.on("dti_blockUnknownTypes"));
    }

    /** The ladder's answer for a project that takes the planet from {@code before} to {@code after} (ADR 0006). */
    public static TypeLadder.Result typeChange(MarketAPI market, Set<String> before, Set<String> after) {
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null) return null;
        return DtiData.ladder().change(planet.getTypeId(), before, after, ladderEnv());
    }

    /** Adds "habitable" when vanilla would give it to this planet and nothing blocks it. True when added. */
    public static boolean restoreHabitable(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null) return false;
        Object type = Global.getSettings().getSpec(PlanetGenDataSpec.class, planet.getTypeId(), true);
        Object hab = Global.getSettings().getSpec(ConditionGenDataSpec.class, Conditions.HABITABLE, true);
        if (!(type instanceof PlanetGenDataSpec) || !(hab instanceof ConditionGenDataSpec)) return false;
        String category = ((PlanetGenDataSpec) type).getCategory();
        boolean habitableCategory = ((ConditionGenDataSpec) hab).hasMultiplier(category) && ((ConditionGenDataSpec) hab).getMultiplier(category) > 0;
        Set<String> blockers = new LinkedHashSet<>();
        for (ConditionGenDataSpec s : Global.getSettings().getAllSpecs(ConditionGenDataSpec.class)) {
            if (s.requiresNotAnyContains(Conditions.HABITABLE)) blockers.add(s.getId());
        }
        if (!TypeLadder.missingHabitable(habitableCategory, conditionIds(market), blockers)) return false;
        addCondition(market, Conditions.HABITABLE);
        return true;
    }

    /** The drying marker of the Hydrosphere Extractor: a step that uses it keeps the farmland, at most the new best level (from a sea: the marine life). */
    public static final String DRIED = "dti_dried";

    /** Applies a ladder result: the new type, then the added and removed conditions, then the farmland of a new type. */
    public static void applyType(MarketAPI market, TypeLadder.Result result) {
        PlanetAPI planet = market.getPlanetEntity();
        if (result.capped) Global.getLogger(Terraform.class).warn("DTI: type ladder hit its step cap on " + market.getId() + ": " + result.rules);
        boolean newType = !result.type.equals(planet.getTypeId()), wasWater = Farming.AQUA_PLANETS.contains(planet.getTypeId());
        List<Industry> lost = foodBuildingsLost(market, result);
        List<ConstructionQueueItem> queued = foodBuildingsQueuedLost(market, result);
        Farmland farmland = farmlandAfter(market, result);
        if (newType) planet.changeType(result.type, null);
        Set<String> now = conditionIds(market);
        for (String c : now) {
            if (!result.conditions.contains(c)) removeCondition(market, c);
        }
        for (String c : result.conditions) {
            if (!now.contains(c)) addCondition(market, c);
        }
        if (newType) setFarmland(market, farmland);
        String why = "The planet is now " + typeName(result.type) + (Farming.AQUA_PLANETS.contains(result.type) ? ", with no land to farm"
                : wasWater ? ", with no sea to farm" : ", with no farmland left");
        for (Industry i : lost) shutDown(market, i, why);
        for (ConstructionQueueItem q : queued) dequeue(market, q, why);
        market.reapplyConditions();
        market.reapplyIndustries();
    }

    /**
     * The ids of the food buildings that this ladder result removes: when it crosses the water border (vanilla
     * Farming.AQUA_PLANETS), Farming or Aquaculture and every building linked to it by an upgrade or a downgrade; when a
     * world that is not water loses all its farmland, Farming ({@link FoodBuildings#farmlandLost}).
     */
    private static Set<String> foodChainLost(MarketAPI market, TypeLadder.Result result) {
        PlanetAPI planet = market.getPlanetEntity();
        Set<String> out = new LinkedHashSet<>();
        if (planet == null) return out;
        boolean isWater = Farming.AQUA_PLANETS.contains(result.type);
        String seed = FoodBuildings.lost(Farming.AQUA_PLANETS.contains(planet.getTypeId()), isWater);
        if (seed != null) return foodChain(seed);
        if (FoodBuildings.farmlandLost(isWater, Farmland.present(conditionIds(market)), farmlandAfter(market, result))) out.add(FoodBuildings.FARMING);
        return out;
    }

    /** The industry and every industry linked to it by an upgrade or a downgrade ({@link FoodBuildings#chain}): AoTD's food buildings too. */
    public static Set<String> foodChain(String seed) {
        Map<String, String> up = new HashMap<>(), down = new HashMap<>();
        for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) {
            up.put(s.getId(), s.getUpgrade());
            down.put(s.getId(), s.getDowngrade());
        }
        return FoodBuildings.chain(seed, up, down);
    }

    /** The food buildings on the colony (built or building) that this ladder result removes ({@link #foodChainLost}). */
    public static List<Industry> foodBuildingsLost(MarketAPI market, TypeLadder.Result result) {
        Set<String> chain = foodChainLost(market, result);
        List<Industry> out = new ArrayList<>();
        for (Industry i : market.getIndustries()) {
            if (chain.contains(i.getId())) out.add(i);
        }
        return out;
    }

    /** The food buildings in the colony's construction queue that this ladder result removes ({@link #foodChainLost}). */
    public static List<ConstructionQueueItem> foodBuildingsQueuedLost(MarketAPI market, TypeLadder.Result result) {
        Set<String> chain = foodChainLost(market, result);
        List<ConstructionQueueItem> out = new ArrayList<>();
        for (ConstructionQueueItem q : market.getConstructionQueue().getItems()) {
            if (chain.contains(q.id)) out.add(q);
        }
        return out;
    }

    /**
     * Takes a building out of the construction queue as the colony screen does: the full queued price back on the
     * player's colony (vanilla also gives it all back when it aborts a queued build).
     */
    private static void dequeue(MarketAPI market, ConstructionQueueItem item, String why) {
        market.getConstructionQueue().removeItem(item.id);
        List<String> lines = new ArrayList<>();
        lines.add(why);
        if (market.isPlayerOwned() && item.cost > 0) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(item.cost);
            lines.add("You got back " + Misc.getDGSCredits(item.cost) + ", the full price");
        }
        message(market, Global.getSettings().getIndustrySpec(item.id).getName() + ": Removed from the construction queue", lines.toArray(new String[0]));
    }

    /**
     * Shuts a building down as the player would from the colony screen: the shutdown refund on the player's colony
     * (Misc.computeShutdownRefund, also for an upgrade under way), its AI core and its item to the colony's storage.
     * On a colony that is not the player's, the core and the item go with the building.
     */
    private static void shutDown(MarketAPI market, Industry industry, String why) {
        List<String> lines = new ArrayList<>();
        lines.add(why);
        if (market.isPlayerOwned()) {
            int refund = Misc.computeShutdownRefund(market, industry);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
            lines.add("You got back " + Misc.getDGSCredits(refund) + ", as for a shutdown");
        }
        if (market.isPlayerOwned() && Misc.getStorageCargo(market) != null) {
            if (industry.getAICoreId() != null) lines.add("The " + Global.getSettings().getCommoditySpec(industry.getAICoreId()).getName() + " went to the colony's storage");
            if (industry.getSpecialItem() != null) lines.add("The " + Global.getSettings().getSpecialItemSpec(industry.getSpecialItem().getId()).getName() + " went to the colony's storage");
        }
        String name = industry.getCurrentName();
        // The REMOTE mode puts the core and the item into the colony's storage, as a shutdown from the colony screen does.
        // On a colony the player lost, that storage is the player's own there: the null mode gives them to no one.
        market.removeIndustry(industry.getId(), market.isPlayerOwned() ? MarketAPI.MarketInteractionMode.REMOTE : null, false);
        message(market, name + ": Shut down", lines.toArray(new String[0]));
    }

    /**
     * The farmland after this ladder result ({@link Farmland#afterTypeChange}): the same for no new type or a type with no
     * ladder row. A drying step (the ladder used {@link #DRIED}) keeps the farmland, at most the new type's best level;
     * from a water world, the marine life becomes the farmland ({@link Farmland#fromMarine}).
     */
    private static Farmland farmlandAfter(MarketAPI market, TypeLadder.Result result) {
        PlanetAPI planet = market.getPlanetEntity();
        Farmland now = Farmland.present(result.conditions);
        if (planet == null || result.type.equals(planet.getTypeId())) return now;
        TypeLadder.TypeRow row = DtiData.ladder().row(result.type, ladderEnv());
        if (row == null) return now;
        boolean dried = market.hasCondition(DRIED) && !result.conditions.contains(DRIED);
        if (dried && "ocean".equals(archetype(market))) {
            int food = 0;
            for (String c : FoodBuildings.MARINE) if (market.hasCondition(c)) food = FoodBuildings.marineFood(c);
            now = Farmland.fromMarine(food);
        }
        return Farmland.afterTypeChange(now, row.farmlandFloor, row.farmlandBest, result.conditions.contains(Conditions.MILD_CLIMATE), dried);
    }

    /** Sets the farmland to this level, removing any other. */
    private static void setFarmland(MarketAPI market, Farmland f) {
        Set<String> cs = conditionIds(market);
        for (Farmland other : Farmland.values()) {
            if (other != f && cs.contains(other.condition())) removeCondition(market, other.condition());
        }
        if (f != Farmland.NONE && !cs.contains(f.condition())) addCondition(market, f.condition());
    }

    /**
     * Vanilla stellar shades or mirrors, or a small star (the Captive Sun, an entity type that starts with "star_"), in
     * orbit, placed when the project starts and kept as a monument when it ends: not clickable, no tooltip, no effect on
     * the colony. {@link CaptiveSun} lights the planet from its star. Shades go on the star side, the others behind. They circle the planet as fast as the body that circles the star
     * (the planet, or its gas giant for a moon), so they keep their place. A moon's own orbit adds a few degrees of drift.
     */
    public static List<SectorEntityToken> addOrbitObjects(MarketAPI market, String entityType, int count) {
        List<SectorEntityToken> out = new ArrayList<>();
        SectorEntityToken planet = market.getPrimaryEntity();
        SectorEntityToken body = planet;
        while (body.getOrbitFocus() != null && body.getOrbitFocus().getOrbitFocus() != null && !isStar(body.getOrbitFocus())) {
            body = body.getOrbitFocus();
        }
        SectorEntityToken star = body.getOrbitFocus();
        float radius = planet.getRadius() + 250f;
        float period = body.getCircularOrbitPeriod() > 0 ? body.getCircularOrbitPeriod() : radius / 10f;
        float toStar = star == null ? 0f : Misc.getAngleInDegrees(planet.getLocation(), star.getLocation());
        float base = toStar + (Entities.STELLAR_SHADE.equals(entityType) ? 0f : 180f);
        for (int i = 0; i < count; i++) {
            float angle = base + (i - (count - 1) / 2f) * 30f;
            if (entityType.startsWith("star_")) {
                out.add(CaptiveSun.add(planet, entityType, angle, radius, period));
                continue;
            }
            SectorEntityToken e = planet.getContainingLocation().addCustomEntity(null, null, entityType, Factions.NEUTRAL);
            e.setCircularOrbitPointingDown(planet, angle, radius, period);
            e.addTag(Tags.NON_CLICKABLE);
            e.addTag(Tags.NO_ENTITY_TOOLTIP);
            out.add(e);
        }
        return out;
    }

    /** Takes these objects out of their star system. */
    public static void removeOrbitObjects(List<SectorEntityToken> objects) {
        for (SectorEntityToken e : objects) {
            CaptiveSun.unlight(e);
            if (e.getContainingLocation() != null) e.getContainingLocation().removeEntity(e);
        }
    }

    private static boolean isStar(SectorEntityToken e) {
        return e instanceof PlanetAPI && ((PlanetAPI) e).isStar();
    }

    public static void message(MarketAPI market, String title, String... lines) {
        if (!market.isPlayerOwned()) return;
        MessageIntel intel = new MessageIntel(title + " at " + market.getName(), Misc.getBasePlayerColor());
        for (String line : lines) intel.addLine(BaseIntelPlugin.BULLET + line);
        intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel, MessageClickAction.COLONY_INFO, market);
        PlanetIntel.note(market, title + (lines.length == 0 ? "" : ". " + String.join("; ", lines)));
    }
}
