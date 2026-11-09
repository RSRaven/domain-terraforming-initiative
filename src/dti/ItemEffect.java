package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.logic.Cells;
import dti.logic.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The effect of a DTI item (docs/features/special-items.md), for the game's item menus and tooltips. It changes nothing
 * by itself: ProjectStructure reads the installed item for the speed, the goods and the wear.
 */
public class ItemEffect extends BaseInstallableItemEffect {
    public ItemEffect(String id) {
        super(id);
    }

    /**
     * At game start: an effect for every DTI item, and the structures each fits (the item column of structures.csv) in
     * its plugin params, which the game's install menu and the item tooltip read. Params another mod set stay.
     */
    static void register() {
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            String family = Items.family(spec.getId());
            if (family == null) continue;
            ItemEffectsRepo.ITEM_EFFECTS.put(spec.getId(), new ItemEffect(spec.getId()));
            List<String> fits = new ArrayList<>(Cells.list(spec.getParams()));
            for (DtiData.Structure s : DtiData.structures()) {
                if (s.item.equals(family) && !fits.contains(s.id)) fits.add(s.id);
            }
            spec.setParams(String.join(", ", fits));
        }
    }

    @Override
    public void apply(Industry industry) {
    }

    @Override
    public void unapply(Industry industry) {
    }

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data, InstallableItemDescriptionMode mode, String pre, float pad) {
        String id = data.getId(), share = (int) (Items.bonus(id) * 100) + "%";
        text.addPara(pre + effect(id), pad, Misc.getHighlightColor(), share, share);
        text.addPara(Items.pristine(id) ? "It wears to Worn when the project ends, or when a step ends in a building that works in steps."
                : "It is used up when the project ends, or when a step ends in a building that works in steps.", 3f);
        text.addPara("It comes back as it was if the project ends with nothing changed, or if you remove the building.", 3f);
    }

    /** What the item does, with a %s for its share: a Worn item gives speed only, a Pristine one its second effect too. */
    static String effect(String itemId) {
        if (!Items.pristine(itemId)) return "The project works %s faster.";
        if (Items.yields(itemId)) return "The project works %s faster and yields %s more.";
        return "The project works %s faster and needs %s fewer goods." + (Unlocks.aotd() ? " The AoTD goods stay the same." : "");
    }
}
