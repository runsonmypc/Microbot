package net.runelite.client.plugins.microbot.farmingcontract.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.farmingcontract.data.FarmingContractData;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.microbot.farmingcontract.managers.PatchStateDetector;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Handler for tree patch operations.
 * Trees require check-health and payment to gardener for removal.
 */
@Slf4j
public class TreePatchHandler extends PatchHandler {
    
    public TreePatchHandler(PatchLocation patchLocation, PatchStateDetector stateDetector) {
        super(patchLocation, stateDetector);
    }
    
    @Override
    public boolean canHandle(Produce produce) {
        return produce.getPatchImplementation() == PatchImplementation.TREE;
    }
    
    @Override
    public PatchImplementation getPatchType() {
        return PatchImplementation.TREE;
    }
    
    @Override
    public boolean requiresPayment() {
        return true;
    }
    
    @Override
    protected boolean performPlanting(TileObject patch, int seedId, Produce produce) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for planting tree");
            return false;
        }
        
        log.info("Planting tree sapling: {}", produce.getName());
        
        // Use sapling on patch
        Rs2Inventory.interact(seedId, "Use");
        sleepUntil(() -> Rs2Inventory.isItemSelected(), 2000);
        
        Rs2GameObject.interact(patch, "Use");
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Inventory.contains(seedId), 5000);
        
        return !Rs2Inventory.contains(seedId);
    }
    
    @Override
    protected boolean performCheckHealth(TileObject patch) {
        log.info("Performing check-health on tree");
        
        Rs2GameObject.interact(patch, "Check-health");
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
        
        log.info("Check-health completed - contract should be complete");
        return true;
    }
    
    @Override
    protected boolean performHarvesting(TileObject patch) {
        // Trees don't have harvestable produce, just check-health
        return performCheckHealth(patch);
    }
    
    @Override
    protected boolean performClearing(TileObject patch) {
        log.info("Clearing tree/stump");
        
        // Try to pay for removal first
        if (payForTreeRemoval()) {
            return true;
        }
        
        // Otherwise try direct clearing
        return clearStump(patch);
    }
    
    /**
     * Clear a tree stump.
     */
    private boolean clearStump(TileObject stump) {
        if (!Rs2Inventory.contains(FarmingContractData.Tools.SPADE)) {
            log.error("No spade for clearing stump");
            return false;
        }
        
        log.info("Clearing tree stump");
        Rs2GameObject.interact(stump, "Clear");
        sleepUntil(() -> {
            TileObject currentPatch = findPatchObject();
            return currentPatch == null || !Rs2Player.isAnimating();
        }, 10000);
        
        return true;
    }
    
    /**
     * Pay gardener to remove tree.
     */
    private boolean payForTreeRemoval() {
        if (Rs2Inventory.count(FarmingContractData.Tools.COINS) < FarmingContractData.Constants.TREE_CLEARING_COST) {
            log.error("Insufficient coins ({}) for tree removal", 
                    Rs2Inventory.count(FarmingContractData.Tools.COINS));
            return false;
        }
        
        NPC gardener = findNearbyGardener();
        if (gardener == null) {
            log.error("Could not find gardener near tree patch");
            return false;
        }
        
        log.info("Paying gardener 200gp to remove tree");
        Rs2Npc.interact(gardener, "Pay");
        
        // Wait for dialogue
        sleepUntil(() -> Rs2Dialogue.isInDialogue(), 5000);
        
        // Handle payment dialogue
        if (Rs2Dialogue.isInDialogue()) {
            // Click through confirmation
            Rs2Dialogue.clickContinue();
            sleep(600, 800);
            
            // Continue dialogue
            Rs2Dialogue.clickContinue();
            
            // Wait for tree to be removed
            sleepUntil(() -> {
                TileObject patch = findPatchObject();
                return patch != null;
            }, 10000);
        }
        
        return true;
    }
}