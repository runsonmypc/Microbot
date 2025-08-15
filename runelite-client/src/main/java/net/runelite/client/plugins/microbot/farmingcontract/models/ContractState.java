package net.runelite.client.plugins.microbot.farmingcontract.models;

/**
 * States for the farming contract state machine.
 */
public enum ContractState {
    // Initial states
    CHECK_CONTRACT("Checking contract status"),
    TALK_TO_JANE("Talking to Jane for contract"),
    
    // Preparation states
    GET_SEEDS("Getting seeds from bank"),
    GET_TOOLS("Getting tools from bank"),
    
    // Farming states
    GO_TO_PATCH("Going to farming patch"),
    PLANT_SEEDS("Planting seeds"),
    APPLY_COMPOST("Applying compost"),
    WAIT_FOR_GROWTH("Waiting for crops to grow"),
    
    // Completion states
    CHECK_HEALTH("Checking health of grown crops"),
    HARVEST("Harvesting crops"),
    CLEAR_PATCH("Clearing patch"),
    
    // Turn-in states
    TURN_IN_CONTRACT("Turning in completed contract"),
    
    // Final states
    COMPLETE("Contract completed"),
    ERROR("Error occurred"),
    STOPPED("Plugin stopped");
    
    private final String description;
    
    ContractState(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this is a terminal state.
     */
    public boolean isTerminal() {
        return this == COMPLETE || this == ERROR || this == STOPPED;
    }
    
    /**
     * Check if this is a farming action state.
     */
    public boolean isFarmingAction() {
        return this == PLANT_SEEDS || this == CHECK_HEALTH || 
               this == HARVEST || this == CLEAR_PATCH;
    }
}