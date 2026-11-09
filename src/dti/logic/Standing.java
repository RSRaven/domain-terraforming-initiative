package dti.logic;

/** Standing with a faction, from -1 to 1 as the game keeps it. */
public final class Standing {
    private Standing() {}

    /** The standing after a loss in setting points (-200 to 0, 100 points = 1): never below -1, as the game clamps it. */
    public static float after(float current, int loss) {
        return Math.max(-1f, current + loss / 100f);
    }
}
