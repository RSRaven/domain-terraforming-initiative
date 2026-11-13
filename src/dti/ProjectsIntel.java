package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import dti.industry.ProjectStructure;
import dti.logic.Area;
import dti.logic.Items;
import dti.logic.Overview;
import dti.logic.Overview.Group;
import dti.logic.Overview.Row;
import dti.logic.Overview.Sort;
import dti.logic.Overview.StateFilter;
import dti.logic.ProjectClock;
import dti.logic.ProjectClock.State;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The "DTI Projects" Intel entry: every running project on the player's colonies,
 * with filters and sorts, and a click on a row opens the colony. One per game, added at load. It saves only the chosen
 * filters and sort: the rows are rebuilt from the player's markets each time it draws.
 */
public class ProjectsIntel extends BaseIntelPlugin {
    /** The tag TaSC uses for its projects. */
    public static final String TAG = "Terraforming";

    // Saved with the game.
    private StateFilter stateFilter = StateFilter.ALL;
    private Group group = Group.ALL;
    private Sort sort = Sort.SYSTEM;

    /** Adds the entry when the game has none. */
    public static void ensure() {
        IntelManagerAPI intel = Global.getSector().getIntelManager();
        if (!intel.hasIntelOfClass(ProjectsIntel.class)) intel.addIntel(new ProjectsIntel(), true);
    }

    @Override
    protected String getName() {
        return "DTI Projects";
    }

    /** First in the tab, then the Terraforming Survey, then the planet files. */
    @Override
    public String getSortString() {
        return "0";
    }

