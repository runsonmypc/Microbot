package net.runelite.client.plugins.microbot.farmingcontract;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.farmingcontract.data.FarmingContractData;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.microbot.farmingcontract.handlers.*;
import net.runelite.client.plugins.microbot.farmingcontract.managers.*;
import net.runelite.client.plugins.microbot.farmingcontract.models.ContractContext;
import net.runelite.client.plugins.microbot.farmingcontract.models.ContractState;
import net.runelite.client.plugins.microbot.farmingcontract.models.StateTransitionManager;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class FarmingContractScript extends Script {
    
    // Core managers
    private ContractManager contractManager;
    private BankingManager bankingManager;
    private PatchStateDetector stateDetector;
    private StateTransitionManager stateManager;
    private ContractContext context;
    
    // Patch handlers
    private Map<PatchLocation, PatchHandler> patchHandlers;
    
    // Plugin reference
    private FarmingContractPlugin plugin;
    private FarmingContractConfig config;
    
    // State management
    private AtomicReference<ContractState> currentState;
    private boolean clearingCompletedContract = false;
    
    // Constants
    private static final WorldPoint JANE_LOCATION = new WorldPoint(1248, 3727, 0);
    private static final int MAX_RETRIES = 3;
    
    public FarmingContractScript(FarmingContractPlugin plugin, FarmingContractConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.currentState = new AtomicReference<>(ContractState.CHECK_CONTRACT);
    }
    
    @Override
    public boolean run() {
        // Initialize managers and handlers
        initialize();
        
        // Schedule the main loop to run asynchronously
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            
            try {
                ContractState state = currentState.get();
                log.debug("Current state: {}", state);
                plugin.setStatus(state.getDescription());
                
                switch (state) {
                    case CHECK_CONTRACT:
                        handleCheckContract();
                        break;
                        
                    case TALK_TO_JANE:
                        handleTalkToJane();
                        break;
                        
                    case GET_SEEDS:
                        handleGetSeeds();
                        break;
                        
                    case GO_TO_PATCH:
                        handleGoToPatch();
                        break;
                        
                    case PLANT_SEEDS:
                        handlePlantSeeds();
                        break;
                        
                    case WAIT_FOR_GROWTH:
                        handleWaitForGrowth();
                        break;
                        
                    case CHECK_HEALTH:
                        handleCheckHealth();
                        break;
                        
                    case HARVEST:
                        handleHarvest();
                        break;
                        
                    case CLEAR_PATCH:
                        handleClearPatch();
                        break;
                        
                    case TURN_IN_CONTRACT:
                        handleTurnInContract();
                        break;
                        
                    case COMPLETE:
                        handleComplete();
                        break;
                        
                    case ERROR:
                        handleError();
                        break;
                        
                    default:
                        log.warn("Unknown state: {}", state);
                }
                
            } catch (Exception e) {
                log.error("Error in main loop: ", e);
                currentState.set(ContractState.ERROR);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    /**
     * Initialize all managers and handlers.
     */
    private void initialize() {
        log.info("Initializing Farming Contract Script");
        
        // Create managers
        contractManager = new ContractManager();
        bankingManager = new BankingManager(config);
        stateDetector = new PatchStateDetector(null); // FarmingWorld not accessible from outside package
        stateManager = new StateTransitionManager();
        context = new ContractContext();
        
        // Initialize patch handlers (will be created on demand)
        patchHandlers = new HashMap<>();
        
        log.info("Initialization complete with {} patch handlers", patchHandlers.size());
    }
    
    /**
     * Check if we have a contract.
     */
    private void handleCheckContract() {
        Produce contract = contractManager.getCurrentContract();
        
        if (contract != null) {
            log.info("Contract already active: {}", contract.getName());
            
            // Determine next state based on patch state
            PatchLocation location = FarmingContractData.getPatchLocation(contract);
            if (location != null) {
                PatchStateDetector.PatchState patchState = stateDetector.detectPatchState(contract, location);
                log.info("Patch state: {}", patchState.description);
                
                // Choose action based on actual patch state
                if (patchState.needsChecking) {
                    log.info("Patch needs check-health for contract");
                    transitionTo(ContractState.CHECK_HEALTH);
                } else if (patchState.needsHarvesting) {
                    transitionTo(ContractState.HARVEST);
                } else if (patchState.needsClearing) {
                    transitionTo(ContractState.CLEAR_PATCH);
                } else if (patchState.needsPlanting) {
                    transitionTo(ContractState.GET_SEEDS);
                } else if (patchState.isGrowing) {
                    transitionTo(ContractState.WAIT_FOR_GROWTH);
                } else {
                    transitionTo(ContractState.GO_TO_PATCH);
                }
            }
        } else {
            log.info("No active contract, need to talk to Jane");
            transitionTo(ContractState.TALK_TO_JANE);
        }
    }
    
    /**
     * Talk to Jane to get a contract.
     */
    private void handleTalkToJane() {
        // Walk to Jane if needed
        if (Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) > 10) {
            log.info("Walking to Jane");
            Rs2Walker.walkTo(JANE_LOCATION);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) < 10, 30000);
        }
        
        // Find and talk to Jane
        NPC jane = Rs2Npc.getNpc("Guildmaster Jane");
            
        if (jane == null) {
            log.error("Could not find Guildmaster Jane");
            // Jane not visible, might need to walk closer
            if (Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) > 5) {
                Rs2Walker.walkTo(JANE_LOCATION);
            }
            return;
        }
        
        log.info("Talking to Jane");
        // Try Contract first, then Talk-to if it fails
        if (!Rs2Npc.interact(jane, "Contract")) {
            Rs2Npc.interact(jane, "Talk-to");
        }
        
        // Wait for dialogue to open
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
        
        // Handle dialogue quickly
        while (Rs2Dialogue.isInDialogue()) {
            // Check for options first
            if (Rs2Dialogue.hasSelectAnOption()) {
                // Select tier if needed
                String tier = contractManager.getContractTier();
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
                    log.info("Jane says we already have a contract");
                    break; // Just exit, we should have the contract saved
                }
                
                // Try to parse the contract
                Produce contract = contractManager.parseContract();
                if (contract != null) {
                    log.info("Got contract: {}", contract.getName());
                    contractManager.setCurrentContract(contract);
                    break; // Exit immediately - dialogue will close when we walk away
                }
            }
            
            // Continue dialogue
            Rs2Dialogue.clickContinue();
            sleep(100); // Fast 100ms delay
        }
        
        if (contractManager.getCurrentContract() != null) {
            // Check patch state first before getting seeds
            log.info("Got contract, checking patch state first");
            transitionTo(ContractState.CHECK_CONTRACT);
        }
    }
    
    /**
     * Get seeds from bank.
     */
    private void handleGetSeeds() {
        Produce contract = contractManager.getCurrentContract();
        if (contract == null) {
            transitionTo(ContractState.CHECK_CONTRACT);
            return;
        }
        
        // First check the actual patch state - we might not need seeds!
        PatchLocation location = FarmingContractData.getPatchLocation(contract);
        if (location != null) {
            PatchStateDetector.PatchState patchState = stateDetector.detectPatchState(contract, location);
            
            // If patch needs check-health, we don't need seeds, just tools
            if (patchState.needsChecking) {
                log.info("Patch needs check-health, getting tools only (no seeds needed)");
                BankingManager.BankingResult result = bankingManager.prepareForCheckHealth(contract);
                if (result.success) {
                    transitionTo(ContractState.GO_TO_PATCH);
                } else {
                    log.error("Failed to get tools from bank");
                    transitionTo(ContractState.ERROR);
                }
                return;
            }
        }
        
        int seedId = contractManager.getCurrentContractSeedId();
        int seedsRequired = contractManager.getSeedsRequired();
        
        // Check if we already have seeds
        if (Rs2Inventory.count(seedId) >= seedsRequired) {
            log.info("Already have required seeds");
            transitionTo(ContractState.GO_TO_PATCH);
            return;
        }
        
        // Bank for seeds and tools
        BankingManager.BankingResult result = bankingManager.prepareForPlanting(contract);
        if (!result.success) {
            log.error("Failed to get items from bank");
            
            // Try requesting easier contract
            String currentTier = contractManager.getContractTier();
            String newTier = contractManager.requestEasierContract(currentTier);
            
            if (newTier != null) {
                log.info("Requesting easier contract tier: {}", newTier);
                transitionTo(ContractState.TALK_TO_JANE);
            } else {
                transitionTo(ContractState.ERROR);
            }
            return;
        }
        
        transitionTo(ContractState.GO_TO_PATCH);
    }
    
    /**
     * Go to the farming patch.
     */
    private void handleGoToPatch() {
        Produce contract = contractManager.getCurrentContract();
        if (contract == null) {
            transitionTo(ContractState.CHECK_CONTRACT);
            return;
        }
        
        PatchLocation location = FarmingContractData.getPatchLocation(contract);
        if (location == null) {
            log.error("No patch location for contract: {}", contract.getName());
            transitionTo(ContractState.ERROR);
            return;
        }
        
        // Walk to patch
        if (Rs2Player.getWorldLocation().distanceTo(location.getLocation()) > 20) {
            log.info("Walking to {} patch", location.getName());
            Rs2Walker.walkTo(location.getLocation());
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(location.getLocation()) < 20, 30000);
        }
        
        // Find patch object
        TileObject patch = Rs2GameObject.getAll(o -> o.getWorldLocation().equals(location.getLocation()))
            .stream().findFirst().orElse(null);
        if (patch == null) {
            log.error("Could not find patch at location");
            sleep(2000);
            return;
        }
        
        // Determine patch state and next action
        PatchStateDetector.PatchState patchState = stateDetector.detectPatchState(contract, location);
        
        if (patchState.needsPlanting) {
            transitionTo(ContractState.PLANT_SEEDS);
        } else if (patchState.needsChecking) {
            transitionTo(ContractState.CHECK_HEALTH);
        } else if (patchState.needsHarvesting) {
            transitionTo(ContractState.HARVEST);
        } else if (patchState.needsClearing) {
            transitionTo(ContractState.CLEAR_PATCH);
        } else if (patchState.isGrowing) {
            transitionTo(ContractState.WAIT_FOR_GROWTH);
        } else {
            log.warn("Unknown patch state: {}", patchState);
            sleep(2000);
        }
    }
    
    /**
     * Plant seeds in the patch.
     */
    private void handlePlantSeeds() {
        Produce contract = contractManager.getCurrentContract();
        PatchLocation location = FarmingContractData.getPatchLocation(contract);
        
        // Create handler if it doesn't exist
        PatchHandler handler = patchHandlers.get(location);
        if (handler == null) {
            handler = createHandlerForLocation(location);
            if (handler == null) {
                log.error("Could not create handler for patch location");
                transitionTo(ContractState.ERROR);
                return;
            }
            patchHandlers.put(location, handler);
        }
        
        if (handler.plant(contract)) {
            log.info("Seeds planted successfully");
            
            // Apply compost if configured
            if (config.useCompost()) {
                String compostType = config.compostType().toString();
                handler.applyCompost(compostType);
            }
            
            // Always wait for growth after planting
            transitionTo(ContractState.WAIT_FOR_GROWTH);
        } else {
            log.error("Failed to plant seeds");
            sleep(2000);
        }
    }
    
    /**
     * Wait for crops to grow.
     */
    private void handleWaitForGrowth() {
        log.info("Crops are growing, waiting...");
        plugin.setStatus("Crops growing - waiting");
        
        // This would normally wait or stop the plugin
        // For now, we'll just transition to check the patch again
        sleep(5000);
        transitionTo(ContractState.GO_TO_PATCH);
    }
    
    /**
     * Check health of grown crops.
     */
    private void handleCheckHealth() {
        Produce contract = contractManager.getCurrentContract();
        PatchLocation location = FarmingContractData.getPatchLocation(contract);
        
        // For bushes and cacti, check-health completes the contract but we need to continue clearing
        PatchImplementation type = location.getType();
        boolean isBushOrCactus = (type == PatchImplementation.BUSH || type == PatchImplementation.CACTUS);
        
        // Create handler if it doesn't exist
        PatchHandler handler = patchHandlers.get(location);
        if (handler == null) {
            handler = createHandlerForLocation(location);
            if (handler == null) {
                log.error("Could not create handler for patch location");
                transitionTo(ContractState.ERROR);
                return;
            }
            patchHandlers.put(location, handler);
        }
        
        if (handler.checkHealth()) {
            log.info("Check-health completed");
            context.setCheckHealthCompleted(true);
            
            if (isBushOrCactus) {
                log.info("Bush/Cactus contract complete after check-health, but patch needs full clearing");
                // Set flag to continue clearing after contract completion
                clearingCompletedContract = true;
                // Don't go to COMPLETE - continue to harvest and clear
                transitionTo(ContractState.HARVEST);
            } else {
                // Trees need special handling for payment
                transitionTo(ContractState.HARVEST);
            }
        } else {
            log.error("Failed to check health");
            sleep(2000);
        }
    }
    
    /**
     * Harvest crops from the patch.
     */
    private void handleHarvest() {
        Produce contract = contractManager.getCurrentContract();
        PatchLocation location = FarmingContractData.getPatchLocation(contract);
        
        // Create handler if it doesn't exist
        PatchHandler handler = patchHandlers.get(location);
        if (handler == null) {
            handler = createHandlerForLocation(location);
            if (handler == null) {
                log.error("Could not create handler for patch location");
                transitionTo(ContractState.ERROR);
                return;
            }
            patchHandlers.put(location, handler);
        }
        
        if (handler.harvest()) {
            log.info("Harvest completed");
            transitionTo(ContractState.CLEAR_PATCH);
        } else {
            log.error("Failed to harvest");
            sleep(2000);
        }
    }
    
    /**
     * Clear the patch after harvesting.
     */
    private void handleClearPatch() {
        Produce contract = contractManager.getCurrentContract();
        PatchLocation location = FarmingContractData.getPatchLocation(contract);
        
        // Create handler if it doesn't exist
        PatchHandler handler = patchHandlers.get(location);
        if (handler == null) {
            handler = createHandlerForLocation(location);
            if (handler == null) {
                log.error("Could not create handler for patch location");
                transitionTo(ContractState.ERROR);
                return;
            }
            patchHandlers.put(location, handler);
        }
        
        // Pay for clearing if needed (trees)
        if (handler.requiresPayment()) {
            handler.payForClearing();
        }
        
        if (handler.clear()) {
            log.info("Patch cleared");
            
            if (contractManager.isContractJustCompleted() || clearingCompletedContract) {
                clearingCompletedContract = false;
                transitionTo(ContractState.TURN_IN_CONTRACT);
            } else {
                transitionTo(ContractState.GO_TO_PATCH);
            }
        } else {
            log.error("Failed to clear patch");
            sleep(2000);
        }
    }
    
    /**
     * Turn in completed contract to Jane.
     */
    private void handleTurnInContract() {
        log.info("Turning in completed contract");
        
        // Walk to Jane
        if (Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) > 10) {
            Rs2Walker.walkTo(JANE_LOCATION);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(JANE_LOCATION) < 10, 30000);
        }
        
        // Talk to Jane
        NPC jane = Rs2Npc.getNpc("Guildmaster Jane");
            
        if (jane != null) {
            Rs2Npc.interact(jane, "Contract");
            sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
            
            // Handle dialogue
            while (Rs2Dialogue.isInDialogue()) {
                Rs2Dialogue.clickContinue();
                sleep(600, 800);
            }
            
            contractManager.clearContract();
            transitionTo(ContractState.COMPLETE);
        }
    }
    
    /**
     * Handle completion state.
     */
    private void handleComplete() {
        log.info("Contract completed successfully!");
        plugin.setStatus("Contract complete!");
        plugin.stopPlugin();
    }
    
    /**
     * Handle error state.
     */
    private void handleError() {
        log.error("Script encountered an error");
        plugin.setStatus("Error - stopping");
        plugin.stopPlugin();
    }
    
    /**
     * Create appropriate handler for a patch location.
     */
    private PatchHandler createHandlerForLocation(PatchLocation location) {
        switch (location.getType()) {
            case HERB:
                return new HerbPatchHandler(location, stateDetector);
            case ALLOTMENT:
                return new AllotmentPatchHandler(location, stateDetector);
            case FLOWER:
                return new FlowerPatchHandler(location, stateDetector);
            case BUSH:
                return new BushPatchHandler(location, stateDetector);
            case CACTUS:
                return new CactusPatchHandler(location, stateDetector);
            case TREE:
                return new TreePatchHandler(location, stateDetector);
            default:
                log.warn("No handler for patch type: {}", location.getType());
                return null;
        }
    }
    
    /**
     * Transition to a new state.
     */
    private void transitionTo(ContractState newState) {
        ContractState oldState = currentState.get();
        if (stateManager.canTransition(oldState, newState)) {
            log.info("State transition: {} -> {}", oldState, newState);
            currentState.set(newState);
            context.setCurrentState(newState);
        } else {
            log.warn("Invalid state transition: {} -> {}", oldState, newState);
        }
    }
    
    /**
     * Handle chat messages for contract completion.
     */
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE || 
            event.getType() == ChatMessageType.SPAM) {
            
            String message = event.getMessage();
            
            if (message.contains("You've completed a farming contract")) {
                log.info("Contract completed!");
                contractManager.markCompleted();
                context.setClearingCompletedContract(true);
            }
        }
    }
    
    @Override
    public void shutdown() {
        log.info("Shutting down Farming Contract Script");
        super.shutdown();
    }
}