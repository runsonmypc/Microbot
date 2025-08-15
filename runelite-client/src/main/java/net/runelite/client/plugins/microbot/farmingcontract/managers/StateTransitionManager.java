package net.runelite.client.plugins.microbot.farmingcontract.managers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.farmingcontract.FarmingContractState;
import net.runelite.client.plugins.microbot.farmingcontract.models.ContractContext;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Manages state transitions for farming contracts.
 * Centralizes all state change logic with proper validation and logging.
 */
@Slf4j
public class StateTransitionManager {
    
    private final ContractContext context;
    private final ContractManager contractManager;
    private final PatchStateDetector stateDetector;
    
    // Timeout constants (milliseconds)
    private static final long STATE_TIMEOUT = 300000; // 5 minutes
    private static final long STUCK_THRESHOLD = 600000; // 10 minutes
    
    public StateTransitionManager(ContractContext context, 
                                 ContractManager contractManager,
                                 PatchStateDetector stateDetector) {
        this.context = context;
        this.contractManager = contractManager;
        this.stateDetector = stateDetector;
    }
    
    /**
     * Process the current state and determine next action.
     * @return Next recommended state or null if no change needed
     */
    public FarmingContractState processCurrentState() {
        FarmingContractState currentState = context.getState();
        
        // Check for stuck states
        if (isStuck()) {
            log.warn("Detected stuck state: {} for {} ms", 
                    currentState, context.getTimeSinceStateChange());
            return handleStuckState();
        }
        
        // Process based on current state
        switch (currentState) {
            case INIT:
                return processInit();
                
            case CHECK_CONTRACT:
                return processCheckContract();
                
            case CHECK_PATCH:
                return processCheckPatch();
                
            case GET_CONTRACT:
                return processGetContract();
                
            case PREPARE:
                return processPrepare();
                
            case FARM:
                return processFarm();
                
            case COMPLETE:
                return processComplete();
                
            case REQUEST_EASIER:
                return processRequestEasier();
                
            default:
                log.error("Unknown state: {}", currentState);
                return FarmingContractState.INIT;
        }
    }
    
    /**
     * Transition to a new state with validation.
     * @param newState The target state
     * @param reason Description of why the transition is happening
     * @return true if transition was successful
     */
    public boolean transitionTo(FarmingContractState newState, String reason) {
        FarmingContractState oldState = context.getState();
        
        log.info("State transition requested: {} -> {} (Reason: {})", 
                oldState, newState, reason);
        
        if (context.transitionTo(newState)) {
            // Log successful transition
            logStateTransition(oldState, newState, reason);
            return true;
        } else {
            log.error("Failed to transition from {} to {}: {}", 
                    oldState, newState, reason);
            return false;
        }
    }
    
    // State processing methods
    
    private FarmingContractState processInit() {
        log.info("Processing INIT state");
        return FarmingContractState.CHECK_CONTRACT;
    }
    
    private FarmingContractState processCheckContract() {
        // Check if we have a contract
        if (context.hasContract()) {
            log.info("Contract exists: {}", context.getContract().getName());
            
            // If we just completed a contract and are clearing, stay in current flow
            if (context.isClearingCompletedContract()) {
                return FarmingContractState.FARM;
            }
            
            return FarmingContractState.CHECK_PATCH;
        } else {
            log.info("No contract found, need to get one");
            return FarmingContractState.GET_CONTRACT;
        }
    }
    
    private FarmingContractState processCheckPatch() {
        Produce contract = context.getContract();
        PatchLocation patch = context.getTargetPatch();
        
        if (contract == null || patch == null) {
            log.warn("Missing contract or patch location");
            return FarmingContractState.CHECK_CONTRACT;
        }
        
        // Check patch state
        PatchStateDetector.PatchState patchState = stateDetector.detectPatchState(contract, patch);
        
        if (patchState.needsPlanting || patchState.needsClearing || patchState.needsCuring) {
            log.info("Patch needs preparation: {}", patchState.description);
            context.setSkipSeedPreparation(false);
            return FarmingContractState.PREPARE;
        }
        
        if (patchState.needsHarvesting || patchState.needsChecking) {
            log.info("Patch ready for action: {}", patchState.description);
            context.setSkipSeedPreparation(true);
            context.setHarvestingContract(true);
            return FarmingContractState.PREPARE;
        }
        
        if (patchState.isGrowing) {
            log.info("Patch is growing - contract in progress");
            // Could implement waiting logic here
            return null; // Stay in current state
        }
        
        log.warn("Unknown patch state: {}", patchState.description);
        return FarmingContractState.CHECK_CONTRACT;
    }
    
