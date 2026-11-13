package dti.achievements;

import com.fs.starfarer.api.Global;
import org.magiclib.achievements.MagicAchievement;

/**
 * Every DTI achievement (data/config/magic_achievements.csv). Done when DTI has set the sector memory key "$" + its id
 * (dti.logic.Firsts). Only this class imports MagicLib, and only MagicLib loads it, so DTI runs without MagicLib.
 */
public class DtiAchievement extends MagicAchievement {
    @Override
    public void advanceAfterInterval(float amount) {
        if (Global.getSector() != null && Global.getSector().getMemoryWithoutUpdate().getBoolean("$" + getSpecId())) completeAchievement();
    }
}
