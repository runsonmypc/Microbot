package net.runelite.client.plugins.microbot.farmingcontract;

public enum FarmingContractState {
    INIT,               // Initial setup
    CHECK_CONTRACT,     // Check if we have a contract
    CHECK_PATCH,        // Check if patch is already grown/harvestable
    GET_CONTRACT,       // Get a new contract from Jane
    PREPARE,           // Bank for seeds and tools
    FARM,              // Handle the farming patch
    COMPLETE,          // Turn in completed contract
    REQUEST_EASIER     // Request an easier contract from Jane when seeds unavailable
}