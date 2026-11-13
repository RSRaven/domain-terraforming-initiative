package dti.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.DtiData;
import dti.DtiSettings;
import dti.Unlocks;
import dti.logic.ProjectClock;

/** One per colony. Every project structure needs it working; an AI core here cuts the upkeep of all of them. */
public class TerraformingControl extends BaseIndustry {
    private static final String UPKEEP_KEY = "dti_controlCore";

    @Override
    public void apply() {
        super.apply(true);
    }

    /** A planet with a built Control gets its DTI history entry. */
    @Override
    protected void buildingFinished() {
        super.buildingFinished();
        if (dti.PlanetIntel.get(market) != null) dti.PlanetIntel.note(market, getCurrentName() + " built");
    }

    @Override
    public boolean isAvailableToBuild() {
        return super.isAvailableToBuild() && DtiSettings.terraforming() && market.getPlanetEntity() != null && Unlocks.unlocked(getId());
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        int projects = 0;
        for (Industry i : market.getIndustries()) {
            if (i instanceof ProjectStructure && ((ProjectStructure) i).running() && ((ProjectStructure) i).area().needsControl) projects++;
        }
        tooltip.addPara("Terraforming projects running on this colony: %s. While this building is under construction or "
                + "disrupted, or after it is removed, every terraforming project here goes back.", 10f, Misc.getHighlightColor(), "" + projects);
    }

    @Override
    public void setAICoreId(String aiCoreId) {
        super.setAICoreId(aiCoreId);
        if (market == null) return;
        // The upkeep cut of the project buildings shows at once, not on their next daily update.
        for (Industry i : market.getIndustries()) {
            if (i instanceof ProjectStructure) i.reapply();
        }
    }

    @Override
    protected void applyAICoreToIncomeAndUpkeep() {
        applyUpkeepCut(this);
    }

    /** The upkeep cut of the AI core in the colony's Control, for the Control and every project building. */
    static void applyUpkeepCut(Industry industry) {
        String core = coreId(industry.getMarket());
        float mult = ProjectClock.upkeepMult(core, DtiSettings.rules());
        if (mult < 1f) {
            String name = Global.getSettings().getCommoditySpec(core).getName() + " in Terraforming Control";
            industry.getUpkeep().modifyMult(UPKEEP_KEY, mult, name);
        } else {
            industry.getUpkeep().unmodifyMult(UPKEEP_KEY);
        }
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
        float cut = DtiSettings.rules().upkeepCut[ProjectClock.core(level.toLowerCase() + "_core")];
        addCoreText(tooltip, mode, level, "Cuts the upkeep of this building and of every terraforming structure on this colony by %s.",
                (int) (cut * 100) + "%");
    }

    /** One AI core text in the vanilla style, with the core's icon in tooltips. */
    static void addCoreText(TooltipMakerAPI tooltip, AICoreDescriptionMode mode, String level, String text, String... values) {
        String pre = level + "-level AI core" + (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST
                || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP ? ". " : " currently assigned. ");
        TooltipMakerAPI target = tooltip;
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP || mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            target = tooltip.beginImageWithText(Global.getSettings().getCommoditySpec(level.toLowerCase() + "_core").getIconName(), 48);
        }
        target.addPara(pre + text, target == tooltip ? 10f : 0f, Misc.getHighlightColor(), values);
        if (target != tooltip) tooltip.addImageWithText(10f);
    }

    /** True when the colony's Control is built, not disrupted. */
    public static boolean works(MarketAPI market) {
        Industry c = market.getIndustry(DtiData.CONTROL);
        return c != null && c.isFunctional();
    }

    public static String coreId(MarketAPI market) {
        Industry c = market.getIndustry(DtiData.CONTROL);
        return c == null ? null : c.getAICoreId();
    }
}
