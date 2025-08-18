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
 * Handler for bush patch operations.
 * Bushes require check-health to complete contract, then harvest and clear.
 */
@Slf4j
public class BushPatchHandler extends PatchHandler {
    
    private boolean checkHealthCompleted = false;
    
    public BushPatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        super(patchLocation, stateDetector);
    }
    
    @Override
    public boolean canHandle(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.BUSH;
    }
    
    @Override
    public PatchImplementation getPatchType() {
        return PatchImplementation.BUSH;
    }
    
    @Override
    protected boolean performPlanting(TileObject patch, int seedId, Produce produce) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SEED_DIBBER)) {
            log.error("No seed dibber for planting bush");
            return false;
        }
        
        log.info("Planting bush seed: {}", produce.getName());
        
        // Use seed on patch
        Rs2Inventory.interact(seedId, "Use");
        sleepUntil(() -> Rs2Inventory.isItemSelected(), 2000);
        
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Inventory.contains(seedId), 5000);
        
        return !Rs2Inventory.contains(seedId);
    }
    
    @Override
    protected boolean performCheckHealth(TileObject patch) {
        log.info("Performing check-health on bush");
        
        Rs2GameObject.interact(patch, "Check-health");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        checkHealthCompleted = true;
        log.info("Check-health completed - contract should be complete");
        
        return true;
    }
    
    @Override
    protected boolean performHarvesting(TileObject patch) {
        // Bushes need check-health first if not done
        if (!checkHealthCompleted) {
            log.info("Bush needs check-health before harvesting");
            performCheckHealth(patch);
            // Don't return here - continue to harvest after check-health!
        }
        
        // Spade is MANDATORY for clearing after harvest
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for bush clearing after harvest - this is required!");
            return false;
        }
        
        // Now actually harvest the berries
        log.info("Harvesting bush berries");
        
        // Keep picking until no more berries (check for "Pick" action)
        int attempts = 0;
        while (attempts < 10) {
            // Check if Pick action still exists
            if (!Rs2GameObject.hasAction(patch, "Pick")) {
                log.info("No more berries to pick");
                break;
            }
            
            Rs2GameObject.interact(patch, "Pick");
            sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
            attempts++;
            
            // Check if inventory is full
            if (Rs2Inventory.isFull()) {
                log.info("Inventory full while harvesting bush");
                break;
            }
        }
        
        log.info("Bush harvesting complete after {} attempts", attempts);
        return true;
    }
    
    @Override
    protected boolean performClearing(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for clearing bush");
            return false;
        }
        
        log.info("Clearing bush patch");
        
        // First pick any remaining berries
        while (Rs2GameObject.hasAction(patch, "Pick")) {
            log.info("Picking remaining berries before clearing");
            Rs2GameObject.interact(patch, "Pick");
            sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
            
            if (Rs2Inventory.isFull()) {
                log.warn("Inventory full, cannot pick more berries");
                break;
            }
        }
        
        // Now clear the empty bush
        log.info("Clearing empty bush");
        Rs2GameObject.interact(patch, "Clear");
        sleepUntil(() -> !Rs2Player.isAnimating(), 10000);
        
        // Reset check-health flag for next contract
        checkHealthCompleted = false;
        
        return true;
    }
    
    
    /**
     * Check if bush has been checked for health.
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