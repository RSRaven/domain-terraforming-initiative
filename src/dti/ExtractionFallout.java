package dti;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import dti.logic.Extraction;
import dti.logic.ProjectRow;
import dti.logic.TypeLadder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The extraction fallout (docs/features/reverse-projects.md): an extractor removed before its deposit is gone leaves
 * pollution and toxic atmosphere ({@link Extraction#afterFallout}), then the type ladder runs as after any project. The
 * colony's Project Trouble gets a stability penalty until the pollution is gone ({@link TerraformingTrouble}).
 * <p>
 * It runs one frame after the removal: the game removes a building while it walks the colony's building list, and the
 * ladder may shut down a food building, which would break that walk.
 */
public class ExtractionFallout implements EveryFrameScript {
    /** Colony memory key: the fallout penalty holds while the colony has pollution. */
    public static final String FLAG = "$dti_extractionFallout";

    private final MarketAPI market;
    private final String building;
    private boolean done;

    private ExtractionFallout(MarketAPI market, String building) {
        this.market = market;
        this.building = building;
    }

    /** The fallout of this building on this colony, on the next frame. */
    public static void later(MarketAPI market, String building) {
        Global.getSector().addScript(new ExtractionFallout(market, building));
    }

    /**
     * The penalty holds: the flag is set and the colony still has pollution. With no pollution the flag goes for good,
     * here, so a later pollution from elsewhere never brings the penalty back.
     */
    public static boolean holds(MarketAPI market) {
        if (!market.hasCondition(Extraction.POLLUTION)) {
            market.getMemoryWithoutUpdate().unset(FLAG);
            return false;
        }
        return market.getMemoryWithoutUpdate().getBoolean(FLAG);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;
        done = true;
        Set<String> before = Terraform.conditionIds(market), after = Extraction.afterFallout(before);
        String oldType = market.getPlanetEntity() == null ? "" : market.getPlanetEntity().getTypeId();
        for (String c : before) {
            if (!after.contains(c)) Terraform.removeCondition(market, c);
        }
        for (String c : after) {
            if (!before.contains(c)) Terraform.addCondition(market, c);
        }
        TypeLadder.Result r = Terraform.typeChange(market, before, after);
        if (r != null && !r.rules.isEmpty()) Terraform.applyType(market, r);
        market.reapplyConditions();
        market.getMemoryWithoutUpdate().set(FLAG, true);
        Set<String> now = Terraform.conditionIds(market);
        List<String> lines = new ArrayList<>();
        for (String c : now) {
            if (!before.contains(c) && !ProjectRow.level(c)) lines.add(Terraform.conditionName(c) + " added");
        }
        for (String c : before) {
            if (!now.contains(c) && !ProjectRow.level(c)) lines.add(Terraform.conditionName(c) + " removed");
        }
        String newType = market.getPlanetEntity() == null ? "" : market.getPlanetEntity().getTypeId();
        if (!newType.equals(oldType)) lines.add("Planet type: " + Terraform.typeName(oldType) + " -> " + Terraform.typeName(newType));
        lines.addAll(ProjectRow.levelLines(before, now));
        if (TerraformingTrouble.falloutPenalty() > 0) lines.add("Stability -" + TerraformingTrouble.falloutPenalty() + " until the pollution is gone");
        if (!lines.isEmpty()) Terraform.message(market, building + ": Extraction fallout", lines.toArray(new String[0]));
        TerraformingTrouble.sync(market);
    }
}
