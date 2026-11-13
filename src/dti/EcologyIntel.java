package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import dti.industry.ProjectStructure;
import dti.logic.Ecology;

import java.util.Set;

/**
 * An ecology event of a running project: an Intel entry in the Terraforming tab.
 * With two choices it has two buttons, and the game does not stop; with no answer in 30 days, the second choice
 * applies. With one choice (Breakthrough) it just happens: the entry comes with no buttons. The choice goes into the
 * planet history. The list shows only the choice; the right side shows what it did.
 */
public class EcologyIntel extends BaseIntelPlugin {
    public static final String SETTING = "dti_ecologyEvents";
    private static final Object A = "dti_ecology_a", B = "dti_ecology_b";

    private final String marketId, structureId, eventId;
    /** The player had finished 3 projects of this structure when the event came: better odds and lower costs (Ecology.EXPERIENCED_*). */
    private final boolean experienced;
    private float daysLeft = Ecology.ANSWER_DAYS;
    /** Null while the event waits for an answer; then the history line of what happened. */
    private String outcome;
    /** The choice's button text ("Feed the bloom"; blank for a one-choice event), or null (open, or the project ended first). */
    private String choice;
    /** The game chose, after 30 days with no answer. */
    private boolean auto;
    /** What the choice did: "75,000 credits, progress +10%". */
    private String effects;

    private EcologyIntel(String marketId, String structureId, String eventId, boolean experienced) {
        this.marketId = marketId;
        this.structureId = structureId;
        this.eventId = eventId;
        this.experienced = experienced;
    }

    /** How many projects of this structure the player finished in this save (from 0.4.5 on). */
    private static int finished(String structureId) {
        return Global.getSector().getMemoryWithoutUpdate().getInt(Ecology.doneKey(structureId));
    }

    /** The player's crews know this structure's work (Ecology.EXPERIENCE). Also for DTI_Event. */
    public static boolean experienced(String structureId) {
        return Ecology.experienced(finished(structureId));
    }

    /** A random event that fits the project, if any. Only for a player colony, while the setting is on. */
    public static void start(ProjectStructure project) {
        MarketAPI market = project.getMarket();
        if (!market.isPlayerOwned() || !DtiSettings.on(SETTING)) return;
        Ecology.Event e = Ecology.pick(DtiData.ecologyEvents(), project.getId(), experienced(project.getId()), Misc.random);
        if (e != null) start(project, e);
    }

    /** This event on this project now (also DTI_Event). An event with one choice just happens: the entry has no buttons. */
    public static void start(ProjectStructure project, Ecology.Event e) {
        MarketAPI market = project.getMarket();
        EcologyIntel intel = new EcologyIntel(market.getId(), project.getId(), e.id, experienced(project.getId()));
        if (e.b == null) intel.choose(e.a, false);
        else intel.setImportant(true);
        // The game shows the new entry; a one-choice event shows what it did.
        Global.getSector().getIntelManager().addIntel(intel);
        // An Intel entry does not advance by itself: the sector runs it, and drops it when it has ended.
        Global.getSector().addScript(intel);
    }

    /** Pays a choice and applies it. Progress last: the project can finish there. */
    private static void apply(ProjectStructure p, Ecology.Event e, Ecology.Choice c, boolean experienced) {
        if (c.cost(experienced) > 0) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(c.cost(experienced));
        if (c.unrest > 0) RecentUnrest.get(p.getMarket()).add(c.unrest, e.name);
        if (c.progress != 0) p.addProgress(c.progress / 100f * p.totalDays());
    }

    private Ecology.Event event() {
        return DtiData.ecologyEvent(eventId);
    }

    private MarketAPI market() {
        return Global.getSector().getEconomy().getMarket(marketId);
    }

    /** The project, while it runs on a player colony; else null. */
    private ProjectStructure project() {
        MarketAPI m = market();
        Industry i = m == null || !m.isPlayerOwned() ? null : m.getIndustry(structureId);
        return i instanceof ProjectStructure && ((ProjectStructure) i).running() ? (ProjectStructure) i : null;
    }

