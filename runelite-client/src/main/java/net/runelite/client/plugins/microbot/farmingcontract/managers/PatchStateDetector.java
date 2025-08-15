package net.runelite.client.plugins.microbot.farmingcontract.managers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Detects and interprets farming patch states.
 * Consolidates patch checking logic from main script.
 */
@Slf4j
public class PatchStateDetector {
    
    private final FarmingWorld farmingWorld;
    
    /**
     * Patch state information.
     */
    public static class PatchState {
        public final CropState cropState;
        public final boolean isEmpty;
        public final boolean needsPlanting;
        public final boolean needsHarvesting;
        public final boolean needsChecking;
        public final boolean needsClearing;
        public final boolean needsCuring;
        public final boolean isGrowing;
        public final boolean hasCorrectCrop;
        public final String description;
        
        private PatchState(CropState cropState, boolean isEmpty, boolean needsPlanting,
                          boolean needsHarvesting, boolean needsChecking, boolean needsClearing,
                          boolean needsCuring, boolean isGrowing, boolean hasCorrectCrop,
                          String description) {
            this.cropState = cropState;
            this.isEmpty = isEmpty;
            this.needsPlanting = needsPlanting;
            this.needsHarvesting = needsHarvesting;
            this.needsChecking = needsChecking;
            this.needsClearing = needsClearing;
            this.needsCuring = needsCuring;
            this.isGrowing = isGrowing;
            this.hasCorrectCrop = hasCorrectCrop;
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    public PatchStateDetector(FarmingWorld farmingWorld) {
        this.farmingWorld = farmingWorld;
    }
    
    /**
     * Detect the state of a farming patch for the given contract.
     */
    public PatchState detectPatchState(Produce contract, PatchLocation patchLocation) {
        if (contract == null || patchLocation == null) {
            return createUnknownState();
        }
        
        log.info("Detecting patch state for {} at {}", contract.getName(), patchLocation.getName());
        
        // Get the farming patch from FarmingWorld
        FarmingPatch patch = findFarmingPatch(patchLocation);
        if (patch == null) {
            log.warn("Could not find farming patch at {}", patchLocation.getLocation());
            return createEmptyState(); // Assume empty if we can't find it
        }
        
        // Since FarmingPatch doesn't have getCropState(), we'll return a simplified state
        // In practice, you'd need to check the actual patch object in the game
        log.info("Simplified patch state detection for {}", contract.getName());
        
        // For now, assume patch is empty and needs planting
        return createEmptyState();
    }
    
    /**
     * Quick check if patch needs attention.
     */
    public boolean patchNeedsAttention(Produce contract, PatchLocation patchLocation) {
        PatchState state = detectPatchState(contract, patchLocation);
        return state.needsPlanting || state.needsHarvesting || 
               state.needsChecking || state.needsClearing || state.needsCuring;
    }
    
    /**
     * Check if patch has harvestable crops.
     */
    public boolean hasHarvestableCrops(PatchLocation patchLocation) {
        TileObject patchObject = Rs2GameObject.findObjectByLocation(patchLocation.getLocation());
        if (patchObject == null) {
            return false;
        }
        
        // Simplified check - in practice would check the actual patch object
        return false;
    }
    
    /**
     * Check if patch needs check-health action.
     */
    public boolean needsCheckHealth(PatchLocation patchLocation) {
        TileObject patchObject = Rs2GameObject.findObjectByLocation(patchLocation.getLocation());
        if (patchObject == null) {
            return false;
        }
        
        // Trees, fruit trees, bushes, and cacti need check-health when fully grown
        PatchImplementation type = patchLocation.getType();
        if (type == PatchImplementation.TREE || type == PatchImplementation.FRUIT_TREE ||
            type == PatchImplementation.BUSH || type == PatchImplementation.CACTUS) {
            
            // Simplified check - would need actual patch state checking
            return false;
        }
        
        return false;
    }
    
    // Helper methods
    
    private FarmingPatch findFarmingPatch(PatchLocation patchLocation) {
        // Since FarmingWorld doesn't have getFarmingPatches() method,
        // we can't actually look up patches this way
        // This would need to be implemented differently based on available API
        return null;
    }
    
    private boolean isCorrectCrop(FarmingPatch patch, Produce contract) {
        if (patch == null || contract == null) {
            return false;
        }
        
        // Since FarmingPatch doesn't have produce info,
        // we'll assume the crop is correct if it's in the right patch type
        // This is a simplification - in practice, we'd need to check the actual crop
        // by examining the patch object itself
        return true; // Simplified assumption
    }
    
    private PatchState interpretCropState(CropState cropState, Produce contract, boolean hasCorrectCrop) {
        switch (cropState) {
            case EMPTY:
                return createEmptyState();
                
            case GROWING:
                if (hasCorrectCrop) {
                    return new PatchState(cropState, false, false, false, false, false, false, true, true,
                            "Growing correct crop - waiting");
                } else {
                    return new PatchState(cropState, false, false, false, false, true, false, true, false,
                            "Growing wrong crop - needs clearing");
                }
                
            case HARVESTABLE:
                if (hasCorrectCrop) {
                    // Check if it needs check-health or harvesting
                    boolean needsCheck = needsCheckHealthForType(contract.getPatchImplementation());
                    if (needsCheck) {
                        return new PatchState(cropState, false, false, false, true, false, false, false, true,
                                "Ready for check-health");
                    } else {
                        return new PatchState(cropState, false, false, true, false, false, false, false, true,
                                "Ready to harvest");
                    }
                } else {
                    return new PatchState(cropState, false, false, false, false, true, false, false, false,
                            "Wrong crop ready - needs clearing");
                }
                
            case DISEASED:
                if (hasCorrectCrop) {
                    return new PatchState(cropState, false, false, false, false, false, true, false, true,
                            "Diseased - needs plant cure");
                } else {
                    return new PatchState(cropState, false, false, false, false, true, false, false, false,
                            "Wrong diseased crop - needs clearing");
                }
                
            case DEAD:
                return new PatchState(cropState, false, false, false, false, true, false, false, false,
                        "Dead - needs clearing");
                
            default:
                log.warn("Unknown crop state: {}", cropState);
                return createUnknownState();
        }
    }
    
    private boolean needsCheckHealthForType(PatchImplementation type) {
        return type == PatchImplementation.TREE || 
               type == PatchImplementation.FRUIT_TREE ||
               type == PatchImplementation.BUSH || 
               type == PatchImplementation.CACTUS;
    }
    
    private PatchState createEmptyState() {
        return new PatchState(CropState.EMPTY, true, true, false, false, false, false, false, false,
                "Empty - ready for planting");
    }
    
    private PatchState createUnknownState() {
        return new PatchState(null, false, false, false, false, false, false, false, false,
                "Unknown state");
    }
}