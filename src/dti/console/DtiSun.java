package dti.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.util.Misc;
import dti.CaptiveSun;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

/** DTI_Sun <radius>|add|reset: to test the Captive Sun's size. */
public class DtiSun implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String arg = args.trim();
        if (arg.equalsIgnoreCase("reset")) {
            Global.getSector().getMemoryWithoutUpdate().unset(CaptiveSun.RADIUS_KEY);
            Console.showMessage("Every Captive Sun has the normal radius again: " + CaptiveSun.RADIUS + ".");
            return CommandResult.SUCCESS;
        }
        if (arg.equalsIgnoreCase("add")) return add();
        float radius;
        try {
            radius = Float.parseFloat(arg);
        } catch (NumberFormatException e) {
            return CommandResult.BAD_SYNTAX;
        }
        if (radius <= 0) return CommandResult.BAD_SYNTAX;
        Global.getSector().getMemoryWithoutUpdate().set(CaptiveSun.RADIUS_KEY, radius);
        Console.showMessage("Every Captive Sun has radius " + radius + " now. 'dti_sun reset' goes back to " + CaptiveSun.RADIUS + ".");
        return CommandResult.SUCCESS;
    }

    /** A Captive Sun next to the planet nearest the fleet, on the fleet's side, so it is easy to see. */
    private static CommandResult add() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        PlanetAPI nearest = null;
        for (PlanetAPI p : fleet.getContainingLocation().getPlanets()) {
            if (p.isStar() || p.hasTag(CaptiveSun.TAG)) continue;
            if (nearest == null || Misc.getDistance(fleet, p) < Misc.getDistance(fleet, nearest)) nearest = p;
        }
        if (nearest == null) {
            Console.showMessage("No planet here.");
            return CommandResult.ERROR;
        }
        float orbit = nearest.getRadius() + 250f;
        CaptiveSun.add(nearest, "star_yellow", Misc.getAngleInDegrees(nearest.getLocation(), fleet.getLocation()), orbit, orbit / 10f);
        Console.showMessage("Added a Captive Sun at " + nearest.getName() + ". It stays in this save.");
        return CommandResult.SUCCESS;
    }
}
