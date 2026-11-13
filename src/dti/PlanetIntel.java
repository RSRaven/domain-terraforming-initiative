package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.logic.Area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A planet's DTI history: one Intel entry for each planet that got a Terraforming
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
    /** The events that changed the planet's type: event -> {old type, new type}. Null in a save from before 0.4.5. */
    private Map<String, String[]> typeChanges = new HashMap<>();
    /** The planet's type at the last event. */
    private String lastType;

    private PlanetIntel(MarketAPI market) {
        marketId = market.getId();
        planet = market.getPlanetEntity();
        lastType = planet.getTypeId();
    }

    private Object readResolve() {
        if (typeChanges == null) typeChanges = new HashMap<>();
        if (lastType == null) lastType = planet.getTypeId();
        return this;
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
    public static void note(MarketAPI market, String title, String... lines) {
        PlanetIntel entry = find(market.getId());
        if (entry == null) return;
        // Saved as "date\ntitle\nline\nline"; an event from before 0.4.5 is one line, "date: text".
        StringBuilder s = new StringBuilder(Global.getSector().getClock().getDateString()).append('\n').append(title);
        boolean typeLine = false;
        for (String l : lines) {
            s.append('\n').append(l);
            typeLine |= l.startsWith("Planet type: ");
        }
        String line = s.toString(), type = entry.planet.getTypeId();
        // Only DTI's own type changes get the pictures: their message has this line (ProjectStructure, ExtractionFallout).
        if (!type.equals(entry.lastType) && typeLine) entry.typeChanges.put(line, new String[]{entry.lastType, type});
        entry.lastType = type;
        entry.history.add(0, line);
        while (entry.history.size() > HISTORY) entry.typeChanges.remove(entry.history.remove(entry.history.size() - 1));
    }

    /**
     * The planet's type just before a DTI change (ProjectStructure, ExtractionFallout): the "Before" picture of the next
     * event, even when a change with no event came first (an AI owner's project, the console, another mod).
     */
    public static void typeBefore(MarketAPI market, String type) {
        PlanetIntel entry = find(market.getId());
        if (entry != null) entry.lastType = type;
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

    /** Hidden while both content areas are off. */
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
        for (String event : history) {
            String[] parts = event.split("\n");
            if (parts.length < 2) {
                ProjectsIntel.line(info, event, Misc.getTextColor(), opad);
            } else {
                // The date in gray, the title bright, each change a bullet.
                ProjectsIntel.line(info, parts[0], Misc.getGrayColor(), opad);
                ProjectsIntel.line(info, parts[1], Misc.getHighlightColor(), 2f);
                info.setBulletedListMode(BULLET);
                for (int k = 2; k < parts.length; k++) ProjectsIntel.line(info, parts[k], Misc.getTextColor(), 2f);
                info.setBulletedListMode(null);
            }
            String[] change = typeChanges.get(event);
            if (change != null) beforeAfter(info, change[0], change[1], width);
        }

        if (colony != null) info.addButton("Open the colony", OPEN, width, 20f, opad);
        addDeleteButton(info, width, "Delete this entry");
    }

    /** The planet before and after a type change, side by side, each with its type name under it. */
    private void beforeAfter(TooltipMakerAPI info, String oldType, String newType, float width) {
        float gap = 10f, w = (width - gap) / 2f, h = w / 1.62f, tall = 0f;
        CustomPanelAPI panel = Global.getSettings().createCustom(width, h + 22f, null);
        String[] types = {oldType, newType};
        for (int k = 0; k < 2; k++) {
            TooltipMakerAPI column = panel.createUIElement(w, h + 22f, false);
            PlanetAPI look = look(types[k]);
            if (look != null) column.showPlanetInfo(look, w, h, false, 0f);
            column.addPara((k == 0 ? "Before: " : "After: ") + Terraform.typeName(types[k]), Misc.getGrayColor(), look == null ? 0f : 3f).setAlignment(Alignment.MID);
            tall = Math.max(tall, column.getHeightSoFar());
            panel.addUIElement(column).inTL(k * (w + gap), 0f);
        }
        // A long type name ("Before: Terran Eccentric") takes two lines: the panel grows with it.
        panel.getPosition().setSize(width, tall);
        info.addCustom(panel, 3f);
    }

    /**
     * A copy of the planet with another type, only for its picture. The game draws a picture only for a planet that
     * exists, so the copy is added to the planet's location and taken out at once; the picture keeps the object.
     */
    private PlanetAPI look(String type) {
        LocationAPI where = planet.getContainingLocation();
        if (where == null) return null;
        try {
            PlanetAPI copy = where.addPlanet(Misc.genUID(), planet, planet.getName(), type, 0f, planet.getRadius(), planet.getRadius() * 4f, 100f);
            where.removeEntity(copy);
            return copy;
        } catch (RuntimeException e) {
            // An unknown type (a mod that is gone): the name only.
            Global.getLogger(PlanetIntel.class).warn("DTI: no picture for the planet type " + type, e);
            return null;
        }
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
