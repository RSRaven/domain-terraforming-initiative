package dti;

import com.fs.starfarer.api.Global;
import dti.logic.UnlockRule;

/** Is a DTI structure unlocked for the player (ADR 0007)? Every other class asks here. */
public final class Unlocks {
    private Unlocks() {}

    /** Save flag, set by DTI_Unlock (later by the quests). */
    public static final String FLAG = "$dti_unlocked";

    public static boolean flag() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(FLAG);
    }

    public static boolean aotd() {
        return Global.getSettings().getModManager().isModEnabled("aotd_vok");
    }

    /** The player researched this tech in AoTD. Without AoTD there is no research, so false. */
    public static boolean researched(String techId) {
        return aotd() && AotdResearch.researched(techId);
    }

    /** The tech's name on the AoTD research screen. */
    public static String techName(String techId) {
        return AotdResearch.name(techId);
    }

    public static boolean unlocked(String structureId) {
        DtiData.Structure s = DtiData.structure(structureId);
        return UnlockRule.unlocked(s != null && s.noAotd, flag(), DtiSettings.on("dti_unlockWithoutQuests"), aotd(), DtiSettings.on("dti_unlockWithoutResearch"),
                () -> s != null && (s.tech.isEmpty() || AotdResearch.researched(s.tech)));
    }
}
