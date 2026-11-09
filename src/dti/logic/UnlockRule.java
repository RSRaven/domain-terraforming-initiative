package dti.logic;

import java.util.function.BooleanSupplier;

/** Is a structure unlocked (ADR 0007)? Pure: dti.Unlocks feeds it the save flag, the settings and AoTD. */
public final class UnlockRule {
    private UnlockRule() {}

    /**
     * @param noAotd          the structure exists only in games without AoTD (structures.csv): with AoTD never, not even by DTI_Unlock
     * @param cheat           the DTI_Unlock save flag: everything, for tests
     * @param withoutQuests   setting: every tech counts as found
     * @param aotd            AoTD Vaults of Knowledge is on
     * @param withoutResearch setting: a found tech needs no AoTD research
     * @param researched      the AoTD research of the structure's tech; asked only with AoTD
     */
    public static boolean unlocked(boolean noAotd, boolean cheat, boolean withoutQuests, boolean aotd, boolean withoutResearch, BooleanSupplier researched) {
        if (noAotd && aotd) return false;
        if (cheat) return true;
        if (!aotd) return withoutQuests;
        // M1 has no quests and AoTD shows every tech in its tree, so with AoTD every tech counts as found.
        return withoutResearch || researched.getAsBoolean();
    }
}
