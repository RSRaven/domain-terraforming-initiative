package dti.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import dti.DtiData;
import dti.DtiSettings;
import dti.Terraform;
import dti.logic.CampEffects;

import java.util.Map;

/**
 * Labor Camps (docs/features/colony-development.md): a permanent building, no project. While it stands, the colony's
 * upkeep goes down and its growth up, at a cost in stability and Luddic Path interest. While any of its goods is
 * short, the upkeep cut and the growth bonus stop and the colony protests ({@link CampEffects}). AI cores work as in vanilla.
 * The player can shut it down at no cost; it closes by itself when its condition is gone.
 */
public class LaborCamps extends BaseIndustry implements MarketImmigrationModifier {
    static final float PATHER_INTEREST = 2f;

    protected transient float dayTimer;

    @Override
    public void apply() {
        super.apply(true);
        // Its projects.csv row gives the condition it needs and its monthly goods.
        DtiData.Project p = DtiData.target(getId(), market);
        // An example market (AoTD's research screen, the Codex) has no decivilized subpopulation: it shows the goods anyway.
        if (p == null && Terraform.example(market) && !DtiData.projectsOf(getId()).isEmpty()) p = DtiData.projectsOf(getId()).get(0);
        if (p != null) {
            for (Map.Entry<String, Integer> e : p.goods.entrySet()) demand(e.getKey(), e.getValue());
        }
        CampEffects e = effects();
        if (e.upkeepCut > 0) market.getUpkeepMult().modifyMult(getModId(), 1f - e.upkeepCut / 100f, getNameForModifier());
        else market.getUpkeepMult().unmodifyMult(getModId());
        market.getStability().modifyFlat(getModId(), -stability(), getNameForModifier());
        if (e.protest > 0) market.getStability().modifyFlat(getModId(1), -e.protest, getNameForModifier() + ": goods short");
        else market.getStability().unmodifyFlat(getModId(1));
        if (!isFunctional()) unapply();
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getUpkeepMult().unmodifyMult(getModId());
        market.getStability().unmodifyFlat(getModId());
        market.getStability().unmodifyFlat(getModId(1));
    }

    private static int upkeepCut() {
        return DtiSettings.integer("dti_laborUpkeepCut", 15);
    }

    private static int stability() {
        return DtiSettings.integer("dti_laborStability", 1);
    }

    private static int shortStability() {
        return DtiSettings.integer("dti_laborShortStability", 1);
    }

    private float growth() {
        return market.getSize() * DtiSettings.integer("dti_laborGrowth", 100) / 100f;
    }

    private CampEffects effects() {
        return new CampEffects(!shortGoods().isEmpty(), upkeepCut(), growth(), shortStability());
    }

    /** The goods short now, by name, or "" when none is. DTI_Short forces a count instead, for tests. */
    private String shortGoods() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (memory.contains(ProjectStructure.TEST_SHORT)) {
            int n = memory.getInt(ProjectStructure.TEST_SHORT);
            return n == 0 ? "" : n + (n == 1 ? " kind of goods (DTI_Short)" : " kinds of goods (DTI_Short)");
        }
        StringBuilder sb = new StringBuilder();
        for (Pair<String, Integer> d : getAllDeficit()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Global.getSettings().getCommoditySpec(d.one).getLowerCaseName());
        }
        return sb.toString();
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        float growth = effects().growth;
        if (growth > 0) incoming.getWeight().modifyFlat(getModId(), growth, getNameForModifier());
    }

    @Override
    public float getPatherInterest() {
        return super.getPatherInterest() + (isFunctional() ? PATHER_INTEREST : 0f);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        dayTimer += Global.getSector().getClock().convertToDays(amount);
        if (dayTimer < 1f) return;
        dayTimer = 0f;
        if (!isBuilding() && DtiData.target(getId(), market) == null) {
            // Gone or hidden: the camps close, and the player gets the same refund as for a shutdown.
            String why = "The decivilized subpopulation is gone, so nobody is left to put to work";
            if (!market.isPlayerOwned()) {
                ProjectStructure.close(this, "Closed", why);
                return;
            }
            float refund = getBuildCost() * Global.getSettings().getFloat("industryRefundFraction");
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
            ProjectStructure.close(this, "Closed", why, "You got back " + Misc.getDGSCredits(refund) + ", as for a shutdown");
            return;
        }
        // A shortage starts or ends: the effects follow within a day.
        reapply();
    }

    // ---- build menu: the same rules as the projects, and one way at a time ----

    /** The build cost setting, as for the project buildings; once built, the price paid (the game saves it as the override). */
    @Override
    public float getBuildCost() {
        return getBuildCostOverride() != null ? super.getBuildCost() : super.getBuildCost() * DtiSettings.number("dti_buildCostMult", 1f);
    }

    private boolean offered() {
        return super.isAvailableToBuild() && ProjectStructure.menuTarget(getId(), market) != null;
    }

    @Override
    public boolean isAvailableToBuild() {
        return offered() && !ProjectStructure.conditionTaken(this);
    }

    @Override
    public boolean showWhenUnavailable() {
        return offered();
    }

    @Override
    public String getUnavailableReason() {
        return ProjectStructure.takenReason(this);
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        tooltip.addPara("While the camps stand: colony upkeep %s, stability %s, population growth %s (based on colony size), "
                        + "Luddic Path interest %s. They close by themselves when the decivilized subpopulation is gone, and you get the shutdown refund.",
                10f, Misc.getHighlightColor(), "-" + upkeepCut() + "%", "-" + stability(), "+" + (int) growth(), "+" + (int) PATHER_INTEREST);
        String goods = mode == IndustryTooltipMode.ADD_INDUSTRY || !isFunctional() ? "" : shortGoods();
        if (!goods.isEmpty()) {
            tooltip.addPara("Short: %s. The upkeep cut and the growth bonus have stopped, and the colony protests: stability %s more "
                    + "until the goods come.", 3f, Misc.getNegativeHighlightColor(), goods, "-" + shortStability());
        }
    }
}
