package net.runelite.client.plugins.microbot.farmingcontract.models;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.farmingcontract.FarmingContractState;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.timetracking.farming.Produce;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe context object for managing farming contract state.
 * Replaces multiple boolean flags with a structured state container.
 */
@Slf4j
@Getter
public class ContractContext {
    
    // Core state - thread-safe
    private final AtomicReference<FarmingContractState> currentState;
    private final AtomicReference<Produce> currentContract;
    private final AtomicReference<PatchLocation> targetPatch;
    
    // Contract flow flags
    @Setter
    private volatile boolean harvestingContract = false;
    @Setter
    private volatile boolean contractJustCompleted = false;
    @Setter
    private volatile boolean clearingCompletedContract = false;
    @Setter
    private volatile boolean skipSeedPreparation = false;
    @Setter
    private volatile boolean checkHealthCompleted = false;
    
    // Current state for new state machine
    @Setter  
    private volatile ContractState simpleState = ContractState.CHECK_CONTRACT;
    
    // Downgrade tracking
    @Setter
    private volatile int downgradeAttempts = 0;
    @Setter
    private volatile String lastRequestedTier = null;
    
    // Performance tracking
    private volatile long stateChangeTimestamp = System.currentTimeMillis();
    private volatile long contractStartTime = 0;
    
    /**
     * Initialize with starting state.
     */
    public ContractContext() {
        this.currentState = new AtomicReference<>(FarmingContractState.INIT);
        this.currentContract = new AtomicReference<>(null);
        this.targetPatch = new AtomicReference<>(null);
    }
    
    /**
     * Thread-safe state transition.
     * @param newState The new state to transition to
     * @return true if transition was successful
     */
    public boolean transitionTo(FarmingContractState newState) {
        FarmingContractState oldState = currentState.get();
        
        if (isValidTransition(oldState, newState)) {
            currentState.set(newState);
            stateChangeTimestamp = System.currentTimeMillis();
            
            log.info("State transition: {} -> {}", oldState, newState);
            
            // Handle state-specific logic
            onStateTransition(oldState, newState);
            
            return true;
        } else {
            log.warn("Invalid state transition attempted: {} -> {}", oldState, newState);
            return false;
        }
    }
    
    /**
     * Get current state safely.
     */
    public FarmingContractState getState() {
        return currentState.get();
    }
    
    /**
     * Set current state for simple state machine.
     */
    public void setCurrentState(ContractState state) {
        this.simpleState = state;
    }
    
    /**
     * Get current simple state.
     */
    public ContractState getCurrentState() {
        return simpleState;
    }
    
    /**
     * Set current contract safely.
     */
    public void setContract(Produce contract) {
        currentContract.set(contract);
        if (contract != null) {
            contractStartTime = System.currentTimeMillis();
            log.info("Contract set: {}", contract.getName());
        }
    }
    
    /**
     * Get current contract safely.
     */
    public Produce getContract() {
        return currentContract.get();
    }
    
    /**
     * Clear current contract.
     */
    public void clearContract() {
        currentContract.set(null);
        targetPatch.set(null);
        contractStartTime = 0;
        resetFlags();
        log.info("Contract cleared");
    }
    
    /**
     * Set target patch location.
     */
    public void setTargetPatch(PatchLocation patch) {
        targetPatch.set(patch);
        if (patch != null) {
            log.info("Target patch set: {}", patch.getName());
        }
    }
    
    /**
     * Get target patch location.
     */
    public PatchLocation getTargetPatch() {
        return targetPatch.get();
    }
    
    /**
     * Reset all flow flags.
     */
    public void resetFlags() {
        harvestingContract = false;
        contractJustCompleted = false;
        clearingCompletedContract = false;
        skipSeedPreparation = false;
        downgradeAttempts = 0;
        log.debug("All flags reset");
    }
    
    /**
     * Check if we have an active contract.
     */
    public boolean hasContract() {
        return currentContract.get() != null;
    }
    
    /**
     * Check if we're in a farming state.
     */
    public boolean isFarming() {
        FarmingContractState state = currentState.get();
        return state == FarmingContractState.FARM || 
               state == FarmingContractState.CHECK_PATCH ||
               state == FarmingContractState.PREPARE;
    }
    
    /**
     * Check if we're ready to get a new contract.
     */
    public boolean needsNewContract() {
        return !hasContract() && 
               (currentState.get() == FarmingContractState.CHECK_CONTRACT ||
                currentState.get() == FarmingContractState.GET_CONTRACT);
    }
    
    /**
     * Get time since last state change in milliseconds.
     */
    public long getTimeSinceStateChange() {
        return System.currentTimeMillis() - stateChangeTimestamp;
    }
    
    /**
     * Get contract duration in milliseconds.
     */
    public long getContractDuration() {
        if (contractStartTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - contractStartTime;
    }
    
    /**
     * Check if state transition is valid.
     */
    private boolean isValidTransition(FarmingContractState from, FarmingContractState to) {
        // Allow any transition from INIT
        if (from == FarmingContractState.INIT) {
            return true;
        }
        
        // Define valid transitions
        switch (from) {
            case CHECK_CONTRACT:
                return to == FarmingContractState.GET_CONTRACT || 
                       to == FarmingContractState.CHECK_PATCH ||
                       to == FarmingContractState.COMPLETE;
                
            case CHECK_PATCH:
                return to == FarmingContractState.PREPARE || 
                       to == FarmingContractState.FARM ||
                       to == FarmingContractState.COMPLETE ||
                       to == FarmingContractState.CHECK_CONTRACT;
                
            case GET_CONTRACT:
                return to == FarmingContractState.CHECK_PATCH || 
                       to == FarmingContractState.CHECK_CONTRACT ||
                       to == FarmingContractState.REQUEST_EASIER;
                
            case PREPARE:
                return to == FarmingContractState.FARM || 
                       to == FarmingContractState.REQUEST_EASIER ||
                       to == FarmingContractState.CHECK_CONTRACT;
                
            case FARM:
                return to == FarmingContractState.COMPLETE || 
                       to == FarmingContractState.CHECK_CONTRACT ||
                       to == FarmingContractState.PREPARE;
                
            case COMPLETE:
                return to == FarmingContractState.CHECK_CONTRACT || 
                       to == FarmingContractState.GET_CONTRACT;
                
            case REQUEST_EASIER:
                return to == FarmingContractState.GET_CONTRACT || 
                       to == FarmingContractState.CHECK_CONTRACT;
                
            default:
                return false;
        }
    }
    
    /**
     * Handle state-specific logic on transition.
     */
    private void onStateTransition(FarmingContractState from, FarmingContractState to) {
        // Reset certain flags on state changes
        if (to == FarmingContractState.CHECK_CONTRACT) {
            // Starting fresh check
            if (!clearingCompletedContract) {
                resetFlags();
            }
        }
        
        if (to == FarmingContractState.GET_CONTRACT) {
            // Getting new contract
            clearContract();
        }
        
        if (to == FarmingContractState.COMPLETE) {
            // Contract completed
            contractJustCompleted = true;
        }
    }
    
    @Override
    public String toString() {
        return String.format("ContractContext[state=%s, contract=%s, patch=%s, harvesting=%s, completed=%s]",
                currentState.get(),
                currentContract.get() != null ? currentContract.get().getName() : "none",
                targetPatch.get() != null ? targetPatch.get().getName() : "none",
                harvestingContract,
                contractJustCompleted);
    }
}