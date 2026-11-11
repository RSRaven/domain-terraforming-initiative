package dti.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue.ConstructionQueueItem;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.*;
import dti.logic.Area;
import dti.logic.Cells;
import dti.logic.Extraction;
import dti.logic.Items;
import dti.logic.Light;
import dti.logic.Pollution;
import dti.logic.ProjectClock;
import dti.logic.ProjectClock.State;
import dti.logic.ProjectRow;
import dti.logic.ProjectRow.Misfit;
import dti.logic.Support;
import dti.logic.TypeLadder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every DTI project structure (ADR 0005). Vanilla builds it; then it runs the project that its data rows describe.
 * While the project runs, isBuilding() and isUpgrading() are both true: vanilla draws the progress bar and keeps
 * the colony's build queue free. The progress lives in our own saved fields, not in vanilla's buildProgress.
 */
public class ProjectStructure extends BaseIndustry {
    /** Save memory key set by the test command DTI_Short: the number of short goods to use instead of the real one. */
    public static final String TEST_SHORT = "$dti_testShortGoods";

    // Saved with the game.
    protected String projectId;
    protected float progress, shortageDays;
    protected LinkedHashSet<String> conditionsAtStart;
    protected String ownerAtStart;
    protected boolean ended;
    /** Repeat mode: the step it works on, from 1. Saves from before 2b have 0, which counts as 1. */
    protected int step;
    /** Its objects in orbit (structures.csv orbitEntity), placed when the project starts; they go if it ends unfinished. */
    protected List<SectorEntityToken> orbit;

    // Recomputed once a day and after a load.
    protected transient State state;
    protected transient ProjectClock.Rules rules;
    protected transient float dayTimer;
    protected transient int shortGoods;

    public boolean running() {
        return projectId != null && !ended;
    }

    public DtiData.Project project() {
        return projectId == null ? null : DtiData.project(projectId);
    }

    public float progress() {
        return progress;
    }

    public int step() {
        return Math.max(1, step);
    }

    /** Repeat mode (ADR 0010): after a step it starts its next one instead of removing itself. */
    public boolean repeat() {
        DtiData.Structure s = DtiData.structure(getId());
        return s != null && s.repeat;
    }

    public float shortageDays() {
        return shortageDays;
    }

    public int shortGoods() {
        return shortGoods;
    }

    public float totalDays() {
        return totalDays(project());
    }

    /** Days of work for a project, after the time setting. */
    public static float totalDays(DtiData.Project p) {
        return p == null ? 1f : Math.max(1f, p.days * DtiSettings.number("dti_projectTimeMult", 1f));
    }

    /** Terraforming or Colony Development (ADR 0011); a structure with no row counts as terraforming. */
    public Area area() {
        DtiData.Structure s = DtiData.structure(getId());
        return s == null ? Area.TERRAFORMING : s.area;
    }

    /** "Terraforming" or "Colony project", for the progress bar and the messages. */
    private String work() {
        return area() == Area.TERRAFORMING ? "Terraforming" : "Colony project";
    }

    /** The structure's area needs a Control, and the colony has no working one. */
    private boolean noControl() {
        return area().needsControl && !TerraformingControl.works(market);
    }

    public State state() {
        if (state == null) refresh();
        return state;
    }

    /** Live: a new AI core or item shows at once. */
    public float speed() {
        if (state == null) refresh();
        return ProjectClock.speed(state, getAICoreId(), Items.bonus(itemId()), rules);
    }

    /** The installed item's id, or null. */
    public String itemId() {
        return getSpecialItem() == null ? null : getSpecialItem().getId();
    }

    private static String itemName(String itemId) {
        return Global.getSettings().getSpecialItemSpec(itemId).getName();
    }

    private boolean ownerChanged() {
        return ownerAtStart != null && !ownerAtStart.equals(market.getFactionId());
    }

    /** Recounts the short goods and works out the state and speed. No side effects. */
    private void refresh() {
        rules = DtiSettings.rules();
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        shortGoods = memory.contains(TEST_SHORT) ? memory.getInt(TEST_SHORT) : getAllDeficit().size();
        boolean paused = ownerChanged() && "pause".equals(DtiSettings.lostColony());
        state = ProjectClock.state(paused, keepsPolluting(project()) || blocker(project()) != null, !noControl(), shortGoods, shortageDays, rules);
    }

    /** Why the running project no longer fits the colony, or null ({@link dti.logic.ProjectRow#misfit}). Suppression does not count here. */
    private Misfit misfit(DtiData.Project p) {
        return p.misfit(Terraform.conditionIds(market), Collections.<String>emptySet(), Terraform.archetype(market), Terraform.farmlandBest(market));
    }

    /** The condition that holds the project (its blockers column), or null. It waits until that condition is gone. */
    private String blocker(DtiData.Project p) {
        Misfit m = p == null ? null : misfit(p);
        return m != null && m.kind == Misfit.Kind.BLOCKED ? m.what : null;
    }

    /** A pollution cleanup cannot work while a building on the colony keeps polluting (docs/features/hazard-set.md). */
    private boolean keepsPolluting(DtiData.Project p) {
        return p != null && Conditions.POLLUTION.equals(p.condition) && pollutionSource(market) != null;
    }