    private FarmingContractState processGetContract() {
        // This state should result in getting a contract
        // If we still don't have one after processing, something went wrong
        if (!context.hasContract()) {
            log.warn("Failed to get contract");
            
            if (context.getDowngradeAttempts() > 2) {
                log.error("Too many downgrade attempts, stopping");
                return null; // Will trigger stuck handler
            }
            
            return FarmingContractState.CHECK_CONTRACT;
        }
        
        return FarmingContractState.CHECK_PATCH;
    }
    
    private FarmingContractState processPrepare() {
        // After preparation, we should be ready to farm
        return FarmingContractState.FARM;
    }
    
    private FarmingContractState processFarm() {
        // Check if contract was completed
        if (context.isContractJustCompleted()) {
            log.info("Contract completed, moving to completion state");
            return FarmingContractState.COMPLETE;
        }
        
        // Check if we need to go back to preparation
        // (e.g., ran out of supplies)
        
        return null; // Stay in farming state
    }
    
    private FarmingContractState processComplete() {
        // After turning in contract, get a new one
        context.clearContract();
        return FarmingContractState.CHECK_CONTRACT;
    }
    
    private FarmingContractState processRequestEasier() {
        // After requesting easier contract, try to get it
        context.setDowngradeAttempts(context.getDowngradeAttempts() + 1);
        return FarmingContractState.GET_CONTRACT;
    }
    
    // Helper methods
    
    /**
     * Check if we're stuck in a state too long.
     */
    private boolean isStuck() {
        long timeInState = context.getTimeSinceStateChange();
        FarmingContractState state = context.getState();
        
        // Some states are expected to take longer
        if (state == FarmingContractState.FARM) {
            return timeInState > STUCK_THRESHOLD;
        }
        
        return timeInState > STATE_TIMEOUT;
    }
    
    /**
     * Handle stuck states by resetting or transitioning.
     */
    private FarmingContractState handleStuckState() {
        FarmingContractState currentState = context.getState();
        
        log.warn("Handling stuck state: {}", currentState);
        
        // Reset to a safe state
        context.resetFlags();
        
        // Determine recovery action based on current state
        switch (currentState) {
            case FARM:
            case PREPARE:
                // Maybe we completed but didn't detect it
                if (context.hasContract()) {
                    return FarmingContractState.CHECK_PATCH;
                }
                break;
                
            case GET_CONTRACT:
            case REQUEST_EASIER:
                // Failed to get contract
                return FarmingContractState.CHECK_CONTRACT;
                
            default:
                break;
        }
        
        // Default recovery - start over
        return FarmingContractState.INIT;
    }
    
    /**
     * Log state transition for debugging.
     */
    private void logStateTransition(FarmingContractState from, FarmingContractState to, String reason) {
        log.info("=== State Transition ===");
        log.info("From: {}", from);
        log.info("To: {}", to);
        log.info("Reason: {}", reason);
        log.info("Contract: {}", context.getContract() != null ? context.getContract().getName() : "None");
        log.info("Flags: harvesting={}, completed={}, clearing={}, skipSeeds={}", 
                context.isHarvestingContract(),
                context.isContractJustCompleted(), 
                context.isClearingCompletedContract(),
                context.isSkipSeedPreparation());
        log.info("=======================");
    }
    
    /**
     * Force reset to initial state.
     */
    public void reset() {
        log.info("Forcing state reset");
        context.clearContract();
        context.resetFlags();
        context.transitionTo(FarmingContractState.INIT);
    }
}