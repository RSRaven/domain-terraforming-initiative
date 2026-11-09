package dti.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.DtiData;
import dti.DtiSettings;
import dti.Terraform;
import dti.logic.Support;

import java.math.BigDecimal;

/**
 * The domes (docs/features/domed-cities.md): one building with three looks, Domed Cities, Sky Cities and Seafloor Cities.
 * The look follows the planet each time it applies. While the domes work: their list is suppressed, the colony's
 * maximum size is one less, its ground defense changes, and its Cryorevival Facility stays disrupted.
 */
public class Domes extends SupportStructure {
    /** An AoTD Floating City is already a city in a gas giant's sky: no domes there. */
    static final String FLOATING_CITY = "aotd_floating_city";
    /** The Cryorevival Facility is disrupted this long, again every frame while the domes work. */
    static final float CRYO_DAYS = 2f;

    @Override
    public Support.By by() {
        return Support.look(market.getPlanetEntity() != null && market.getPlanetEntity().isGasGiant(), market.hasCondition(Support.WATER));
    }

    @Override
    public String getCurrentName() {
        if (market == null) return super.getCurrentName();
        switch (by()) {
            case SKY:
                return "Sky Cities";
            case SEAFLOOR:
                return "Seafloor Cities";
            default:
                return super.getCurrentName();
        }
    }

    @Override
    public String getCurrentImage() {
        if (market == null) return super.getCurrentImage();
        switch (by()) {
            case SKY:
                return "graphics/dti/industry/dti_domed_cities_sky.png";
            case SEAFLOOR:
                return "graphics/dti/industry/dti_domed_cities_seafloor.png";
            default:
                return super.getCurrentImage();
        }
    }

    @Override
    protected String getDescriptionOverride() {
        if (market == null) return null;
        switch (by()) {
            case SKY:
                return "Sealed cities ride the winds of the upper atmosphere, held up by lift cells and thrusters. "
                        + "Their hulls keep out the storms and the poison below, though they make poor fortresses.";
            case SEAFLOOR:
                return "Pressure domes stand on the seabed, linked to the surface by lifts and tunnels. "
                        + "The ocean above keeps out the weather and the hostile life, and any attacker must come down through it.";
            default:
                return null;
        }
    }

    /** Ground defense in percent: Seafloor Cities have the sea on their side. */
    private int defense() {
        return by() == Support.By.SEAFLOOR ? DtiSettings.integer("dti_seafloorDefense", 200) : DtiSettings.integer("dti_domeDefense", 25);
    }

    @Override
    public void apply() {
        super.apply();
        if (!works()) {
            unmodify();
            return;
        }
        market.getStats().getDynamic().getMod(Stats.MAX_MARKET_SIZE).modifyFlat(getModId(), -1f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyMult(getModId(), defense() / 100f, getNameForModifier());
    }

    @Override
    public void unapply() {
        super.unapply();
        unmodify();
    }

    private void unmodify() {
        market.getStats().getDynamic().getMod(Stats.MAX_MARKET_SIZE).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId());
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        // A disrupted building is not functional, and the base game's Cryorevival Facility adds growth only when it is.
        Industry cryo = market.getIndustry(Industries.CRYOREVIVAL);
        if (cryo != null && works()) cryo.setDisrupted(CRYO_DAYS, true);
    }

    @Override
    public void notifyBeingRemoved(MarketAPI.MarketInteractionMode mode, boolean forUpgrade) {
        super.notifyBeingRemoved(mode, forUpgrade);
        // No effect stays: the Cryorevival Facility works again at once, unless a raid disrupted it for longer.
        Industry cryo = market.getIndustry(Industries.CRYOREVIVAL);
        if (cryo != null && cryo.getDisruptedDays() <= CRYO_DAYS) cryo.setDisrupted(0f);
    }

    @Override
    protected boolean offered() {
        return !market.hasCondition(FLOATING_CITY) && super.offered();
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);
        tooltip.addPara("While they stand: maximum colony size %s, ground defense %s.", 3f, Misc.getHighlightColor(),
                "-1", "x" + BigDecimal.valueOf(defense()).movePointLeft(2).stripTrailingZeros().toPlainString());
        tooltip.addPara("They keep the colony's Cryorevival Facility disrupted, so it gives no growth.", 3f, Misc.getNegativeHighlightColor());
        // An example market shows the Domed Cities look; the other two follow the planet.
        if (Terraform.example(market)) {
            tooltip.addPara("On a gas giant they are Sky Cities, with the same effects.", 10f);
            tooltip.addPara("On a water world they are Seafloor Cities: only these conditions have no effect: %s. Ground defense %s.", 3f,
                    Misc.getHighlightColor(), names(DtiData.suppression(Support.By.SEAFLOOR)),
                    "x" + BigDecimal.valueOf(DtiSettings.integer("dti_seafloorDefense", 200)).movePointLeft(2).stripTrailingZeros().toPlainString());
        }
    }
}
