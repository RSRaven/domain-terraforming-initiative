package dti.logic;

/**
 * The content area of a structure: the area column of structures.csv. Each area has its own LunaLib switch,
 * and the AoTD memory flag that DTI sets while that switch is on (the otherReq column of our techs).
 */
public enum Area {
    TERRAFORMING("dti_terraforming", "$dti_terraformingOn", true),
    COLONY("dti_colonyDevelopment", "$dti_colonyDevelopmentOn", false);

    /** The master switch. */
    public final String setting;
    /** Set in the sector memory while the switch is on. */
    public final String flag;
    /** Its structures need a Terraforming Control on the colony, and their projects go back without a working one. */
    public final boolean needsControl;

    Area(String setting, String flag, boolean needsControl) {
        this.setting = setting;
        this.flag = flag;
        this.needsControl = needsControl;
    }

    /** Blank or "terraforming": terraforming; "colony": Colony Development. Anything else throws. */
    public static Area of(String cell) {
        String v = cell == null ? "" : cell.trim();
        if (v.isEmpty() || v.equals("terraforming")) return TERRAFORMING;
        if (v.equals("colony")) return COLONY;
        throw new IllegalArgumentException("unknown area '" + v + "'");
    }

    /** The build menu, as far as the area goes: its switch is on and, if it needs one, the colony has a Control. */
    public boolean allows(boolean switchOn, boolean hasControl) {
        return switchOn && (!needsControl || hasControl);
    }
}
