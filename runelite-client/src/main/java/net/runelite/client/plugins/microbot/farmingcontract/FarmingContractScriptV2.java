package net.runelite.client.plugins.microbot.farmingcontract;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.timetracking.farming.Produce;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FarmingContractScriptV2 extends Script {
    
    private static final WorldPoint JANE_LOCATION = new WorldPoint(1248, 3727, 0);
    private static final Pattern CONTRACT_PATTERN = Pattern.compile("(?:grow|need) (?:some|a|an) ([a-zA-Z ]+)");
    
    private final FarmingContractPlugin plugin;
    private final FarmingContractConfig config;
    private FarmingContractState state = FarmingContractState.INIT;
    private Produce currentContract = null;
    private String inventorySetupName = "FarmingContract";
    
    @Inject
    public FarmingContractScriptV2(FarmingContractPlugin plugin, FarmingContractConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            
            try {
                switch (state) {
                    case INIT:
                        handleInit();
                        break;
                    case CHECK_CONTRACT:
                        handleCheckContract();
                        break;
                    case GET_CONTRACT:
                        handleGetContract();
                        break;
                    case PREPARE:
                        handlePrepare();
                        break;
                    case FARM:
                        handleFarm();
                        break;
                    case COMPLETE:
                        handleComplete();
                        break;
                }
                
                plugin.setStatus(state.toString());
            } catch (Exception e) {
                log.error("Error in farming contract", e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private void handleInit() {
        // Setup inventory if configured
        Rs2InventorySetup inventorySetup = new Rs2InventorySetup(inventorySetupName, mainScheduledFuture);
        if (!inventorySetup.doesInventoryMatch()) {
            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
            inventorySetup.loadInventory();
        }
        
        // Load contract from config
        loadContract();
        state = FarmingContractState.CHECK_CONTRACT;
    }
    
    private void handleCheckContract() {
        if (currentContract == null) {
            state = FarmingContractState.GET_CONTRACT;
        } else {
            // Check if contract is completed
            if (isContractComplete()) {
                state = FarmingContractState.COMPLETE;
            } else {
                state = FarmingContractState.PREPARE;
            }
        }
    }
    
    private void handleGetContract() {
        if (!isNearJane()) {
            Rs2Walker.walkTo(JANE_LOCATION);
            return;
        }
        
        NPC jane = Rs2Npc.getNpc("Guildmaster Jane");
        if (jane == null) return;
        
        // Try Contract first, then Talk-to
        if (!Rs2Npc.interact(jane, "Contract")) {
            Rs2Npc.interact(jane, "Talk-to");
        }
        
        Rs2Player.waitForAnimation(5000);
        
        // Handle dialogue
        while (Rs2Dialogue.isInDialogue()) {
            // Parse contract from dialogue
            String text = Rs2Dialogue.getDialogueText();
            if (text != null) {
                Matcher m = CONTRACT_PATTERN.matcher(text);
                if (m.find()) {
                    String contractName = m.group(1);
                    currentContract = findProduce(contractName);
                    if (currentContract != null) {
                        log.info("Got contract: {}", currentContract.getName());
                        saveContract();
                    }
                }
            }
            
            // Select tier if needed
            if (Rs2Dialogue.hasSelectAnOption()) {
                String tier = getContractTier();
                Rs2Dialogue.clickOption(tier);
            }
            
            Rs2Dialogue.clickContinue();
            sleep(600);
        }
        
        if (currentContract != null) {
            state = FarmingContractState.PREPARE;
        }
    }
    
    private void handlePrepare() {
        if (currentContract == null) {
            state = FarmingContractState.CHECK_CONTRACT;
            return;
        }
        
        // Check if we have seeds
        int seedId = getSeedId(currentContract);
        if (seedId == -1 || Rs2Inventory.contains(seedId)) {
            state = FarmingContractState.FARM;
            return;
        }
        
        // Bank for seeds
        if (!Rs2Bank.isNearBank(10)) {
            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
            return;
        }
        
        if (Rs2Bank.openBank()) {
            Rs2Bank.depositAllExcept("Rake", "Spade", "Seed dibber", "Magic secateurs");
            
            if (Rs2Bank.hasBankItem(seedId)) {
                Rs2Bank.withdrawX(seedId, 10);
                
                if (config.useCompost()) {
                    Rs2Bank.withdrawX(config.compostType().toString(), 5);
                }
            }
            
            Rs2Bank.closeBank();
            state = FarmingContractState.FARM;
        }
    }
    
    private void handleFarm() {
        if (currentContract == null) {
            state = FarmingContractState.CHECK_CONTRACT;
            return;
        }
        
        WorldPoint patchLocation = getPatchLocation(currentContract);
        if (patchLocation == null) return;
        
        if (!Rs2Player.isNear(patchLocation, 10)) {
            Rs2Walker.walkTo(patchLocation);
            return;
        }
        
        // Simple patch handling using Rs2GameObject
        if (Rs2GameObject.exists("Weeds")) {
            Rs2GameObject.interact("Weeds", "Rake");
            Rs2Player.waitForAnimation(3000);
        } else if (Rs2GameObject.exists("Dead")) {
            Rs2GameObject.interact("Dead", "Clear");
            Rs2Player.waitForAnimation(3000);
        } else if (isPatchEmpty()) {
            // Plant seeds
            int seedId = getSeedId(currentContract);
            if (seedId != -1 && Rs2Inventory.contains(seedId)) {
                Rs2Inventory.use(seedId);
                Rs2GameObject.interact(getPatchObjectName(currentContract), "Use");
                Rs2Player.waitForAnimation(3000);
                
                // Apply compost
                if (config.useCompost()) {
                    String compost = config.compostType().toString();
                    if (Rs2Inventory.contains(compost)) {
                        Rs2Inventory.use(compost);
                        Rs2GameObject.interact(getPatchObjectName(currentContract), "Use");
                        Rs2Player.waitForAnimation(3000);
                    }
                }
                
                // After planting, we're done - stop the plugin
                log.info("Contract planted successfully. Plugin stopping.");
                plugin.setStatus("Contract planted - stopping");
                shutdown();
            }
        } else if (isReadyToHarvest()) {
            // Harvest
            String action = getHarvestAction(currentContract);
            Rs2GameObject.interact(getPatchObjectName(currentContract), action);
            Rs2Inventory.waitForInventoryChanges(10000);
            
            // Check if complete
            if (isContractComplete()) {
                state = FarmingContractState.COMPLETE;
            }
        } else if (isGrowing()) {
            // Something is already growing - stop the plugin
            log.info("Patch is already growing. Plugin stopping.");
            plugin.setStatus("Patch growing - stopping");
            shutdown();
        }
    }
    
    private void handleComplete() {
        if (!isNearJane()) {
            Rs2Walker.walkTo(JANE_LOCATION);
            return;
        }
        
        NPC jane = Rs2Npc.getNpc("Guildmaster Jane");
        if (jane == null) return;
        
        if (!Rs2Npc.interact(jane, "Contract")) {
            Rs2Npc.interact(jane, "Talk-to");
        }
        
        while (Rs2Dialogue.isInDialogue()) {
            String text = Rs2Dialogue.getDialogueText();
            if (text != null) {
                // Check for reward text
                if (text.contains("reward")) {
                    currentContract = null;
                    saveContract();
                }
                
                // Parse new contract from dialogue
                Matcher m = CONTRACT_PATTERN.matcher(text);
                if (m.find()) {
                    String contractName = m.group(1);
                    currentContract = findProduce(contractName);
                    if (currentContract != null) {
                        log.info("Got new contract after completion: {}", currentContract.getName());
                        saveContract();
                    }
                }
            }
            
            // Select tier for new contract if needed
            if (Rs2Dialogue.hasSelectAnOption()) {
                String tier = getContractTier();
                Rs2Dialogue.clickOption(tier);
            }
            
            Rs2Dialogue.clickContinue();
            sleep(600);
        }
        
        // After completing and getting new contract, go prepare and plant it
        if (currentContract != null) {
            state = FarmingContractState.PREPARE;
        } else {
            state = FarmingContractState.GET_CONTRACT;
        }
    }
    
    // Helper methods
    private boolean isNearJane() {
        return Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) <= 10;
    }
    
    private String getContractTier() {
        int level = Rs2Player.getRealSkillLevel(Skill.FARMING);
        if (level >= 85) return "Hard";
        if (level >= 65) return "Medium";
        return "Easy";
    }
    
    private void loadContract() {
        try {
            String stored = Microbot.getConfigManager().getRSProfileConfiguration(
                "farmingcontract", "currentContract");
            if (stored != null) {
                int itemId = Integer.parseInt(stored);
                currentContract = findProduceByItemId(itemId);
            }
        } catch (Exception e) {
            // No stored contract
        }
    }
    
    private void saveContract() {
        if (currentContract != null) {
            Microbot.getConfigManager().setRSProfileConfiguration(
                "farmingcontract", "currentContract", 
                String.valueOf(currentContract.getItemID()));
        } else {
            Microbot.getConfigManager().unsetRSProfileConfiguration(
                "farmingcontract", "currentContract");
        }
    }
    
    private Produce findProduce(String name) {
        // Use reflection to access package-private method
        try {
            Method method = Produce.class.getDeclaredMethod("getByContractName", String.class);
            method.setAccessible(true);
            return (Produce) method.invoke(null, name);
        } catch (Exception e) {
            // Fallback to simple name matching
            String lower = name.toLowerCase();
            for (Produce p : Produce.values()) {
                if (p.getName().toLowerCase().contains(lower) || 
                    lower.contains(p.getName().toLowerCase())) {
                    return p;
                }
            }
        }
        return null;
    }
    
    private Produce findProduceByItemId(int itemId) {
        // Use reflection to access package-private method
        try {
            Method method = Produce.class.getDeclaredMethod("getByItemID", int.class);
            method.setAccessible(true);
            return (Produce) method.invoke(null, itemId);
        } catch (Exception e) {
            // Fallback
            for (Produce p : Produce.values()) {
                if (p.getItemID() == itemId) {
                    return p;
                }
            }
        }
        return null;
    }
    
    private int getSeedId(Produce produce) {
        // Simple seed mapping - extend as needed
        switch (produce.getName().toLowerCase()) {
            case "potato": return ItemID.POTATO_SEED;
            case "onion": return ItemID.ONION_SEED;
            case "tomato": return ItemID.TOMATO_SEED;
            case "sweetcorn": return ItemID.SWEETCORN_SEED;
            case "strawberry": return ItemID.STRAWBERRY_SEED;
            case "watermelon": return ItemID.WATERMELON_SEED;
            // Add more as needed
            default: return -1;
        }
    }
    
    private WorldPoint getPatchLocation(Produce produce) {
        // Simple patch locations - can be extended
        switch (produce.getPatchImplementation()) {
            case HERB: return new WorldPoint(1239, 3728, 0);
            case TREE: return new WorldPoint(1233, 3734, 0);
            case FRUIT_TREE: return new WorldPoint(1243, 3757, 0);
            case FLOWER: return new WorldPoint(1260, 3727, 0);
            case BUSH: return new WorldPoint(1260, 3732, 0);
            case ALLOTMENT: return new WorldPoint(1265, 3729, 0);
            case CACTUS: return new WorldPoint(1264, 3746, 0);
            default: return null;
        }
    }
    
    private String getPatchObjectName(Produce produce) {
        switch (produce.getPatchImplementation()) {
            case HERB: return "Herb patch";
            case TREE: return "Tree patch";
            case FRUIT_TREE: return "Fruit tree patch";
            case FLOWER: return "Flower patch";
            case BUSH: return "Bush patch";
            case ALLOTMENT: return "Allotment";
            case CACTUS: return "Cactus patch";
            default: return "patch";
        }
    }
    
    private String getHarvestAction(Produce produce) {
        switch (produce.getPatchImplementation()) {
            case TREE:
            case FRUIT_TREE:
                return "Check-health";
            case HERB:
            case FLOWER:
                return "Pick";
            case BUSH:
                return "Pick-from";
            case CACTUS:
                return "Pick-spine";
            default:
                return "Harvest";
        }
    }
    
    private boolean isPatchEmpty() {
        // Check if patch has no crops
        return Rs2GameObject.exists("patch") && 
               !Rs2GameObject.exists("Weeds") &&
               !Rs2GameObject.exists("Dead");
    }
    
    private boolean isReadyToHarvest() {
        // Simple check - can be improved
        return Rs2GameObject.exists("Pick") ||
               Rs2GameObject.exists("Harvest") ||
               Rs2GameObject.exists("Check-health");
    }
    
    private boolean isContractComplete() {
        // Check if we harvested the contract produce
        return currentContract != null && 
               Rs2Inventory.contains(currentContract.getName());
    }
    
    private boolean isGrowing() {
        // Check if patch has growing crops (not ready to harvest yet)
        return !isPatchEmpty() && !isReadyToHarvest() && 
               !Rs2GameObject.exists("Weeds") && !Rs2GameObject.exists("Dead");
    }
}