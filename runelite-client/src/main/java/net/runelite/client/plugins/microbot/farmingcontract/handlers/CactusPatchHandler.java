package net.runelite.client.plugins.microbot.farmingcontract.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.farmingcontract.data.FarmingContractData;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.microbot.farmingcontract.managers.PatchStateDetector;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Handler for cactus patch operations.
 * Cacti require check-health, then pick-spine harvesting, then clearing.
 */
@Slf4j
public class CactusPatchHandler extends PatchHandler {
    
    private boolean checkHealthCompleted = false;
    
    public CactusPatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        super(patchLocation, stateDetector);
    }
    
    @Override
    public boolean canHandle(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.CACTUS;
    }
    
    @Override
    public PatchImplementation getPatchType() {
        return PatchImplementation.CACTUS;
    }
    
    @Override
    protected boolean performPlanting(TileObject patch, int seedId, Produce produce) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SEED_DIBBER)) {
            log.error("No seed dibber for planting cactus");
            return false;
        }
        
        log.info("Planting cactus seed: {}", produce.getName());
        
        // Use seed on patch
        Rs2Inventory.interact(seedId, "Use");
        sleepUntil(() -> Rs2Inventory.isItemSelected(), 2000);
        
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Inventory.contains(seedId), 5000);
        
        return !Rs2Inventory.contains(seedId);
    }
    
    @Override
    protected boolean performCheckHealth(TileObject patch) {
        log.info("Performing check-health on cactus");
        
        Rs2GameObject.interact(patch, "Check-health");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        checkHealthCompleted = true;
        log.info("Check-health completed - contract should be complete");
        
        return true;
    }
    
    @Override
    protected boolean performHarvesting(TileObject patch) {
        // Cacti need check-health first if not done
        if (!checkHealthCompleted) {
            log.info("Cactus needs check-health before harvesting");
            return performCheckHealth(patch);
        }
        
        // Spade is MANDATORY for clearing after harvest
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for cactus clearing after harvest - this is required!");
            return false;
        }
        
        // Harvest cactus spines
        String action = getHarvestAction(patch);
        if (action == null) {
            log.warn("No harvest action available for cactus");
            return false;
        }
        
        log.info("Harvesting cactus spines with action: {}", action);
        
        // Keep harvesting until no more spines
        int attempts = 0;
        while (attempts < 10) {
            Rs2GameObject.interact(patch, action);
            sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
            attempts++;
            
            // Check if inventory is full
            if (Rs2Inventory.isFull()) {
                log.info("Inventory full while harvesting cactus");
                break;
            }
        }
        
        log.info("Cactus harvesting complete after {} attempts", attempts);
        return true;
    }
    
    @Override
    protected boolean performClearing(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for clearing cactus");
            return false;
        }
        
        log.info("Clearing cactus patch");
        
        // Default clearing action
        String action = "Clear";
        
        log.info("Using action '{}' to clear cactus", action);
        Rs2GameObject.interact(patch, action);
        sleepUntil(() -> {
            TileObject currentPatch = findPatchObject();
            return currentPatch == null || !Rs2Player.isAnimating();
        }, 10000);
        
        // Reset check-health flag for next contract
        checkHealthCompleted = false;
        
        return true;
    }
    
    /**
     * Get the harvest action for the cactus.
     */
    private String getHarvestAction(TileObject patch) {
        // Cacti use "Pick-spine" action
        return "Pick-spine";
    }
    
    /**
     * Check if cactus has been checked for health.
     */
    public boolean isCheckHealthCompleted() {
        return checkHealthCompleted;
    }
    
    /**
     * Reset the check-health flag.
     */
    public void resetCheckHealthFlag() {
        checkHealthCompleted = false;
    }
}