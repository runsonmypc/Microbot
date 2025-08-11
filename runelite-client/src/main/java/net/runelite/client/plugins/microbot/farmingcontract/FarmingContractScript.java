package net.runelite.client.plugins.microbot.farmingcontract;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.farming.Produce;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class FarmingContractScript extends Script {
    
    private static final int FARMING_GUILD_REGION = 4922;
    // Guildmaster Jane is in the main area of the Farming Guild
    private static final WorldPoint JANE_LOCATION = new WorldPoint(1248, 3727, 0);
    private static final Random random = new Random();
    
    // Farming Guild patch object IDs
    private static final int[] HERB_PATCH_IDS = {
        ObjectID.FARMING_HERB_PATCH_1, ObjectID.FARMING_HERB_PATCH_2,
        ObjectID.FARMING_HERB_PATCH_3, ObjectID.FARMING_HERB_PATCH_4,
        ObjectID.FARMING_HERB_PATCH_5, ObjectID.FARMING_HERB_PATCH_6,
        ObjectID.FARMING_HERB_PATCH_7, ObjectID.FARMING_HERB_PATCH_8
    };
    
    private static final int[] TREE_PATCH_IDS = {
        ObjectID.FARMING_TREE_PATCH_1, ObjectID.FARMING_TREE_PATCH_2,
        ObjectID.FARMING_TREE_PATCH_3, ObjectID.FARMING_TREE_PATCH_4,
        ObjectID.FARMING_TREE_PATCH_5
    };
    
    private static final int[] FRUIT_TREE_PATCH_IDS = {
        ObjectID.FARMING_FRUIT_TREE_PATCH_1, ObjectID.FARMING_FRUIT_TREE_PATCH_2,
        ObjectID.FARMING_FRUIT_TREE_PATCH_3, ObjectID.FARMING_FRUIT_TREE_PATCH_4,
        ObjectID.FARMING_FRUIT_TREE_PATCH_5
    };
    
    private static final int[] FLOWER_PATCH_IDS = {
        ObjectID.FARMING_FLOWER_PATCH_1, ObjectID.FARMING_FLOWER_PATCH_2,
        ObjectID.FARMING_FLOWER_PATCH_3, ObjectID.FARMING_FLOWER_PATCH_4,
        ObjectID.FARMING_FLOWER_PATCH_5
    };
    
    private static final int[] BUSH_PATCH_IDS = {
        ObjectID.FARMING_BUSH_PATCH_1, ObjectID.FARMING_BUSH_PATCH_2,
        ObjectID.FARMING_BUSH_PATCH_3, ObjectID.FARMING_BUSH_PATCH_4
    };
    
    // Allotment patches (called VEG patches in ObjectID)
    private static final int[] ALLOTMENT_PATCH_IDS = {
        ObjectID.FARMING_VEG_PATCH_1, ObjectID.FARMING_VEG_PATCH_2,
        ObjectID.FARMING_VEG_PATCH_3, ObjectID.FARMING_VEG_PATCH_4,
        ObjectID.FARMING_VEG_PATCH_5, ObjectID.FARMING_VEG_PATCH_6,
        ObjectID.FARMING_VEG_PATCH_7, ObjectID.FARMING_VEG_PATCH_8
    };
    
    // Cactus patch
    private static final int[] CACTUS_PATCH_IDS = {
        ObjectID.FARMING_CACTUS_PATCH
    };
    
    private final FarmingContractPlugin plugin;
    private final FarmingContractConfig config;
    private FarmingWorld farmingWorld;
    private FarmingContractState state = FarmingContractState.NO_CONTRACT;
    private Produce currentContract = null;
    private FarmingPatch targetPatch = null;
    private boolean initialInventorySetupComplete = false;
    private String lastDialogueText = "";
    
    // Dialogue patterns for contract detection
    private static final Pattern CONTRACT_ASSIGN_PATTERN = Pattern.compile("(?:We need you to grow|Please could you grow) (?:some|a|an) ([a-zA-Z ]+)(?: for us\\?|\\.)");
    private static final String CONTRACT_COMPLETED_TEXT = "You've completed a Farming Guild Contract";
    private static final String CONTRACT_REWARDED = "You'll be wanting a reward then. Here you go.";
    
    private static final Map<Produce, Integer> PRODUCE_TO_SEED = new HashMap<>();
    
    static {
        // Allotments
        PRODUCE_TO_SEED.put(Produce.POTATO, ItemID.POTATO_SEED);
        PRODUCE_TO_SEED.put(Produce.ONION, ItemID.ONION_SEED);
        PRODUCE_TO_SEED.put(Produce.CABBAGE, ItemID.CABBAGE_SEED);
        PRODUCE_TO_SEED.put(Produce.TOMATO, ItemID.TOMATO_SEED);
        PRODUCE_TO_SEED.put(Produce.SWEETCORN, ItemID.SWEETCORN_SEED);
        PRODUCE_TO_SEED.put(Produce.STRAWBERRY, ItemID.STRAWBERRY_SEED);
        PRODUCE_TO_SEED.put(Produce.WATERMELON, ItemID.WATERMELON_SEED);
        PRODUCE_TO_SEED.put(Produce.SNAPE_GRASS, ItemID.SNAPE_GRASS_SEED);
        
        // Flowers
        PRODUCE_TO_SEED.put(Produce.MARIGOLD, ItemID.MARIGOLD_SEED);
        PRODUCE_TO_SEED.put(Produce.ROSEMARY, ItemID.ROSEMARY_SEED);
        PRODUCE_TO_SEED.put(Produce.NASTURTIUM, ItemID.NASTURTIUM_SEED);
        PRODUCE_TO_SEED.put(Produce.WOAD, ItemID.WOAD_SEED);
        PRODUCE_TO_SEED.put(Produce.LIMPWURT, ItemID.LIMPWURT_SEED);
        PRODUCE_TO_SEED.put(Produce.WHITE_LILY, ItemID.WHITE_LILY_SEED);
        
        // Herbs
        PRODUCE_TO_SEED.put(Produce.GUAM, ItemID.GUAM_SEED);
        PRODUCE_TO_SEED.put(Produce.MARRENTILL, ItemID.MARRENTILL_SEED);
        PRODUCE_TO_SEED.put(Produce.TARROMIN, ItemID.TARROMIN_SEED);
        PRODUCE_TO_SEED.put(Produce.HARRALANDER, ItemID.HARRALANDER_SEED);
        PRODUCE_TO_SEED.put(Produce.RANARR, ItemID.RANARR_SEED);
        PRODUCE_TO_SEED.put(Produce.TOADFLAX, ItemID.TOADFLAX_SEED);
        PRODUCE_TO_SEED.put(Produce.IRIT, ItemID.IRIT_SEED);
        PRODUCE_TO_SEED.put(Produce.AVANTOE, ItemID.AVANTOE_SEED);
        PRODUCE_TO_SEED.put(Produce.KWUARM, ItemID.KWUARM_SEED);
        PRODUCE_TO_SEED.put(Produce.SNAPDRAGON, ItemID.SNAPDRAGON_SEED);
        PRODUCE_TO_SEED.put(Produce.CADANTINE, ItemID.CADANTINE_SEED);
        PRODUCE_TO_SEED.put(Produce.LANTADYME, ItemID.LANTADYME_SEED);
        PRODUCE_TO_SEED.put(Produce.DWARF_WEED, ItemID.DWARF_WEED_SEED);
        PRODUCE_TO_SEED.put(Produce.TORSTOL, ItemID.TORSTOL_SEED);
        
        // Trees
        PRODUCE_TO_SEED.put(Produce.OAK, ItemID.ACORN);
        PRODUCE_TO_SEED.put(Produce.WILLOW, ItemID.WILLOW_SEED);
        PRODUCE_TO_SEED.put(Produce.MAPLE, ItemID.MAPLE_SEED);
        PRODUCE_TO_SEED.put(Produce.YEW, ItemID.YEW_SEED);
        PRODUCE_TO_SEED.put(Produce.MAGIC, ItemID.MAGIC_TREE_SEED);
        
        // Fruit trees
        PRODUCE_TO_SEED.put(Produce.APPLE, ItemID.APPLE_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.BANANA, ItemID.BANANA_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.ORANGE, ItemID.ORANGE_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.CURRY, ItemID.CURRY_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.PINEAPPLE, ItemID.PINEAPPLE_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.PAPAYA, ItemID.PAPAYA_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.PALM, ItemID.PALM_TREE_SEED);
        PRODUCE_TO_SEED.put(Produce.DRAGONFRUIT, ItemID.DRAGONFRUIT_TREE_SEED);
        
        // Bushes
        PRODUCE_TO_SEED.put(Produce.REDBERRIES, ItemID.REDBERRY_BUSH_SEED);
        PRODUCE_TO_SEED.put(Produce.CADAVABERRIES, ItemID.CADAVABERRY_BUSH_SEED);
        PRODUCE_TO_SEED.put(Produce.DWELLBERRIES, ItemID.DWELLBERRY_BUSH_SEED);
        PRODUCE_TO_SEED.put(Produce.JANGERBERRIES, ItemID.JANGERBERRY_BUSH_SEED);
        PRODUCE_TO_SEED.put(Produce.WHITEBERRIES, ItemID.WHITEBERRY_BUSH_SEED);
        PRODUCE_TO_SEED.put(Produce.POISON_IVY, ItemID.POISONIVY_BUSH_SEED);
        
        // Hops
        PRODUCE_TO_SEED.put(Produce.BARLEY, ItemID.BARLEY_SEED);
        PRODUCE_TO_SEED.put(Produce.HAMMERSTONE, ItemID.HAMMERSTONE_HOP_SEED);
        PRODUCE_TO_SEED.put(Produce.ASGARNIAN, ItemID.ASGARNIAN_HOP_SEED);
        PRODUCE_TO_SEED.put(Produce.JUTE, ItemID.JUTE_SEED);
        PRODUCE_TO_SEED.put(Produce.YANILLIAN, ItemID.YANILLIAN_HOP_SEED);
        PRODUCE_TO_SEED.put(Produce.KRANDORIAN, ItemID.KRANDORIAN_HOP_SEED);
        PRODUCE_TO_SEED.put(Produce.WILDBLOOD, ItemID.WILDBLOOD_HOP_SEED);
        
        // Cactus
        PRODUCE_TO_SEED.put(Produce.CACTUS, ItemID.CACTUS_SEED);
        PRODUCE_TO_SEED.put(Produce.POTATO_CACTUS, ItemID.POTATO_CACTUS_SEED);
    }
    
    public FarmingContractScript(FarmingContractPlugin plugin, FarmingContractConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            
            try {
                // Initial inventory setup
                if (!initialInventorySetupComplete) {
                    state = FarmingContractState.INITIAL_INVENTORY_SETUP;
                }
                
                if (farmingWorld == null) {
                    farmingWorld = Microbot.getInjector().getInstance(FarmingWorld.class);
                    if (farmingWorld == null) {
                        log.error("Failed to get FarmingWorld");
                        state = FarmingContractState.ERROR;
                        return;
                    }
                }
                
                // Update current contract from varbit
                updateCurrentContract();
                
                // State machine
                switch (state) {
                    case INITIAL_INVENTORY_SETUP:
                        handleInitialInventorySetup();
                        break;
                    case NO_CONTRACT:
                        handleNoContract();
                        break;
                    case GETTING_CONTRACT:
                        handleGettingContract();
                        break;
                    case CHECKING_SEEDS:
                        handleCheckingSeeds();
                        break;
                    case NO_SEEDS_AVAILABLE:
                        handleNoSeeds();
                        break;
                    case DOWNGRADING_CONTRACT:
                        handleDowngradingContract();
                        break;
                    case BANKING_FOR_SEEDS:
                        handleBankingForSeeds();
                        break;
                    case WALKING_TO_PATCH:
                        handleWalkingToPatch();
                        break;
                    case CLEARING_PATCH:
                        handleClearingPatch();
                        break;
                    case PLANTING:
                        handlePlanting();
                        break;
                    case WAITING_FOR_GROWTH:
                        handleWaitingForGrowth();
                        break;
                    case HARVESTING:
                        handleHarvesting();
                        break;
                    case CLEARING_AFTER_HARVEST:
                        handleClearingAfterHarvest();
                        break;
                    case COMPLETING_CONTRACT:
                        handleCompletingContract();
                        break;
                    case STOPPED_NO_SEEDS:
                        log.info("Plugin stopped - no seeds available");
                        shutdown();
                        break;
                    case ERROR:
                        log.error("Plugin encountered an error");
                        shutdown();
                        break;
                }
                
                // Update plugin status
                plugin.setStatus(state.toString());
                
            } catch (Exception e) {
                log.error("Error in farming contract script", e);
                state = FarmingContractState.ERROR;
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private void handleInitialInventorySetup() {
        // Walk to bank if not near one
        if (!Rs2Bank.isNearBank(10)) {
            log.info("Walking to bank for initial inventory setup");
            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
            sleepUntil(() -> Rs2Bank.isNearBank(10), 10000);
            return;
        }
        
        // Open bank
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(() -> Rs2Bank.isOpen(), 5000);
            return;
        }
        
        // First, deposit everything from inventory (but not equipment)
        if (!Rs2Inventory.isEmpty()) {
            Rs2Bank.depositAll();
            sleepUntil(() -> Rs2Inventory.isEmpty(), 2000);
            return;
        }
        
        // Withdraw essential farming tools
        boolean hasRake = Rs2Inventory.contains("Rake");
        boolean hasSpade = Rs2Inventory.contains("Spade");
        boolean hasDibber = Rs2Inventory.contains("Seed dibber");
        boolean hasSecateurs = Rs2Inventory.contains("Magic secateurs");
        
        // Withdraw rake if we don't have it
        if (!hasRake) {
            if (Rs2Bank.hasBankItem("Rake")) {
                Rs2Bank.withdrawOne("Rake");
                sleep(600);
            } else {
                log.error("No rake found in bank!");
                state = FarmingContractState.ERROR;
                return;
            }
        }
        
        // Withdraw spade if we don't have it
        if (!hasSpade) {
            if (Rs2Bank.hasBankItem("Spade")) {
                Rs2Bank.withdrawOne("Spade");
                sleep(600);
            } else {
                log.error("No spade found in bank!");
                state = FarmingContractState.ERROR;
                return;
            }
        }
        
        // Withdraw seed dibber if we don't have it
        if (!hasDibber) {
            if (Rs2Bank.hasBankItem("Seed dibber")) {
                Rs2Bank.withdrawOne("Seed dibber");
                sleep(600);
            } else {
                log.error("No seed dibber found in bank!");
                state = FarmingContractState.ERROR;
                return;
            }
        }
        
        // Withdraw magic secateurs if available and we don't have them
        if (!hasSecateurs && Rs2Bank.hasBankItem("Magic secateurs")) {
            Rs2Bank.withdrawOne("Magic secateurs");
            sleep(600);
        }
        
        // Also withdraw some coins for tree clearing (200gp per tree)
        if (!Rs2Inventory.contains("Coins")) {
            if (Rs2Bank.hasBankItem("Coins")) {
                Rs2Bank.withdrawX("Coins", 1000); // Withdraw 1000 coins for multiple tree clearings
                sleep(600);
            }
        }
        
        // Close bank
        Rs2Bank.closeBank();
        sleep(600);
        
        // Mark setup as complete and move to checking contract
        initialInventorySetupComplete = true;
        log.info("Initial inventory setup complete - have essential farming tools");
        state = FarmingContractState.NO_CONTRACT;
    }
    
    /**
     * Update current contract from dialogue or stored config
     */
    private void updateCurrentContract() {
        // Check if we have dialogue from Jane
        if (Rs2Dialogue.isInDialogue()) {
            String dialogueText = Rs2Dialogue.getDialogueText();
            if (dialogueText != null && !dialogueText.equals(lastDialogueText)) {
                lastDialogueText = dialogueText;
                parseContractFromDialogue(dialogueText);
            }
        }
        
        // If no current contract, try to load from config
        if (currentContract == null) {
            loadContractFromConfig();
        }
    }
    
    /**
     * Parse contract assignment from Jane's dialogue
     */
    private void parseContractFromDialogue(String dialogueText) {
        // Check for contract completion
        if (dialogueText.contains(CONTRACT_REWARDED)) {
            log.info("Contract completed - reward received");
            currentContract = null;
            saveContractToConfig(null);
            return;
        }
        
        // Check for new contract assignment
        Matcher matcher = CONTRACT_ASSIGN_PATTERN.matcher(dialogueText);
        if (matcher.find()) {
            String contractName = matcher.group(1);
            log.info("New contract detected from dialogue: {}", contractName);
            
            // Find the Produce that matches this contract name
            Produce newContract = findProduceByContractName(contractName);
            if (newContract != null) {
                currentContract = newContract;
                saveContractToConfig(newContract);
                log.info("Contract set to: {}", newContract.getName());
            }
        }
    }
    
    /**
     * Find Produce by item ID (since getByItemID is package-private)
     */
    private Produce findProduceByItemId(int itemId) {
        for (Produce produce : Produce.values()) {
            if (produce.getItemID() == itemId) {
                return produce;
            }
        }
        return null;
    }
    
    /**
     * Find Produce by contract name (since getByContractName is package-private)
     */
    private Produce findProduceByContractName(String contractName) {
        // Try exact name match first
        for (Produce produce : Produce.values()) {
            if (produce.getName().equalsIgnoreCase(contractName)) {
                return produce;
            }
        }
        
        // Handle special contract names that differ from produce names
        String normalizedName = contractName.toLowerCase();
        
        // Map contract names to produce enums
        if (normalizedName.contains("potato")) return Produce.POTATO;
        if (normalizedName.contains("onion")) return Produce.ONION;
        if (normalizedName.contains("cabbage")) return Produce.CABBAGE;
        if (normalizedName.contains("tomato")) return Produce.TOMATO;
        if (normalizedName.contains("sweetcorn") || normalizedName.contains("corn")) return Produce.SWEETCORN;
        if (normalizedName.contains("strawberr")) return Produce.STRAWBERRY;
        if (normalizedName.contains("watermelon")) return Produce.WATERMELON;
        if (normalizedName.contains("snape grass")) return Produce.SNAPE_GRASS;
        
        // Flowers
        if (normalizedName.contains("marigold")) return Produce.MARIGOLD;
        if (normalizedName.contains("rosemary")) return Produce.ROSEMARY;
        if (normalizedName.contains("nasturtium")) return Produce.NASTURTIUM;
        if (normalizedName.contains("woad")) return Produce.WOAD;
        if (normalizedName.contains("limpwurt")) return Produce.LIMPWURT;
        if (normalizedName.contains("white lily") || normalizedName.contains("lily")) return Produce.WHITE_LILY;
        
        // Herbs
        if (normalizedName.contains("guam")) return Produce.GUAM;
        if (normalizedName.contains("marrentill")) return Produce.MARRENTILL;
        if (normalizedName.contains("tarromin")) return Produce.TARROMIN;
        if (normalizedName.contains("harralander")) return Produce.HARRALANDER;
        if (normalizedName.contains("ranarr")) return Produce.RANARR;
        if (normalizedName.contains("toadflax")) return Produce.TOADFLAX;
        if (normalizedName.contains("irit")) return Produce.IRIT;
        if (normalizedName.contains("avantoe")) return Produce.AVANTOE;
        if (normalizedName.contains("kwuarm")) return Produce.KWUARM;
        if (normalizedName.contains("snapdragon")) return Produce.SNAPDRAGON;
        if (normalizedName.contains("cadantine")) return Produce.CADANTINE;
        if (normalizedName.contains("lantadyme")) return Produce.LANTADYME;
        if (normalizedName.contains("dwarf weed")) return Produce.DWARF_WEED;
        if (normalizedName.contains("torstol")) return Produce.TORSTOL;
        
        // Trees
        if (normalizedName.contains("oak")) return Produce.OAK;
        if (normalizedName.contains("willow")) return Produce.WILLOW;
        if (normalizedName.contains("maple")) return Produce.MAPLE;
        if (normalizedName.contains("yew")) return Produce.YEW;
        if (normalizedName.contains("magic tree") || normalizedName.contains("magic")) return Produce.MAGIC;
        
        // Fruit trees
        if (normalizedName.contains("apple")) return Produce.APPLE;
        if (normalizedName.contains("banana")) return Produce.BANANA;
        if (normalizedName.contains("orange")) return Produce.ORANGE;
        if (normalizedName.contains("curry")) return Produce.CURRY;
        if (normalizedName.contains("pineapple")) return Produce.PINEAPPLE;
        if (normalizedName.contains("papaya")) return Produce.PAPAYA;
        if (normalizedName.contains("palm")) return Produce.PALM;
        if (normalizedName.contains("dragonfruit")) return Produce.DRAGONFRUIT;
        
        // Bushes
        if (normalizedName.contains("redberr")) return Produce.REDBERRIES;
        if (normalizedName.contains("cadavaberr")) return Produce.CADAVABERRIES;
        if (normalizedName.contains("dwellberr")) return Produce.DWELLBERRIES;
        if (normalizedName.contains("jangerberr")) return Produce.JANGERBERRIES;
        if (normalizedName.contains("whiteberr")) return Produce.WHITEBERRIES;
        if (normalizedName.contains("poison ivy")) return Produce.POISON_IVY;
        
        // Cactus
        if (normalizedName.contains("potato cactus")) return Produce.POTATO_CACTUS;
        if (normalizedName.contains("cactus")) return Produce.CACTUS;
        
        log.warn("Could not find produce for contract name: {}", contractName);
        return null;
    }
    
    /**
     * Load contract from config storage
     */
    private void loadContractFromConfig() {
        try {
            // First try our own config
            String storedContract = Microbot.getConfigManager().getRSProfileConfiguration(
                "farmingcontract", "currentContract");
            if (storedContract != null) {
                int itemId = Integer.parseInt(storedContract);
                currentContract = findProduceByItemId(itemId);
                if (currentContract != null) {
                    log.info("Loaded existing contract from plugin config: {}", currentContract.getName());
                    return;
                }
            }
            
            // Also try to get contract from TimeTracking plugin's config
            String timeTrackingContract = Microbot.getConfigManager().getRSProfileConfiguration(
                "timetracking", "contract");
            if (timeTrackingContract != null) {
                int itemId = Integer.parseInt(timeTrackingContract);
                currentContract = findProduceByItemId(itemId);
                if (currentContract != null) {
                    log.info("Loaded existing contract from TimeTracking config: {}", currentContract.getName());
                    // Save to our config too
                    saveContractToConfig(currentContract);
                }
            }
        } catch (Exception e) {
            log.debug("No stored contract found or error loading: {}", e.getMessage());
        }
    }
    
    /**
     * Save contract to config storage
     */
    private void saveContractToConfig(Produce contract) {
        if (contract != null) {
            Microbot.getConfigManager().setRSProfileConfiguration(
                "farmingcontract", "currentContract", String.valueOf(contract.getItemID()));
        } else {
            Microbot.getConfigManager().unsetRSProfileConfiguration(
                "farmingcontract", "currentContract");
        }
    }
    
    /**
     * Get contract completion state by checking patch states
     */
    private SummaryState getContractState() {
        if (currentContract == null) {
            return SummaryState.UNKNOWN;
        }
        
        // Check the actual patch state in the farming guild
        GameObject patch = findContractPatchObject();
        if (patch == null) {
            return SummaryState.UNKNOWN;
        }
        
        String patchState = getPatchState(patch);
        switch (patchState) {
            case "Weeds":
            case "Empty":
                return SummaryState.EMPTY;
            case "Growing":
            case "Diseased":
                return SummaryState.IN_PROGRESS;
            case "Harvestable":
            case "CheckHealth":
                return SummaryState.COMPLETED;
            case "Dead":
                return SummaryState.OCCUPIED; // Dead crop occupying patch
            default:
                // Check if it's the wrong crop
                ObjectComposition comp = Rs2GameObject.getObjectComposition(patch.getId());
                if (comp != null && !comp.getName().toLowerCase().contains(currentContract.getName().toLowerCase())) {
                    return SummaryState.OCCUPIED;
                }
                return SummaryState.IN_PROGRESS;
        }
    }
    
    private void handleNoContract() {
        if (currentContract != null) {
            // Check the state of existing contract
            SummaryState contractState = getContractState();
            log.info("Found existing contract: {} in state: {}", currentContract.getName(), contractState);
            
            switch (contractState) {
                case COMPLETED:
                    // Crop is ready to harvest
                    state = FarmingContractState.HARVESTING;
                    break;
                case IN_PROGRESS:
                    // Crop is growing
                    state = FarmingContractState.WAITING_FOR_GROWTH;
                    break;
                case OCCUPIED:
                    // Wrong crop in patch - need to clear it
                    state = FarmingContractState.WALKING_TO_PATCH;
                    // Will transition to CLEARING_PATCH when at patch
                    break;
                case EMPTY:
                    // Need to plant
                    state = FarmingContractState.CHECKING_SEEDS;
                    break;
                default:
                    state = FarmingContractState.CHECKING_SEEDS;
                    break;
            }
            return;
        }
        
        // Walk to Jane
        if (!isNearJane()) {
            Rs2Walker.walkTo(JANE_LOCATION);
            sleepUntil(() -> isNearJane(), 10000);
            return;
        }
        
        state = FarmingContractState.GETTING_CONTRACT;
    }
    
    private void handleGettingContract() {
        // Try to find Jane using multiple NPC IDs
        NPC jane = findGuildmasterJane();
        if (jane == null) {
            log.warn("Could not find Guildmaster Jane");
            state = FarmingContractState.NO_CONTRACT;
            return;
        }
        
        // Determine which interaction to use based on Jane's ID
        int janeId = jane.getId();
        boolean interacted = false;
        
        if (janeId == NpcID.FARMING_GUILD_MASTER_1OP) {
            // Single option Jane - use Contract directly
            interacted = Rs2Npc.interact(jane, "Contract");
        } else if (janeId == NpcID.FARMING_GUILD_MASTER_2OP) {
            // Two option Jane - use Talk-to first
            interacted = Rs2Npc.interact(jane, "Talk-to");
        } else {
            // Default Jane (8628) or unknown - try Talk-to
            interacted = Rs2Npc.interact(jane, "Talk-to");
            if (!interacted) {
                interacted = Rs2Npc.interact(jane, "Contract");
            }
        }
        
        if (!interacted) {
            log.warn("Failed to interact with Jane");
            return;
        }
        
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
        
        // Handle contract dialogue options
        if (Rs2Dialogue.hasSelectAnOption()) {
            // Try to click contract-related option
            if (!Rs2Dialogue.clickOption(".*[Cc]ontract.*")) {
                // If no contract option, try farming-related option
                Rs2Dialogue.clickOption(".*[Ff]arming.*");
            }
            sleep(600);
        }
        
        // If we already have a contract, this might show the completion dialogue
        // Otherwise, it will show contract tier selection
        if (Rs2Dialogue.hasSelectAnOption()) {
            String tierOption = getTierDialogueOption();
            Rs2Dialogue.clickOption(tierOption);
            sleep(600);
        }
        
        // Continue through dialogue and check for contract assignment
        while (Rs2Dialogue.isInDialogue()) {
            // Update contract from dialogue text
            updateCurrentContract();
            Rs2Dialogue.clickContinue();
            sleep(600);
        }
        
        // Check if we got a contract
        sleep(1000);
        if (currentContract != null) {
            log.info("Contract received: {}", currentContract.getName());
            state = FarmingContractState.CHECKING_SEEDS;
        } else {
            // Try again - might need to complete existing contract first
            state = FarmingContractState.NO_CONTRACT;
        }
    }
    
    /**
     * Find Guildmaster Jane using multiple possible NPC IDs
     */
    private NPC findGuildmasterJane() {
        // Try all known Jane IDs
        NPC jane = Rs2Npc.getNpc(NpcID.FARMING_GUILD_MASTER_1OP);
        if (jane != null) return jane;
        
        jane = Rs2Npc.getNpc(NpcID.FARMING_GUILD_MASTER_2OP);
        if (jane != null) return jane;
        
        jane = Rs2Npc.getNpc(NpcID.FARMING_GUILD_MASTER);
        if (jane != null) return jane;
        
        // Try by name as fallback
        jane = Rs2Npc.getNpc("Guildmaster Jane");
        return jane;
    }
    
    private void handleCheckingSeeds() {
        if (currentContract == null) {
            state = FarmingContractState.NO_CONTRACT;
            return;
        }
        
        // Reset target patch for new contract
        targetPatch = null;
        
        Integer seedId = PRODUCE_TO_SEED.get(currentContract);
        if (seedId == null) {
            log.error("No seed mapping for produce: " + currentContract);
            state = FarmingContractState.ERROR;
            return;
        }
        
        if (Rs2Inventory.contains(seedId)) {
            state = FarmingContractState.WALKING_TO_PATCH;
        } else {
            // Always check bank for seeds
            state = FarmingContractState.BANKING_FOR_SEEDS;
        }
    }
    
    private void handleNoSeeds() {
        if (config.autoDowngrade() && !isEasyContract()) {
            state = FarmingContractState.DOWNGRADING_CONTRACT;
        } else if (config.stopIfNoSeeds()) {
            log.warn("No seeds available for contract: " + currentContract.getName());
            state = FarmingContractState.STOPPED_NO_SEEDS;
        } else {
            // Wait for user to get seeds
            sleep(5000);
            state = FarmingContractState.CHECKING_SEEDS;
        }
    }
    
    private void handleDowngradingContract() {
        if (!isNearJane()) {
            Rs2Walker.walkTo(JANE_LOCATION);
            sleepUntil(() -> isNearJane(), 10000);
            return;
        }
        
        NPC jane = findGuildmasterJane();
        if (jane == null) {
            log.warn("Could not find Guildmaster Jane for downgrade");
            return;
        }
        
        // Interact with Jane
        if (!Rs2Npc.interact(jane, "Talk-to") && !Rs2Npc.interact(jane, "Contract")) {
            log.warn("Failed to interact with Jane for downgrade");
            return;
        }
        
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
        
        // Look for easier contract option
        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption(".*easier.*");
            sleep(600);
        }
        
        // Continue through dialogue and parse for new contract
        while (Rs2Dialogue.isInDialogue()) {
            updateCurrentContract();
            Rs2Dialogue.clickContinue();
            sleep(600);
        }
        
        sleep(1000);
        state = FarmingContractState.CHECKING_SEEDS;
    }
    
    private void handleBankingForSeeds() {
        Integer seedId = PRODUCE_TO_SEED.get(currentContract);
        if (seedId == null) return;
        
        // Walk to bank if not near one
        if (!Rs2Bank.isNearBank(10)) {
            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
            sleepUntil(() -> Rs2Bank.isNearBank(10), 10000);
            return;
        }
        
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            sleepUntil(() -> Rs2Bank.isOpen(), 10000);
            return;
        }
        
        // Deposit everything except essential tools
        Rs2Bank.depositAllExcept("Rake", "Spade", "Seed dibber", "Magic secateurs", "Coins");
        sleep(600);
        
        if (Rs2Bank.hasBankItem(seedId, 1)) {
            // Withdraw at least 5 seeds for planting
            Rs2Bank.withdrawX(seedId, 10);
            sleep(600);
            
            // Also grab compost if configured and available
            if (config.useCompost()) {
                String compostName = config.compostType().toString();
                if (Rs2Bank.hasBankItem(compostName)) {
                    Rs2Bank.withdrawX(compostName, 5);
                    sleep(600);
                }
            }
            
            Rs2Bank.closeBank();
            state = FarmingContractState.WALKING_TO_PATCH;
        } else {
            Rs2Bank.closeBank();
            state = FarmingContractState.NO_SEEDS_AVAILABLE;
        }
    }
    
    private void handleWalkingToPatch() {
        GameObject patch = findContractPatchObject();
        if (patch == null) {
            log.error("Could not find patch object for: " + currentContract);
            // Try walking to general area
            WorldPoint approxLocation = getApproximatePatchLocation();
            if (approxLocation != null) {
                Rs2Walker.walkTo(approxLocation);
                sleep(2000);
            }
            return;
        }
        
        WorldPoint patchLocation = patch.getWorldLocation();
        if (Rs2Player.getWorldLocation().distanceTo(patchLocation) > 5) {
            Rs2Walker.walkTo(patchLocation);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(patchLocation) <= 5, 10000);
        } else {
            // Check patch state to determine next action
            String patchState = getPatchState(patch);
            log.info("Patch state: {}", patchState);
            
            switch (patchState) {
                case "Weeds":
                case "Dead":
                    state = FarmingContractState.CLEARING_PATCH;
                    break;
                case "Empty":
                    state = FarmingContractState.PLANTING;
                    break;
                case "Harvestable":
                case "CheckHealth":
                    state = FarmingContractState.HARVESTING;
                    break;
                case "Growing":
                    state = FarmingContractState.WAITING_FOR_GROWTH;
                    break;
                default:
                    // Check contract state as fallback
                    SummaryState contractState = getContractState();
                    if (contractState == SummaryState.OCCUPIED) {
                        state = FarmingContractState.CLEARING_PATCH;
                    } else {
                        state = FarmingContractState.PLANTING;
                    }
                    break;
            }
        }
    }
    
    /**
     * Get approximate patch location for initial walking
     */
    private WorldPoint getApproximatePatchLocation() {
        if (currentContract == null) return null;
        
        // Farming Guild patch locations - verified coordinates
        switch (currentContract.getPatchImplementation()) {
            case HERB:
                return new WorldPoint(1239, 3728, 0);
            case TREE:
                return new WorldPoint(1233, 3734, 0);
            case FRUIT_TREE:
                return new WorldPoint(1243, 3757, 0);
            case FLOWER:
                return new WorldPoint(1260, 3727, 0);
            case BUSH:
                return new WorldPoint(1260, 3732, 0);
            case ALLOTMENT:
                return new WorldPoint(1265, 3729, 0);
            case CACTUS:
                return new WorldPoint(1264, 3746, 0);
            default:
                return null;
        }
    }
    
    private void handlePlanting() {
        Integer seedId = PRODUCE_TO_SEED.get(currentContract);
        if (seedId == null || !Rs2Inventory.contains(seedId)) {
            state = FarmingContractState.CHECKING_SEEDS;
            return;
        }
        
        // Clear patch if needed
        GameObject deadCrops = Rs2GameObject.findObject("Dead crops", true, 10, false, null);
        GameObject weeds = Rs2GameObject.findObject("Weeds", true, 10, false, null);
        if (deadCrops != null) {
            Rs2GameObject.interact(deadCrops, "Clear");
            sleep(2000);
        } else if (weeds != null) {
            Rs2GameObject.interact(weeds, "Rake");
            sleep(2000);
        }
        
        // Use seed on patch
        Rs2Inventory.use(seedId);
        sleep(600);
        
        GameObject patch = findContractPatchObject();
        if (patch != null) {
            Rs2GameObject.interact(patch, "Use");
            sleep(2000);
        }
        
        // Apply compost if configured
        if (config.useCompost()) {
            String compostName = config.compostType().toString();
            if (Rs2Inventory.contains(compostName)) {
                Rs2Inventory.use(compostName);
                sleep(600);
                if (patch != null) {
                    Rs2GameObject.interact(patch, "Use");
                    sleep(2000);
                }
            }
        }
        
        state = FarmingContractState.WAITING_FOR_GROWTH;
    }
    
    private void handleWaitingForGrowth() {
        SummaryState contractState = getContractState();
        
        if (contractState == SummaryState.COMPLETED) {
            state = FarmingContractState.HARVESTING;
        } else if (contractState == SummaryState.IN_PROGRESS) {
            // Check if patch is diseased by looking at the patch object
            GameObject patch = findContractPatchObject();
            if (patch != null) {
                String patchState = getPatchState(patch);
                if (patchState.equals("Diseased")) {
                    if (Rs2Inventory.contains("Plant cure")) {
                        Rs2Inventory.use("Plant cure");
                        Rs2GameObject.interact(patch, "Use");
                        sleep(2000);
                    }
                }
            }
            // Wait for growth
            sleep(30000); // Check every 30 seconds
        }
    }
    
    private void handleHarvesting() {
        // Walk to patch if not there
        WorldPoint patchLocation = getPatchLocation();
        if (patchLocation == null) {
            log.error("Could not determine patch location for harvesting");
            state = FarmingContractState.ERROR;
            return;
        }
        
        if (Rs2Player.getWorldLocation().distanceTo(patchLocation) > 5) {
            Rs2Walker.walkTo(patchLocation);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(patchLocation) <= 5, 10000);
            return;
        }
        
        GameObject patch = findContractPatchObject();
        if (patch == null) {
            log.error("Could not find patch object for harvesting");
            state = FarmingContractState.ERROR;
            return;
        }
        
        // Perform the correct interaction based on patch type
        PatchImplementation patchType = currentContract.getPatchImplementation();
        String interaction = getHarvestInteraction(patchType);
        
        log.info("Harvesting {} with interaction: {}", currentContract.getName(), interaction);
        
        if (Rs2GameObject.interact(patch, interaction)) {
            sleep(2000);
            
            // Wait until harvesting animation completes
            sleepUntil(() -> !Rs2Player.isAnimating(), 30000);
            
            // For herbs and allotments, continue harvesting until patch is empty
            if (patchType == PatchImplementation.HERB || patchType == PatchImplementation.ALLOTMENT) {
                // Keep harvesting while there's more to pick
                GameObject currentPatch = findContractPatchObject();
                while (currentPatch != null && Rs2GameObject.interact(currentPatch, interaction)) {
                    sleep(2000);
                    sleepUntil(() -> !Rs2Player.isAnimating(), 10000);
                    currentPatch = findContractPatchObject();
                }
            }
            
            // Move to clearing the patch after harvest
            state = FarmingContractState.CLEARING_AFTER_HARVEST;
        } else {
            log.warn("Failed to interact with patch for harvesting");
            sleep(1000);
        }
    }
    
    /**
     * Get the correct harvest interaction based on patch type
     */
    private String getHarvestInteraction(PatchImplementation patchType) {
        switch (patchType) {
            case TREE:
            case FRUIT_TREE:
                return "Check-health";
            case HERB:
            case FLOWER:
                return "Pick";
            case BUSH:
                return "Pick-from";
            case ALLOTMENT:
                return "Harvest";
            case CACTUS:
                return "Pick-spine";
            default:
                return "Harvest"; // Default fallback
        }
    }
    
    private void handleClearingPatch() {
        // This is for clearing wrong crops before planting
        GameObject patch = findContractPatchObject();
        if (patch == null) return;
        
        // Clear dead crops or wrong crops
        GameObject deadCrops = Rs2GameObject.findObject("Dead crops", true, 10, false, null);
        GameObject weeds = Rs2GameObject.findObject("Weeds", true, 10, false, null);
        
        if (deadCrops != null) {
            Rs2GameObject.interact(deadCrops, "Clear");
            sleep(2000);
        } else if (weeds != null) {
            Rs2GameObject.interact(weeds, "Rake");
            sleep(2000);
        } else {
            // If it's the wrong crop, need to dig it up or clear it
            Rs2GameObject.interact(patch, "Clear");
            sleep(2000);
        }
        
        // After clearing, check seeds
        state = FarmingContractState.CHECKING_SEEDS;
    }
    
    private void handleClearingAfterHarvest() {
        PatchImplementation patchType = currentContract.getPatchImplementation();
        
        if (patchType == PatchImplementation.TREE) {
            // For regular trees, need to pay Rosie 200gp
            if (!Rs2Inventory.contains("Coins")) {
                log.error("Need 200gp to clear tree patch");
                state = FarmingContractState.ERROR;
                return;
            }
            
            NPC rosie = Rs2Npc.getNpc("Rosie");
            if (rosie != null && Rs2Player.distanceTo(rosie.getWorldLocation()) <= 10) {
                Rs2Npc.interact(rosie, "Pay (tree patch)");
                sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
                
                // Continue through dialogue
                while (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                    sleep(600);
                }
                
                // Wait for tree to be removed
                sleep(2000);
            } else {
                log.warn("Rosie not found nearby for tree clearing");
            }
        } else if (patchType == PatchImplementation.FRUIT_TREE) {
            // For fruit trees, need to pay Nikkie 200gp
            if (!Rs2Inventory.contains("Coins")) {
                log.error("Need 200gp to clear fruit tree patch");
                state = FarmingContractState.ERROR;
                return;
            }
            
            NPC nikkie = Rs2Npc.getNpc("Nikkie");
            if (nikkie != null && Rs2Player.distanceTo(nikkie.getWorldLocation()) <= 10) {
                Rs2Npc.interact(nikkie, "Pay (fruit tree)");
                sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
                
                // Continue through dialogue
                while (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                    sleep(600);
                }
                
                // Wait for tree to be removed
                sleep(2000);
            } else {
                log.warn("Nikkie not found nearby for fruit tree clearing");
            }
        } else {
            // For other patches, just clear them
            GameObject patch = findContractPatchObject();
            GameObject weeds = Rs2GameObject.findObject("Weeds", true, 10, false, null);
            
            if (patch != null) {
                Rs2GameObject.interact(patch, "Clear");
                sleep(2000);
            } else if (weeds != null) {
                Rs2GameObject.interact(weeds, "Rake");
                sleep(2000);
            }
        }
        
        // After clearing, complete the contract
        state = FarmingContractState.COMPLETING_CONTRACT;
    }
    
    private void handleCompletingContract() {
        if (!isNearJane()) {
            Rs2Walker.walkTo(JANE_LOCATION);
            sleepUntil(() -> isNearJane(), 10000);
            return;
        }
        
        NPC jane = findGuildmasterJane();
        if (jane == null) {
            log.warn("Could not find Guildmaster Jane for completion");
            return;
        }
        
        // Try to interact with Jane
        boolean interacted = false;
        if (jane.getId() == NpcID.FARMING_GUILD_MASTER_1OP) {
            interacted = Rs2Npc.interact(jane, "Contract");
        } else {
            interacted = Rs2Npc.interact(jane, "Talk-to");
            if (!interacted) {
                interacted = Rs2Npc.interact(jane, "Contract");
            }
        }
        
        if (!interacted) {
            log.warn("Failed to interact with Jane for completion");
            return;
        }
        
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
        
        // Handle any dialogue options for contract
        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption(".*[Cc]ontract.*");
            sleep(600);
        }
        
        // Continue through dialogue to complete and check for reward
        while (Rs2Dialogue.isInDialogue()) {
            updateCurrentContract();
            Rs2Dialogue.clickContinue();
            sleep(600);
        }
        
        sleep(1000);
        // Current contract should be null after completion
        if (currentContract == null) {
            log.info("Contract completed successfully");
        }
        state = FarmingContractState.NO_CONTRACT;
    }
    
    private boolean isNearJane() {
        return Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) <= 10;
    }
    
    private boolean isEasyContract() {
        // Easy contracts are typically basic allotments, flowers, and low-level herbs
        return currentContract != null && (
            currentContract == Produce.POTATO ||
            currentContract == Produce.ONION ||
            currentContract == Produce.CABBAGE ||
            currentContract == Produce.MARIGOLD ||
            currentContract == Produce.GUAM ||
            currentContract == Produce.MARRENTILL
        );
    }
    
    private String getTierDialogueOption() {
        // Always select the highest tier available based on farming level
        int farmingLevel = Rs2Player.getRealSkillLevel(Skill.FARMING);
        
        // Hard contracts require level 85
        if (farmingLevel >= 85) {
            return "Hard";
        }
        // Medium contracts require level 65
        else if (farmingLevel >= 65) {
            return "Medium";
        }
        // Easy contracts require level 45
        else if (farmingLevel >= 45) {
            return "Easy";
        }
        
        // Below level 45, can't do contracts
        log.error("Farming level too low for contracts (need 45+): " + farmingLevel);
        return "Easy"; // Fallback
    }
    
    /**
     * Find the farming patch GameObject for the current contract
     * Uses object IDs directly for more reliable detection
     */
    private GameObject findContractPatchObject() {
        if (currentContract == null) return null;
        
        int[] patchIds = getPatchObjectIds();
        if (patchIds == null) return null;
        
        // Find all matching patches
        List<GameObject> patches = new ArrayList<>();
        for (int id : patchIds) {
            TileObject tileObj = Rs2GameObject.findObjectById(id);
            if (tileObj != null && tileObj instanceof GameObject) {
                patches.add((GameObject) tileObj);
            }
        }
        
        if (patches.isEmpty()) {
            log.warn("No patches found for contract: {}", currentContract.getName());
            return null;
        }
        
        // For allotments, randomly select one
        if (currentContract.getPatchImplementation() == PatchImplementation.ALLOTMENT && patches.size() > 1) {
            int randomIndex = random.nextInt(patches.size());
            return patches.get(randomIndex);
        }
        
        return patches.get(0);
    }
    
    /**
     * Get the object IDs for the current contract's patch type
     */
    private int[] getPatchObjectIds() {
        if (currentContract == null) return null;
        
        switch (currentContract.getPatchImplementation()) {
            case HERB:
                return HERB_PATCH_IDS;
            case TREE:
                return TREE_PATCH_IDS;
            case FRUIT_TREE:
                return FRUIT_TREE_PATCH_IDS;
            case FLOWER:
                return FLOWER_PATCH_IDS;
            case BUSH:
                return BUSH_PATCH_IDS;
            case ALLOTMENT:
                return ALLOTMENT_PATCH_IDS;
            case CACTUS:
                return CACTUS_PATCH_IDS;
            default:
                log.error("Unknown patch type: {}", currentContract.getPatchImplementation());
                return null;
        }
    }
    
    /**
     * Get patch state by examining object actions
     */
    private String getPatchState(GameObject patch) {
        if (patch == null) return "Unknown";
        
        ObjectComposition comp = Rs2GameObject.getObjectComposition(patch.getId());
        if (comp == null) return "Unknown";
        
        String[] actions = comp.getActions();
        if (actions == null) return "Unknown";
        
        // Check available actions to determine state
        for (String action : actions) {
            if (action == null) continue;
            
            if (action.contains("Rake")) return "Weeds";
            if (action.contains("Pick") || action.contains("Harvest")) return "Harvestable";
            if (action.contains("Check-health") || action.contains("Check health")) return "CheckHealth";
            if (action.contains("Clear")) return "Dead";
            if (action.contains("Inspect") || action.contains("Plant")) return "Empty";
        }
        
        // Check object name for additional state info
        String name = comp.getName().toLowerCase();
        if (name.contains("patch") && !name.contains("diseased") && !name.contains("dead")) {
            return "Empty";
        }
        
        return "Growing";
    }
    
    private WorldPoint getPatchLocation() {
        GameObject patch = findContractPatchObject();
        return patch != null ? patch.getWorldLocation() : null;
    }
    
    private String getPatchObjectName() {
        if (currentContract == null) return null;
        
        switch (currentContract.getPatchImplementation()) {
            case ALLOTMENT:
                return "Allotment";
            case FLOWER:
                return "Flower Patch";
            case HERB:
                return "Herb patch";
            case BUSH:
                return "Bush patch";
            case TREE:
                return "Tree patch";
            case FRUIT_TREE:
                return "Fruit tree patch";
            case CACTUS:
                return "Cactus patch";
            default:
                return null;
        }
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        state = FarmingContractState.NO_CONTRACT;
        initialInventorySetupComplete = false;
    }
}