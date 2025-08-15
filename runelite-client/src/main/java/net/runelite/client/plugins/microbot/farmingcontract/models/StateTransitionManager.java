package net.runelite.client.plugins.microbot.farmingcontract.models;

import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages valid state transitions for the farming contract state machine.
 * Ensures contract flow follows logical progression.
 */
@Slf4j
public class StateTransitionManager {
    
    private final Map<ContractState, Set<ContractState>> validTransitions;
    
    public StateTransitionManager() {
        this.validTransitions = new HashMap<>();
        initializeTransitions();
    }
    
    /**
     * Initialize valid state transitions.
     */
    private void initializeTransitions() {
        // CHECK_CONTRACT can transition to multiple states based on current situation
        addTransitions(ContractState.CHECK_CONTRACT,
            ContractState.TALK_TO_JANE,    // No contract
            ContractState.GET_SEEDS,        // Have contract, need seeds
            ContractState.GO_TO_PATCH,      // Have everything, go to patch
            ContractState.TURN_IN_CONTRACT, // Contract complete
            ContractState.ERROR);
            
        // TALK_TO_JANE transitions
        addTransitions(ContractState.TALK_TO_JANE,
            ContractState.GET_SEEDS,        // Got contract
            ContractState.CHECK_CONTRACT,   // Check again
            ContractState.ERROR);
            
        // GET_SEEDS transitions
        addTransitions(ContractState.GET_SEEDS,
            ContractState.GET_TOOLS,        // Need tools too
            ContractState.GO_TO_PATCH,      // Have everything
            ContractState.TALK_TO_JANE,     // Request easier contract
            ContractState.ERROR);
            
        // GET_TOOLS transitions
        addTransitions(ContractState.GET_TOOLS,
            ContractState.GO_TO_PATCH,      // Ready to go
            ContractState.ERROR);
            
        // GO_TO_PATCH transitions
        addTransitions(ContractState.GO_TO_PATCH,
            ContractState.PLANT_SEEDS,      // Empty patch
            ContractState.CHECK_HEALTH,     // Grown, needs check
            ContractState.HARVEST,          // Ready to harvest
            ContractState.CLEAR_PATCH,      // Needs clearing
            ContractState.WAIT_FOR_GROWTH,  // Still growing
            ContractState.ERROR);
            
        // PLANT_SEEDS transitions
        addTransitions(ContractState.PLANT_SEEDS,
            ContractState.APPLY_COMPOST,    // Apply compost after planting
            ContractState.WAIT_FOR_GROWTH,  // Wait for growth
            ContractState.COMPLETE,         // Stop after planting if configured
            ContractState.ERROR);
            
        // APPLY_COMPOST transitions
        addTransitions(ContractState.APPLY_COMPOST,
            ContractState.WAIT_FOR_GROWTH,  // Wait for growth
            ContractState.COMPLETE,         // Stop after composting
            ContractState.ERROR);
            
        // WAIT_FOR_GROWTH transitions
        addTransitions(ContractState.WAIT_FOR_GROWTH,
            ContractState.GO_TO_PATCH,      // Check patch again
            ContractState.CHECK_HEALTH,     // Ready for check-health
            ContractState.HARVEST,          // Ready to harvest
            ContractState.COMPLETE,         // Stop while growing
            ContractState.ERROR);
            
        // CHECK_HEALTH transitions
        addTransitions(ContractState.CHECK_HEALTH,
            ContractState.HARVEST,          // Harvest after check
            ContractState.TURN_IN_CONTRACT, // Contract complete
            ContractState.ERROR);
            
        // HARVEST transitions
        addTransitions(ContractState.HARVEST,
            ContractState.CLEAR_PATCH,      // Clear after harvest
            ContractState.TURN_IN_CONTRACT, // Contract complete
            ContractState.ERROR);
            
        // CLEAR_PATCH transitions
        addTransitions(ContractState.CLEAR_PATCH,
            ContractState.GO_TO_PATCH,      // Check patch again
            ContractState.TURN_IN_CONTRACT, // Contract complete
            ContractState.CHECK_CONTRACT,   // Re-check contract
            ContractState.ERROR);
            
        // TURN_IN_CONTRACT transitions
        addTransitions(ContractState.TURN_IN_CONTRACT,
            ContractState.COMPLETE,         // Done
            ContractState.TALK_TO_JANE,     // Get new contract
            ContractState.CHECK_CONTRACT,   // Check status
            ContractState.ERROR);
            
        // Terminal states have no transitions
        addTransitions(ContractState.COMPLETE);
        addTransitions(ContractState.ERROR);
        addTransitions(ContractState.STOPPED);
    }
    
    /**
     * Add valid transitions from a state.
     */
    private void addTransitions(ContractState from, ContractState... to) {
        Set<ContractState> transitions = new HashSet<>();
        for (ContractState state : to) {
            transitions.add(state);
        }
        validTransitions.put(from, transitions);
    }
    
    /**
     * Check if a transition is valid.
     */
    public boolean canTransition(ContractState from, ContractState to) {
        if (from == null || to == null) {
            return false;
        }
        
        Set<ContractState> valid = validTransitions.get(from);
        if (valid == null) {
            log.warn("No transitions defined for state: {}", from);
            return false;
        }
        
        return valid.contains(to);
    }
    
    /**
     * Get all valid transitions from a state.
     */
    public Set<ContractState> getValidTransitions(ContractState from) {
        return validTransitions.getOrDefault(from, new HashSet<>());
    }
    
    /**
     * Validate a transition and log if invalid.
     */
    public boolean validateTransition(ContractState from, ContractState to) {
        boolean valid = canTransition(from, to);
        
        if (!valid) {
            log.error("Invalid state transition attempted: {} -> {}", from, to);
            log.info("Valid transitions from {}: {}", from, getValidTransitions(from));
        }
        
        return valid;
    }
}