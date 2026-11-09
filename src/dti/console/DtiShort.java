package dti.console;

import com.fs.starfarer.api.Global;
import dti.industry.ProjectStructure;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

/** DTI_Short <count>|off: for tests, every DTI project and the Labor Camps count this many short goods instead of the real number. */
public class DtiShort implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String arg = args.trim();
        if (arg.equalsIgnoreCase("off")) {
            Global.getSector().getMemoryWithoutUpdate().unset(ProjectStructure.TEST_SHORT);
            Console.showMessage("DTI projects and the Labor Camps use the real goods shortage again (from the next day).");
            return CommandResult.SUCCESS;
        }
        int count;
        try {
            count = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return CommandResult.BAD_SYNTAX;
        }
        if (count < 0) return CommandResult.BAD_SYNTAX;
        Global.getSector().getMemoryWithoutUpdate().set(ProjectStructure.TEST_SHORT, count);
        Console.showMessage("Every DTI project and the Labor Camps count " + count + " short goods (from the next day). 'dti_short off' ends this.");
        return CommandResult.SUCCESS;
    }
}
