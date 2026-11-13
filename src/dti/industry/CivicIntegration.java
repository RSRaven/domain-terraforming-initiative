package dti.industry;

import dti.DtiSettings;
import dti.IntegratedCitizens;

import java.util.List;

/** Civic Integration: a project; at the end the colony grows faster for a while. */
public class CivicIntegration extends ProjectStructure {
    @Override
    protected void projectFinished(List<String> lines) {
        int days = IntegratedCitizens.start(market);
        if (days > 0) lines.add("Population growth goes up for " + days(days));
    }

    @Override
    protected String endText() {
        int days = DtiSettings.integer("dti_civicGrowthDays", 180);
        return days > 0 ? "At the end: population growth +" + (int) IntegratedCitizens.growth(market) + " (based on colony size) for "
                + days(days) + "." : null;
    }
}