    /**
     * The first building that pollutes the colony, or null. A disrupted or unfinished one counts too:
     * vanilla does not look at that either.
     */
    private static Industry pollutionSource(MarketAPI market) {
        boolean habitable = market.hasCondition(Conditions.HABITABLE);
        for (Industry i : market.getIndustries()) {
            SpecialItemSpecAPI item = itemSpec(i);
            if (Pollution.source(habitable, i instanceof HeavyIndustry, item == null ? null : item.getTags())) return i;
        }
        return null;
    }

    private static SpecialItemSpecAPI itemSpec(Industry i) {
        return i.getSpecialItem() == null ? null : Global.getSettings().getSpecialItemSpec(i.getSpecialItem().getId());
    }

    /**
     * The good of the mining deposit this project takes away (vanilla's ResourceDepositsCondition: a deposit that Mining
     * works), or null. Such a project is an extraction, with a yield and a fallout (docs/data-formats.md, deposit rows).
     */
    static String depositGood(DtiData.Project p) {
        String good = p == null ? null : ResourceDepositsCondition.COMMODITY.get(p.condition);
        return good != null && Industries.MINING.equals(ResourceDepositsCondition.INDUSTRY.get(good)) ? good : null;
    }

    /** What an extraction supplies at the deposit's level now, as much as a vanilla Mining building ({@link Extraction#yield}); 0 for any other project. */
    private int yieldNow(DtiData.Project p) {
        String good = depositGood(p);
        if (good == null) return 0;
        Integer base = ResourceDepositsCondition.BASE_MODIFIER.get(good), level = ResourceDepositsCondition.MODIFIER.get(p.condition);
        if (base == null || level == null) return 0;
        int size = ResourceDepositsCondition.BASE_ZERO.contains(good) ? 0 : market.getSize();
        return Extraction.yield(size, base, level, itemId());
    }

    // ---- life of a project ----

    @Override
    protected void buildingFinished() {
        super.buildingFinished();
        startProject();
    }

    private void startProject() {
        // A blocker that came during the build does not end it: the project starts and waits (WAITING).
        DtiData.Project p = DtiData.startTarget(getId(), market);
        if (p == null) {
            endUnused(area() == Area.TERRAFORMING ? "Nothing to terraform" : "Nothing to do", ProjectRow.onlyAdds(DtiData.projectsOf(getId()))
                    ? "The colony can no longer take what this building adds." : "The colony no longer has a condition this building can fix.");
            return;
        }
        if (DtiData.ladder().marker(p.adds) && newType(market, p) == null) {
            endUnused("Nothing to terraform", "This step would not change the planet type now.");
            return;
        }
        step = 1;
        ownerAtStart = market.getFactionId();
        begin(p);
        placeOrbit();
    }

    /** {@link dti.CaptiveSun} swaps an old Fusion Lamp for its star. */
    public void swapOrbit(Map<SectorEntityToken, SectorEntityToken> swapped) {
        if (orbit != null) orbit.replaceAll(e -> swapped.getOrDefault(e, e));
    }

    /** Places its objects in orbit once; a project started before 2026-11-05 gets them when it finishes. */
    private void placeOrbit() {
        DtiData.Structure s = DtiData.structure(getId());
        if (orbit == null && s != null && !s.orbitEntity.isEmpty() && market.getPrimaryEntity() != null) {
            orbit = Terraform.addOrbitObjects(market, s.orbitEntity, s.orbitCount);
        }
    }

    /** Starts a step: progress 0, new start conditions, shortage count reset. The owner at the start stays from step 1. */
    private void begin(DtiData.Project p) {
        projectId = p.id;
        progress = 0f;
        shortageDays = 0f;
        conditionsAtStart = new LinkedHashSet<>(Terraform.conditionIds(market));
        state = null;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        // Added as a finished building (console AddIndustry, another mod): start the project now.
        if (projectId == null && !building && !ended) startProject();
        if (!running()) return;
        float days = Global.getSector().getClock().convertToDays(amount);
        if (state == null) refresh();
        dayTimer += days;
        if (dayTimer >= 1f) {
            dayTimer = 0f;
            if (!dailyCheck()) return;
            refresh();
            reapply();
            TerraformingTrouble.sync(market);
        }
        shortageDays = ProjectClock.shortageDays(shortageDays, state, shortGoods, days);
        addProgress(speed() * days);
    }

    /** False when the project ended. */
    private boolean dailyCheck() {
        DtiData.Project p = project();
        if (p == null) {
            endUnused(work() + " ended", "This project is no longer in the game data. Nothing changed.");
            return false;
        }
        Misfit m = misfit(p);
        if (m != null && m.kind != Misfit.Kind.BLOCKED) {
            // Repeat mode (ADR 0010): it removes itself only when no step is left.
            DtiData.Project next = repeat() ? DtiData.nextStep(getId(), step(), market) : null;
            if (next == null) {
                endUnused(work() + " ended", misfitText(m) + " Nothing changed.");
                return false;
            }
            nextStep(next, "Step " + step() + " ended", new ArrayList<>(Collections.singletonList(misfitText(m))));
        }
        if (ownerChanged() && "remove".equals(DtiSettings.lostColony())) {
            end(work() + " ended", "The colony changed owner.");
            return false;
        }
        return true;
    }

