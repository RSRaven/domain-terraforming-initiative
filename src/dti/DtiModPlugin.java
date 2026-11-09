package dti;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import dti.logic.Area;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

public class DtiModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        DtiData.load();
        ItemEffect.register();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        ProjectsIntel.ensure();
        if (!Unlocks.aotd()) return;
        if (!LunaSettings.hasSettingsListenerOfClass(TabSync.class)) LunaSettings.addSettingsListener(new TabSync());
        // AoTD rebuilds its tab list in its own onGameLoad, so sync on the first frame, after every mod has loaded.
        Global.getSector().addTransientScript(new TabSync());
    }

    /**
     * ADR 0011: the DTI tab of the AoTD research screen shows while at least one area is on. Each area's memory flag
     * (the otherReq of its techs) is set while its switch is on.
     */
    static final class TabSync implements LunaSettingsListener, EveryFrameScript {
        private boolean done;

        static void sync() {
            if (Global.getSector() == null) return;
            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
            boolean any = false;
            for (Area a : Area.values()) {
                boolean on = DtiSettings.on(a.setting);
                memory.set(a.flag, on);
                any |= on;
            }
            AotdResearch.showTab(any);
            AotdResearch.relock();
            AotdResearch.teachItems();
        }

        @Override
        public void settingsChanged(String modId) {
            if (DtiData.MOD_ID.equals(modId)) sync();
        }

        @Override
        public void advance(float amount) {
            sync();
            done = true;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }
    }
}
