package net.runelite.client.plugins.microbot.farmingcontract;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.timetracking.farming.Produce;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import java.util.Arrays;
import java.awt.Polygon;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class FarmingContractScript extends Script {
    
    private static final WorldPoint JANE_LOCATION = new WorldPoint(1248, 3727, 0);
    // Pattern from timetracking plugin - handles both dialogue variants and captures crop name
    private static final Pattern CONTRACT_PATTERN = Pattern.compile("(?:We need you to grow|Please could you grow) (?:some|a|an) ([a-zA-Z ]+)(?: for us\\?|\\.)");
    
    private final FarmingContractPlugin plugin;
    private final FarmingContractConfig config;
    private FarmingContractState state = FarmingContractState.INIT;
    private Produce currentContract = null;
    private String selectedPatchName = null; // Remember which patch we're working on (North/South for allotments)
    
    private FarmingWorld farmingWorld;
    private ConfigManager configManager;
    private FarmingHandler farmingHandler;
    
    public FarmingContractScript(FarmingContractPlugin plugin, FarmingContractConfig config, FarmingWorld farmingWorld, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = config;
        this.farmingWorld = farmingWorld;
        this.configManager = configManager;
    }
    
    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            
            try {
                // Log current state and contract
                log.info("Main loop - State: {}, Contract: {}", state, 
                        currentContract != null ? currentContract.getName() : "None");
                
                // Safety check: if we have no contract but we're trying to farm/prepare, reset
                if (currentContract == null && 
                    (state == FarmingContractState.FARM || 
                     state == FarmingContractState.PREPARE || 
                     state == FarmingContractState.CHECK_PATCH)) {
                    log.info("No contract but in farming state, resetting to CHECK_CONTRACT");
                    state = FarmingContractState.CHECK_CONTRACT;
                }
                
                FarmingContractState oldState = state;
                switch (state) {
                    case INIT:
                        handleInit();
                        break;
                    case CHECK_CONTRACT:
                        handleCheckContract();
                        break;
                    case CHECK_PATCH:
                        handleCheckPatch();
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
                
                // Log state transitions
                if (oldState != state) {
                    log.info("State transition: {} -> {}", oldState, state);
                }
                
                plugin.setStatus(state.toString());
            } catch (Exception e) {
                log.error("Error in farming contract", e);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private void handleInit() {
        // Skip inventory setup for now - can be added to config later if needed
        // Would need to add inventorySetupName to FarmingContractConfig
        
        // Initialize farming handler
        if (configManager != null) {
            farmingHandler = new FarmingHandler(Microbot.getClient(), configManager);
        }
        
        // DON'T load cached contract - always check with Jane for the truth
        // Clear any cached contract to start fresh
        currentContract = null;
        selectedPatchName = null; // Clear patch selection
        saveContract(); // Clear from config
        log.info("Starting fresh - will check contract status with Jane");
        
        // Go directly to getting contract from Jane
        state = FarmingContractState.GET_CONTRACT;
    }
    
    private void handleCheckContract() {
        // Always check with Jane if we don't have a contract
        if (currentContract == null) {
            log.info("No current contract, going to get one from Jane");
            state = FarmingContractState.GET_CONTRACT;
            return;
        }
        
        // If we think we have a contract, verify it's still valid
        // For completed tree contracts, currentContract might still be set
        if (isContractComplete()) {
            state = FarmingContractState.COMPLETE;
        } else {
            // Check patch state before preparing seeds
            state = FarmingContractState.CHECK_PATCH;
        }
    }
    
    private CropState lastKnownCropState = null;  // Track the crop state from CHECK_PATCH
    private boolean harvestingContract = false;  // Track when we're harvesting for contract completion
    private boolean contractJustCompleted = false;  // Set when contract completion is detected
    private boolean clearingCompletedContract = false;  // Set when clearing a completed bush/cactus contract
    private boolean skipSeedPreparation = false;  // Set when patch already has our contract crop
    
    private void handleCheckPatch() {
        if (currentContract == null) {
            log.info("CHECK_PATCH: No contract, going to CHECK_CONTRACT");
            state = FarmingContractState.CHECK_CONTRACT;
            return;
        }
        
        log.info("CHECK_PATCH: Checking patch for contract: {} ({})", 
                currentContract.getName(), currentContract.getPatchImplementation());
        
        // Try to check patch state remotely using FarmingHandler
        if (farmingHandler != null && farmingWorld != null) {
            log.info("CHECK_PATCH: FarmingHandler available, looking for patch");
            FarmingPatch targetPatch = findFarmingPatch(currentContract);
            if (targetPatch != null) {
                log.info("CHECK_PATCH: Found patch: '{}' in region '{}'", 
                        targetPatch.getName(), targetPatch.getRegion().getName());
                CropState cropState = farmingHandler.predictPatch(targetPatch);
                log.info("CHECK_PATCH: Patch state for {} is: {}", currentContract.getName(), cropState);
                lastKnownCropState = cropState;  // Store for PREPARE state
                
                if (cropState == CropState.GROWING) {
                    // Still growing, stop the plugin
                    log.info("CHECK_PATCH: Contract {} is still growing - stopping plugin", currentContract.getName());
                    plugin.setStatus("Contract still growing - stopping");
                    plugin.stopPlugin();
                } else {
                    // Check if we need seeds or if the patch already has our crop
                    if (cropState == CropState.HARVESTABLE || cropState == CropState.UNCHECKED) {
                        log.info("CHECK_PATCH: Contract crop already grown - no seeds needed");
                        skipSeedPreparation = true;
                        plugin.setStatus("Contract ready - no seeds needed");
                    } else {
                        skipSeedPreparation = false;
                        if (cropState == CropState.DEAD || cropState == CropState.DISEASED) {
                            plugin.setStatus("Patch needs attention - checking inventory");
                        } else {
                            plugin.setStatus("Preparing to plant");
                        }
                    }
                    
                    // Go to PREPARE, which will check what we actually need
                    log.info("CHECK_PATCH: Moving to PREPARE state (skipSeeds={})", skipSeedPreparation);
                    state = FarmingContractState.PREPARE;
                }
            } else {
                // Can't find patch info, proceed to prepare
                log.info("CHECK_PATCH: Could not find patch for {} - proceeding to PREPARE", currentContract.getName());
                lastKnownCropState = null;
                state = FarmingContractState.PREPARE;
            }
        } else {
            // FarmingHandler not available, proceed normally
            log.info("CHECK_PATCH: FarmingHandler not available (handler={}, world={}) - proceeding to PREPARE", 
                    farmingHandler != null, farmingWorld != null);
            lastKnownCropState = null;
            state = FarmingContractState.PREPARE;
        }
    }
    
    private void handleGetContract() {
        // Reset completion flags when getting a new contract
        contractJustCompleted = false;
        clearingCompletedContract = false;
        skipSeedPreparation = false;
        
        NPC jane = Rs2Npc.getNpc("Guildmaster Jane");
        if (jane == null) {
            // Jane not visible, walk to her location
            if (Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) > 10) {
                Rs2Walker.walkTo(JANE_LOCATION);
            }
            return;
        }
        
        // Try Contract first, then Talk-to
        // Rs2Npc.interact will automatically walk if Jane is not in line of sight
        if (!Rs2Npc.interact(jane, "Contract")) {
            Rs2Npc.interact(jane, "Talk-to");
        }
        
        // Wait for dialogue to open (not animation)
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
        
        // Handle dialogue quickly
        while (Rs2Dialogue.isInDialogue()) {
            // Check for options first
            if (Rs2Dialogue.hasSelectAnOption()) {
                // Select tier if needed
                String tier = getContractTier();
                if (Rs2Dialogue.hasDialogueOption(tier)) {
                    log.info("Selecting contract tier: {}", tier);
                    Rs2Dialogue.clickOption(tier);
                    sleep(100);
                    continue;
                }
            }
            
            // Parse contract from dialogue
            String text = Rs2Dialogue.getDialogueText();
            if (text != null && !text.isEmpty()) {
                // Check if Jane is telling us we already have a contract
                if (text.contains("already asked") || text.contains("already given you") || 
                    text.contains("still working on")) {
                    log.info("Jane says we already have a contract - checking saved contract");
                    break; // Just exit, we should have the contract saved
                }
                
                // Try to parse the contract
                Matcher m = CONTRACT_PATTERN.matcher(text);
                if (m.find()) {
                    String contractName = m.group(1).trim();
                    currentContract = findProduce(contractName);
                    if (currentContract != null) {
                        log.info("Got contract: {}", currentContract.getName());
                        // Clear any previous contract state
                        lastKnownCropState = null;
                        skipSeedPreparation = false;
                        saveContract();
                        break; // Exit immediately - dialogue will close when we walk away
                    }
                }
            }
            
            // Continue dialogue
            Rs2Dialogue.clickContinue();
            sleep(100); // Fast 100ms delay
        }
        
        if (currentContract != null) {
            // Check patch state first before preparing
            state = FarmingContractState.CHECK_PATCH;
        }
    }
    
    private void handlePrepare() {
        if (currentContract == null) {
            state = FarmingContractState.CHECK_CONTRACT;
            return;
        }
        
        // If patch already has our contract crop, check if we have tools for harvesting
        if (skipSeedPreparation) {
            log.info("Contract crop already grown - checking if we have harvesting tools");
            
            // Check what tools we have
            boolean hasSpade = Rs2Inventory.contains("Spade");
            boolean hasSecateurs = Rs2Inventory.contains("Magic secateurs");
            
            // For herb contracts, we NEED a spade for harvesting
            if (lastKnownCropState == CropState.HARVESTABLE && 
                currentContract.getPatchImplementation() == PatchImplementation.HERB) {
                
                if (hasSpade) {
                    // We have the required tool, can proceed
                    if (!hasSecateurs) {
                        log.warn("No magic secateurs for herb harvesting - lower yield expected");
                    }
                    log.info("Have required tools for herb harvesting - proceeding to FARM");
                    state = FarmingContractState.FARM;
                    skipSeedPreparation = false; // Reset flag
                    return;
                } else {
                    // Need to get spade first
                    log.info("Need spade for herb harvesting - continuing to banking");
                    skipSeedPreparation = false; // Reset flag and continue to prepare tools
                }
            } else {
                // For non-herb contracts or non-harvestable states, go straight to farm
                log.info("Skipping seed preparation - going to FARM");
                state = FarmingContractState.FARM;
                skipSeedPreparation = false; // Reset flag
                return;
            }
        }
        
        // First, handle any seed packs we might have before banking
        handleSeedPacks();
        
        // Determine what we need based on the last known crop state
        boolean needsHarvesting = (lastKnownCropState == CropState.HARVESTABLE);
        boolean needsChecking = (lastKnownCropState == CropState.UNCHECKED);  // Check-health, no seeds needed
        boolean needsClearing = (lastKnownCropState == CropState.DEAD);
        boolean needsCuring = (lastKnownCropState == CropState.DISEASED);
        boolean needsPlanting = (lastKnownCropState == CropState.EMPTY || 
                                 lastKnownCropState == null);  // null means we couldn't check
        
        // Check what we already have
        boolean hasSpade = Rs2Inventory.contains("Spade");
        boolean hasRake = Rs2Inventory.contains("Rake");
        boolean hasDibber = Rs2Inventory.contains("Seed dibber");
        boolean hasPlantCure = Rs2Inventory.contains("Plant cure");
        boolean hasSecateurs = Rs2Inventory.contains("Magic secateurs");
        
        // Get seed ID early since we'll need it for multiple checks
        int seedId = getSeedId(currentContract);
        if (seedId == -1) {
            log.error("No seed mapping found for contract: " + currentContract.getName());
            plugin.setStatus("Cannot complete contract - unknown seed type: " + currentContract.getName());
            plugin.stopPlugin();
            return;
        }
        
        // Determine if we're ready
        if (needsChecking) {
            // For bushes and cacti, spade is MANDATORY for the clearing process after check-health
            boolean isBushOrCactus = currentContract.getPatchImplementation() == PatchImplementation.BUSH ||
                                      currentContract.getPatchImplementation() == PatchImplementation.CACTUS;
            
            if (isBushOrCactus) {
                if (hasSpade) {
                    log.info("Ready to check-health on {} (spade equipped for clearing)", 
                            currentContract.getPatchImplementation());
                    state = FarmingContractState.FARM;
                    return;
                } else {
                    log.info("Need spade for {} clearing after check-health", 
                            currentContract.getPatchImplementation());
                    // Continue to banking to get spade
                }
            } else {
                // For other check-health contracts, spade is optional but recommended
                if (hasSpade) {
                    log.info("Ready to check-health on patch");
                    state = FarmingContractState.FARM;
                    return;
                } else {
                    log.info("Proceeding to check-health without spade");
                    state = FarmingContractState.FARM;
                    return;
                }
            }
        }
        
        if (needsHarvesting) {
            // For harvesting contracts, we just need harvesting tools
            // We do NOT need seeds since the contract will be complete after harvesting
            boolean isHerbContract = currentContract.getPatchImplementation() == PatchImplementation.HERB;
            boolean isBushOrCactus = currentContract.getPatchImplementation() == PatchImplementation.BUSH ||
                                      currentContract.getPatchImplementation() == PatchImplementation.CACTUS;
            
            if (isHerbContract) {
                // Herbs REQUIRE a spade for harvesting
                if (hasSpade) {
                    if (!hasSecateurs) {
                        log.warn("Harvesting herbs without magic secateurs - lower yield expected");
                    }
                    log.info("Ready to harvest herbs (spade: yes, magic secateurs: {})", hasSecateurs);
                    state = FarmingContractState.FARM;
                    return;
                } else {
                    log.info("Need spade for herb harvesting");
                }
            } else if (isBushOrCactus) {
                // Bushes and cacti REQUIRE a spade for clearing after harvesting
                if (hasSpade) {
                    log.info("Ready to harvest {} (spade equipped for clearing)", 
                            currentContract.getPatchImplementation());
                    state = FarmingContractState.FARM;
                    return;
                } else {
                    log.info("Need spade for {} - required for clearing after harvest", 
                            currentContract.getPatchImplementation());
                }
            } else {
                // For other harvesting types, spade is still needed
                if (hasSpade) {
                    log.info("Ready to harvest");
                    state = FarmingContractState.FARM;
                    return;
                } else {
                    log.info("Need spade for harvesting");
                }
            }
        }
        
        if (needsClearing && hasSpade) {
            // For clearing dead crops, also check if we have planting items
            boolean hasPlantingItems = hasRake && hasDibber && Rs2Inventory.contains(seedId);
            if (hasPlantingItems) {
                log.info("Ready to clear and have planting items ready");
                state = FarmingContractState.FARM;
                return;
            }
        }
        
        if (needsCuring && hasPlantCure) {
            state = FarmingContractState.FARM;
            return;
        }
        
        if (needsPlanting) {
            // For just planting (empty patch), check if we have everything
            if (hasSpade && hasRake && hasDibber && Rs2Inventory.contains(seedId)) {
                log.info("Ready to plant on empty patch");
                state = FarmingContractState.FARM;
                return;
            }
        }
        
        // Bank for what we need
        // Rs2Bank.openBank() automatically walks to the nearest bank if needed
        if (Rs2Bank.openBank()) {
            Rs2Bank.depositAllExcept("Rake", "Spade", "Seed dibber", "Magic secateurs", "Coins", "Plant cure");
            
            // Get only what we need - use withdrawX to ensure we get exactly 1
            if (!hasSpade && Rs2Bank.hasBankItem("Spade", 1)) {
                Rs2Bank.withdrawX("Spade", 1);
            }
            
            // We need planting items only if we're planting or clearing dead crops
            // NOT for harvesting (contract will be complete) or check-health (don't know if we'll need to replant)
            if (needsPlanting || needsClearing) {
                if (!hasRake && Rs2Bank.hasBankItem("Rake", 1)) {
                    Rs2Bank.withdrawX("Rake", 1);
                }
                if (!hasDibber && Rs2Bank.hasBankItem("Seed dibber", 1)) {
                    Rs2Bank.withdrawX("Seed dibber", 1);
                }
                
                if (seedId != -1 && !Rs2Inventory.contains(seedId)) {
                    // Allotments need 3 seeds, everything else needs 1
                    int seedsNeeded = (currentContract.getPatchImplementation() == PatchImplementation.ALLOTMENT) ? 3 : 1;
                    
                    if (Rs2Bank.hasBankItem(seedId, seedsNeeded)) {
                        Rs2Bank.withdrawX(seedId, seedsNeeded);
                        log.info("Withdrew {} {} seeds for planting after {}", 
                                seedsNeeded, currentContract.getName(),
                                needsHarvesting ? "harvesting" : (needsClearing ? "clearing" : "preparing"));
                        
                        if (config.useCompost()) {
                            Rs2Bank.withdrawX(config.compostType().toString(), 1);
                        }
                    } else {
                        // No seeds in bank - stop the plugin
                        log.error("No seeds available in bank for contract: " + currentContract.getName());
                        plugin.setStatus("No seeds in bank for " + currentContract.getName() + " - stopping");
                        Rs2Bank.closeBank();
                        plugin.stopPlugin();
                        return;
                    }
                }
            }
            
            if (needsCuring && !hasPlantCure && Rs2Bank.hasBankItem("Plant cure", 1)) {
                Rs2Bank.withdrawX("Plant cure", 1);
            }
            
            // For herb harvesting, withdraw magic secateurs for better yield
            if (needsHarvesting && currentContract.getPatchImplementation() == PatchImplementation.HERB && !hasSecateurs) {
                if (Rs2Bank.hasBankItem("Magic secateurs", 1)) {
                    Rs2Bank.withdrawX("Magic secateurs", 1);
                    log.info("Withdrew magic secateurs for herb harvesting");
                } else {
                    log.warn("No magic secateurs available in bank - will harvest herbs with reduced yield");
                }
            }
            
            // For tree contracts, ensure we have coins for clearing
            // We need coins for both harvesting and checking (since check-health leads to clearing)
            if ((needsHarvesting || needsChecking) && (currentContract.getPatchImplementation() == PatchImplementation.TREE ||
                currentContract.getPatchImplementation() == PatchImplementation.FRUIT_TREE)) {
                if (Rs2Inventory.count("Coins") < 200) {
                    Rs2Bank.withdrawX("Coins", 200);
                }
            }
            
            Rs2Bank.closeBank();
            state = FarmingContractState.FARM;
        }
    }
    
    private void handleFarm() {
        // Don't take actions while moving or animating
        if (Rs2Player.isAnimating() || Rs2Player.isWalking()) {
            return;
        }
        
        if (currentContract == null) {
            state = FarmingContractState.CHECK_CONTRACT;
            return;
        }
        
        WorldPoint patchLocation = getPatchLocation(currentContract);
        if (patchLocation == null) return;
        
        // Try to find the patch object first (it might be visible even from a distance)
        GameObject patch = findPatchUsingActions(currentContract);
        
        // If patch is not visible, try to walk closer
        if (patch == null) {
            // Try to interact with any patch object at the location - this will auto-walk if needed
            Integer[] patchIds = getPatchObjectIds(currentContract);
            for (int patchId : patchIds) {
                TileObject obj = Rs2GameObject.findObjectById(patchId);
                if (obj != null && obj.getWorldLocation().equals(patchLocation)) {
                    // Found patch at location - interact will walk there if needed
                    log.info("Patch found but not reachable, using interact to walk");
                    if (!Rs2GameObject.interact(obj, "Inspect", true)) {
                        // Returns false if walking was initiated
                        return;
                    }
                    // After walking, the patch should be found on next loop
                    break;
                }
            }
            
            // If still no patch, manually walk there
            if (patch == null) {
                if (Rs2Player.getWorldLocation().distanceTo(patchLocation) > 10) {
                    log.info("Patch not found, walking to location");
                    Rs2Walker.walkTo(patchLocation);
                } else {
                    log.warn("Could not find patch for {} despite being close. Player location: {}", 
                            currentContract.getName(), Rs2Player.getWorldLocation());
                }
                return;
            }
        }
        
        // Patch is visible - check its state
        log.info("Patch found for {} at distance {}", currentContract.getName(), 
                Rs2Player.getWorldLocation().distanceTo(patch.getWorldLocation()));
        
        // Check the actual patch state from its actions
        CropState actualState = inferStateFromActions(patch);
        log.info("Patch state from actions: {}", actualState);
        
        // If patch is growing, stop immediately
        if (actualState == CropState.GROWING) {
            log.error("Patch already has crops growing - cannot plant contract!");
            plugin.setStatus("Patch occupied - cannot plant " + currentContract.getName());
            plugin.stopPlugin();
            return;
        }
        
        // First, infer state from actual patch actions (most reliable for current state)
        CropState actionState = inferStateFromActions(patch);
        log.info("Patch state from actions: {}", actionState);
        
        // Get state from FarmingHandler for comparison
        CropState handlerState = null;
        if (farmingHandler != null && farmingWorld != null) {
            FarmingPatch targetPatch = findFarmingPatch(currentContract);
            if (targetPatch != null) {
                handlerState = farmingHandler.predictPatch(targetPatch);
                log.info("FarmingHandler state for {}: {}", currentContract.getName(), handlerState);
            }
        }
        
        // Determine which state to use
        CropState cropState = actionState;
        
        // Special case: if actions show DEAD but handler shows HARVESTABLE
        // This happens with bushes after they've been fully harvested
        if (actionState == CropState.DEAD && handlerState == CropState.HARVESTABLE) {
            log.info("Bush appears dead after harvesting - needs clearing");
            cropState = CropState.DEAD;  // Trust the actions
        }
        // Special case: if handler shows EMPTY but actions show something else
        // This happens when FarmingHandler is out of sync
        else if (handlerState == CropState.EMPTY && actionState != CropState.EMPTY) {
            log.info("FarmingHandler reports EMPTY but patch has actions - trusting actions");
            cropState = actionState;  // Trust the actions
        }
        // Special handling for trees/fruit trees - HARVESTABLE means it was checked and needs clearing
        else if (handlerState == CropState.HARVESTABLE && 
            (currentContract.getPatchImplementation() == PatchImplementation.TREE ||
             currentContract.getPatchImplementation() == PatchImplementation.FRUIT_TREE)) {
            // For trees, HARVESTABLE after check-health means it needs to be cleared by gardener
            // We'll handle this as a special "CHECKED" case
            handleCheckedTree(patch);
            return;
        }
        // If actions couldn't determine state, use handler state
        else if (actionState == null && handlerState != null) {
            cropState = handlerState;
            log.info("Using FarmingHandler state as fallback: {}", cropState);
        }
        
        log.info("Handling {} patch in state: {} (Object ID: {})", 
                currentContract.getName(), cropState, patch.getId());
        
        // Handle based on CropState
        switch (cropState) {
            case EMPTY:
                handleEmptyPatch(patch);
                break;
            case HARVESTABLE:
                handleHarvestablePatch(patch);
                break;
            case UNCHECKED:
                handleUncheckedPatch(patch);  // Trees need check-health
                break;
            case GROWING:
                log.info("Patch is growing - stopping");
                plugin.setStatus("Patch growing - stopping");
                plugin.stopPlugin();
                break;
            case DEAD:
                handleDeadPatch(patch);
                break;
            case DISEASED:
                handleDiseasedPatch(patch);
                break;
            case STUMP:
                handleStumpPatch(patch);  // Tree stump needs clearing
                break;
            case FILLING:
                log.info("Patch is being filled - waiting");
                break;
        }
    }
    
    private void handleEmptyPatch(GameObject patch) {
        // Don't take actions while moving or animating
        if (Rs2Player.isAnimating() || Rs2Player.isWalking()) {
            return;
        }
        
        // Don't plant if we just completed a contract - go get a new one instead
        if (contractJustCompleted || clearingCompletedContract) {
            log.info("Contract completed, going to get new contract instead of planting");
            // Clear the contract and flags
            currentContract = null;
            contractJustCompleted = false;
            clearingCompletedContract = false;
            saveContract();
            state = FarmingContractState.COMPLETE;
            return;
        }
        
        // The patch state has already been properly determined by inferStateFromActions
        // which correctly handles the fact that "Inspect" is always present on all patches
        
        // Always check for weeds first (they can grow between checks)
        if (checkAndClearWeeds(patch)) {
            log.info("Cleared weeds that grew after initial check");
            return; // Will re-evaluate patch state on next loop
        }
        
        // Plant seeds
        int seedId = getSeedId(currentContract);
        if (seedId == -1) {
            log.error("No seed mapping found for contract: " + currentContract.getName());
            plugin.setStatus("Cannot complete contract - unknown seed type: " + currentContract.getName());
            plugin.stopPlugin();
            return;
        }
        
        if (!Rs2Inventory.contains(seedId)) {
            log.error("No seeds found in inventory for: " + currentContract.getName());
            plugin.setStatus("Need seeds: " + currentContract.getName());
            return;
        }
        
        // Store initial seed count for verification
        int initialSeedCount = Rs2Inventory.count(seedId);
        log.info("Attempting to plant {} (seed count: {})", currentContract.getName(), initialSeedCount);
        
        // Attempt to plant seeds
        Rs2Inventory.use(seedId);
        sleep(100, 200);
        if (!Rs2GameObject.interact(patch, "Use", true)) {
            // Can't reach patch, walk to it
            log.info("Can't reach patch to plant, walking to location");
            Rs2Walker.walkTo(patch.getWorldLocation());
            return;
        }
        Rs2Player.waitForAnimation(3000);
        
        // Verify seeds were actually planted
        sleep(300); // Small delay to ensure inventory updates
        int newSeedCount = Rs2Inventory.count(seedId);
        
        if (newSeedCount >= initialSeedCount) {
            // Seeds weren't planted - likely weeds grew or some other issue
            log.warn("Seeds were not planted! Initial: {}, Current: {}. Checking for weeds again...", 
                    initialSeedCount, newSeedCount);
            
            // Check for weeds again - they may have grown during the planting attempt
            if (checkAndClearWeeds(patch)) {
                log.info("Found and cleared weeds that grew during planting attempt");
                return; // Will retry planting on next loop
            }
            
            log.error("Failed to plant seeds for unknown reason");
            return;
        }
        
        log.info("Seeds planted successfully (used {} seeds)", initialSeedCount - newSeedCount);
        
        // Apply compost if configured and patch isn't already composted
        if (config.useCompost() && shouldApplyCompost(patch)) {
            applyCompost(patch);
        }
        
        log.info("Contract planted successfully. Plugin stopping.");
        plugin.setStatus("Contract planted - stopping");
        plugin.stopPlugin();
    }
    
    /**
     * Checks if a patch has weeds and clears them if present.
     * @param patch The patch to check
     * @return true if weeds were found and cleared, false if no weeds
     */
    private boolean checkAndClearWeeds(GameObject patch) {
        // For farming patches, we need the impostor which has the actual actions
        var comp = Rs2GameObject.convertToObjectComposition(patch, false);  // false = don't ignore impostor
        if (comp != null && comp.getActions() != null) {
            // Log all available actions for debugging
            log.info("Available actions for patch (ID: {}): {}", patch.getId(), Arrays.toString(comp.getActions()));
            
            // Check for Rake action (case insensitive)
            for (String action : comp.getActions()) {
                if (action != null && action.toLowerCase().contains("rake")) {
                    log.info("Weeds detected - raking patch using action: {}", action);
                    if (!Rs2GameObject.interact(patch, action, true)) {
                        // Interact failed - walk to patch location manually
                        log.info("Can't reach patch, walking to location");
                        Rs2Walker.walkTo(patch.getWorldLocation());
                        return false;  // Return false because weeds weren't cleared yet
                    }
                    
                    // Wait for raking to start
                    sleepUntil(() -> Rs2Player.isAnimating(), 2000);
                    
                    // Wait for raking to complete
                    if (Rs2Player.isAnimating()) {
                        log.info("Raking in progress - waiting for completion");
                        Rs2Player.waitForAnimation(1500); // Wait until stopped animating for 1.5 seconds
                    }
                    
                    sleep(300); // Small additional delay to ensure patch state updates
                    log.info("Finished raking weeds");
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Checks if compost should be applied to a patch.
     * @param patch The patch to check
     * @return true if compost should be applied, false if already composted
     */
    private boolean shouldApplyCompost(GameObject patch) {
        // Check patch actions - composted patches typically don't have "Use" action
        // or have different inspect text
        var comp = Rs2GameObject.convertToObjectComposition(patch, true);
        if (comp != null && comp.getActions() != null) {
            // If patch has "Inspect" action, it might already be treated
            for (String action : comp.getActions()) {
                if (action != null && action.contains("Inspect")) {
                    // Patch likely already has something planted/treated
                    log.info("Patch appears to already be treated (has Inspect action)");
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Applies compost to a patch if available in inventory.
     * @param patch The patch to apply compost to
     */
    private void applyCompost(GameObject patch) {
        String compost = config.compostType().toString();
        if (!Rs2Inventory.contains(compost)) {
            log.info("No {} available for composting", compost);
            return;
        }
        
        int initialCompostCount = Rs2Inventory.count(compost);
        log.info("Applying {} to patch (count: {})", compost, initialCompostCount);
        
        Rs2Inventory.use(compost);
        sleep(100, 200);
        if (!Rs2GameObject.interact(patch, "Use", true)) {
            // Can't reach patch, walk to it
            log.info("Can't reach patch to apply compost, walking to location");
            Rs2Walker.walkTo(patch.getWorldLocation());
            return;
        }
        Rs2Player.waitForAnimation(3000);
        
        // Verify compost was used
        sleep(300);
        int newCompostCount = Rs2Inventory.count(compost);
        
        if (newCompostCount < initialCompostCount) {
            log.info("Compost applied successfully");
        } else {
            log.warn("Compost was not applied - patch may already be treated");
        }
    }
    
    private void handleHarvestablePatch(GameObject patch) {
        // Don't take actions while moving or animating
        if (Rs2Player.isAnimating() || Rs2Player.isWalking()) {
            return;
        }
        
        // Check if inventory is full before starting
        if (Rs2Inventory.isFull()) {
            log.info("Inventory full before harvesting - attempting to note crops");
            if (!noteHarvestedCrops()) {
                log.error("Failed to note crops and inventory is full - cannot continue");
                plugin.setStatus("Inventory full - cannot continue");
                plugin.stopPlugin();
                return;
            }
        }
        
        // Mark that we're harvesting for contract completion tracking
        harvestingContract = true;
        
        // For regular crops - harvest
        String action = getHarvestAction(currentContract);
        
        // Check if we're already harvesting (animating)
        if (Rs2Player.isAnimating()) {
            log.info("Already harvesting - waiting for completion");
            return; // Don't interrupt the current harvesting
        }
        
        log.info("Starting harvest of {} using action: {}", currentContract.getName(), action);
        if (!Rs2GameObject.interact(patch, action, true)) {
            // Can't reach patch, walk to it manually
            log.info("Can't reach patch for harvesting, walking to location");
            Rs2Walker.walkTo(patch.getWorldLocation());
            return;
        }
        
        // Wait for harvesting to start
        sleepUntil(() -> Rs2Player.isAnimating(), 2000);
        
        // Wait for harvesting to complete or inventory to fill
        if (Rs2Player.isAnimating()) {
            log.info("Harvesting in progress - waiting for completion or inventory full");
            
            // Wait until either:
            // 1. Player stops animating (harvesting complete)
            // 2. Inventory becomes full (need to note crops)
            boolean stoppedDueToFull = sleepUntil(() -> 
                !Rs2Player.isAnimating() || Rs2Inventory.isFull(), 
                20000  // Max wait time
            );
            
            // If we stopped because inventory is full
            if (Rs2Inventory.isFull() && Rs2Player.isAnimating()) {
                log.info("Inventory filled during harvesting - noting crops");
                
                // Wait for current animation to finish
                Rs2Player.waitForAnimation(1500);
                
                // Note the crops
                if (noteHarvestedCrops()) {
                    log.info("Successfully noted crops - resuming harvest");
                    // Will continue harvesting on next loop iteration
                    return;
                } else {
                    log.error("Failed to note crops - stopping");
                    plugin.setStatus("Failed to note crops");
                    plugin.stopPlugin();
                    return;
                }
            } else {
                // Normal completion - wait for animation to fully stop
                Rs2Player.waitForAnimation(1500);
            }
        }
        
        // Small additional delay to ensure we're really done
        sleep(300);
        
        // After harvesting completes, check if patch is now empty
        // This indicates we've fully harvested the contract crop
        if (harvestingContract && currentContract != null) {
            CropState newState = null;
            if (farmingHandler != null && farmingWorld != null) {
                FarmingPatch targetPatch = findFarmingPatch(currentContract);
                if (targetPatch != null) {
                    newState = farmingHandler.predictPatch(targetPatch);
                    log.info("Patch state after harvesting: {}", newState);
                }
            }
            
            // For bushes/cacti, they might need further clearing after harvesting
            if (currentContract.getPatchImplementation() == PatchImplementation.BUSH ||
                currentContract.getPatchImplementation() == PatchImplementation.CACTUS) {
                if (newState == CropState.DEAD) {
                    log.info("Bush/Cactus harvested but now dead - needs clearing");
                    // Will handle clearing in next loop iteration
                    harvestingContract = false;
                    return;
                }
            }
            
            // If patch is now empty, we've completed the contract
            if (newState == CropState.EMPTY) {
                log.info("Patch cleared after harvesting contract crop - contract complete!");
                state = FarmingContractState.COMPLETE;
                harvestingContract = false;
                return;
            }
        }
        
        // Reset flag
        harvestingContract = false;
        
        log.info("Harvesting done - will re-evaluate patch state on next loop");
        // The patch state will be re-evaluated on next loop
    }
    
    /**
     * Notes harvested crops with the Tool Leprechaun to free inventory space.
     * @return true if crops were successfully noted, false otherwise
     */
    private boolean noteHarvestedCrops() {
        // Find the Tool Leprechaun NPC
        Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool Leprechaun");
        if (leprechaun == null) {
            log.warn("Tool Leprechaun not found nearby");
            return false;
        }
        
        // Get the harvested crop name based on the contract
        String cropName = currentContract.getName().toLowerCase();
        
        // Find the unnoted crop item in inventory
        Rs2ItemModel unnotedCrop = Rs2Inventory.getUnNotedItem(cropName, false);
        if (unnotedCrop == null) {
            log.warn("No unnoted {} found in inventory to note", cropName);
            return false;
        }
        
        int initialCount = Rs2Inventory.count(unnotedCrop.getId());
        int initialEmptySlots = Rs2Inventory.emptySlotCount();
        log.info("Noting {} {} (empty slots: {})", initialCount, cropName, initialEmptySlots);
        
        // Use the crop on the Tool Leprechaun
        Rs2Inventory.useItemOnNpc(unnotedCrop.getId(), leprechaun);
        
        // Wait for inventory to change (crops get noted)
        boolean changed = Rs2Inventory.waitForInventoryChanges(5000);
        
        if (!changed) {
            log.warn("Inventory didn't change after using crop on leprechaun");
            return false;
        }
        
        // Small delay to ensure the noting is complete
        sleep(100, 300);
        
        // Verify that we freed up inventory space
        int newEmptySlots = Rs2Inventory.emptySlotCount();
        int newCount = Rs2Inventory.count(unnotedCrop.getId());
        
        if (newEmptySlots > initialEmptySlots || newCount < initialCount) {
            log.info("Successfully noted crops - freed {} slots (now have {} empty)", 
                    newEmptySlots - initialEmptySlots, newEmptySlots);
            return true;
        } else {
            log.warn("Noting may have failed - no inventory space freed");
            return false;
        }
    }
    
    private void handleUncheckedPatch(GameObject patch) {
        // Don't take actions while moving or animating
        if (Rs2Player.isAnimating() || Rs2Player.isWalking()) {
            return;
        }
        
        // Check health first (for trees, bushes, etc.)
        log.info("Performing check-health on {} patch", currentContract.getPatchImplementation());
        if (!Rs2GameObject.interact(patch, "Check-health", true)) {
            // Walking to patch, will retry on next loop
            return;
        }
        Rs2Inventory.waitForInventoryChanges(5000);
        
        // For bushes and cacti, check-health completes the contract but we need to continue clearing
        if (currentContract.getPatchImplementation() == PatchImplementation.BUSH ||
            currentContract.getPatchImplementation() == PatchImplementation.CACTUS) {
            log.info("Bush/Cactus contract complete after check-health, but patch needs full clearing");
            // Set flag to continue clearing after contract completion
            clearingCompletedContract = true;
            // Don't set state to COMPLETE - let the patch continue to be processed
            // The patch will go through HARVESTABLE -> DEAD -> EMPTY states
            // We'll handle it in subsequent loops
            return;
        }
        
        // For trees and fruit trees, need to pay for clearing after check-health
        if (currentContract.getPatchImplementation() == PatchImplementation.TREE ||
            currentContract.getPatchImplementation() == PatchImplementation.FRUIT_TREE) {
            
            var coinItem = Rs2Inventory.get(ItemID.COINS);
            int coinCount = coinItem != null ? coinItem.getQuantity() : 0;
            if (coinCount < 200) {
                log.error("Need 200gp to clear tree patch (current: {}gp)", coinCount);
                return;
            }
            
            // Find the appropriate gardener
            NPC gardener = null;
            if (currentContract.getPatchImplementation() == PatchImplementation.TREE) {
                gardener = Rs2Npc.getNpc("Rosie");
            } else if (currentContract.getPatchImplementation() == PatchImplementation.FRUIT_TREE) {
                gardener = Rs2Npc.getNpc("Nikkie");
            }
            
            if (gardener != null && Rs2Player.getWorldLocation().distanceTo(gardener.getWorldLocation()) <= 10) {
                String payAction = currentContract.getPatchImplementation() == PatchImplementation.TREE ? 
                                   "Pay (tree patch)" : "Pay (fruit tree)";
                Rs2Npc.interact(gardener, payAction);
                
                // Wait for dialogue to open
                sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
                
                // Handle the confirmation dialogue quickly
                while (Rs2Dialogue.isInDialogue()) {
                    // Check if we have options (Yes/No)
                    if (Rs2Dialogue.hasDialogueOption("Yes.")) {
                        Rs2Dialogue.clickOption("Yes.");
                        log.info("Confirmed payment for tree removal");
                        sleep(100);
                        continue;
                    } else if (Rs2Dialogue.hasDialogueOption("Yes")) {
                        Rs2Dialogue.clickOption("Yes");
                        log.info("Confirmed payment for tree removal");
                        sleep(100);
                        continue;
                    }
                    
                    // Continue through dialogue
                    Rs2Dialogue.clickContinue();
                    sleep(100); // Fast dialogue processing
                }
            } else {
                log.warn("Gardener not found nearby for tree clearing");
            }
        }
        
        // Check if complete
        if (isContractComplete()) {
            state = FarmingContractState.COMPLETE;
        }
    }
    
    private void handleDeadPatch(GameObject patch) {
        // Don't take actions while moving or animating
        if (Rs2Player.isAnimating() || Rs2Player.isWalking()) {
            return;
        }
        
        if (!Rs2GameObject.interact(patch, "Clear", true)) {
            // Walking to patch, will retry on next loop
            return;
        }
        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Dialogue.isInDialogue(), 3000);
        
        // Handle confirmation dialogue for bushes and cacti
        if (Rs2Dialogue.isInDialogue()) {
            log.info("Clearing confirmation dialogue appeared");
            
            // Look for the "Yes" option to clear for new crops
            if (Rs2Dialogue.hasDialogueOption("Yes, I want to clear it for new crops.")) {
                log.info("Confirming clear for new crops");
                Rs2Dialogue.clickOption("Yes, I want to clear it for new crops.");
                sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 2000);
            }
        }
        
        // Wait for clearing animation
        if (Rs2Player.isAnimating()) {
            Rs2Player.waitForAnimation(3000);
        }
        
        // For bushes/cacti that have already completed their contract
        // Check if the patch is now empty and the contract is done
        if (currentContract != null && 
            (currentContract.getPatchImplementation() == PatchImplementation.BUSH ||
             currentContract.getPatchImplementation() == PatchImplementation.CACTUS)) {
            
            // Check if the patch is now empty
            CropState newState = inferStateFromActions(patch);
            if (newState == CropState.EMPTY) {
                log.info("Bush/Cactus patch fully cleared after contract completion");
                // The contract is already complete (from check-health)
                // Now the patch is also empty, so we can move to COMPLETE
                state = FarmingContractState.COMPLETE;
            }
        }
    }
    
    private void handleDiseasedPatch(GameObject patch) {
        // Don't take actions while moving or animating
        if (Rs2Player.isAnimating() || Rs2Player.isWalking()) {
            return;
        }
        
        if (Rs2Inventory.contains("Plant cure")) {
            Rs2Inventory.use("Plant cure");
            Rs2GameObject.interact(patch, "Use", true);
            Rs2Player.waitForAnimation(3000);
        } else {
            log.warn("Patch is diseased but no plant cure available");
        }
    }
    
    private void handleCheckedTree(GameObject patch) {
        // Tree has been checked, needs to be cleared by paying gardener
        log.info("Tree has been checked, paying gardener to clear it");
        
        // Check if we have coins - need to get stack quantity, not item count
        var coinItem = Rs2Inventory.get(ItemID.COINS);
        int coinCount = coinItem != null ? coinItem.getQuantity() : 0;
        log.info("Coin count in inventory: {}", coinCount);
        
        if (coinCount < 200) {
            log.error("Need 200gp to clear tree patch (current: {}gp)", coinCount);
            plugin.setStatus("Need 200gp to clear tree");
            return;
        }
        
        // Find the appropriate gardener
        NPC gardener = null;
        if (currentContract.getPatchImplementation() == PatchImplementation.TREE) {
            gardener = Rs2Npc.getNpc("Rosie");
        } else if (currentContract.getPatchImplementation() == PatchImplementation.FRUIT_TREE) {
            gardener = Rs2Npc.getNpc("Nikkie");
        }
        
        if (gardener != null && Rs2Player.getWorldLocation().distanceTo(gardener.getWorldLocation()) <= 10) {
            String payAction = currentContract.getPatchImplementation() == PatchImplementation.TREE ? 
                               "Pay (tree patch)" : "Pay (fruit tree)";
            Rs2Npc.interact(gardener, payAction);
            
            // Wait for dialogue to open
            sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
            
            // Handle the confirmation dialogue quickly
            while (Rs2Dialogue.isInDialogue()) {
                // Check if we have options (Yes/No)
                if (Rs2Dialogue.hasDialogueOption("Yes.")) {
                    Rs2Dialogue.clickOption("Yes.");
                    log.info("Confirmed payment for tree removal");
                    sleep(100);
                    continue;
                } else if (Rs2Dialogue.hasDialogueOption("Yes")) {
                    Rs2Dialogue.clickOption("Yes");
                    log.info("Confirmed payment for tree removal");
                    sleep(100);
                    continue;
                }
                
                // Continue through dialogue
                Rs2Dialogue.clickContinue();
                sleep(100); // Fast dialogue processing
            }
            
            // After payment, wait a bit for the tree to be cleared
            sleep(2000, 3000);
            
            // Check if patch is now empty after tree clearing
            CropState newState = null;
            if (farmingHandler != null && farmingWorld != null) {
                FarmingPatch targetPatch = findFarmingPatch(currentContract);
                if (targetPatch != null) {
                    newState = farmingHandler.predictPatch(targetPatch);
                    log.info("Tree patch state after clearing: {}", newState);
                }
            }
            
            // If patch is now empty, contract is complete
            if (newState == CropState.EMPTY) {
                log.info("Tree patch cleared - contract complete!");
                currentContract = null; // Clear the contract since it's done
                saveContract(); // Clear from config
                state = FarmingContractState.COMPLETE;
            } else {
                log.warn("Tree clearing may not have completed, state: {}", newState);
                // Will retry on next loop
            }
            return; // Important: return here to avoid continuing
        } else {
            log.warn("Gardener not found nearby for tree clearing");
        }
    }
    
    private void handleStumpPatch(GameObject patch) {
        // Tree stump needs to be chopped down (this is for actual stumps after chopping)
        if (!Rs2GameObject.interact(patch, "Chop", true)) {
            // Walking to patch, will retry on next loop
            return;
        }
        Rs2Player.waitForAnimation(5000);
        
        // For farming contracts, we already got the XP from check-health
        if (isContractComplete()) {
            state = FarmingContractState.COMPLETE;
        }
    }
    
    private void handleComplete() {
        // Clear contract immediately when entering COMPLETE state
        // This ensures we don't try to prepare for a completed contract
        if (currentContract != null) {
            log.info("Clearing completed contract: {}", currentContract.getName());
            currentContract = null;
            saveContract();
        }
        
        NPC jane = Rs2Npc.getNpc("Guildmaster Jane");
        if (jane == null) {
            // Jane not visible, walk to her location
            if (Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) > 10) {
                Rs2Walker.walkTo(JANE_LOCATION);
            }
            return;
        }
        
        // Rs2Npc.interact will automatically walk if Jane is not in line of sight
        if (!Rs2Npc.interact(jane, "Contract")) {
            Rs2Npc.interact(jane, "Talk-to");
        }
        
        // Wait for dialogue to open
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
        
        // Handle dialogue quickly
        boolean gotReward = false;
        while (Rs2Dialogue.isInDialogue()) {
            // Check for options first
            if (Rs2Dialogue.hasSelectAnOption()) {
                // Select tier for new contract if needed
                String tier = getContractTier();
                if (Rs2Dialogue.hasDialogueOption(tier)) {
                    log.info("Selecting new contract tier: {}", tier);
                    Rs2Dialogue.clickOption(tier);
                    sleep(100);
                    continue;
                }
            }
            
            String text = Rs2Dialogue.getDialogueText();
            if (text != null && !text.isEmpty()) {
                // Check for reward text
                if (text.contains("reward")) {
                    log.info("Got reward from Jane");
                    gotReward = true;
                }
                
                // Parse new contract from dialogue
                Matcher m = CONTRACT_PATTERN.matcher(text);
                if (m.find()) {
                    String contractName = m.group(1).trim();
                    currentContract = findProduce(contractName);
                    if (currentContract != null) {
                        log.info("Got new contract after completion: {}", currentContract.getName());
                        saveContract();
                        break; // Exit immediately - dialogue will close when we walk away
                    }
                }
            }
            
            // Continue dialogue
            Rs2Dialogue.clickContinue();
            sleep(100); // Fast 100ms delay
        }
        
        // Handle seed packs if we have any (from this or previous rewards)
        handleSeedPacks();
        
        // After completing and getting new contract, check the patch first
        if (currentContract != null) {
            // Clear previous contract's state
            lastKnownCropState = null;
            skipSeedPreparation = false;
            // Always check patch state for new contract
            state = FarmingContractState.CHECK_PATCH;
        } else {
            state = FarmingContractState.GET_CONTRACT;
        }
    }
    
    /**
     * Handles opening seed packs received as rewards.
     * Ensures we have enough inventory space and banks the seeds after opening.
     */
    private void handleSeedPacks() {
        // Check for seed packs in inventory (using name pattern matching)
        var seedPacks = Rs2Inventory.all(item -> 
            item.getName().toLowerCase().contains("seed pack"));
        
        if (seedPacks.isEmpty()) {
            log.info("No seed packs found to open");
            return;
        }
        
        log.info("Found {} seed pack(s) to open", seedPacks.size());
        
        // Check if we have enough inventory space (need at least 10 empty slots)
        int emptySlots = Rs2Inventory.emptySlotCount();
        if (emptySlots < 10) {
            log.info("Not enough inventory space for seed packs ({} empty slots). Banking first.", emptySlots);
            
            // Go to bank to make space
            if (!Rs2Bank.isNearBank(20)) {
                Rs2Bank.walkToBank();
                sleepUntil(() -> Rs2Bank.isNearBank(20), 10000);
            }
            
            if (Rs2Bank.openBank()) {
                // Deposit everything except seed packs and essential tools
                Rs2Bank.depositAllExcept("Seed pack", "Spade", "Rake", "Seed dibber", "Magic secateurs");
                Rs2Bank.closeBank();
                sleep(300);
                
                // Now check if we have enough space after banking
                emptySlots = Rs2Inventory.emptySlotCount();
                if (emptySlots < 10) {
                    log.warn("Still not enough space after banking. Need to bank more items.");
                    return;  // Will retry on next loop
                }
            }
        }
        
        // Open all seed packs
        for (var seedPack : Rs2Inventory.all(item -> 
                item.getName().toLowerCase().contains("seed pack"))) {
            log.info("Opening seed pack: {}", seedPack.getName());
            Rs2Inventory.interact(seedPack, "Take-all");
            Rs2Inventory.waitForInventoryChanges(2000);
            sleep(300);
        }
        
        // Go to bank to deposit the seeds
        log.info("Banking seeds from seed packs");
        if (!Rs2Bank.isNearBank(20)) {
            Rs2Bank.walkToBank();
            sleepUntil(() -> Rs2Bank.isNearBank(20), 10000);
        }
        
        if (Rs2Bank.openBank()) {
            // Deposit all seeds (they typically have "seed" in the name)
            var seeds = Rs2Inventory.all(item -> 
                item.getName().toLowerCase().contains("seed") && 
                !item.getName().toLowerCase().contains("seed dibber") &&
                !item.getName().toLowerCase().contains("seed pack"));
            
            for (var seed : seeds) {
                Rs2Bank.depositAll(seed.getId());
            }
            
            Rs2Bank.closeBank();
            sleep(300);
        }
    }
    
    // Helper methods
    
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
        // First try exact match using getByContractName (handles plurals correctly)
        try {
            Method method = Produce.class.getDeclaredMethod("getByContractName", String.class);
            method.setAccessible(true);
            Produce result = (Produce) method.invoke(null, name);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            log.info("Failed to use getByContractName: {}", e.getMessage());
        }
        
        // Fallback: check both singular and plural forms
        String lower = name.toLowerCase().trim();
        for (Produce p : Produce.values()) {
            // Check exact match with contract name (plural)
            if (p.getContractName().toLowerCase().equals(lower)) {
                return p;
            }
            // Check exact match with singular name
            if (p.getName().toLowerCase().equals(lower)) {
                return p;
            }
        }
        
        // Last resort: partial matching
        for (Produce p : Produce.values()) {
            if (p.getContractName().toLowerCase().contains(lower) || 
                lower.contains(p.getContractName().toLowerCase()) ||
                p.getName().toLowerCase().contains(lower) || 
                lower.contains(p.getName().toLowerCase())) {
                return p;
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
        // Complete seed mapping for all farming contract produce
        switch (produce) {
            // Allotments
            case POTATO: return ItemID.POTATO_SEED;
            case ONION: return ItemID.ONION_SEED;
            case CABBAGE: return ItemID.CABBAGE_SEED;
            case TOMATO: return ItemID.TOMATO_SEED;
            case SWEETCORN: return ItemID.SWEETCORN_SEED;
            case STRAWBERRY: return ItemID.STRAWBERRY_SEED;
            case WATERMELON: return ItemID.WATERMELON_SEED;
            case SNAPE_GRASS: return ItemID.SNAPE_GRASS_SEED;
            
            // Flowers
            case MARIGOLD: return ItemID.MARIGOLD_SEED;
            case ROSEMARY: return ItemID.ROSEMARY_SEED;
            case NASTURTIUM: return ItemID.NASTURTIUM_SEED;
            case WOAD: return ItemID.WOAD_SEED;
            case LIMPWURT: return ItemID.LIMPWURT_SEED;
            case WHITE_LILY: return ItemID.WHITE_LILY_SEED;
            
            // Herbs
            case GUAM: return ItemID.GUAM_SEED;
            case MARRENTILL: return ItemID.MARRENTILL_SEED;
            case TARROMIN: return ItemID.TARROMIN_SEED;
            case HARRALANDER: return ItemID.HARRALANDER_SEED;
            case RANARR: return ItemID.RANARR_SEED;
            case TOADFLAX: return ItemID.TOADFLAX_SEED;
            case IRIT: return ItemID.IRIT_SEED;
            case AVANTOE: return ItemID.AVANTOE_SEED;
            case KWUARM: return ItemID.KWUARM_SEED;
            case SNAPDRAGON: return ItemID.SNAPDRAGON_SEED;
            case CADANTINE: return ItemID.CADANTINE_SEED;
            case LANTADYME: return ItemID.LANTADYME_SEED;
            case DWARF_WEED: return ItemID.DWARF_WEED_SEED;
            case TORSTOL: return ItemID.TORSTOL_SEED;
            
            // Trees - use saplings (in plantpots)
            case OAK: return ItemID.PLANTPOT_OAK_SAPLING;
            case WILLOW: return ItemID.PLANTPOT_WILLOW_SAPLING;
            case MAPLE: return ItemID.PLANTPOT_MAPLE_SAPLING;
            case YEW: return ItemID.PLANTPOT_YEW_SAPLING;
            case MAGIC: return ItemID.PLANTPOT_MAGIC_TREE_SAPLING;
            
            // Fruit trees - use saplings (in plantpots)
            case APPLE: return ItemID.PLANTPOT_APPLE_SAPLING;
            case BANANA: return ItemID.PLANTPOT_BANANA_SAPLING;
            case ORANGE: return ItemID.PLANTPOT_ORANGE_SAPLING;
            case CURRY: return ItemID.PLANTPOT_CURRY_SAPLING;
            case PINEAPPLE: return ItemID.PLANTPOT_PINEAPPLE_SAPLING;
            case PAPAYA: return ItemID.PLANTPOT_PAPAYA_SAPLING;
            case PALM: return ItemID.PLANTPOT_PALM_SAPLING;
            case DRAGONFRUIT: return ItemID.PLANTPOT_DRAGONFRUIT_SAPLING;
            
            // Bushes
            case REDBERRIES: return ItemID.REDBERRY_BUSH_SEED;
            case CADAVABERRIES: return ItemID.CADAVABERRY_BUSH_SEED;
            case DWELLBERRIES: return ItemID.DWELLBERRY_BUSH_SEED;
            case JANGERBERRIES: return ItemID.JANGERBERRY_BUSH_SEED;
            case WHITEBERRIES: return ItemID.WHITEBERRY_BUSH_SEED;
            case POISON_IVY: return ItemID.POISONIVY_BUSH_SEED;
            
            // Hops
            case BARLEY: return ItemID.BARLEY_SEED;
            case HAMMERSTONE: return ItemID.HAMMERSTONE_HOP_SEED;
            case ASGARNIAN: return ItemID.ASGARNIAN_HOP_SEED;
            case JUTE: return ItemID.JUTE_SEED;
            case YANILLIAN: return ItemID.YANILLIAN_HOP_SEED;
            case KRANDORIAN: return ItemID.KRANDORIAN_HOP_SEED;
            case WILDBLOOD: return ItemID.WILDBLOOD_HOP_SEED;
            
            // Cactus
            case CACTUS: return ItemID.CACTUS_SEED;
            case POTATO_CACTUS: return ItemID.POTATO_CACTUS_SEED;
            
            default: return -1;
        }
    }
    
    private WorldPoint getPatchLocation(Produce produce) {
        // Guild patch center locations
        switch (produce.getPatchImplementation()) {
            case HERB: return new WorldPoint(1239, 3728, 0);
            case TREE: return new WorldPoint(1233, 3734, 0);
            case FRUIT_TREE: return new WorldPoint(1243, 3757, 0);
            case FLOWER: return new WorldPoint(1260, 3727, 0);
            case BUSH: return new WorldPoint(1260, 3732, 0);
            case ALLOTMENT: return new WorldPoint(1265, 3729, 0);
            case CACTUS: return new WorldPoint(1264, 3747, 0);  // Corrected Y coordinate
            default: return null;
        }
    }
    
    private Polygon getPatchArea(Produce produce) {
        // Guild patch polygon areas based on FarmingWorld definitions
        switch (produce.getPatchImplementation()) {
            case HERB:
                // Herb patch at (1238-1239, 3726-3727)
                return new Polygon(
                    new int[]{1238, 1238, 1239, 1239},
                    new int[]{3726, 3727, 3727, 3726},
                    4
                );
            case TREE:
                // Tree patch at (1231-1233, 3735-3737)
                return new Polygon(
                    new int[]{1231, 1231, 1233, 1233},
                    new int[]{3735, 3737, 3737, 3735},
                    4
                );
            case FRUIT_TREE:
                // Fruit tree patch at guild - using estimated area
                return new Polygon(
                    new int[]{1241, 1241, 1244, 1244},
                    new int[]{3756, 3759, 3759, 3756},
                    4
                );
            case FLOWER:
                // Flower patch at (1260-1261, 3725-3726)
                return new Polygon(
                    new int[]{1260, 1260, 1261, 1261},
                    new int[]{3725, 3726, 3726, 3725},
                    4
                );
            case BUSH:
                // Bush patch at (1260-1261, 3733-3734)
                return new Polygon(
                    new int[]{1260, 1260, 1261, 1261},
                    new int[]{3733, 3734, 3734, 3733},
                    4
                );
            case ALLOTMENT:
                // South allotment - simplified area
                return new Polygon(
                    new int[]{1265, 1265, 1268, 1268},
                    new int[]{3724, 3730, 3730, 3724},
                    4
                );
            case CACTUS:
                // Cactus patch in Farming Guild - corrected coordinates
                // NOTE FOR MASON: The cactus patch is between (1264, 3747) and (1265, 3748)
                return new Polygon(
                    new int[]{1264, 1264, 1265, 1265},
                    new int[]{3747, 3748, 3748, 3747},
                    4
                );
            default:
                return null;
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
    
    private TileObject findPatchObject(Produce produce) {
        if (produce == null) return null;
        
        // First try area-based search (more reliable)
        Polygon patchArea = getPatchArea(produce);
        WorldPoint patchCenter = getPatchLocation(produce);
        
        if (patchArea != null && patchCenter != null) {
            // Get all GameObjects in the area
            List<GameObject> objectsInArea = Rs2GameObject.getGameObjects(
                obj -> patchArea.contains(obj.getWorldLocation().getX(), 
                                         obj.getWorldLocation().getY())
            );
            
            if (!objectsInArea.isEmpty()) {
                log.debug("Found {} objects in patch area for {}", objectsInArea.size(), produce.getName());
                
                // Get the correct harvest action for this produce type
                String expectedHarvestAction = getHarvestAction(produce);
                log.debug("Looking for harvest action: '{}' for {}", expectedHarvestAction, produce.getName());
                
                GameObject bestMatch = null;
                GameObject patchObject = null;
                
                for (GameObject obj : objectsInArea) {
                    // Get object composition to check actions
                    var comp = Rs2GameObject.convertToObjectComposition(obj, true);
                    if (comp != null) {
                        log.debug("  Object ID={} Name='{}' at {}", 
                                 obj.getId(), comp.getName(), obj.getWorldLocation());
                        
                        if (comp.getActions() != null) {
                            // Log all actions for debugging
                            for (String action : comp.getActions()) {
                                if (action != null) {
                                    log.debug("    Action: '{}'", action);
                                }
                            }
                            
                            // Check if this object has relevant farming actions
                            for (String action : comp.getActions()) {
                                if (action != null) {
                                    // Priority 1: Object with the expected harvest action for this produce type
                                    if (action.equalsIgnoreCase(expectedHarvestAction)) {
                                        log.info("Found harvestable {} with action '{}': ID={} Name={}", 
                                                produce.getName(), action, obj.getId(), comp.getName());
                                        return obj;  // Return immediately - this is what we want
                                    }
                                    
                                    // Priority 2: Any other farming-related action
                                    if (bestMatch == null && (
                                        action.equalsIgnoreCase("Rake") ||
                                        action.equalsIgnoreCase("Clear") ||
                                        action.equalsIgnoreCase("Inspect") ||
                                        action.toLowerCase().contains("chop"))) {
                                        patchObject = obj;  // Save as fallback
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Return the best match we found
                if (bestMatch != null) {
                    log.debug("Found crop object: ID={} at {}", 
                             bestMatch.getId(), bestMatch.getWorldLocation());
                    return bestMatch;
                }
                if (patchObject != null) {
                    log.debug("Found patch object: ID={} at {}", 
                             patchObject.getId(), patchObject.getWorldLocation());
                    return patchObject;
                }
                
                // If no farming objects found, return closest object as fallback
                GameObject closest = null;
                int minDistance = Integer.MAX_VALUE;
                
                for (GameObject obj : objectsInArea) {
                    int distance = obj.getWorldLocation().distanceTo(patchCenter);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closest = obj;
                    }
                }
                
                if (closest != null) {
                    log.debug("Found closest object as fallback: ID={} at {}", 
                             closest.getId(), closest.getWorldLocation());
                    return closest;
                }
            }
        }
        
        // Fallback to ID-based search for supported types
        Integer[] patchIds = getPatchObjectIds(produce);
        if (patchIds.length > 0) {
            TileObject patch = Rs2GameObject.findObject(patchIds);
            if (patch != null) {
                log.debug("Found patch object using ID search: ID={}", patch.getId());
                return patch;
            }
        }
        
        log.warn("Could not find patch object for {}", produce.getName());
        return null;
    }
    
    private Integer[] getPatchObjectIds(Produce produce) {
        // Get proper ObjectIDs for each patch type (fallback method)
        switch (produce.getPatchImplementation()) {
            case HERB:
                return new Integer[] {
                    ObjectID.FARMING_HERB_PATCH_1, ObjectID.FARMING_HERB_PATCH_2,
                    ObjectID.FARMING_HERB_PATCH_3, ObjectID.FARMING_HERB_PATCH_4,
                    ObjectID.FARMING_HERB_PATCH_5, ObjectID.FARMING_HERB_PATCH_6,
                    ObjectID.FARMING_HERB_PATCH_7, ObjectID.FARMING_HERB_PATCH_8
                };
            case FLOWER:
                return new Integer[] {
                    ObjectID.FARMING_FLOWER_PATCH_1, ObjectID.FARMING_FLOWER_PATCH_2,
                    ObjectID.FARMING_FLOWER_PATCH_3, ObjectID.FARMING_FLOWER_PATCH_4,
                    ObjectID.FLOWER_PATCH_WEEDED, ObjectID.FLOWER_PATCH_WEEDS_1,
                    ObjectID.FLOWER_PATCH_WEEDS_2, ObjectID.FLOWER_PATCH_WEEDS_3
                };
            case BUSH:
                return new Integer[] {
                    ObjectID.FARMING_BUSH_PATCH_1, ObjectID.FARMING_BUSH_PATCH_2,
                    ObjectID.FARMING_BUSH_PATCH_3, ObjectID.FARMING_BUSH_PATCH_4,
                    ObjectID.BUSH_PATCH_WEEDED, ObjectID.BUSH_PATCH_WEEDS_1,
                    ObjectID.BUSH_PATCH_WEEDS_2, ObjectID.BUSH_PATCH_WEEDS_3
                };
            case FRUIT_TREE:
                return new Integer[] {
                    ObjectID.FARMING_FRUIT_TREE_PATCH_1, ObjectID.FARMING_FRUIT_TREE_PATCH_2,
                    ObjectID.FARMING_FRUIT_TREE_PATCH_3, ObjectID.FARMING_FRUIT_TREE_PATCH_4,
                    ObjectID.FRUIT_TREE_PATCH_WEEDED, ObjectID.FRUIT_TREE_PATCH_WEEDS_1,
                    ObjectID.FRUIT_TREE_PATCH_WEEDS_2, ObjectID.FRUIT_TREE_PATCH_WEEDS_3
                };
            case CACTUS:
                return new Integer[] {
                    ObjectID.FARMING_CACTUS_PATCH, ObjectID.FARMING_CACTUS_PATCH_2,
                    ObjectID.CACTUS_PATCH_WEEDED, ObjectID.CACTUS_PATCH_WEEDS_1,
                    ObjectID.CACTUS_PATCH_WEEDS_2, ObjectID.CACTUS_PATCH_WEEDS_3,
                    // Note: There are many other cactus IDs for growing/harvestable states
                    // but these base IDs should help find the patch
                };
            default:
                // For other types, we'll rely on area-based search
                return new Integer[0];
        }
    }
    
    private GameObject findPatchUsingActions(Produce produce) {
        // Simple approach: find any object in the patch area
        Polygon area = getPatchArea(produce);
        if (area != null) {
            // Get all objects in patch area
            List<GameObject> allObjects = Rs2GameObject.getGameObjects();
            List<GameObject> objects = allObjects.stream()
                .filter(obj -> obj != null && 
                       area.contains(obj.getWorldLocation().getX(), 
                                    obj.getWorldLocation().getY()))
                .collect(Collectors.toList());
            
            // Return first valid object found
            for (GameObject obj : objects) {
                if (obj != null) {
                    log.debug("Found object in patch area: ID={}", obj.getId());
                    return obj;
                }
            }
        }
        
        // Fallback to finding by object ID or name
        TileObject patch = findPatchObject(produce);
        if (patch instanceof GameObject) {
            return (GameObject) patch;
        }
        
        log.warn("Could not find patch object for {}", produce.getName());
        return null;
    }
    
    private CropState inferStateFromActions(GameObject patch) {
        // Fallback state inference from actions when FarmingHandler is not available
        if (patch == null) return CropState.EMPTY;
        
        // Use ignoreImpostor=false for farming patches to get current state's actions
        var comp = Rs2GameObject.convertToObjectComposition(patch, false);
        if (comp == null || comp.getActions() == null) return CropState.EMPTY;
        
        // Log available actions for debugging
        log.info("inferStateFromActions - Patch ID: {}, Actions: {}", 
                patch.getId(), Arrays.toString(comp.getActions()));
        
        // Collect all non-null actions
        Set<String> actions = new HashSet<>();
        for (String action : comp.getActions()) {
            if (action != null && !action.isEmpty()) {
                actions.add(action.toLowerCase());
            }
        }
        
        // Remove "Guide" and "Inspect" as they're always present
        actions.remove("guide");
        actions.remove("inspect");
        
        // If no actions remain after removing Guide/Inspect, patch is empty
        if (actions.isEmpty()) {
            return CropState.EMPTY;
        }
        
        // If only "Rake" remains, patch is empty with weeds
        if (actions.size() == 1 && actions.contains("rake")) {
            return CropState.EMPTY;
        }
        
        // Check remaining actions for specific states
        for (String action : actions) {
            // Check-health = Tree/fruit tree ready for harvest
            if (action.equalsIgnoreCase("check-health") || 
                action.equalsIgnoreCase("check health") || 
                action.equalsIgnoreCase("check")) {
                return CropState.UNCHECKED;
            }
            
            // Pick/Harvest = Regular crops ready to harvest
            if (action.equalsIgnoreCase("pick") || 
                action.equalsIgnoreCase("harvest") ||
                action.equalsIgnoreCase("pick-from") || 
                action.equalsIgnoreCase("pick-spine")) {
                return CropState.HARVESTABLE;
            }
            
            // Chop = Tree that needs to be removed by NPC (treated as harvestable/ready to clear)
            if (action.contains("chop")) {
                // Tree with chop action needs to be cleared by paying NPC
                return CropState.HARVESTABLE;
            }
            
            // Clear = Something clearable (dead plants, stumps, etc.)
            if (action.equalsIgnoreCase("clear")) {
                return CropState.DEAD;  // Using DEAD state for clearable items
            }
        }
        
        // If we have other actions but none match specific states, patch is growing
        return CropState.GROWING;
    }
    
    private boolean isContractComplete() {
        // Contract is complete if it was detected via chat message
        return contractJustCompleted;
    }
    
    private FarmingPatch findFarmingPatch(Produce produce) {
        if (farmingWorld == null) {
            log.info("findFarmingPatch: farmingWorld is null");
            return null;
        }
        
        // Get the tab for this produce type
        Tab tab = getTabForProduce(produce);
        if (tab == null) {
            log.info("findFarmingPatch: No tab found for {}", produce.getName());
            return null;
        }
        
        log.info("findFarmingPatch: Looking for {} patch in guild", produce.getPatchImplementation());
        
        // For allotments, we need to check both north and south patches
        if (produce.getPatchImplementation() == PatchImplementation.ALLOTMENT) {
            FarmingPatch northPatch = null;
            FarmingPatch southPatch = null;
            
            // Find both allotment patches
            for (FarmingPatch patch : farmingWorld.getTabs().get(tab)) {
                if (patch.getRegion() != null && 
                    patch.getRegion().getName() != null && 
                    patch.getRegion().getName().contains("Farming Guild")) {
                    
                    String patchName = patch.getName();
                    if (patchName != null && patchName.contains("North")) {
                        northPatch = patch;
                        log.info("findFarmingPatch: Found North allotment patch");
                    } else if (patchName != null && patchName.contains("South")) {
                        southPatch = patch;
                        log.info("findFarmingPatch: Found South allotment patch");
                    } else if (patchName == null || patchName.isEmpty()) {
                        // Some patches might not have North/South in name
                        if (northPatch == null) {
                            northPatch = patch;
                            log.info("findFarmingPatch: Found first allotment patch (assuming north)");
                        } else if (southPatch == null) {
                            southPatch = patch;
                            log.info("findFarmingPatch: Found second allotment patch (assuming south)");
                        }
                    }
                }
            }
            
            // If we've already selected a patch, stick with it
            if (selectedPatchName != null) {
                if (selectedPatchName.equals("North") && northPatch != null) {
                    log.info("findFarmingPatch: Using previously selected North patch");
                    return northPatch;
                } else if (selectedPatchName.equals("South") && southPatch != null) {
                    log.info("findFarmingPatch: Using previously selected South patch");
                    return southPatch;
                }
            }
            
            // Try to determine which patch to use
            if (farmingHandler != null) {
                // Check north patch state
                if (northPatch != null) {
                    CropState northState = farmingHandler.predictPatch(northPatch);
                    log.info("findFarmingPatch: North patch state: {}", northState);
                    
                    // Use north if it's empty or has our contract crop
                    if (northState == null || northState == CropState.EMPTY) {
                        log.info("findFarmingPatch: Using North patch (empty/null)");
                        selectedPatchName = "North";
                        return northPatch;
                    }
                }
                
                // Check south patch state
                if (southPatch != null) {
                    CropState southState = farmingHandler.predictPatch(southPatch);
                    log.info("findFarmingPatch: South patch state: {}", southState);
                    
                    // Use south if it's empty or north wasn't suitable
                    if (southState == null || southState == CropState.EMPTY) {
                        log.info("findFarmingPatch: Using South patch (empty/null)");
                        selectedPatchName = "South";
                        return southPatch;
                    }
                }
            }
            
            // If we can't determine state, prefer north patch
            FarmingPatch result = northPatch != null ? northPatch : southPatch;
            if (result != null) {
                selectedPatchName = result == northPatch ? "North" : "South";
                log.info("findFarmingPatch: Defaulting to {} patch", selectedPatchName);
            } else {
                log.info("findFarmingPatch: No allotment patches found in guild!");
            }
            return result;
        }
        
        // For non-allotments, find the single guild patch
        for (FarmingPatch patch : farmingWorld.getTabs().get(tab)) {
            if (patch.getRegion() != null && 
                patch.getRegion().getName() != null && 
                patch.getRegion().getName().contains("Farming Guild")) {
                
                // Special check for CACTUS to avoid getting Hespori patch
                if (produce.getPatchImplementation() == PatchImplementation.CACTUS) {
                    // Make sure we get the Cactus patch, not Hespori
                    if (patch.getName() != null && patch.getName().toLowerCase().contains("cactus")) {
                        log.info("findFarmingPatch: Found Cactus patch");
                        return patch;
                    }
                    // Skip non-cactus patches in SPECIAL tab
                    continue;
                }
                
                log.info("findFarmingPatch: Found {} patch", produce.getPatchImplementation());
                return patch;
            }
        }
        
        log.info("findFarmingPatch: No guild patch found for {}", produce.getPatchImplementation());
        return null;
    }
    
    private Tab getTabForProduce(Produce produce) {
        if (produce == null) return null;
        
        switch (produce.getPatchImplementation()) {
            case HERB:
                return Tab.HERB;
            case TREE:
                return Tab.TREE;
            case FRUIT_TREE:
                return Tab.FRUIT_TREE;
            case FLOWER:
                return Tab.FLOWER;
            case ALLOTMENT:
                return Tab.ALLOTMENT;
            case BUSH:
                return Tab.BUSH;
            case CACTUS:
                // Cactus doesn't have a specific tab, might be under special
                return Tab.SPECIAL;
            default:
                return null;
        }
    }
    
    /**
     * Called when contract completion is detected via chat message.
     * This is called from the plugin when it detects the completion message.
     */
    public void onContractCompleted() {
        log.info("Contract completion detected via chat message");
        contractJustCompleted = true;
        
        // Clear previous contract state to ensure clean state for next contract
        lastKnownCropState = null;
        skipSeedPreparation = false;
        
        // If we're working with bushes/cacti that need full clearing after check-health
        if (currentContract != null && 
            (currentContract.getPatchImplementation() == PatchImplementation.BUSH ||
             currentContract.getPatchImplementation() == PatchImplementation.CACTUS)) {
            clearingCompletedContract = true;
            log.info("Bush/Cactus contract - will continue clearing before getting new contract");
        }
    }
}