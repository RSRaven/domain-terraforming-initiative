package dti.console;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import dti.industry.ProjectStructure;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.ArrayList;

/** DTI_Progress <days>: moves every running DTI project on the player's colonies by that many days of work. */
public class DtiProgress implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        float days;
        try {
            days = Float.parseFloat(args.trim());
        } catch (NumberFormatException e) {
            return CommandResult.BAD_SYNTAX;
        }
        int moved = 0;
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            for (Industry i : new ArrayList<>(market.getIndustries())) {
                if (!(i instanceof ProjectStructure) || !((ProjectStructure) i).running()) continue;
                ProjectStructure p = (ProjectStructure) i;
                p.addProgress(days);
                moved++;
                Console.showMessage(market.getName() + ", " + p.getCurrentName() + ": "
                        + (p.running() ? (int) p.progress() + " / " + (int) p.totalDays() + " days" : "finished"));
            }
        }
        if (moved == 0) Console.showMessage("No running DTI project on your colonies.");
        return CommandResult.SUCCESS;
    }
}
