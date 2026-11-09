package dti.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import dti.DtiSettings;
import dti.logic.Standing;

import java.util.List;

/**
 * Lobster Hatchery (docs/features/marine-biosphere.md): at the end, on the player's colony, the player's standing with the
 * Sindrian Diktat drops by the setting, never below -100. The way Purge Detachment changes standing.
 */
public class LobsterHatchery extends ProjectStructure {
    /** The standing loss in points, -200 to 0; 0 changes nothing. */
    private static int loss() {
        return DtiSettings.integer("dti_lobsterDiktat", -200);
    }

    @Override
    protected void projectFinished(List<String> lines) {
        // The player answers for a hatchery on their own colony only, as for a purge.
        if (!market.isPlayerOwned() || Global.getSector().getFaction(Factions.DIKTAT) == null) return;
        float current = Global.getSector().getPlayerFaction().getRelationship(Factions.DIKTAT);
        float after = Standing.after(current, loss());
        if (after >= current) return;
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = after - current;
        Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact), Factions.DIKTAT);
        lines.add("The Sindrian Diktat calls it theft: your standing with them dropped to " + Math.round(after * 100) + " ("
                + RepLevel.getLevelFor(after).getDisplayName() + ")");
    }

    @Override
    protected String endText() {
        return loss() < 0 ? "The Sindrian Diktat will call this hatchery theft." : null;
    }
}
