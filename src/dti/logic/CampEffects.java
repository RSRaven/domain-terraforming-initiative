package dti.logic;

/**
 * What the Labor Camps do now. While any of their goods is short, the good parts
 * stop (the upkeep cut and the growth bonus) and the population protests (more stability loss). The bad parts that
 * always apply (stability, Luddic Path interest) are not here. Pure: the caller passes the settings.
 */
public final class CampEffects {
    /** Colony upkeep cut in percent. */
    public final int upkeepCut;
    /** Population growth bonus. */
    public final float growth;
    /** Extra stability loss from the protests. */
    public final int protest;

    public CampEffects(boolean goodsShort, int upkeepCut, float growth, int shortStability) {
        this.upkeepCut = goodsShort ? 0 : upkeepCut;
        this.growth = goodsShort ? 0f : growth;
        this.protest = goodsShort ? shortStability : 0;
    }
}
