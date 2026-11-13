package dti;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import dti.industry.ProjectStructure;
import org.lwjgl.util.vector.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Captive Sun is a small vanilla star in orbit. A star lights only its system, so this script
 * gives the planet's night side its light, as the vanilla Fusion Lamp did, and keeps every Captive Sun at one size.
 * On load it turns the Fusion Lamps of DTI 0.4.1 and older into stars.
 */
public final class CaptiveSun implements EveryFrameScript {
    public static final String TAG = "dti_captive_sun";
    /** Sector memory: a radius set with the DTI_Sun console command, to try other sizes. */
    public static final String RADIUS_KEY = "$dti_captiveSunRadius";
    /** The normal radius of every Captive Sun, unless DTI_Sun set another. */
    public static final float RADIUS = 25f;
    private static final Color LIGHT = new Color(255, 225, 170);
    private static final Color NO_LIGHT = new Color(0, 0, 0, 0);

    private boolean converted;

    public static float radius() {
        float r = Global.getSector().getMemoryWithoutUpdate().getFloat(RADIUS_KEY);
        return r > 0 ? r : RADIUS;
    }

    /** A Captive Sun of this star type around the planet: not clickable, no tooltip. */
    public static PlanetAPI add(SectorEntityToken planet, String starType, float angle, float orbitRadius, float period) {
        PlanetAPI sun = planet.getContainingLocation().addPlanet("dti_captive_sun_" + Misc.genUID(), planet, "Captive Sun",
                starType, angle, radius(), orbitRadius, period);
        sun.addTag(TAG);
        sun.addTag(Tags.NON_CLICKABLE);
        sun.addTag(Tags.NO_ENTITY_TOOLTIP);
        return sun;
    }

    /** Before a Captive Sun leaves: its planet loses the light. */
    static void unlight(SectorEntityToken e) {
        if (e.hasTag(TAG) && e.getOrbitFocus() instanceof PlanetAPI) {
            ((PlanetAPI) e.getOrbitFocus()).setSecondLight(new Vector3f(), NO_LIGHT);
        }
    }

    @Override
    public void advance(float amount) {
        if (!converted) {
            convertLamps();
            converted = true;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        LocationAPI here = fleet == null ? null : fleet.getContainingLocation();
        if (here == null) return;
        float size = radius();
        for (PlanetAPI sun : here.getPlanets()) {
            if (!sun.hasTag(TAG) || !(sun.getOrbitFocus() instanceof PlanetAPI)) continue;
            if (sun.getRadius() != size) sun.setRadius(size);
            // As the vanilla lamp: the light comes from the sun, a little above the plane.
            float alpha = 0.8f * sun.getSensorFaderBrightness() * sun.getSensorContactFaderBrightness();
            ((PlanetAPI) sun.getOrbitFocus()).setSecondLight(
                    new Vector3f(sun.getLocation().x, sun.getLocation().y, sun.getCircularOrbitRadius() * 0.75f),
                    Misc.scaleColor(LIGHT, alpha));
        }
    }

    /**
     * DTI 0.4.1 and older placed a vanilla Fusion Lamp at the planet's radius + 250, not clickable. The vanilla lamp item
     * puts its lamp at + 100, clickable, so it stays. A running project's orbit list gets the new star too.
     */
    private static void convertLamps() {
        DtiData.Structure s = DtiData.structure("dti_captive_sun");
        if (s == null || !s.orbitEntity.startsWith("star_")) return;
        Map<SectorEntityToken, SectorEntityToken> swapped = new HashMap<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            List<CustomCampaignEntityAPI> lamps = new ArrayList<>(system.getCustomEntitiesWithTag(Tags.NON_CLICKABLE));
            for (CustomCampaignEntityAPI lamp : lamps) {
                SectorEntityToken planet = lamp.getOrbitFocus();
                if (!Entities.FUSION_LAMP.equals(lamp.getCustomEntityType()) || !(planet instanceof PlanetAPI)) continue;
                if (Math.abs(lamp.getCircularOrbitRadius() - (planet.getRadius() + 250f)) > 1f) continue;
                swapped.put(lamp, add(planet, s.orbitEntity, lamp.getCircularOrbitAngle(), lamp.getCircularOrbitRadius(),
                        lamp.getCircularOrbitPeriod()));
                system.removeEntity(lamp);
            }
        }
        if (swapped.isEmpty()) return;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (Industry ind : market.getIndustries()) {
                if (ind instanceof ProjectStructure) ((ProjectStructure) ind).swapOrbit(swapped);
            }
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }
}
