package dti.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.DtiData;
import dti.Terraform;
import dti.logic.Support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A standing Colony Development building that suppresses conditions while it works: the City Agrav Grid and
 * the Impact Defense Grid; the domes add their costs in {@link Domes}. Its list is its row in suppressions.csv: the
 * data column of industries.csv names it ("agrav", "impact"). With nothing to do it stays, and its upkeep goes on.
 */
public class SupportStructure extends BaseIndustry {
    public static final String DOMES = "dti_domed_cities";
    /** The Impact Defense Grid's guns also fight on the ground: ground defense x1.1, as vanilla Ground Defenses gives x1.5. */
    public static final float IMPACT_DEFENSE = 0.1f;

    /** The conditions it suppresses now. Saved, so it releases exactly these after a change of look or a load. */
    protected LinkedHashSet<String> suppressing;

    protected transient float dayTimer;

    /** Its list in suppressions.csv. */
    public Support.By by() {
        return Support.By.of(getSpec().getData());
    }

    /** It suppresses now: built and not disrupted, and for the City Agrav Grid, the domes stand. */
    public boolean works() {
        return isFunctional() && (!by().needsDomes() || domesStand(market));
    }

    /** The colony's domes are built and not disrupted. */
    static boolean domesStand(MarketAPI market) {
        Industry domes = market.getIndustry(DOMES);
        return domes != null && domes.isFunctional();
    }

    /** What it suppresses on this colony now. */
    public Set<String> targets() {
        return works() ? Support.targets(DtiData.suppression(by()), Terraform.conditionIds(market)) : Collections.<String>emptySet();
    }

    /**
     * The conditions DTI's own support structures suppress on this colony. Terraforming still works on them: the build
     * menu offers their projects, and a project's hazard cut leaves them out.
     */
    public static Set<String> suppressedHere(MarketAPI market) {
        Set<String> out = new LinkedHashSet<>();
        for (Industry i : market.getIndustries()) {
            if (i instanceof SupportStructure) out.addAll(((SupportStructure) i).targets());
        }
        return out;
    }

    @Override
    public void apply() {
        super.apply(true);
        release();
        suppressing = new LinkedHashSet<>(targets());
        for (String c : suppressing) market.suppressCondition(c);
        if (by() == Support.By.IMPACT && works()) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(getModId(), 1f + IMPACT_DEFENSE, getNameForModifier());
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        release();
    }

    private void release() {
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId());
        if (suppressing != null) {
            for (String c : suppressing) market.unsuppressCondition(c);
        }
        suppressing = null;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        dayTimer += Global.getSector().getClock().convertToDays(amount);
        if (dayTimer < 1f) return;
        dayTimer = 0f;
        // The domes went, a condition went, the planet changed its look: the effects follow within a day.
        Set<String> before = suppressing == null ? Collections.<String>emptySet() : new LinkedHashSet<>(suppressing);
        reapply();
        if (!before.equals(suppressing)) market.reapplyConditions();
    }

    // ---- build menu ----

    /** Its area, unlock and toggle allow it, and the colony has a condition of its list that nothing else suppresses. */
    protected boolean offered() {
        return ProjectStructure.menuAllows(getId(), market) && Support.offered(DtiData.suppression(by()), Terraform.conditionIds(market), Terraform.suppressed(market));
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && offered() && (!by().needsDomes() || domesBuilt(market));
    }

    /** The domes are built, not under construction. */
    private static boolean domesBuilt(MarketAPI market) {
        Industry domes = market.getIndustry(DOMES);
        return domes != null && !domes.isBuilding();
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    // ---- tooltip ----

    /** The names of these conditions: "High Gravity, Low Gravity". */
    static String names(Set<String> conditions) {
        List<String> out = new ArrayList<>();
        for (String c : conditions) out.add(Terraform.conditionName(c));
        return String.join(", ", out);
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        Set<String> list = DtiData.suppression(by());
        // An example market (AoTD's research screen, the Codex) has none of them: it shows the whole list.
        Set<String> here = Terraform.example(market) ? list : Support.targets(list, Terraform.conditionIds(market));
        if (Terraform.example(market) || mode == IndustryTooltipMode.ADD_INDUSTRY || isBuilding()) {
            tooltip.addPara("While it stands, these conditions have no effect: %s.", 10f, Misc.getHighlightColor(), names(here));
            return;
        }
        if (!works() && isFunctional()) {
            tooltip.addPara("It does nothing now: it works only together with the domes (Domed, Sky or Seafloor Cities). Its upkeep goes on.", 10f, Misc.getNegativeHighlightColor());
        } else if (here.isEmpty()) {
            tooltip.addPara("It has nothing to stop now: the colony has none of these conditions: %s. Its upkeep goes on.", 10f, Misc.getNegativeHighlightColor(), names(list));
        } else if (works()) {
            tooltip.addPara("These conditions have no effect: %s.", 10f, Misc.getHighlightColor(), names(here));
        }
    }

    @Override
    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        return by() == Support.By.IMPACT;
    }

    /** The ground defense line, as vanilla Ground Defenses shows it. */
    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (by() == Support.By.IMPACT) addGroundDefensesImpactSection(tooltip, IMPACT_DEFENSE, (String[]) null);
    }
}
