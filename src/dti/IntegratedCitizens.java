package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * After Civic Integration: more population growth for a while, then the
 * condition removes itself.
 */
public class IntegratedCitizens extends BaseMarketConditionPlugin implements MarketImmigrationModifier {
    public static final String ID = "dti_integrated_citizens";

    protected float daysLeft;

    /** Starts the bonus on this colony, or starts it over. Returns its days; 0 when the settings turn it off. */
    public static int start(MarketAPI market) {
        int days = DtiSettings.integer("dti_civicGrowthDays", 180);
        if (days <= 0) return 0;
        if (market.getCondition(ID) == null) market.addCondition(ID);
        ((IntegratedCitizens) market.getCondition(ID).getPlugin()).daysLeft = days;
        market.reapplyCondition(market.getCondition(ID).getIdForPluginModifications());
        return days;
    }

    private float bonus() {
        return growth(market);
    }

    /** The growth bonus on this colony: it scales with the colony size. */
    public static float growth(MarketAPI market) {
        return market.getSize() * DtiSettings.integer("dti_civicGrowth", 100) / 100f;
    }

    @Override
    public void apply(String id) {
        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply(String id) {
        market.removeTransientImmigrationModifier(this);
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        incoming.getWeight().modifyFlat(getModId(), bonus(), condition.getName());
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        daysLeft -= Misc.getDays(amount);
        if (daysLeft <= 0) market.removeSpecificCondition(condition.getIdForPluginModifications());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        if (Global.CODEX_TOOLTIP_MODE) return;
        int days = (int) Math.ceil(daysLeft);
        tooltip.addPara("%s population growth (based on colony size), for %s more " + (days == 1 ? "day." : "days."), 10f,
                Misc.getHighlightColor(), "+" + (int) bonus(), "" + days);
    }

    @Override
    public boolean isTransient() {
        return false;
    }
}
