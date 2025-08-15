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
 * Handler for herb patch operations.
 * Herbs require spade for harvesting and benefit from magic secateurs.
 */
@Slf4j
public class HerbPatchHandler extends PatchHandler {
    
    public HerbPatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        super(patchLocation, stateDetector);
    }
    
    @Override
    public boolean canHandle(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.HERB;
    }
    
    @Override
    public PatchImplementation getPatchType() {
        return PatchImplementation.HERB;
    }
    
    @Override
    protected boolean performPlanting(TileObject patch, int seedId, Produce produce) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SEED_DIBBER)) {
            log.error("No seed dibber for planting herbs");
            return false;
        }
        
        log.info("Planting herb seed: {}", produce.getName());
        
        // Use seed on patch
        Rs2Inventory.interact(seedId, "Use");
        sleepUntil(() -> Rs2Inventory.isItemSelected(), 2000);
        
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Inventory.contains(seedId), 5000);
        
        return !Rs2Inventory.contains(seedId);
    }
    
    @Override
    protected boolean performHarvesting(TileObject patch) {
        // Herbs REQUIRE a spade for harvesting
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for harvesting herbs - this is required!");
            return false;
        }
        
        // Check for magic secateurs for better yield
        boolean hasSecateurs = Rs2Inventory.contains(FarmingContractData.Tools.MAGIC_SECATEURS);
        if (!hasSecateurs) {
            log.warn("Harvesting herbs without magic secateurs - reduced yield");
        } else {
            log.info("Harvesting herbs with magic secateurs for increased yield");
        }
        
        // Harvest the herbs
        String action = getHarvestAction(patch);
        if (action == null) {
            log.error("Could not determine harvest action for herb patch");
            return false;
        }
        
        log.info("Harvesting herbs with action: {}", action);
        Rs2GameObject.interact(patch, action);
        
        // Wait for harvesting to complete
        sleepUntil(() -> {
            // Check if patch is empty or if we stopped animating
            TileObject currentPatch = findPatchObject();
            return currentPatch == null || !Rs2Player.isAnimating();
        }, 30000);
        
        return true;
    }
    
    @Override
    protected boolean performClearing(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for clearing dead herbs");
            return false;
        }
        
        log.info("Clearing dead herbs");
        Rs2GameObject.interact(patch, "Clear");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        return true;
    }
    
    /**
     * Get the harvest action for the herb patch.
     * Different herbs may have different harvest actions.
     */
    private String getHarvestAction(TileObject patch) {
        // Most herbs use "Pick" action
        // Check available actions
        return "Pick"; // Default for herbs
    }
}