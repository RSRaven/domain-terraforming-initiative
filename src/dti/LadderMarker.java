package dti;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;

/**
 * A ladder marker (dti_hydrated, dti_dried): a project adds it at its end, and the type ladder uses and removes it at
 * once. It has no effect and no icon, so the player never sees it.
 */
public class LadderMarker extends BaseMarketConditionPlugin {
    @Override
    public boolean showIcon() {
        return false;
    }
}
