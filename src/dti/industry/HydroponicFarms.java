package dti.industry;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Pair;
import dti.Terraform;
import dti.logic.Support;

/**
 * Hydroponic Farms (docs/features/hydroponic-farms.md): an industry that makes food on a colony that cannot farm. Food is
 * the colony size -2; it needs heavy machinery equal to the colony size, as the base game's food industries. Offered only
 * with no farmland and no water surface; once built it keeps working if a project adds farmland later.
 */
public class HydroponicFarms extends BaseIndustry {
    @Override
    public void apply() {
        super.apply(true);
        int size = market.getSize();
        demand(0, Commodities.HEAVY_MACHINERY, size, BASE_VALUE_TEXT);
        supply(0, Commodities.FOOD, Support.hydroponicFood(size), BASE_VALUE_TEXT);
        Pair<String, Integer> deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY);
        // Slot 1, as the base game's industries: in slot 0 the shortage replaced the food, so Production never showed.
        applyDeficitToProduction(1, deficit, Commodities.FOOD);
        if (!isFunctional()) supply.clear();
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && ProjectStructure.menuAllows(getId(), market) && Support.hydroponicsFit(Terraform.conditionIds(market));
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
