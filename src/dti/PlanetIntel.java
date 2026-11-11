package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.logic.Area;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A planet's DTI history (docs/features/planet-history.md): one Intel entry for each planet that got a Terraforming
 * Control, with DTI's events and their dates. It stays when the Control goes or the colony is lost; the player can
 * delete it, and only a new Control makes a new one.
 */
public class PlanetIntel extends BaseIntelPlugin {
    private static final int HISTORY = 50;
    /** Sector memory: the entries for the Controls of a save from before 0.4.2 are made. */
    private static final String MADE_KEY = "$dti_historyEntriesMade";
    private static final Object OPEN = "dti_open_colony";

    private final String marketId;
    /** The planet stays in the save when the colony is lost. */
    private final PlanetAPI planet;
    /** Newest first: "date: event". */
    private final List<String> history = new ArrayList<>();

    private PlanetIntel(MarketAPI market) {
        marketId = market.getId();
        planet = market.getPlanetEntity();
    }

    private static PlanetIntel find(String marketId) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(PlanetIntel.class)) {
            if (((PlanetIntel) i).marketId.equals(marketId)) return (PlanetIntel) i;
        }
        return null;
    }

    /** The planet's entry; a new one for a player colony on a planet. Null when there is none and none can be made. */
    public static PlanetIntel get(MarketAPI market) {
        PlanetIntel entry = find(market.getId());
        if (entry != null || !market.isPlayerOwned() || market.getPlanetEntity() == null) return entry;
        entry = new PlanetIntel(market);
        Global.getSector().getIntelManager().addIntel(entry, true);
        return entry;
    }

    /** A DTI event with today's date, for a planet that has an entry. */
    public static void note(MarketAPI market, String event) {
        PlanetIntel entry = find(market.getId());
        if (entry == null) return;
        entry.history.add(0, Global.getSector().getClock().getDateString() + ": " + event);
        while (entry.history.size() > HISTORY) entry.history.remove(entry.history.size() - 1);
    }

    /** Once per save: an entry for each colony with a Terraforming Control, for a save from before 0.4.2. */
    static void ensureAll() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (memory.getBoolean(MADE_KEY)) return;
        for (MarketAPI m : Misc.getPlayerMarkets(true)) {
            if (m.hasIndustry(DtiData.CONTROL)) get(m);
        }
        memory.set(MADE_KEY, true);
    }

    /** The colony, while it is the player's; else null. */
    private MarketAPI colony() {
        MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
        return m != null && m.isPlayerOwned() ? m : null;
    }

    @Override
    protected String getName() {
        return planet.getName();
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "new_planet_info");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(ProjectsIntel.TAG);
        return tags;
    }

    @Override
    public String getSortString() {
        return "2 " + getName();
    }

    /** Hidden while both areas are off (ADR 0011). */
    @Override
    public boolean isHidden() {
        return super.isHidden() || !DtiSettings.on(Area.TERRAFORMING.setting) && !DtiSettings.on(Area.COLONY.setting);
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return planet;
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        int n = history.size();
        info.addPara(Terraform.typeName(planet.getTypeId()) + ", " + (n == 1 ? "1 DTI event" : n + " DTI events"), Misc.getGrayColor(), 3f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        MarketAPI colony = colony();
        info.showPlanetInfo(planet, width, 150f, false, 0f);
        String state = colony == null ? "not your colony now" : "your colony, hazard " + Math.round(colony.getHazardValue() * 100f) + "%";
        ProjectsIntel.line(info, Terraform.typeName(planet.getTypeId()) + ", " + state + ".", Misc.getTextColor(), opad);

        info.addSectionHeading("DTI history", Alignment.MID, opad);
        if (history.isEmpty()) info.addPara("No DTI event yet.", opad);
        for (String event : history) ProjectsIntel.line(info, event, Misc.getTextColor(), 3f);

        if (colony != null) info.addButton("Open the colony", OPEN, width, 20f, opad);
        addDeleteButton(info, width, "Delete this entry");
    }

    @Override
    protected void createDeleteConfirmationPrompt(TooltipMakerAPI prompt) {
        prompt.addPara("Delete the DTI history of " + getName() + "? You cannot get it back. A new Terraforming Control on this planet starts a new entry.",
                Misc.getTextColor(), 0f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == OPEN) {
            ProjectsIntel.openColony(marketId);
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }
}
