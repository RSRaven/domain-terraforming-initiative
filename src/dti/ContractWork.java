package dti;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import dti.logic.Support;
import dti.logic.TypeLadder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The work of a paid Eridani-Utopia contract: a timer on the other faction's market, with
 * no building there. At the end the condition goes, as a finished project would take it: its removes column too, then
 * the planet type, habitable and the meteors. Saved with the game; it ends itself.
 */
public class ContractWork implements EveryFrameScript {
    private final String marketId, projectId;
    /** The conditions at the delivery: the start of the type ladder's before and after. */
    private final LinkedHashSet<String> atStart;
    private float daysLeft;
    private boolean done;

    ContractWork(MarketAPI market, DtiData.Project p, int days) {
        marketId = market.getId();
        projectId = p.id;
        atStart = new LinkedHashSet<>(Terraform.conditionIds(market));
        daysLeft = days;
    }

    /** Work on this condition of this market runs (for the contract offer). */
    static boolean runs(MarketAPI market, String condition) {
        for (EveryFrameScript s : Global.getSector().getScripts()) {
            if (!(s instanceof ContractWork) || ((ContractWork) s).done) continue;
            ContractWork w = (ContractWork) s;
            DtiData.Project p = DtiData.project(w.projectId);
            if (w.marketId.equals(market.getId()) && p != null && condition.equals(p.condition)) return true;
        }
        return false;
    }

    /** For the test (DTI_Contract done): the work ends now. True when it was still running. */
    public boolean finishNow() {
        if (done) return false;
        finish();
        return true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;
        daysLeft -= Global.getSector().getClock().convertToDays(amount);
        if (daysLeft <= 0f) finish();
    }

    private void finish() {
        done = true;
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        DtiData.Project p = DtiData.project(projectId);
        if (market == null || p == null || market.getPlanetEntity() == null) return;
        String oldType = market.getPlanetEntity().getTypeId();
        List<String> lines = new ArrayList<>();
        if (Terraform.removeCondition(market, p.condition)) lines.add(Terraform.conditionName(p.condition) + " removed");
        for (String c : p.removes) {
            if (Terraform.removeCondition(market, c)) lines.add(Terraform.conditionName(c) + " removed");
        }
        TypeLadder.Result r = Terraform.typeChange(market, atStart, Terraform.conditionIds(market));
        if (r != null && !r.rules.isEmpty()) Terraform.applyType(market, r);
        if (Terraform.restoreHabitable(market)) lines.add(Terraform.conditionName("habitable") + " restored");
        if (Support.meteorsBurnUp(Terraform.conditionIds(market)) && Terraform.removeCondition(market, Support.METEORS)) {
            lines.add(Terraform.conditionName(Support.METEORS) + " removed");
        }
        market.reapplyConditions();
        String newType = market.getPlanetEntity().getTypeId();
        if (!newType.equals(oldType)) lines.add("Planet type: " + Terraform.typeName(oldType) + " -> " + Terraform.typeName(newType));
        if (lines.isEmpty()) lines.add("Nothing was left to do: the condition was already gone.");
        MessageIntel intel = new MessageIntel("Contract work done at " + market.getName(), Misc.getBasePlayerColor());
        for (String line : lines) intel.addLine(BaseIntelPlugin.BULLET + line);
        intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
        intel.setSound(BaseIntelPlugin.getSoundStandardUpdate());
        Global.getSector().getCampaignUI().addMessage(intel, MessageClickAction.NOTHING);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
