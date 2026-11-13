package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.kaysaar.aotd.vok.campaign.econ.listeners.AoTDResearchListener;
import data.kaysaar.aotd.vok.scripts.research.AoTDMainResearchManager;
import data.kaysaar.aotd.vok.scripts.research.models.ResearchOption;

import java.util.Collection;
import java.util.List;

/** The only class that touches AoTD. Call it only after Unlocks.aotd() says AoTD is on. */
final class AotdResearch {
    private AotdResearch() {}

    private static boolean warned, tabWarned, relockWarned, teachWarned;

    /** AoTD's workshop makes an item for a faction once this flag plus the item id is true in the faction's memory. */
    static final String KNOWS_ITEM = "$aotd";

    /**
     * Shows or hides the DTI tab of the AoTD research screen. The screen lists the tabs of getModIDsRepo(), which AoTD
     * rebuilds on every game load, so call this after the load. The techs and their research stay; only the tab goes.
     */
    static void showTab(boolean show) {
        try {
            List<String> tabs = AoTDMainResearchManager.getInstance().getModIDsRepo();
            if (!show) tabs.remove(DtiData.MOD_ID);
            else if (!tabs.contains(DtiData.MOD_ID)) tabs.add(DtiData.MOD_ID);
        } catch (Throwable t) {
            if (!tabWarned) {
                tabWarned = true;
                Global.getLogger(AotdResearch.class).error("DTI: could not change the AoTD research tabs; the DTI tab stays as AoTD made it", t);
            }
        }
    }

    /**
     * AoTD marks a tech's otherReq as met once its memory flag is true, and never takes that back. So DTI sets it back
     * to the flag for every DTI tech of the player: a tech of an area that was turned off cannot be researched again.
     */
    static void relock() {
        try {
            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
            for (ResearchOption o : AoTDMainResearchManager.getInstance().getManagerForPlayer().getResearchRepoOfFaction()) {
                if (o.otherReq != null && DtiData.MOD_ID.equals(o.modID)) o.metOtherReq = memory.is(o.otherReq.one, true);
            }
        } catch (Throwable t) {
            if (!relockWarned) {
                relockWarned = true;
                Global.getLogger(AotdResearch.class).error("DTI: could not relock the DTI techs; a tech of an area turned off may stay researchable", t);
            }
        }
    }

    /** Fails open: if AoTD changed and the call breaks, DTI treats the tech as researched, so nobody gets stuck. */
    static boolean researched(String techId) {
        try {
            return AoTDMainResearchManager.getInstance().isResearchedForPlayer(techId);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Global.getLogger(AotdResearch.class).error("DTI: the AoTD research check failed; DTI techs count as researched", t);
            }
            return true;
        }
    }

    /** The tech's name on the research screen; its id if AoTD changed. */
    static String name(String techId) {
        try {
            ResearchOption o = AoTDMainResearchManager.getInstance().getManagerForPlayer().getResearchOptionFromRepo(techId);
            if (o != null && o.Name != null) return o.Name;
        } catch (Throwable ignored) {
            // The id is a fine fallback for a modifier name.
        }
        return techId;
    }

    /**
     * The AoTD workshop: a faction that finishes a DTI tech learns its items. Also, on every
     * load and settings change, the player learns the items of every structure already unlocked: old saves, DTI_Unlock
     * and "Unlock without AoTD research". A flag once set stays, as AoTD's own.
     */
    static void teachItems() {
        try {
            if (!Global.getSector().getListenerManager().hasListenerOfClass(ItemTeacher.class)) {
                Global.getSector().getListenerManager().addListener(new ItemTeacher(), true);
            }
            FactionAPI player = Global.getSector().getPlayerFaction();
            for (DtiData.Structure s : DtiData.structures()) {
                if (Unlocks.unlocked(s.id)) teach(player, DtiData.taught(s.tech));
            }
        } catch (Throwable t) {
            if (!teachWarned) {
                teachWarned = true;
                Global.getLogger(AotdResearch.class).error("DTI: could not teach the DTI items to the AoTD workshop; they still drop as loot", t);
            }
        }
    }

    static void teach(FactionAPI faction, Collection<String> itemIds) {
        for (String id : itemIds) faction.getMemoryWithoutUpdate().set(KNOWS_ITEM + id, true);
    }

    /** Transient: teachItems adds it again on every load. */
    static final class ItemTeacher implements AoTDResearchListener {
        @Override
        public void finishedResearchOfTechnology(String techId, FactionAPI faction) {
            teach(faction, DtiData.taught(techId));
        }
    }
}
