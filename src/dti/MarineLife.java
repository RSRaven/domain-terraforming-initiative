package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.logic.FoodBuildings;

import java.util.Set;

/**
 * Marine life (docs/features/marine-biosphere.md): a flat food bonus to Aquaculture and to every building of its upgrade
 * chain (AoTD's Fishing Harbour). Not vanilla's ResourceDepositsCondition: that would add the colony size again. The
 * bonus has its own modifier id, so the industry's own apply() leaves it alone, as with vanilla's farmland.
 */
public class MarineLife extends BaseMarketConditionPlugin {
    @Override
    public void apply(String id) {
        super.apply(id);
        int food = FoodBuildings.marineFood(condition.getId());
        Set<String> chain = Terraform.foodChain(FoodBuildings.AQUACULTURE);
        for (Industry i : market.getIndustries()) {
            if (!chain.contains(i.getId())) continue;
            if (i.isFunctional()) i.supply(id, Commodities.FOOD, food, condition.getName());
            else i.getSupply(Commodities.FOOD).getQuantity().unmodifyFlat(id);
        }
    }

    @Override
    public void unapply(String id) {
        super.unapply(id);
        Set<String> chain = Terraform.foodChain(FoodBuildings.AQUACULTURE);
        for (Industry i : market.getIndustries()) {
            if (chain.contains(i.getId())) i.getSupply(Commodities.FOOD).getQuantity().unmodifyFlat(id);
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        StringBuilder names = new StringBuilder();
        for (String id : Terraform.foodChain(FoodBuildings.AQUACULTURE)) {
            if (names.length() > 0) names.append(", ");
            names.append(Global.getSettings().getIndustrySpec(id).getName());
        }
        String bonus = "+" + FoodBuildings.marineFood(condition.getId());
        tooltip.addPara("%s food production (" + names + ")", 10f, Misc.getHighlightColor(), bonus);
    }
}