    /** A line of text that may hold a "%": the game reads a plain line as a format, so it goes in as the one value. */
    static void line(TooltipMakerAPI info, String text, Color color, float pad) {
        info.addPara("%s", pad, color, text);
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "new_planet_info");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);
        return tags;
    }

    /** Hidden while both content areas are off. */
    @Override
    public boolean isHidden() {
        return super.isHidden() || !DtiSettings.on(Area.TERRAFORMING.setting) && !DtiSettings.on(Area.COLONY.setting);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(Overview.summary(rows()), Misc.getGrayColor(), 3f);
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    /** Every running DTI project on the player's colonies. */
    static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        ProjectClock.Rules rules = DtiSettings.rules();
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            StarSystemAPI system = market.getStarSystem();
            for (Industry i : market.getIndustries()) {
                if (!(i instanceof ProjectStructure) || !((ProjectStructure) i).running()) continue;
                ProjectStructure p = (ProjectStructure) i;
                DtiData.Project project = p.project();
                if (project == null) continue;
                float days = Overview.daysLeft(p.progress(), p.totalDays(), p.state(), p.getAICoreId(), Items.bonus(p.itemId()), rules);
                Row r = new Row(market.getName(), system == null ? "" : system.getBaseName(), p.getCurrentName(), p.area(), p.state(), days);
                r.marketId = market.getId();
                r.industryId = p.getId();
                if (p.repeat()) r.work = "Step " + p.step() + " of " + p.lastStep();
                else if (DtiData.ladder().marker(project.subject())) {
                    // A ladder marker (the Comet Shepherd, the Hydrosphere Extractor) has no name for the player: the type after, as in the tooltip.
                    String type = p.typeAfter(project);
                    r.work = type == null ? "No type change" : "To " + Terraform.typeName(type);
                } else r.work = Terraform.conditionName(project.subject());
                r.percent = (int) (100f * p.progress() / p.totalDays());
                List<String> names = new ArrayList<>();
                for (Pair<String, Integer> d : p.getAllDeficit()) names.add(Global.getSettings().getCommoditySpec(d.one).getName());
                r.shortGoods = String.join(", ", names);
                rows.add(r);
            }
        }
        return rows;
    }

    static String stateName(State s) {
        switch (s) {
            case STOPPED:
                return "Stopped";
            case GOING_BACK:
                return "Going back";
            case WAITING:
                return "Waiting";
            case PAUSED:
                return "Paused";
            default:
                return "Running";
        }
    }

    private static Color stateColor(State s) {
        if (s == State.RUNNING) return Misc.getTextColor();
        return s == State.GOING_BACK ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10f, top = 0f;
        List<Row> all = rows();
        if (all.isEmpty()) {
            TooltipMakerAPI info = panel.createUIElement(width, height, false);
            info.addPara("None of your colonies has a project now.", 0f);
            panel.addUIElement(info).inTL(0, 0);
            return;
        }
        TooltipMakerAPI head = panel.createUIElement(width, 20f, false);
        head.addPara(Overview.summary(all) + ". Click a line to open the colony.", Misc.getGrayColor(), 0f);
        panel.addUIElement(head).inTL(0, top);
        top += 25f;
        // A save from before 0.4.0 may hold Stopped or Going back, which have no button now.
        if (stateFilter == StateFilter.STOPPED || stateFilter == StateFilter.GOING_BACK) stateFilter = StateFilter.PROBLEMS;
        top = buttons(panel, top, "State:", new Object[]{StateFilter.ALL, StateFilter.RUNNING, StateFilter.PROBLEMS},
                new String[]{"All", "Running", "Problems"}, stateFilter);
        top = buttons(panel, top, "Group:", new Object[]{Group.ALL, Group.TERRAFORMING, Group.COLONY},
                new String[]{"All", "Terraforming", "Colony Development"}, group);
        top = buttons(panel, top, "Sort:", new Object[]{Sort.SYSTEM, Sort.END, Sort.COLONY},
                new String[]{"Star system", "Closest to its end", "Colony"}, sort);

        TooltipMakerAPI info = panel.createUIElement(width, height - top - opad, true);
        float w = width - 20f;
        List<Row> shown = Overview.view(all, stateFilter, group, sort);
        if (shown.isEmpty()) info.addPara("No project fits these filters.", 5f);
        String system = null;
        for (List<Row> box : Overview.byColony(shown).values()) {
            MarketAPI market = Global.getSector().getEconomy().getMarket(box.get(0).marketId);
            if (market == null) continue;
            // Sorted by star system: a heading per system, as the colony list has.
            if (sort == Sort.SYSTEM && !box.get(0).system.equals(system)) {
                system = box.get(0).system;
                info.addSectionHeading(system.isEmpty() ? "Hyperspace" : system, Alignment.MID, 15f);
            }
            info.addCustom(colonyLine(panel, market, w), 10f);
            for (Row r : box) info.addCustom(projectLine(panel, market, r, w), 2f);
        }
        panel.addUIElement(info).inTL(0, top + opad);
    }

    /** The head of a colony's box: the planet, its name and size, its type and star system, its condition icons. */
    private static CustomPanelAPI colonyLine(CustomPanelAPI parent, MarketAPI market, float w) {
        float h = 56f, x = 5f;
        CustomPanelAPI line = parent.createCustomPanel(w, h, null);
        hover(line, market.getId(), w, h, null);
        PlanetAPI planet = market.getPlanetEntity();
        if (planet != null) {
            TooltipMakerAPI pic = line.createUIElement(h, h, false);
            pic.showPlanetInfo(planet, h - 6f, h - 6f, false, 3f);
            line.addUIElement(pic).inTL(x, 0f);
        }
        x += h + 10f;
        TooltipMakerAPI name = line.createUIElement(300f, h, false);
        name.addPara(market.getName() + ", size " + market.getSize(), Misc.getHighlightColor(), 9f);
        String where = market.getStarSystem() == null ? "hyperspace" : market.getStarSystem().getNameWithLowercaseType();
        name.addPara((planet == null ? "Station" : Terraform.typeName(planet.getTypeId())) + ", " + where, Misc.getGrayColor(), 3f);
        line.addUIElement(name).inTL(x, 0f);
        x += 310f;
        float size = 28f, gap = 4f;
        List<String> icons = new ArrayList<>(), names = new ArrayList<>();
        for (MarketConditionAPI c : market.getConditions()) {
            if (icons.size() >= (int) ((w - x) / (size + gap))) break;
            // Only what a DTI building can work on, planetary or not (Decivilized Subpopulation): no population, no pirate activity.
            if (!DtiData.changeable(c.getId()) || c.getPlugin() == null || !c.getPlugin().showIcon()) continue;
            icons.add(c.getPlugin().getIconName());
            names.add(c.getName());
        }
        if (!icons.isEmpty()) {
            TooltipMakerAPI cond = line.createUIElement(icons.size() * (size + gap), h, false);
            cond.addImages(icons.size() * (size + gap), size, (h - size) / 2f, gap, icons.toArray(new String[0]));
            cond.addTooltipToPrevious(text(String.join(", ", names)), TooltipMakerAPI.TooltipLocation.BELOW);
            line.addUIElement(cond).inTL(x, 0f);
        }
        return line;
    }

    /**
     * One project in a colony's box: the building's picture, its name, what it works on, a progress bar, the state and
     * the short goods. Under the mouse it shows the building's own tooltip. A long list of short goods makes it taller.
     */
    private static CustomPanelAPI projectLine(CustomPanelAPI parent, MarketAPI market, Row r, float w) {
        Industry industry = market.getIndustry(r.industryId);
        float h = 30f, indent = 20f, picW = 48f, barW = w * 0.14f;
        float[] cols = {w * 0.22f, w * 0.2f, barW, w * 0.1f};
        float goodsX = indent + picW + 10f + cols[0] + cols[1] + cols[2] + cols[3];
        CustomPanelAPI line = parent.createCustomPanel(w, h, null);
        String[] texts = {r.structure, r.work, r.percent + "%", stateName(r.state)};
        Color[] colors = {Misc.getTextColor(), Misc.getTextColor(), Misc.getHighlightColor(), stateColor(r.state)};
        List<TooltipMakerAPI> cells = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            // The percent sits right of the bar.
            TooltipMakerAPI cell = line.createUIElement(i == 2 ? 40f : cols[i] - 10f, h, false);
            cell.addPara(texts[i], colors[i], 8f);
            cells.add(cell);
        }
        TooltipMakerAPI goods = line.createUIElement(w - goodsX - 5f, h, false);
        goods.addPara(r.shortGoods, Misc.getNegativeHighlightColor(), 8f);
        h = Math.max(h, goods.getHeightSoFar() + 8f);
        line.getPosition().setSize(w, h);
        hover(line, market.getId(), w, h, industry == null ? null : buildingTooltip(industry));
        if (industry != null) {
            TooltipMakerAPI pic = line.createUIElement(picW, h, false);
            pic.addImage(industry.getCurrentImage(), picW, picW / 2f, (h - picW / 2f) / 2f);
            line.addUIElement(pic).inTL(indent, 0f);
        }
        float x = indent + picW + 10f;
        for (int i = 0; i < cells.size(); i++) {
            if (i == 2) {
                Color bar = r.state == State.RUNNING ? Misc.getBasePlayerColor() : stateColor(r.state);
                line.addComponent(line.createCustomPanel(barW - 50f, 8f, new Bar(r.percent / 100f, bar))).inTL(x, (h - 8f) / 2f);
                line.addUIElement(cells.get(i)).inTL(x + barW - 45f, 0f);
            } else {
                line.addUIElement(cells.get(i)).inTL(x, 0f);
            }
            x += cols[i];
        }
        line.addUIElement(goods).inTL(goodsX, 0f);
        return line;
    }

    /**
     * A wide button under a line: it lights up under the mouse, as the rows of the colony list do, and a click opens the
     * colony ({@link #buttonPressConfirmed}). Added first, so the texts lie on top of it.
     */
    static void hover(CustomPanelAPI line, String marketId, float w, float h, TooltipMakerAPI.TooltipCreator tip) {
        TooltipMakerAPI back = line.createUIElement(w, h, false);
        back.addAreaCheckbox("", marketId, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(), w, h, 0f);
        if (tip != null) back.addTooltipToPrevious(tip, TooltipMakerAPI.TooltipLocation.RIGHT);
        line.addUIElement(back).inTL(0f, 0f);
    }

    /** The building's own tooltip, as in the colony screen. */
    private static TooltipMakerAPI.TooltipCreator buildingTooltip(final Industry industry) {
        return new BaseTooltipCreator() {
            @Override
            public float getTooltipWidth(Object param) {
                return 450f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
                try {
                    industry.createTooltip(Industry.IndustryTooltipMode.NORMAL, tooltip, expanded);
                } catch (RuntimeException e) {
                    // ponytail: the colony screen is the building tooltip's home; if it breaks here, a plain line instead.
                    Global.getLogger(ProjectsIntel.class).warn("DTI: no building tooltip in DTI Projects", e);
                    tooltip.addPara(industry.getCurrentName(), 0f);
                }
            }
        };
    }

    static TooltipMakerAPI.TooltipCreator text(final String s) {
        return new BaseTooltipCreator() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object param) {
                tooltip.addPara(s, 0f);
            }
        };
    }

    /** A thin progress bar: the dark track, then the done part in the given color. */
    private static final class Bar extends BaseCustomUIPanelPlugin {
        private final float done;
        private final Color color;
        private PositionAPI p;

        Bar(float done, Color color) {
            this.done = Math.max(0f, Math.min(1f, done));
            this.color = color;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            p = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (p == null) return;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Misc.renderQuad(p.getX(), p.getY(), p.getWidth(), p.getHeight(), Misc.getDarkPlayerColor(), alphaMult);
            Misc.renderQuad(p.getX(), p.getY(), p.getWidth() * done, p.getHeight(), color, alphaMult);
        }
    }

    /** One row of filter buttons with its label; the chosen one is checked. Returns the y below the row. */
    static float buttons(CustomPanelAPI panel, float y, String label, Object[] ids, String[] names, Object chosen) {
        float x = 0f, labelW = 60f, bh = 20f;
        TooltipMakerAPI l = panel.createUIElement(labelW, bh, false);
        l.addPara(label, 3f);
        panel.addUIElement(l).inTL(x, y);
        x += labelW;
        for (int i = 0; i < ids.length; i++) {
            TooltipMakerAPI b = panel.createUIElement(200f, bh, false);
            float bw = b.computeStringWidth(names[i]) + 30f;
            ButtonAPI button = b.addAreaCheckbox(names[i], ids[i], Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Misc.getBrightPlayerColor(), bw, bh, 0f);
            button.setChecked(ids[i] == chosen);
            panel.addUIElement(b).inTL(x, y);
            x += bw + 5f;
        }
        return y + bh + 5f;
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId instanceof String) {
            openColony(buttonId);
            return;
        }
        if (buttonId instanceof StateFilter) stateFilter = (StateFilter) buttonId;
        else if (buttonId instanceof Group) group = (Group) buttonId;
        else if (buttonId instanceof Sort) sort = (Sort) buttonId;
        ui.updateUIForItem(this);
    }

    /** Opens the colony's screen, as a vanilla COLONY_INFO message does. */
    static void openColony(Object marketId) {
        MarketAPI market = Global.getSector().getEconomy().getMarket((String) marketId);
        if (market != null) Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.OUTPOSTS, market);
    }
}
