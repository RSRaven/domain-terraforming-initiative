package dti.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarData;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.SpecBarEventCreator;
import com.fs.starfarer.api.loading.BarEventSpec;
import dti.ContractMission;
import dti.ContractWork;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.List;

/**
 * DTI_Contract on|off|done: for tests. On: the contract bar event is very common until the
 * game restarts; typed while docked, that bar shows it first. Done: the work of every delivered contract ends now.
 */
public class DtiContract implements BaseCommand {
    private static final String ID = "dtiContract";
    /** Vanilla BarCMD: the events a bar showed, kept 20 to 40 days. The bar shows only these until then. */
    private static final String SHOWN = "$BarCMD_shownEvents";
    private static final float TEST_FREQ = 1000f;
    /** The bar_events.csv value, kept from the first "on". */
    private static Float normal;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        BarEventSpec spec = Global.getSettings().getBarEventSpec(ID);
        if (spec == null) {
            Console.showMessage("The contract bar event is not loaded.");
            return CommandResult.ERROR;
        }
        if (normal == null) normal = spec.getFreq();
        String arg = args.trim();
        if (arg.equalsIgnoreCase("on")) {
            spec.setFreq(TEST_FREQ);
            // An accepted or missed offer sets a timeout of 30 to 365 days before the next one: clear it.
            BarEventManager bars = BarEventManager.getInstance();
            for (BarEventManager.GenericBarEventCreator c : bars.getCreators()) {
                if (c instanceof SpecBarEventCreator && ID.equals(((SpecBarEventCreator) c).getBarEventId())) bars.getTimeout().remove(c);
            }
            Console.showMessage("Contracts are very common at bars now, until you restart the game or type 'dti_contract off'.");
            MarketAPI m = context.getMarket();
            if (m == null) Console.showMessage("Dock at a planet and type 'dti_contract on' there: its bar then shows the contract first.");
            else Console.showMessage(putFirst(m));
            return CommandResult.SUCCESS;
        }
        if (arg.equalsIgnoreCase("done")) {
            int n = 0;
            for (EveryFrameScript s : Global.getSector().getScripts()) {
                if (s instanceof ContractWork && ((ContractWork) s).finishNow()) n++;
            }
            Console.showMessage(n == 0 ? "No contract work runs: deliver the goods first." : n + " contract work(s) done.");
            return CommandResult.SUCCESS;
        }
        if (arg.equalsIgnoreCase("off")) {
            spec.setFreq(normal);
            Console.showMessage("Contracts are back to normal at bars.");
            return CommandResult.SUCCESS;
        }
        return CommandResult.BAD_SYNTAX;
    }

    /**
     * The bar of this market shows the contract first: it goes to the front of the bar's kept list (a bar can keep
     * its other events for weeks). Returns what the player reads.
     */
    private static String putFirst(MarketAPI m) {
        String no = ContractMission.whyNot(m);
        if (no != null) return m.getName() + " cannot offer a contract: " + no + ".";
        boolean pooled = false;
        for (PortsideBarEvent e : PortsideBarData.getInstance().getEvents()) pooled |= ID.equals(e.getBarEventId());
        if (!pooled) return "No contract is in the game's bar pool yet. Wait one or two days, then type 'dti_contract on' here again.";
        MemoryAPI mem = m.getMemoryWithoutUpdate();
        List<String> shown = new ArrayList<>();
        shown.add(ID);
        Object old = mem.get(SHOWN);
        if (old instanceof List) {
            for (Object o : (List<?>) old) if (!ID.equals(o)) shown.add(String.valueOf(o));
        }
        mem.set(SHOWN, shown, 30f);
        return "Go to the bar of " + m.getName() + ": the contract is there now, first.";
    }
}
