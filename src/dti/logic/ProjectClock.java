package dti.logic;

/**
 * Speed, shortage and unrest rules of a running project.
 * Progress is counted in days of work, from 0 to the project's total days.
 */
public final class ProjectClock {
    private ProjectClock() {}

    public enum State { RUNNING, STOPPED, GOING_BACK, PAUSED, WAITING }

    /**
     * The numbers, from the LunaLib settings; the defaults here are the settings' defaults.
     * Shares are fractions: 0.5 = 50%. The per-core arrays are indexed by {@link #core}: none, gamma, beta, alpha.
     */
    public static final class Rules {
        public float declineAfterDays = 60;
        /** Forward speed bonus of an AI core in the project building. */
        public float[] speedBonus = {0f, 0.25f, 0.5f, 1f};
        /** Going back speed, by the AI core in the project building. */
        public float[] goingBack = {2f, 1.5f, 1f, 0.5f};
        /** Upkeep cut of an AI core in the Terraforming Control, for the Control and every project building. */
        public float[] upkeepCut = {0f, 0.15f, 0.3f, 0.5f};
        public int unrestStopped = 1, unrestGoingBack = 2, unrestRemoved = 2;
    }

    /** 0 none (or an unknown core), 1 gamma, 2 beta, 3 alpha. */
    public static int core(String coreId) {
        if ("gamma_core".equals(coreId)) return 1;
        if ("beta_core".equals(coreId)) return 2;
        if ("alpha_core".equals(coreId)) return 3;
        return 0;
    }

    /**
     * @param paused       the lost-colony rule says pause
     * @param waiting      a reason the player can fix outside DTI holds the project (a pollution source for the
     *                     Reclamation Plant): no progress, no going back, no unrest
     * @param controlWorks the colony has a built, working Terraforming Control
     * @param shortGoods   how many kinds of goods the colony gets less of than the project demands
     * @param shortageDays days in a row with at least one short good
     */
    public static State state(boolean paused, boolean waiting, boolean controlWorks, int shortGoods, float shortageDays, Rules r) {
        if (paused) return State.PAUSED;
        if (waiting) return State.WAITING;
        if (!controlWorks || shortGoods >= 2) return State.GOING_BACK;
        if (shortGoods == 1) return shortageDays >= r.declineAfterDays ? State.GOING_BACK : State.STOPPED;
        return State.RUNNING;
    }

    /**
     * The shortage count after {@code days} more days. It resets when nothing is short.
     * A waiting project holds it: no growth, no reset, so it goes on from there when the wait ends. A pause still counts.
     */
    public static float shortageDays(float before, State state, int shortGoods, float days) {
        if (state == State.WAITING) return before;
        return shortGoods > 0 ? before + days : 0f;
    }

    /**
     * Days of progress per day. The AI core in the project building speeds it up and slows the way back. An item's
     * bonus ({@link Items#bonus}) adds to the core's going forward; it does not change the way back.
     */
    public static float speed(State state, String coreId, float itemBonus, Rules r) {
        switch (state) {
            case RUNNING:
                return 1f + r.speedBonus[core(coreId)] + itemBonus;
            case GOING_BACK:
                return -r.goingBack[core(coreId)];
            default:
                return 0f;
        }
    }

    /** Upkeep multiplier from the AI core in the Terraforming Control. */
    public static float upkeepMult(String controlCoreId, Rules r) {
        return 1f - r.upkeepCut[core(controlCoreId)];
    }

    /**
     * The build cost the game should see for a running project. The game pays back that cost x its own share
     * (industryRefundFraction). Repeat mode: from step 2 on it is 0, so no refund: the price, paid once
     * before step 1, covered every step, and the finished steps stay.
     */
    public static float refundCost(float cost, int step) {
        return step >= 2 ? 0f : cost;
    }

    /**
     * The credits a shutdown of a running project gives back: what the game pays for {@link #refundCost}, cost x its
     * share. Also what a project that ends with nothing changed gives back.
     */
    public static float shutdownRefund(float cost, float gameShare, int step) {
        return refundCost(cost, step) * gameShare;
    }

    public static float progress(float progress, float speed, float days, float totalDays) {
        return Math.max(0f, Math.min(totalDays, progress + speed * days));
    }

    /**
     * The hazard cut while a project runs: the hazard of the condition it removes minus the hazard of the one it adds,
     * times the progress share. An added good condition (mild climate, -25%) lowers the hazard little by little; a
     * project never raises it while it runs.
     */
    public static float hazardCut(float removed, float added, float share) {
        return Math.max(0f, removed - added) * share;
    }

    /** Stability penalty while a project is in this state. */
    public static int unrest(State state, Rules r) {
        if (state == State.STOPPED) return r.unrestStopped;
        if (state == State.GOING_BACK) return r.unrestGoingBack;
        return 0;
    }

    /**
     * Repeat mode: removal causes no unrest from step 2 on, at any progress, so the player can stop at the
     * level they want. Step 1 follows the normal removal rules: its build cost was just paid.
     */
    public static boolean freeRemoval(int step) {
        return step >= 2;
    }

    /** Recent unrest when the player removes a running project: the base, +1 per full 25% of progress lost. */
    public static int removalUnrest(float progressShare, Rules r) {
        return r.unrestRemoved + Math.min(3, (int) (progressShare * 4));
    }
}
