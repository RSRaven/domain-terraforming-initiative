package dti.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The achievements that a finished terraforming project earns. DTI sets the sector
 * memory key "$" + id; the MagicLib achievement with that id reads it (dti.achievements.DtiAchievement).
 */
public final class Firsts {
    public static final String FIRST_STEP = "dti_firstStep", SECOND_TERRA = "dti_secondTerra", UTOPIA = "dti_utopia", BREADBASKET = "dti_breadbasket";

    private Firsts() {
    }

    /** The planet's type and conditions when the project ended, before and after its work. The project must make the change. */
    public static List<String> earned(String oldType, Set<String> before, String newType, Set<String> after) {
        List<String> out = new ArrayList<>();
        out.add(FIRST_STEP);
        boolean wasTerran = "terran".equals(oldType), isTerran = "terran".equals(newType);
        if (isTerran && !wasTerran) out.add(SECOND_TERRA);
        if (isTerran && after.contains("mild_climate") && !(wasTerran && before.contains("mild_climate"))) out.add(UTOPIA);
        if (mostFood(after) && !mostFood(before)) out.add(BREADBASKET);
        return out;
    }

    private static boolean mostFood(Set<String> conditions) {
        return conditions.contains("farmland_bountiful") || conditions.contains("dti_marine_abundant");
    }
}
