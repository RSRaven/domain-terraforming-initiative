package dti.console;

import com.fs.starfarer.api.Global;
import dti.Unlocks;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

/** DTI_Unlock [off]: sets or clears the save flag that unlocks every DTI structure (with AoTD, not the Lobster Hatchery). */
public class DtiUnlock implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String arg = args.trim();
        if (arg.equalsIgnoreCase("off")) {
            Global.getSector().getMemoryWithoutUpdate().unset(Unlocks.FLAG);
            Console.showMessage("DTI structures are locked again in this save. Research and the setting still apply.");
        } else if (arg.isEmpty()) {
            Global.getSector().getMemoryWithoutUpdate().set(Unlocks.FLAG, true);
            // UnlockRule: a noAotd structure stays locked with AoTD, even with the flag.
            Console.showMessage("All DTI structures are unlocked in this save."
                    + (Unlocks.aotd() ? " The Lobster Hatchery stays locked: it is not in games with AoTD." : ""));
        } else {
            return CommandResult.BAD_SYNTAX;
        }
        return CommandResult.SUCCESS;
    }
}
