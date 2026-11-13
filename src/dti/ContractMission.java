package dti;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import dti.industry.ProjectStructure;
import dti.logic.Area;
import dti.logic.Contract;
import dti.logic.Firsts;
import dti.logic.Light;

import java.awt.Color;
import java.util.*;

/**
 * An Eridani-Utopia contract: at a bar, an official of a faction that likes
 * the player asks for a condition of that market to be fixed. The player brings the goods (dti.logic.Contract), in parts
 * if needed, and is paid when the last part comes. The work then runs as a timer (ContractWork), with the structure's objects in orbit; nothing is built
 * on their market. The bar event is data/campaign/bar_events.csv "dtiContract"; the dialog texts are data/campaign/rules.csv.
 */
public class ContractMission extends HubMissionWithBarEvent {
    public enum Stage {DELIVER, COMPLETED, FAILED, ENDED}

    /** The faction's standing with the player must be above this. */
    public static final float MIN_STANDING = 0.30f;
    private static final float DELIVER_DAYS = 180f;

    protected MarketAPI target;
    protected String projectId, factionId;
    protected LinkedHashMap<String, Integer> goods = new LinkedHashMap<>();
    /** What the player brought so far: the goods can come in parts. */
    protected LinkedHashMap<String, Integer> delivered = new LinkedHashMap<>();
    /** The work after the delivery. */
    protected int days;
    /** Why the contract ended early (stage ENDED), or null. */
    protected String endReason;
    /** The first line of the offer: an index into INTROS, so one mission keeps its line. */
    protected int intro;
    /** Sector memory: how many contracts the player accepted. The offers take the INTROS in turn by it. */
    private static final String ACCEPTED = "$dti_contractsAccepted";
    private static final String[] INTROS = {
            "Captain. They say you brought a dead world back to life, the old Eridani-Utopia way.",
            "Captain. People here talk about the world you remade. Few still know how the Domain did it.",
            "So you are the captain who shapes worlds. I hoped that you would come to this bar one day.",
    };