    private String misfitText(Misfit m) {
        switch (m.kind) {
            case ADDS_PRESENT:
                return "The colony already has " + Terraform.conditionName(m.what) + ".";
            case NEEDS_GONE:
                return "The project needs " + Terraform.conditionName(m.what) + ", which is gone.";
            case TYPE:
                return "The planet is now " + Terraform.typeName(typeId()) + ", and this project does not work there.";
            case FARMLAND_BEST:
                return m.what.isEmpty() ? "The planet can have no farmland now."
                        : "The farmland here can now be no better than " + Terraform.conditionName(m.what) + ".";
            default:
                return Terraform.conditionName(m.what) + " is already gone.";
        }
    }

    /** Moves progress by days of work (negative: back). The project ends when it reaches its total. Used by DTI_Progress. */
    public void addProgress(float days) {
        if (!running()) return;
        progress = ProjectClock.progress(progress, 1f, days, totalDays());
        if (progress >= totalDays()) finishProject();
    }

    /** Console FastBuild calls this on every building that is building or upgrading: it finishes the project too. */
    @Override
    public void finishBuildingOrUpgrading() {
        if (running()) finishProject();
        else super.finishBuildingOrUpgrading();
    }

    private void finishProject() {
        DtiData.Project p = project();
        Misfit m = p == null ? null : misfit(p);
        if (p == null || m != null && m.kind != Misfit.Kind.BLOCKED) {
            dailyCheck();
            return;
        }
        // A blocker holds it (DTI_Progress or FastBuild while it waits): it finishes when the blocker is gone.
        if (m != null) return;
        String oldType = typeId();
        Set<String> before = Terraform.conditionIds(market);
        Set<String> after = p.after(before);
        List<String> lines = new ArrayList<>();
        // Farmland and marine life get one line each below, "Farmland: Poor -> Adequate".
        if (!p.condition.isEmpty()) {
            Terraform.removeCondition(market, p.condition);
            if (!ProjectRow.level(p.condition)) lines.add(Terraform.conditionName(p.condition) + " removed");
        }
        // The removes column: only those the planet has.
        for (String c : p.removes) {
            if (before.contains(c) && Terraform.removeCondition(market, c) && !ProjectRow.level(c)) lines.add(Terraform.conditionName(c) + " removed");
        }
        boolean marker = !p.adds.isEmpty() && DtiData.ladder().marker(p.adds);
        if (!p.adds.isEmpty()) {
            Terraform.addCondition(market, p.adds);
            // A marker stays hidden; its name, in sentence case, says the work done: "Water delivered".
            String name = Terraform.conditionName(p.adds);
            if (marker) lines.add(name.charAt(0) + name.substring(1).toLowerCase());
            else if (!ProjectRow.level(p.adds)) lines.add(name + " added");
        }
        boolean habitable = false;
        // A Colony Development building changes the colony, not the planet.
        if (area() == Area.TERRAFORMING) {
            TypeLadder.Result r = Terraform.typeChange(market, conditionsAtStart, after);
            if (r != null && !r.rules.isEmpty()) Terraform.applyType(market, r);
            habitable = Terraform.restoreHabitable(market);
        }
        // A marker never stays: the ladder rule that used it removed it, or no rule did.
        if (marker && market.hasCondition(p.adds)) Terraform.removeCondition(market, p.adds);
        // The meteor rule (impact-defense-grid.md): a real atmosphere burns the meteors up.
        if (Support.meteorsBurnUp(Terraform.conditionIds(market)) && Terraform.removeCondition(market, Support.METEORS)) {
            lines.add(Terraform.conditionName(Support.METEORS) + " removed");
        }
        market.reapplyConditions();
        market.reapplyIndustries();
        // The objects stay in orbit as a monument when the building goes.
        placeOrbit();
        orbit = null;
        String newType = typeId();
        if (!newType.equals(oldType)) lines.add("Planet type: " + Terraform.typeName(oldType) + " -> " + Terraform.typeName(newType));
        lines.addAll(ProjectRow.levelLines(before, Terraform.conditionIds(market)));
        if (habitable) lines.add(Terraform.conditionName("habitable") + " restored");
        // The ladder takes mild climate away with habitable (TypeLadder.dropMildClimate).
        if (before.contains(Conditions.MILD_CLIMATE) && !market.hasCondition(Conditions.MILD_CLIMATE)) lines.add(Terraform.conditionName(Conditions.MILD_CLIMATE) + " removed");
        projectFinished(lines);
        wear(lines);
        // A next step that only a blocker holds starts and waits, as a building at the end of its build.
        DtiData.Project next = repeat() ? DtiData.nextStep(getId(), step(), market) : null;
        if (next == null) {
            end(work() + " completed", lines.toArray(new String[0]));
            return;
        }
        nextStep(next, "Step " + step() + " completed", lines);
    }

