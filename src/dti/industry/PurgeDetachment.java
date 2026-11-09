package dti.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import dti.DtiSettings;

import java.util.List;

/**
 * Purge Detachment (docs/features/colony-development.md): a fast project; at the end an atrocity, recent unrest, and
 * standing lost with the factions that care about atrocities, the way vanilla saturation bombardment does it.
 */
public class PurgeDetachment extends ProjectStructure {
    @Override
    protected void projectFinished(List<String> lines) {
        int unrest = DtiSettings.integer("dti_purgeUnrest", 3);
        if (unrest > 0) RecentUnrest.get(market).add(unrest, getCurrentName());
        // The player answers for a purge on their own colony only, not for one a new owner finished.
        if (!market.isPlayerOwned()) return;
        MemoryAPI memory = Global.getSector().getCharacterData().getMemoryWithoutUpdate();
        memory.set(MemFlags.PLAYER_ATROCITIES, (int) memory.getFloat(MemFlags.PLAYER_ATROCITIES) + 1);
        lines.add("The Sector counts it as an atrocity");
        float loss = DtiSettings.integer("dti_purgeStanding", 10) / 100f;
        if (loss <= 0f) return;
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isPlayerFaction() || !f.getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES)) continue;
            CustomRepImpact impact = new CustomRepImpact();
            impact.delta = -loss;
            Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.CUSTOM, impact), f.getId());
        }
    }

    @Override
    protected String endText() {
        return "At the end: an atrocity, " + DtiSettings.integer("dti_purgeUnrest", 3) + " recent unrest, and "
                + DtiSettings.integer("dti_purgeStanding", 10) + " standing lost with every faction that cares about atrocities, such as the Hegemony and the Luddic Church.";
    }
}
