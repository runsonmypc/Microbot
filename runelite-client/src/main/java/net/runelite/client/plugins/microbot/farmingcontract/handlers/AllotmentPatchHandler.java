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
 * Handler for allotment patch operations.
 * Allotments require 3 seeds to plant.
 */
@Slf4j
public class AllotmentPatchHandler extends PatchHandler {
    
    public AllotmentPatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        super(patchLocation, stateDetector);
    }
    
    @Override
    public boolean canHandle(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.ALLOTMENT;
    }
    
    @Override
    public PatchImplementation getPatchType() {
        return PatchImplementation.ALLOTMENT;
    }
    
    @Override
    protected boolean performPlanting(TileObject patch, int seedId, Produce produce) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SEED_DIBBER)) {
            log.error("No seed dibber for planting allotment");
            return false;
        }
        
        // Allotments need 3 seeds
        int seedCount = Rs2Inventory.count(seedId);
        if (seedCount < FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED) {
            log.error("Insufficient seeds for allotment (have {}, need {})", 
                    seedCount, FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED);
            return false;
        }
        
        log.info("Planting {} seeds in allotment: {}", 
                FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED, produce.getName());
        
        // Plant 3 seeds
        for (int i = 0; i < FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED; i++) {
            if (!Rs2Inventory.contains(seedId)) {
                log.warn("Ran out of seeds after planting {}", i);
                break;
            }
            
            Rs2Inventory.interact(seedId, "Use");
            sleepUntil(() -> Rs2Inventory.isItemSelected(), 2000);
            
            Rs2GameObject.interact(patch, "Use");
            sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
            
            // Small delay between plantings
            sleep(300, 500);
        }
        
        // Verify all seeds were planted
        int remainingSeeds = Rs2Inventory.count(seedId);
        int seedsPlanted = seedCount - remainingSeeds;
        
        log.info("Planted {} seeds in allotment", seedsPlanted);
        return seedsPlanted >= FarmingContractData.Constants.ALLOTMENT_SEEDS_REQUIRED;
    }
    
    @Override
    protected boolean performHarvesting(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for harvesting allotment");
            return false;
        }
        
        String action = getHarvestAction(patch);
        if (action == null) {
            log.error("Could not determine harvest action for allotment");
            return false;
        }
        
        log.info("Harvesting allotment with action: {}", action);
        
        // Keep harvesting until patch is empty
        int attempts = 0;
        while (attempts < 15) {
            TileObject currentPatch = findPatchObject();
            if (currentPatch == null) break;
            
            Rs2GameObject.interact(currentPatch, action);
            sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
            attempts++;
            
            // Check if inventory is full
            if (Rs2Inventory.isFull()) {
                log.info("Inventory full while harvesting allotment");
                break;
            }
        }
        
        log.info("Allotment harvesting complete after {} attempts", attempts);
        return true;
    }
    
    @Override
    protected boolean performClearing(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for clearing dead allotment");
            return false;
        }
        
        log.info("Clearing dead allotment crops");
        Rs2GameObject.interact(patch, "Clear");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        return true;
    }
    
    /**
     * Get the harvest action for the allotment.
     */
    private String getHarvestAction(TileObject patch) {
        // Most allotments use "Harvest" action
        // Since we can't check actions directly on TileObject,
        // we'll default to common harvest actions
        return "Harvest";
    }
}