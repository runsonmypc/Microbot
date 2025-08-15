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
 * Handler for flower patch operations.
 * Standard single-seed planting and harvesting.
 */
@Slf4j
public class FlowerPatchHandler extends PatchHandler {
    
    public FlowerPatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        super(patchLocation, stateDetector);
    }
    
    @Override
    public boolean canHandle(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.FLOWER;
    }
    
    @Override
    public PatchImplementation getPatchType() {
        return PatchImplementation.FLOWER;
    }
    
    @Override
    protected boolean performPlanting(TileObject patch, int seedId, Produce produce) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SEED_DIBBER)) {
            log.error("No seed dibber for planting flower");
            return false;
        }
        
        log.info("Planting flower seed: {}", produce.getName());
        
        // Use seed on patch
        Rs2Inventory.interact(seedId, "Use");
        sleepUntil(() -> Rs2Inventory.isItemSelected(), 2000);
        
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Inventory.contains(seedId), 5000);
        
        return !Rs2Inventory.contains(seedId);
    }
    
    @Override
    protected boolean performHarvesting(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for harvesting flowers");
            return false;
        }
        
        String action = getHarvestAction(patch);
        if (action == null) {
            log.error("Could not determine harvest action for flower patch");
            return false;
        }
        
        log.info("Harvesting flowers with action: {}", action);
        Rs2GameObject.interact(patch, action);
        
        // Wait for harvesting to complete
        sleepUntil(() -> {
            TileObject currentPatch = findPatchObject();
            return currentPatch == null || !Rs2Player.isAnimating();
        }, 10000);
        
        return true;
    }
    
    @Override
    protected boolean performClearing(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for clearing dead flowers");
            return false;
        }
        
        log.info("Clearing dead flowers");
        Rs2GameObject.interact(patch, "Clear");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        return true;
    }
    
    /**
     * Get the harvest action for the flower patch.
     */
    private String getHarvestAction(TileObject patch) {
        // Most flowers use "Pick" action
        // Since we can't check actions directly on TileObject,
        // we'll default to common harvest actions
        return "Pick";
    }
}