    @Override
    protected void advanceImpl(float amount) {
        if (outcome != null) return;
        daysLeft -= Global.getSector().getClock().convertToDays(amount);
        Ecology.Event e = event();
        if (e == null || project() == null) close("The project ended first. Nothing changed.", true);
        else if (daysLeft <= 0f) choose(e.fallback(), true);
    }

    /** The player's choice, or the default when no answer came (auto: then a message tells it). */
    private void choose(Ecology.Choice c, boolean auto) {
        ProjectStructure p = project();
        if (p == null) {
            close("The project ended first. Nothing changed.", auto);
            return;
        }
        choice = c.label;
        this.auto = auto;
        effects = Misc.ucFirst(c.effects(experienced));
        String text = c.label.isEmpty() ? effects : c.label + ": " + c.effects(experienced) + (auto ? " (no answer)" : "");
        PlanetIntel.note(p.getMarket(), p.getCurrentName() + ": " + event().name, text);
        close(text, auto);
        apply(p, event(), c, experienced);
    }

    private void close(String text, boolean tell) {
        outcome = text;
        setImportant(false);
        // The game decided, not the player: show it on the screen.
        if (tell) sendUpdateIfPlayerHasIntel(null, false);
        endAfterDelay();
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        Ecology.Event e = event();
        if (outcome == null && e != null) {
            if (buttonId == A) choose(e.a, false);
            else if (buttonId == B && e.b != null) choose(e.b, false);
        }
        ui.updateUIForItem(this);
    }

    @Override
    protected String getName() {
        Ecology.Event e = event();
        MarketAPI m = market();
        return (e == null ? "Ecology event" : e.name) + (m == null ? "" : " at " + m.getName());
    }

    /** One short line in the list, so every entry has two lines: the choice, not what it did. */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        String line;
        if (outcome == null) line = "Answer in " + Math.max(1, Math.round(daysLeft)) + " days";
        else if (choice == null) line = effects == null && outcome.startsWith("The project ended") ? "The project ended first" : outcome;
        else if (choice.isEmpty()) line = effects;
        else line = choice + (auto ? " (no answer)" : "");
        info.addPara(line, Misc.getGrayColor(), 3f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Ecology.Event e = event();
        if (e == null) return;
        ProjectsIntel.line(info, Global.getSettings().getIndustrySpec(structureId).getName() + ": " + e.text, Misc.getTextColor(), opad);
        if (outcome != null) {
            if (choice == null) {
                ProjectsIntel.line(info, outcome, Misc.getGrayColor(), opad);
                return;
            }
            if (!choice.isEmpty()) ProjectsIntel.line(info, (auto ? "No answer in 30 days. The game chose: " : "Your choice: ") + choice + ".", Misc.getTextColor(), opad);
            info.setBulletedListMode(BULLET);
            ProjectsIntel.line(info, effects, Misc.getHighlightColor(), choice.isEmpty() ? opad : 3f);
            info.setBulletedListMode(null);
            return;
        }
        if (experienced) ProjectsIntel.line(info, "Your crews know this work: " + finished(structureId) + " " + Global.getSettings().getIndustrySpec(structureId).getName()
                + " projects done. Disasters are rarer, good news more common, and every cost here is 25% lower.", Misc.getPositiveHighlightColor(), opad);
        // The first button shows its cost; the second only its name, so both texts fit in their buttons.
        button(info, A, e.a, true, width, opad);
        if (e.b != null) button(info, B, e.b, false, width, 3f);
        ProjectsIntel.line(info, "With no answer in " + Math.max(1, Math.round(daysLeft)) + " days: " + e.fallback().label + ".", Misc.getGrayColor(), opad);
    }

    private void button(TooltipMakerAPI info, Object id, Ecology.Choice c, boolean showEffects, float width, float pad) {
        ButtonAPI b = info.addButton(showEffects ? c.label + " (" + c.buttonEffects(experienced) + ")" : c.label, id, width, 20f, pad);
        if (c.cost(experienced) > Global.getSector().getPlayerFleet().getCargo().getCredits().get()) b.setEnabled(false);
    }

    @Override
    public String getIcon() {
        return Global.getSector().getPlayerFaction().getCrest();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(ProjectsIntel.TAG);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        MarketAPI m = market();
        return m == null ? null : m.getPrimaryEntity();
    }
}
