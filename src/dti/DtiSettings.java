package dti;

import dti.logic.Area;
import dti.logic.ProjectClock;
import lunalib.lunaSettings.LunaSettings;

/** The DTI LunaLib settings (data/config/LunaSettings.csv). Read live, so a change applies at once. */
public final class DtiSettings {
    private DtiSettings() {}

    public static boolean on(String id) {
        return Boolean.TRUE.equals(LunaSettings.getBoolean(DtiData.MOD_ID, id));
    }

    public static float number(String id, float fallback) {
        Double v = LunaSettings.getDouble(DtiData.MOD_ID, id);
        return v == null ? fallback : v.floatValue();
    }

    public static int integer(String id, int fallback) {
        Integer v = LunaSettings.getInt(DtiData.MOD_ID, id);
        return v == null ? fallback : v;
    }

    /** The master switch: off hides the Control and every project, and locks the terraforming research. Built ones keep working. */
    public static boolean terraforming() {
        return on(Area.TERRAFORMING.setting);
    }

    /** "keep", "pause" or "remove". */
    public static String lostColony() {
        String v = LunaSettings.getString(DtiData.MOD_ID, "dti_lostColony");
        return v == null ? "keep" : v.trim().toLowerCase();
    }

    public static ProjectClock.Rules rules() {
        ProjectClock.Rules r = new ProjectClock.Rules();
        r.declineAfterDays = integer("dti_declineAfterDays", (int) r.declineAfterDays);
        String[] cores = {"Gamma", "Beta", "Alpha"};
        for (int i = 1; i <= 3; i++) {
            r.speedBonus[i] = integer("dti_coreBonus" + cores[i - 1], (int) (r.speedBonus[i] * 100)) / 100f;
            r.goingBack[i] = integer("dti_goingBack" + cores[i - 1], (int) (r.goingBack[i] * 100)) / 100f;
            r.upkeepCut[i] = integer("dti_controlUpkeep" + cores[i - 1], (int) (r.upkeepCut[i] * 100)) / 100f;
        }
        r.goingBack[0] = integer("dti_goingBackNone", (int) (r.goingBack[0] * 100)) / 100f;
        r.unrestStopped = integer("dti_unrestStopped", r.unrestStopped);
        r.unrestGoingBack = integer("dti_unrestGoingBack", r.unrestGoingBack);
        r.unrestRemoved = integer("dti_unrestRemoved", r.unrestRemoved);
        return r;
    }
}
