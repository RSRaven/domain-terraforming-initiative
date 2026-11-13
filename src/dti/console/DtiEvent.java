package dti.console;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import dti.DtiData;
import dti.EcologyIntel;
import dti.industry.ProjectStructure;
import dti.logic.Ecology;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

/** DTI_Event [id]: for tests, an ecology event now on the first running project it fits. */
public class DtiEvent implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String id = args.trim();
        Ecology.Event wanted = id.isEmpty() ? null : DtiData.ecologyEvent(id);
        if (!id.isEmpty() && wanted == null) {
            StringBuilder ids = new StringBuilder();
            for (Ecology.Event e : DtiData.ecologyEvents()) ids.append(' ').append(e.id);
            Console.showMessage("No event " + id + ". The events:" + ids);
            return CommandResult.ERROR;
        }
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            for (Industry i : market.getIndustries()) {
                if (!(i instanceof ProjectStructure) || !((ProjectStructure) i).running()) continue;
                Ecology.Event e = wanted != null ? wanted : Ecology.pick(DtiData.ecologyEvents(), i.getId(), EcologyIntel.experienced(i.getId()), Misc.random);
                if (e == null || !e.fits(i.getId())) continue;
                EcologyIntel.start((ProjectStructure) i, e);
                Console.showMessage(e.name + " at " + market.getName() + ", " + i.getCurrentName() + ". See the Intel tab.");
                return CommandResult.SUCCESS;
            }
        }
        Console.showMessage("No running DTI project on your colonies" + (wanted == null ? "." : " that this event fits."));
        return CommandResult.ERROR;
    }
}
