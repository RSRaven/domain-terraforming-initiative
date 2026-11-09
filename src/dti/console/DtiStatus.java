package dti.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import dti.DtiData;
import dti.DtiSettings;
import dti.Terraform;
import dti.Unlocks;
import dti.industry.ProjectStructure;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

/** DTI_Status: the unlock state, then every DTI building on the player's colonies. */
public class DtiStatus implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        Console.showMessage("Terraforming " + onOff(DtiSettings.terraforming()) + ". Unlock: save flag " + onOff(Unlocks.flag()) + ", without quests " + onOff(DtiSettings.on("dti_unlockWithoutQuests"))
                + ", AoTD " + onOff(Unlocks.aotd()) + ", without AoTD research " + onOff(DtiSettings.on("dti_unlockWithoutResearch")));
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        if (memory.contains(ProjectStructure.TEST_SHORT)) {
            Console.showMessage("Test: DTI_Short forces " + memory.getInt(ProjectStructure.TEST_SHORT) + " short goods on every project and the Labor Camps.");
        }
        for (MarketAPI market : Misc.getPlayerMarkets(true)) {
            Industry control = market.getIndustry(DtiData.CONTROL);
            boolean any = control != null;
            String type = market.getPlanetEntity() == null ? "station" : market.getPlanetEntity().getTypeId();
            StringBuilder sb = new StringBuilder(market.getName() + " (" + type + "): ");
            if (control == null) sb.append("no Terraforming Control");
            else sb.append("Terraforming Control ").append(control.isDisrupted() ? "disrupted" : control.isFunctional() ? "working" : "under construction")
                    .append(", AI core ").append(control.getAICoreId() == null ? "none" : control.getAICoreId());
            for (Industry i : market.getIndustries()) {
                if (!(i instanceof ProjectStructure)) continue;
                any = true;
                ProjectStructure p = (ProjectStructure) i;
                sb.append("\n  ").append(p.getCurrentName()).append(": ");
                if (!p.running()) {
                    sb.append("building, ").append(p.getBuildOrUpgradeDaysText()).append(" left");
                    continue;
                }
                DtiData.Project project = p.project();
                sb.append(p.state().name().toLowerCase().replace('_', ' '))
                        .append(" | ").append(project == null ? "?" : Terraform.conditionName(project.subject()))
                        .append(p.repeat() ? " | step " + p.step() : "")
                        .append(" | ").append(String.format("%.1f / %.0f days (%d%%)", p.progress(), p.totalDays(), (int) (100 * p.progress() / p.totalDays())))
                        .append(" | speed ").append((int) (p.speed() * 100)).append('%')
                        .append(" | AI core ").append(p.getAICoreId() == null ? "none" : p.getAICoreId());
                if (!p.reason().isEmpty()) sb.append(" (").append(p.reason()).append(')');
                sb.append(" | short goods ").append(p.shortGoods()).append(", shortage ").append((int) p.shortageDays()).append(" days");
            }
            if (any) Console.showMessage(sb.toString());
        }
        return CommandResult.SUCCESS;
    }

    private static String onOff(boolean b) {
        return b ? "on" : "off";
    }
}