    /** The next offer starts with the next line. */
    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.acceptImpl(dialog, memoryMap);
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        sector.set(ACCEPTED, sector.getInt(ACCEPTED) + 1);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!barEvent || createdAt == null || blocker(createdAt) != null) return false;
        DtiData.Project p = pick(createdAt, genRandom);
        if (p == null) return false;
        setGiverRank(Ranks.CITIZEN);
        setGiverPost(pickOne(Ranks.POST_ADMINISTRATOR, Ranks.POST_SCIENTIST, Ranks.POST_ACADEMICIAN));
        setGiverImportance(pickImportance());
        setGiverFaction(createdAt.getFactionId());
        findOrCreateGiver(createdAt, false, false);
        PersonAPI person = getPerson();
        if (person == null || !setPersonMissionRef(person, "$dtiContract_ref")) return false;
        setGiverIsPotentialContactOnSuccess();

        target = createdAt;
        projectId = p.id;
        intro = Global.getSector().getMemoryWithoutUpdate().getInt(ACCEPTED) % INTROS.length;
        factionId = createdAt.getFactionId();
        days = Contract.workDays(ProjectStructure.totalDays(p));
        Map<String, Integer> demand = new LinkedHashMap<>(p.goods);
        demand.putAll(p.aotdGoods);
        goods.putAll(Contract.goods(demand, p.days));
        if (goods.isEmpty()) return false;
        float value = 0f;
        for (Map.Entry<String, Integer> g : goods.entrySet()) value += g.getValue() * Global.getSettings().getCommoditySpec(g.getKey()).getBasePrice();

        ensurePersonIsInCommDirectory(target, person);
        setPersonDoGenericPortAuthorityCheck(person);
        makeImportant(person, "$dtiContract_needsGoods", Stage.DELIVER);
        setStartingStage(Stage.DELIVER);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);
        // Not the player's fault: no reputation lost.
        setNoPenaltyFailureStage(Stage.ENDED);
        setTimeLimit(Stage.FAILED, DELIVER_DAYS, null);
        setCreditReward(Contract.reward(value, Contract.premium(p.days)));
        setRepFactionChangesMedium();
        setRepPersonChangesMedium();
        return true;
    }

    /**
     * What keeps this market from offering any contract, or null: it must be another faction's visible planet, that
     * faction above +30 with the player, terraforming on, and the player must have finished a terraforming project
     * (the key of the first achievement).
     */
    private static String blocker(MarketAPI m) {
        if (m.isPlayerOwned() || Factions.PLAYER.equals(m.getFactionId()) || m.isHidden() || m.getPlanetEntity() == null || m.getPlanetEntity().isStar()) {
            return "it must be a planet of another faction";
        }
        float standing = m.getFaction().getRelationship(Factions.PLAYER);
        if (standing <= MIN_STANDING) return "its faction must be above +30 with you (now " + Math.round(standing * 100f) + ")";
        if (!DtiSettings.terraforming()) return "terraforming is off in the settings";
        if (!Global.getSector().getMemoryWithoutUpdate().getBoolean("$" + Firsts.FIRST_STEP)) return "you must finish a terraforming project first";
        return null;
    }

    /** For DTI_Contract: why this market's bar cannot offer a contract now, or null when it can. */
    public static String whyNot(MarketAPI m) {
        String b = blocker(m);
        if (b != null || pick(m, new Random()) != null) return b;
        return "it has no condition that a DTI structure you unlocked removes" + (Unlocks.aotd() ? " (with AoTD: whose AoTD goods your colonies make)" : "");
    }

    /**
     * A project that only removes a condition of the market, that the player has unlocked and that LunaLib offers, as
     * the build menu would on a colony there, and whose AoTD goods a player colony makes; null when there is none.
     */
    private static DtiData.Project pick(MarketAPI m, Random random) {
        WeightedRandomPicker<DtiData.Project> picker = new WeightedRandomPicker<>(random);
        boolean realStar = ProjectStructure.realStar(m);
        for (DtiData.Structure s : DtiData.structures()) {
            // A blank setting (the Terraforming Control) is no LunaLib id: asking LunaLib for it logs an error.
            if (s.area != Area.TERRAFORMING || !(s.setting.isEmpty() || DtiSettings.on(s.setting)) || !Unlocks.unlocked(s.id)) continue;
            DtiData.Project p = DtiData.target(s.id, m);
            if (p == null || p.condition.isEmpty() || !p.adds.isEmpty() || !m.hasCondition(p.condition) || !Light.offers(p.star, realStar)) continue;
            // A level is no fix; an extraction takes a deposit away, which no faction pays for.
            if (dti.logic.ProjectRow.level(p.condition) || ProjectStructure.depositGood(p) != null || taken(m, p.condition)) continue;
            if (!madeByPlayer(p.aotdGoods.keySet())) continue;
            picker.add(p);
        }
        return picker.pick();
    }

    /** Each of these goods is made by one of the player's colonies (the AoTD goods: the player needs a real source). */
    private static boolean madeByPlayer(Set<String> ids) {
        for (String id : ids) {
            boolean made = false;
            for (MarketAPI m : Misc.getPlayerMarkets(true)) {
                if (m.getCommodityData(id).getMaxSupply() > 0) {
                    made = true;
                    break;
                }
            }
            if (!made) return false;
        }
        return true;
    }

    /** Work on this condition of the market already runs: another contract, its work, or a DTI project (a lost colony on "Keep"). */
    private static boolean taken(MarketAPI m, String condition) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(ContractMission.class)) {
            ContractMission c = (ContractMission) i;
            if (!c.isEnding() && !c.isEnded() && c.target != null && c.target.getId().equals(m.getId()) && c.project() != null && condition.equals(c.project().condition)) return true;
        }
        if (ContractWork.runs(m, condition)) return true;
        for (Industry i : m.getIndustries()) {
            if (!(i instanceof ProjectStructure) || !((ProjectStructure) i).running()) continue;
            DtiData.Project p = ((ProjectStructure) i).project();
            if (p != null && condition.equals(p.condition)) return true;
        }
        return false;
    }

    private DtiData.Project project() {
        return DtiData.project(projectId);
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);
        if (currentStage != Stage.DELIVER) return;
        DtiData.Project p = project();
        // Nexerelin: the market changed owner. Or someone else fixed it.
        if (p == null || !target.hasCondition(p.condition)) endReason = "the condition is gone";
        else if (target.isPlayerOwned() || !factionId.equals(target.getFactionId()) || !target.isInEconomy()) endReason = target.getName() + " changed owner";
        if (endReason != null) setCurrentStage(Stage.ENDED, null, null);
    }

    /**
     * "deliver": the player gives what they carry of the goods still needed. "finishIfDone": when nothing is left, the
     * structure's objects go into orbit, the work starts, and the mission is paid.
     */
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if ("deliver".equals(action)) {
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            for (Map.Entry<String, Integer> g : remaining().entrySet()) {
                int n = Math.min(g.getValue(), (int) cargo.getCommodityQuantity(g.getKey()));
                if (n <= 0) continue;
                cargo.removeCommodity(g.getKey(), n);
                delivered.merge(g.getKey(), n, Integer::sum);
                if (dialog != null) AddRemoveCommodity.addCommodityLossText(g.getKey(), n, dialog.getTextPanel());
            }
            updateInteractionData(dialog, memoryMap);
            return true;
        }
        if (!"finishIfDone".equals(action)) return false;
        if (!remaining().isEmpty()) return true;
        DtiData.Project p = project();
        DtiData.Structure s = p == null ? null : DtiData.structure(p.structure);
        if (s != null && !s.orbitEntity.isEmpty()) Terraform.addOrbitObjects(target, s.orbitEntity, s.orbitCount);
        if (p != null) Global.getSector().addScript(new ContractWork(target, p, days));
        setCurrentStage(Stage.COMPLETED, dialog, memoryMap);
        return true;
    }

    /** The goods still needed. */
    private Map<String, Integer> remaining() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> g : goods.entrySet()) {
            int left = g.getValue() - (delivered.containsKey(g.getKey()) ? delivered.get(g.getKey()) : 0);
            if (left > 0) out.put(g.getKey(), left);
        }
        return out;
    }

    /** The player carries at least one unit of a good still needed. */
    private boolean playerHasSome() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        for (String id : remaining().keySet()) if (cargo.getCommodityQuantity(id) >= 1f) return true;
        return false;
    }

    private boolean playerHasAll() {
        for (Map.Entry<String, Integer> g : remaining().entrySet()) if (!playerHasEnough(g.getKey(), g.getValue())) return false;
        return true;
    }

    /** "3,750 heavy machinery, 3,750 organics and 2,500 metals". */
    private static String goodsList(Map<String, Integer> goods) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> g : goods.entrySet()) {
            parts.add(Misc.getWithDGS(g.getValue()) + " " + Global.getSettings().getCommoditySpec(g.getKey()).getLowerCaseName());
        }
        return Misc.getAndJoined(parts);
    }

    private int cargoUnits() {
        int n = 0;
        for (int q : goods.values()) n += q;
        return n;
    }

    /** "an Atmospheric Scrubber". */
    private String aStructure() {
        String name = structureName();
        return Misc.getAOrAnFor(name) + " " + name;
    }

    private String structureName() {
        DtiData.Project p = project();
        return p == null ? "terraforming" : Global.getSettings().getIndustrySpec(p.structure).getName();
    }

    private String conditionName() {
        DtiData.Project p = project();
        return p == null ? "a problem" : Terraform.conditionName(p.condition);
    }

    /** A contract_texts.csv text with its {words} filled in; the generic text for a project with no row. */
    private String text(int column, String generic) {
        DtiData.Project p = project();
        String[] row = p == null ? null : DtiData.contractText(p.id);
        String t = row == null || row[column].isEmpty() ? generic : row[column];
        return t.replace("{person}", getPerson().getManOrWoman()).replace("{market}", target.getName()).replace("{condition}", conditionName())
                .replace("{structure}", structureName()).replace("{aStructure}", aStructure()).replace("{AStructure}", Misc.ucFirst(aStructure()));
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$dtiContract_barEvent", isBarEvent());
        set("$dtiContract_manOrWoman", getPerson().getManOrWoman());
        set("$dtiContract_marketName", target.getName());
        set("$dtiContract_conditionName", conditionName());
        set("$dtiContract_structureName", structureName());
        set("$dtiContract_aStructureName", aStructure());
        set("$dtiContract_intro", INTROS[Math.min(intro, INTROS.length - 1)]);
        set("$dtiContract_blurb", text(0,"A {person} at a corner table reads a planetary survey and frowns."));
        set("$dtiContract_option", text(1, "Talk to the {person} with the planetary survey"));
        set("$dtiContract_problem", text(2, "{market} has a problem that our crews cannot fix alone. {AStructure} project can fix it."));
        set("$dtiContract_goodsList", goodsList(goods));
        set("$dtiContract_remaining", goodsList(remaining()));
        set("$dtiContract_cargo", Misc.getWithDGS(cargoUnits()));
        set("$dtiContract_reward", Misc.getDGSCredits(getCreditsReward()));
        set("$dtiContract_playerHasSome", playerHasSome());
        set("$dtiContract_allDelivered", remaining().isEmpty());
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        if (currentStage != Stage.DELIVER) return;
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        info.addPara("Bring %s to " + getPerson().getNameString() + " " + target.getOnOrAt() + " " + target.getName() + ", in parts if you need to. "
                + "You are paid %s when the last part arrives. Then " + aStructure() + " project removes " + conditionName() + ".",
                opad, h, goodsList(goods), Misc.getDGSCredits(getCreditsReward()));
        if (!delivered.isEmpty()) info.addPara("Still needed: %s.", opad, h, goodsList(remaining()));
        info.addPara(playerHasAll() ? "You have the rest of the goods in your cargo holds." : playerHasSome() ? "You have some of the goods in your cargo holds."
                : "You have none of the goods in your cargo holds.", opad);
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage != Stage.DELIVER) return false;
        if (playerHasSome()) info.addPara("Go to " + target.getName() + " and contact " + getPerson().getNameString(), tc, pad);
        else info.addPara("Get " + goodsList(remaining()), tc, pad);
        return true;
    }

    @Override
    public String getStageDescriptionText() {
        if (currentStage == Stage.COMPLETED) {
            return "You delivered the goods and were paid. The " + structureName() + " work " + target.getOnOrAt() + " " + target.getName()
                    + " now removes " + conditionName() + ".";
        }
        if (currentStage == Stage.ENDED) {
            return "The contract ended early: " + (endReason == null ? "the work could not start" : endReason) + ". You lose no standing.";
        }
        return null;
    }

    @Override
    public String getBaseName() {
        return "Terraforming Contract";
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(ProjectsIntel.TAG);
        return tags;
    }
}
