package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.industry.ProjectStructure;
import dti.logic.ProjectClock;
import dti.logic.ProjectClock.State;

/**
 * The stability penalty of stopped or going-back projects, as a colony condition with its own icon.
 * One penalty per colony: the worst state of its running projects (they do not add up). The extraction fallout
 * ({@link ExtractionFallout}) adds its own penalty on top, until the pollution is gone.
 */
public class TerraformingTrouble extends BaseMarketConditionPlugin {
    public static final String ID = "dti_terraforming_trouble";

    /** Adds, updates or removes the condition to match the colony's projects. */
    public static void sync(MarketAPI market) {
        MarketConditionAPI c = market.getCondition(ID);
        if (penalty(market) + fallout(market) == 0) {
            if (c != null) market.removeSpecificCondition(c.getIdForPluginModifications());
        } else if (c == null) {
            market.reapplyCondition(market.addCondition(ID));
        } else {
            market.reapplyCondition(c.getIdForPluginModifications());
        }
    }

    private static State worst(MarketAPI market) {
        ProjectClock.Rules rules = DtiSettings.rules();
        State worst = null;
        for (Industry i : market.getIndustries()) {
            if (!(i instanceof ProjectStructure) || !((ProjectStructure) i).running()) continue;
            State s = ((ProjectStructure) i).state();
            if (ProjectClock.unrest(s, rules) > (worst == null ? 0 : ProjectClock.unrest(worst, rules))) worst = s;
        }
        return worst;
    }

    private static int penalty(MarketAPI market) {
        State worst = worst(market);
        return worst == null ? 0 : ProjectClock.unrest(worst, DtiSettings.rules());
    }

    /** The extraction fallout penalty (dti_falloutStability). */
    public static int falloutPenalty() {
        return DtiSettings.integer("dti_falloutStability", 2);
    }

    /** The fallout penalty on this colony now: while the fallout holds, else 0. */
    private static int fallout(MarketAPI market) {
        return ExtractionFallout.holds(market) ? falloutPenalty() : 0;
    }

    @Override
    public void apply(String id) {
        State worst = worst(market);
        if (worst == null) {
            market.getStability().unmodifyFlat(id);
        } else {
            market.getStability().modifyFlat(id, -ProjectClock.unrest(worst, DtiSettings.rules()),
                    worst == State.STOPPED ? "Project stopped" : "Project going back");
        }
        if (fallout(market) > 0) market.getStability().modifyFlat(id + "_fallout", -fallout(market), "Extraction fallout");
        else market.getStability().unmodifyFlat(id + "_fallout");
    }

    @Override
    public void unapply(String id) {
        market.getStability().unmodifyFlat(id);
        market.getStability().unmodifyFlat(id + "_fallout");
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        if (Global.CODEX_TOOLTIP_MODE) return;
        if (fallout(market) > 0) {
            tooltip.addPara("Extraction fallout: %s stability, until the pollution is gone.", 10f, Misc.getHighlightColor(), "-" + fallout(market));
        }
        if (penalty(market) == 0) return;
        tooltip.addPara("%s stability, from the project in the worst state.", 10f, Misc.getHighlightColor(), "-" + penalty(market));
        float pad = 3f;
        for (Industry i : market.getIndustries()) {
            if (!(i instanceof ProjectStructure) || !((ProjectStructure) i).running()) continue;
            String reason = ((ProjectStructure) i).reason();
            if (reason.isEmpty()) continue;
            tooltip.addPara(BaseIntelPlugin.BULLET + i.getCurrentName() + ": " + reason, pad);
            pad = 0f;
        }
    }

    @Override
    public boolean isTransient() {
        return false;
    }
}
