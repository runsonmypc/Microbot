package net.runelite.client.plugins.microbot.farmingcontract.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.farmingcontract.data.FarmingContractData;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.microbot.farmingcontract.managers.PatchStateDetector;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Abstract base class for handling patch-specific farming operations.
 * Each patch type has its own handler with specialized logic.
 */
@Slf4j
public abstract class PatchHandler {
    
    protected final PatchLocation patchLocation;
    protected final PatchStateDetector stateDetector;
    
    public PatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        this.patchLocation = patchLocation;
        this.stateDetector = stateDetector;
    }
    
    /**
     * Check if this handler can handle the given produce.
     */
    public abstract boolean canHandle(Produce produce);
    
    /**
     * Get the patch type this handler manages.
     */
    public abstract PatchImplementation getPatchType();
    
    /**
     * Plant seeds/saplings in the patch.
     * @return true if planting was successful
     */
    public boolean plant(Produce produce) {
        log.info("Planting {} at {}", produce.getName(), patchLocation.getName());
        
        TileObject patch = findPatchObject();
        if (patch == null) {
            log.error("Could not find patch object at {}", patchLocation.getLocation());
            return false;
        }
        
        // Clear weeds if needed
        if (patchNeedsRaking(patch)) {
            if (!clearWeeds(patch)) {
                return false;
            }
        }
        
        // Plant the seeds
        int seedId = FarmingContractData.getSeedId(produce);
        if (seedId == -1 || !Rs2Inventory.contains(seedId)) {
            log.error("No seeds available for planting");
            return false;
        }
        
        return performPlanting(patch, seedId, produce);
    }
    
    /**
     * Harvest produce from the patch.
     * @return true if harvesting was successful or started
     */
    public boolean harvest() {
        log.info("Harvesting from {}", patchLocation.getName());
        
        TileObject patch = findPatchObject();
        if (patch == null) {
            log.error("Could not find patch object");
            return false;
        }
        
        return performHarvesting(patch);
    }
    
    /**
     * Clear the patch (dead crops, stumps, etc).
     * @return true if clearing was successful
     */
    public boolean clear() {
        log.info("Clearing patch at {}", patchLocation.getName());
        
        TileObject patch = findPatchObject();
        if (patch == null) {
            log.error("Could not find patch object");
            return false;
        }
        
        return performClearing(patch);
    }
    
    /**
     * Check health of fully grown crops (trees, bushes, etc).
     * @return true if check-health was performed
     */
    public boolean checkHealth() {
        log.info("Checking health at {}", patchLocation.getName());
        
        TileObject patch = findPatchObject();
        if (patch == null) {
            log.error("Could not find patch object");
            return false;
        }
        
        return performCheckHealth(patch);
    }
    
    /**
     * Apply compost to the patch.
     * @param compostType Type of compost to use
     * @return true if compost was applied
     */
    public boolean applyCompost(String compostType) {
        if (!Rs2Inventory.contains(compostType)) {
            log.warn("No {} in inventory", compostType);
            return false;
        }
        
        TileObject patch = findPatchObject();
        if (patch == null) {
            return false;
        }
        
        log.info("Applying {} to patch", compostType);
        Rs2Inventory.interact(compostType, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Inventory.contains(compostType) || !Rs2Player.isAnimating(), 5000);
        
        return true;
    }
    
    /**
     * Apply plant cure to diseased crops.
     * @return true if cure was applied
     */
    public boolean applyCure() {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.PLANT_CURE)) {
            log.warn("No plant cure in inventory");
            return false;
        }
        
        TileObject patch = findPatchObject();
        if (patch == null) {
            return false;
        }
        
        log.info("Applying plant cure");
        Rs2Inventory.interact(FarmingContractData.Tools.PLANT_CURE, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating(), 3000);
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Inventory.contains(FarmingContractData.Tools.PLANT_CURE) || !Rs2Player.isAnimating(), 5000);
        
        return true;
    }
    
    /**
     * Check if this patch type requires payment for clearing.
     */
    public boolean requiresPayment() {
        return false; // Override in tree handlers
    }
    
    /**
     * Pay gardener to remove tree/stump.
     * @return true if payment was successful
     */
    public boolean payForClearing() {
        if (!requiresPayment()) {
            return true;
        }
        
        if (Rs2Inventory.count(FarmingContractData.Tools.COINS) < FarmingContractData.Constants.TREE_CLEARING_COST) {
            log.error("Insufficient coins for tree clearing");
            return false;
        }
        
        NPC gardener = findNearbyGardener();
        if (gardener == null) {
            log.error("Could not find gardener for payment");
            return false;
        }
        
        log.info("Paying gardener to remove tree");
        Rs2Npc.interact(gardener, "Pay");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        return true;
    }
    
    // Protected helper methods for subclasses
    
    protected TileObject findPatchObject() {
        return Rs2GameObject.findObjectByLocation(patchLocation.getLocation());
    }
    
    protected boolean patchNeedsRaking(TileObject patch) {
        // Try to interact with Rake action to check if patch has weeds
        // This is a simplified check - we'll try the action and see if it works
        return true; // Assume it might need raking and let clearWeeds handle it
    }
    
    protected boolean clearWeeds(TileObject patch) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.RAKE)) {
            log.error("No rake for clearing weeds");
            return false;
        }
        
        log.info("Clearing weeds");
        Rs2GameObject.interact(patch, "Rake");
        sleepUntil(() -> !Rs2Player.isAnimating() && !patchNeedsRaking(findPatchObject()), 10000);
        
        return true;
    }
    
    protected NPC findNearbyGardener() {
        return Rs2Npc.getNpcs()
            .filter(npc -> npc.getName() != null && npc.getName().contains("Gardener"))
            .filter(npc -> npc.getWorldLocation().distanceTo(patchLocation.getLocation()) < 15)
            .findFirst()
            .orElse(null);
    }
    
    // Abstract methods for subclasses to implement
    
    /**
     * Perform the actual planting operation.
     */
    protected abstract boolean performPlanting(TileObject patch, int seedId, Produce produce);
    
    /**
     * Perform the actual harvesting operation.
     */
    protected abstract boolean performHarvesting(TileObject patch);
    
    /**
     * Perform the actual clearing operation.
     */
    protected abstract boolean performClearing(TileObject patch);
    
    /**
     * Perform check-health operation (for trees/bushes).
     */
    protected boolean performCheckHealth(TileObject patch) {
        // Default implementation for patches that don't need check-health
        return false;
    }
}