    /**
     * A DTI item stays installed while its project runs (the user, 2026-11-05): it wears only at the end, so taking it
     * out a few days before would keep it as it was. The game's "Manage item..." menu is greyed out then; a shutdown
     * still gives the item back unchanged. An item can still go into a project that has none.
     */
    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> out = new ArrayList<>();
        for (InstallableIndustryItemPlugin p : super.getInstallableItems()) {
            out.add(!(p instanceof GenericInstallableItemPlugin) ? p : new GenericInstallableItemPlugin(this) {
                @Override
                public boolean isMenuItemEnabled() {
                    return !itemLocked();
                }

                @Override
                public void createMenuItemTooltip(TooltipMakerAPI tooltip, boolean expanded) {
                    super.createMenuItemTooltip(tooltip, expanded);
                    if (itemLocked()) tooltip.addPara("The item stays installed while the project runs, and wears when it ends: "
                            + "a Pristine item becomes Worn, a Worn one is used up. Shutting the project down gives it back unchanged.", 10f, Misc.getNegativeHighlightColor());
                }
            });
        }
        return out;
    }

    /** The installed item would wear at the end of the running project: it cannot come out now. */
    private boolean itemLocked() {
        String item = itemId();
        return running() && item != null && !item.equals(Items.afterEnd(item, true));
    }

    /**
     * The project or a repeat-mode step did its work: the installed item wears ({@link Items#afterEnd}). A Worn item
     * that comes out stays installed for the next step, or goes to storage with the end ({@link #close}).
     */
    private void wear(List<String> lines) {
        String before = itemId(), after = Items.afterEnd(before, true);
        if (before == null || before.equals(after)) return;
        setSpecialItem(after == null ? null : new SpecialItemData(after, null));
        lines.add(after == null ? "The " + itemName(before) + " was used up" : "The " + itemName(before) + " wore down to a " + itemName(after));
    }

    /** Repeat mode: the step ended (title); a message with these lines and the next step, which starts now. */
    private void nextStep(DtiData.Project next, String title, List<String> lines) {
        lines.add("Step " + (step() + 1) + " starts: it " + does(next));
        Terraform.message(market, getCurrentName() + ": " + title, lines.toArray(new String[0]));
        step = step() + 1;
        begin(next);
        reapply();
    }

    /** What else happens when the project ends by itself, after the condition is gone; adds lines to the message. */
    protected void projectFinished(List<String> lines) {
    }

    /** One line for the tooltip about what else happens at the end; null for nothing. */
    protected String endText() {
        return null;
    }

    private String typeId() {
        return market.getPlanetEntity() == null ? "" : market.getPlanetEntity().getTypeId();
    }

    /** Ends the project without unrest and removes the structure. Its AI core and item go to the colony's storage ({@link #close}). */
    private void end(String title, String... lines) {
        ended = true;
        close(this, title, lines);
    }

    /**
     * Ends with nothing changed: on the player's colony, the refund a shutdown would give now (the user, 2026-10-23),
     * so none in step 2 or later of repeat mode.
     */
    private void endUnused(String title, String... lines) {
        float refund = market.isPlayerOwned()
                ? ProjectClock.shutdownRefund(paidCost(), Global.getSettings().getFloat("industryRefundFraction"), step()) : 0f;
        if (refund > 0f) {
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
            lines = Arrays.copyOf(lines, lines.length + 1);
            lines[lines.length - 1] = "You got back " + Misc.getDGSCredits(refund) + ", as for a shutdown";
        }
        end(title, lines);
    }

    /**
     * Removes a DTI building with a message and no unrest. Its AI core and its item go to the player's colony's storage;
     * on another colony they go with the building.
     */
    static void close(Industry industry, String title, String... lines) {
        MarketAPI market = industry.getMarket();
        String core = industry.getAICoreId();
        SpecialItemData item = industry.getSpecialItem();
        List<String> all = new ArrayList<>(Arrays.asList(lines));
        // removeIndustry with the API's null mode gives no core or item back, so DTI moves them to the storage itself.
        CargoAPI storage = market.isPlayerOwned() ? Misc.getStorageCargo(market) : null;
        if (core != null && storage != null) {
            storage.addCommodity(core, 1);
            all.add("The " + Global.getSettings().getCommoditySpec(core).getName() + " went to the colony's storage");
            industry.setAICoreId(null);
        }
        if (item != null && storage != null) {
            storage.addSpecial(item, 1);
            all.add("The " + itemName(item.getId()) + " went to the colony's storage");
            industry.setSpecialItem(null);
        }
        Terraform.message(market, industry.getCurrentName() + ": " + title, all.toArray(new String[0]));
        market.removeIndustry(industry.getId(), null, false);
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        // DTI's own ends set ended first, so running() is false for them.
        DtiData.Project p = project();
        if (!forUpgrade && depositGood(p) != null && Extraction.fallout(!running(), ownerChanged(), market.hasCondition(p.condition))) {
            ExtractionFallout.later(market, getCurrentName());
        }
        if (running() && !forUpgrade) {
            ProjectClock.Rules r = DtiSettings.rules();
            int points = ProjectClock.freeRemoval(step()) ? 0 : ProjectClock.removalUnrest(progress / totalDays(), r);
            if (points > 0) RecentUnrest.get(market).add(points, (area() == Area.TERRAFORMING ? "Terraforming project" : "Colony project") + " cancelled");
            ended = true;
        }
        market.getHazard().unmodifyFlat(getModId());
        TerraformingTrouble.sync(market);
        // Shut down, or ended with nothing changed: its objects leave the orbit too.
        if (orbit != null) Terraform.removeOrbitObjects(orbit);
        orbit = null;
    }

    // ---- effects on the colony ----

    @Override
    public void apply() {
        super.apply(true);
        for (String id : new ArrayList<>(demand.keySet())) demand(id, 0);
        for (String id : new ArrayList<>(supply.keySet())) supply(id, 0);
        DtiData.Project p = running() ? project() : null;
        if (p != null) {
            for (Map.Entry<String, Integer> e : goods(p).entrySet()) demand(e.getKey(), e.getValue());
            // An extraction yields only while its project runs; AI cores add nothing (no vanilla supply bonus here).
            if (depositGood(p) != null && state() == State.RUNNING) supply(depositGood(p), yieldNow(p));
            // A condition that DTI's own support structures suppress has no hazard to cut (domed-cities.md).
            float removed = SupportStructure.suppressedHere(market).contains(p.condition) ? 0f : Terraform.hazard(p.condition);
            float cut = ProjectClock.hazardCut(removed, Terraform.hazard(p.adds), progress / totalDays());
            market.getHazard().modifyFlat(getModId(), -cut, getNameForModifier());
        } else {
            market.getHazard().unmodifyFlat(getModId());
            // An example market shows every good its projects can ask for, each at its most.
            if (Terraform.example(market)) {
                for (DtiData.Project r : DtiData.projectsOf(getId())) {
                    for (Map.Entry<String, Integer> e : goods(r).entrySet()) {
                        if (e.getValue() > getDemand(e.getKey()).getQuantity().getModifiedInt()) demand(e.getKey(), e.getValue());
                    }
                }
                DtiData.Project t = DtiData.target(getId(), market);
                if (depositGood(t) != null) supply(depositGood(t), yieldNow(t));
            }
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getHazard().unmodifyFlat(getModId());
    }

    /**
     * The build menu shows and charges this on an instance made for the colony; the game then saves the price paid as
     * the override. While a project runs, the game's shutdown refund is this cost x its own share; see ProjectClock.refundCost.
     */
    @Override
    public float getBuildCost() {
        float cost = paidCost();
        if (!running()) return cost;
        return ProjectClock.refundCost(cost, step());
    }

    /** The price paid (the game saves it as the override), else the menu price: the step's cost, after the build cost setting. */
    private float paidCost() {
        Float paid = getBuildCostOverride();
        return paid != null ? paid : price(market == null ? null : DtiData.target(getId(), market), getSpec());
    }

    /** The menu price of a step: its row's cost mult, else the industries.csv cost, after the build cost setting. */
    public static float price(DtiData.Project p, com.fs.starfarer.api.loading.IndustrySpecAPI spec) {
        // The game turns a cost mult into credits this way when it loads industries.csv.
        float cost = p == null || p.cost == null ? spec.getCost()
                : p.cost * Global.getSettings().getFloat("creditsPerCostUnit") * Global.getSettings().getFloat("industryBuildCostMult");
        return cost * DtiSettings.number("dti_buildCostMult", 1f);
    }

    // ---- AI cores: the core here sets the speed; the Control's core cuts the upkeep ----

    /** research-gating.md (2026-11-05): a researched tier 2 tech cuts the upkeep of its whole line ({@link DtiData#lineTech}). */
    public static final float RESEARCH_UPKEEP_MULT = 0.9f;
    private static final String RESEARCH_UPKEEP_KEY = "dti_research_upkeep";

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
        // The Control's core cuts the upkeep of terraforming structures only; a core here never cuts upkeep.
        if (area() == Area.TERRAFORMING) TerraformingControl.applyUpkeepCut(this);
        String tech = DtiData.lineTech(getId());
        if (tech != null && Unlocks.researched(tech)) getUpkeep().modifyMult(RESEARCH_UPKEEP_KEY, RESEARCH_UPKEEP_MULT, Unlocks.techName(tech));
        else getUpkeep().unmodifyMult(RESEARCH_UPKEEP_KEY);
    }

    @Override
    protected void updateAICoreToSupplyAndDemandModifiers() {
        // No vanilla demand cut: the core only changes the speed.
    }

    @Override
    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addCore(tooltip, mode, "Alpha");
    }

    @Override
    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addCore(tooltip, mode, "Beta");
    }

    @Override
    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        addCore(tooltip, mode, "Gamma");
    }

    private void addCore(TooltipMakerAPI tooltip, AICoreDescriptionMode mode, String level) {
        ProjectClock.Rules r = DtiSettings.rules();
        int i = ProjectClock.core(level.toLowerCase() + "_core");
        TerraformingControl.addCoreText(tooltip, mode, level,
                "The project works %s faster. When it goes back, it goes back at %s speed instead of %s.",
                (int) (r.speedBonus[i] * 100) + "%", (int) (r.goingBack[i] * 100) + "%", (int) (r.goingBack[0] * 100) + "%");
    }

    @Override
    public boolean canBeDisrupted() {
        return false;
    }

    // ---- build menu ----

    /**
     * In the menu at all: what {@link #menuTarget} asks, and for a pollution cleanup no building that keeps polluting.
     */
    private boolean offered() {
        if (!super.isAvailableToBuild()) return false;
        DtiData.Project p = menuTarget(getId(), market);
        return p != null && !keepsPolluting(p);
    }

    /**
     * The project a DTI structure would run here if the menu offers it, else null: its area allows it (the area's switch,
     * a Control for terraforming), unlocked, turned on, the colony has a condition for it, and that project row's star
     * rule fits the system ({@link Light#offers}).
     */
    static DtiData.Project menuTarget(String id, MarketAPI market) {
        if (!menuAllows(id, market)) return null;
        DtiData.Project p = DtiData.target(id, market);
        if (p == null || !Light.offers(p.star, realStar(market))) return null;
        // A ladder marker does nothing but change the planet type: offered only when the preview shows a change.
        return DtiData.ladder().marker(p.adds) && newType(market, p) == null ? null : p;
    }

    /** The build menu, before the colony's conditions: the structure's area allows it (ADR 0011), it is unlocked and turned on. */
    static boolean menuAllows(String id, MarketAPI market) {
        DtiData.Structure s = DtiData.structure(id);
        if (s == null || !s.area.allows(DtiSettings.on(s.area.setting), market.getIndustry(DtiData.CONTROL) != null) || !Unlocks.unlocked(id)) return false;
        return s.setting.isEmpty() || DtiSettings.on(s.setting);
    }

    /**
     * The planet type after this project as the tooltip shows it, or null when the type stays: the ladder preview (ADR 0006)
     * from the conditions at the start of a running project, or from the conditions now.
     */
    public String typeAfter(DtiData.Project p) {
        TypeLadder.Result r = ladderAfter(p);
        return r == null ? null : r.type;
    }

    /** The ladder's answer for this project as the tooltip previews it, or null when no rule fires. */
    private TypeLadder.Result ladderAfter(DtiData.Project p) {
        TypeLadder.Result r = Terraform.typeChange(market, running() ? conditionsAtStart : Terraform.conditionIds(market), p.after(Terraform.conditionIds(market)));
        return r == null || r.rules.isEmpty() ? null : r;
    }

    /** The planet type after this project, by the ladder preview (ADR 0006), or null when the type stays. */
    static String newType(MarketAPI market, DtiData.Project p) {
        Set<String> now = Terraform.conditionIds(market);
        TypeLadder.Result r = Terraform.typeChange(market, now, p.after(now));
        return r == null || r.rules.isEmpty() ? null : r.type;
    }

    /**
     * Another DTI building on the colony (built, building or queued) already works on this one's condition, one removes
     * the condition the other works on (the atmosphere), or both change the water ({@link ProjectRow#clashes}). The Labor Camps count too: one way at a time for a decivilized
     * subpopulation.
     */
    static boolean conditionTaken(Industry self) {
        return taken(self) != null;
    }

    /** The project of another DTI building on the colony that clashes with this one's ({@link #conditionTaken}), or null. */
    private static DtiData.Project taken(Industry self) {
        MarketAPI market = self.getMarket();
        DtiData.Project mine = DtiData.target(self.getId(), market);
        if (mine == null) return null;
        for (Industry i : market.getIndustries()) {
            if (i == self || !DtiData.isProjectStructure(i.getId())) continue;
            boolean running = i instanceof ProjectStructure && ((ProjectStructure) i).running();
            DtiData.Project p = running ? ((ProjectStructure) i).project() : DtiData.target(i.getId(), market);
            if (p != null && p.clashes(mine, DtiData.ladder()::marker)) return p;
        }
        for (ConstructionQueueItem q : market.getConstructionQueue().getItems()) {
            DtiData.Project p = DtiData.isProjectStructure(q.id) ? DtiData.target(q.id, market) : null;
            if (p != null && p.clashes(mine, DtiData.ladder()::marker)) return p;
        }
        return null;
    }

    /** The colony's system has a star whose light the Solar Concentrator and the Mirrors can use ({@link Light#realStar}). */
    public static boolean realStar(MarketAPI market) {
        StarSystemAPI system = market.getStarSystem();
        if (system == null) return false;
        List<Boolean> blackHoles = new ArrayList<>();
        for (PlanetAPI star : new PlanetAPI[]{system.getStar(), system.getSecondary(), system.getTertiary()}) {
            if (star != null) blackHoles.add(star.isBlackHole());
        }
        return Light.realStar(system.isNebula(), system.isDeepSpace(), blackHoles);
    }

    @Override
    public boolean isAvailableToBuild() {
        return offered() && !conditionTaken(this);
    }

    @Override
    public boolean showWhenUnavailable() {
        return offered();
    }

    @Override
    public String getUnavailableReason() {
        return takenReason(this);
    }

    static String takenReason(Industry self) {
        DtiData.Project p = DtiData.target(self.getId(), self.getMarket()), other = taken(self);
        if (p == null || other == null) return null;
        // One atmosphere project at a time (the removes column).
        if (other.removesSubjectOf(p)) return "Another building already removes " + Terraform.conditionName(p.subject()) + ".";
        if (p.removesSubjectOf(other)) return "Another building already works on " + Terraform.conditionName(other.subject()) + ".";
        if (!other.subject().equals(p.subject())) return "Another building already changes this planet's water.";
        return "Another building already works on " + Terraform.conditionName(p.subject()) + ".";
    }

    // ---- progress bar and tooltip ----

    private String stateText() {
        switch (state()) {
            case STOPPED:
                return "stopped";
            case GOING_BACK:
                return "going back";
            case PAUSED:
                return "paused";
            case WAITING:
                return "waiting";
            default:
                int left = (int) Math.ceil((totalDays() - progress) / Math.max(0.01f, speed()));
                return left + (left == 1 ? " day" : " days");
        }
    }

    @Override
    public float getBuildOrUpgradeProgress() {
        return running() ? Math.min(1f, progress / totalDays()) : super.getBuildOrUpgradeProgress();
    }

    @Override
    public String getBuildOrUpgradeDaysText() {
        return running() ? stateText() : super.getBuildOrUpgradeDaysText();
    }

    @Override
    public String getBuildOrUpgradeProgressText() {
        if (!running()) return super.getBuildOrUpgradeProgressText();
        return state() == State.RUNNING ? work() + ": " + stateText() + " left" : work() + " " + stateText();
    }

    @Override
    public boolean isBuilding() {
        return super.isBuilding() || running();
    }

    @Override
    public boolean isUpgrading() {
        return !building && running();
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        Color h = Misc.getHighlightColor();
        DtiData.Project p = running() ? project() : DtiData.target(getId(), market);
        if (p == null && Terraform.example(market)) {
            exampleText(tooltip);
            return;
        }
        if (p == null) return;
        if (running()) {
            // Percent only: the days change with AI cores and shortages.
            tooltip.addPara("Progress: %s. Now: %s. Speed: %s.", 10f, h, (int) (100f * progress / totalDays()) + "%",
                    state() == State.RUNNING ? "running" : stateText(), (int) (speed() * 100f) + "%");
            if (!reason().isEmpty()) tooltip.addPara(reason(), 3f);
        } else {
            tooltip.addPara("When built, it %s in %s days of work. It needs every month: %s.", 10f, h,
                    does(p), "" + (int) totalDays(p), goodsText(p));
        }
        List<String> also = new ArrayList<>();
        for (String c : p.removes) {
            if (market.hasCondition(c)) also.add(Terraform.conditionName(c));
        }
        if (market.hasCondition(Support.METEORS) && Support.meteorsBurnUp(p.after(Terraform.conditionIds(market)))) also.add(Terraform.conditionName(Support.METEORS));
        if (!also.isEmpty()) tooltip.addPara("When it ends, it also removes %s.", 3f, h, String.join(", ", also));
        String good = depositGood(p);
        if (good != null) {
            String name = Global.getSettings().getCommoditySpec(good).getLowerCaseName();
            if (!running()) tooltip.addPara("While it works, it yields %s " + name + ", as much as a Mining building, and less as the deposit runs down.", 3f, h, "" + yieldNow(p));
            else if (state() == State.RUNNING) tooltip.addPara("It yields %s " + name + " now.", 3f, h, "" + yieldNow(p));
            else tooltip.addPara("It yields nothing while the project is " + stateText() + ".", 3f);
            tooltip.addPara("Removing it before the deposit is gone %s.", 3f, Misc.getNegativeHighlightColor(), "poisons the colony");
        }
        if (repeat()) addSteps(tooltip, p);
        if (endText() != null) tooltip.addPara(endText(), 3f);
        if (area() != Area.TERRAFORMING) return; // a colony project never changes the planet type
        TypeLadder.Result after = ladderAfter(p);
        if (after != null) {
            tooltip.addPara("Planet type after the project: %s.", 3f, h, Terraform.typeName(after.type));
            for (Industry i : Terraform.foodBuildingsLost(market, after)) {
                tooltip.addPara("When it ends, %s shuts down, and you get the shutdown refund.", 3f, Misc.getNegativeHighlightColor(), i.getCurrentName());
            }
            for (ConstructionQueueItem q : Terraform.foodBuildingsQueuedLost(market, after)) {
                tooltip.addPara("When it ends, %s leaves the construction queue, and you get its full price back.", 3f,
                        Misc.getNegativeHighlightColor(), Global.getSettings().getIndustrySpec(q.id).getName());
            }
        }
    }

    /** On an example market (AoTD's research screen, the Codex): everything its projects can do, and how long they take. */
    private void exampleText(TooltipMakerAPI tooltip) {
        Set<String> does = new LinkedHashSet<>();
        int min = Integer.MAX_VALUE, max = 0;
        for (DtiData.Project r : DtiData.projectsOf(getId())) {
            does.add(does(r));
            min = Math.min(min, (int) totalDays(r));
            max = Math.max(max, (int) totalDays(r));
        }
        if (does.isEmpty()) return;
        tooltip.addPara("When built, it %s in %s days of work.", 10f, Misc.getHighlightColor(),
                String.join(", or ", does), min == max ? "" + min : min + " to " + max);
    }

    /**
     * Repeat mode (ADR 0010): every step this structure will do on this colony, in order, from the one it works on or
     * would start with; where it stops; and when removal is free.
     */
    private void addSteps(TooltipMakerAPI tooltip, DtiData.Project p) {
        Color h = Misc.getHighlightColor();
        int first = running() ? step() : 1;
        List<DtiData.Project> steps = steps(p, first);
        tooltip.addPara("It works in steps, one after another:", 10f);
        for (int i = 0; i < steps.size(); i++) {
            String now = running() && i == 0 ? " (now)" : "";
            tooltip.addPara("Step %s" + now + ": " + does(steps.get(i)) + ".", 3f, h, "" + (first + i));
        }
        int last = first + steps.size() - 1;
        tooltip.addPara("It stops after step %s and removes itself.", 3f, h, "" + last);
        // Free removal is for step 2 and later: a plan of one step has none.
        if (last < 2) return;
        tooltip.addPara("From step 2 on, removal causes no unrest, but gives no money back.", 3f);
        if (running() && ProjectClock.freeRemoval(step())) tooltip.addPara("Removing it now causes no unrest.", 3f);
    }

    /** Repeat mode: step {@code first} (project p) and the steps after it on this colony, up to step MAX_STEPS. */
    private List<DtiData.Project> steps(DtiData.Project p, int first) {
        List<DtiData.Project> steps = new ArrayList<>();
        steps.add(p);
        List<DtiData.Project> more = ProjectRow.chain(DtiData.projectsOf(getId()), p.after(Terraform.conditionIds(market)), Terraform.suppressed(market), Terraform.archetype(market), Terraform.farmlandBest(market));
        // It stops after step MAX_STEPS (ProjectRow.next), counted from step 1.
        steps.addAll(more.subList(0, Math.max(0, Math.min(more.size(), ProjectRow.MAX_STEPS - first))));
        return steps;
    }

    /** Repeat mode: the step a running project stops after, as the tooltip counts it; its own step for any other project. */
    public int lastStep() {
        DtiData.Project p = project();
        return repeat() && p != null ? step() + steps(p, step()).size() - 1 : step();
    }

    /** What a project does, for the tooltips: "removes Hot", "adds Mild Climate", "turns Poor Farmland into Adequate Farmland". */
    static String does(DtiData.Project p) {
        // A ladder marker has no name for the player; the tooltip shows the planet type after the project.
        boolean adds = !p.adds.isEmpty() && !DtiData.ladder().marker(p.adds);
        if (p.condition.isEmpty()) return adds ? "adds " + Terraform.conditionName(p.adds) : "changes the planet";
        String removes = Terraform.conditionName(p.condition);
        return adds ? "turns " + removes + " into " + Terraform.conditionName(p.adds) : "removes " + removes;
    }

    /** "1 day", "12 days". */
    static String days(int n) {
        return n + (n == 1 ? " day" : " days");
    }

    /** Why the project is not running at full speed; blank while it runs. */
    public String reason() {
        if (state() == State.RUNNING) return "";
        if (state() == State.PAUSED) return "The colony changed owner, and the settings pause its projects.";
        if (state() == State.WAITING) {
            String blocker = blocker(project());
            if (blocker != null) return "Waiting: " + Terraform.conditionName(blocker) + " holds this project until it is gone.";
            Industry source = pollutionSource(market);
            SpecialItemSpecAPI item = source == null ? null : itemSpec(source);
            if (item == null) return "Waiting: a building keeps polluting this colony.";
            return "Waiting: " + source.getCurrentName() + " uses the " + item.getName() + ", which keeps polluting this colony.";
        }
        if (noControl()) return "No working Terraforming Control on this colony.";
        if (shortGoods >= 2) return shortGoods + " kinds of goods are short.";
        if (state() == State.GOING_BACK) return "Goods have been short for " + days((int) shortageDays) + ".";
        return "One kind of goods is short: " + days((int) shortageDays) + " so far. At " + days((int) rules.declineAfterDays)
                + " the project starts to go back.";
    }

    /**
     * The goods the project needs every month, after dti_goodsMult: the basic goods (the goods column) less the item's
     * cut ({@link Items#goodsMult}), and with AoTD the AoTD goods, which no item cuts.
     */
    private Map<String, Integer> goods(DtiData.Project p) {
        float mult = DtiSettings.number("dti_goodsMult", 1f);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : p.goods.entrySet()) out.put(e.getKey(), Cells.scaled(e.getValue(), mult * Items.goodsMult(itemId())));
        if (Unlocks.aotd()) {
            for (Map.Entry<String, Integer> e : p.aotdGoods.entrySet()) out.put(e.getKey(), Cells.scaled(e.getValue(), mult));
        }
        return out;
    }

    private String goodsText(DtiData.Project p) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : goods(p).entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append(' ').append(Global.getSettings().getCommoditySpec(e.getKey()).getLowerCaseName());
        }
        return sb.toString();
    }
}
