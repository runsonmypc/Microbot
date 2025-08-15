package net.runelite.client.plugins.microbot.farmingcontract.managers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.farmingcontract.data.PatchLocation;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.Produce;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        
        // Find the patch GameObject within the polygon area
        GameObject patch = findPatchInArea(patchLocation);
        if (patch == null) {
            log.warn("Could not find patch object in area for {}", patchLocation.getName());
            return createEmptyState(); // Assume empty if we can't find it
        }
        
        // Infer the state from the patch's actions
        CropState cropState = inferStateFromActions(patch);
        log.info("Detected crop state: {} for {}", cropState, contract.getName());
        
        // Check if it's the correct crop (simplified - assumes correct if not empty)
        boolean hasCorrectCrop = cropState != CropState.EMPTY && cropState != CropState.DEAD;
        
        // Convert CropState to our PatchState
        return interpretCropState(cropState, contract, hasCorrectCrop);
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
    
    /**
     * Find a patch GameObject within the polygon area.
     * Based on the old script's findPatchUsingActions method.
     */
    private GameObject findPatchInArea(PatchLocation patchLocation) {
        if (patchLocation == null || patchLocation.getArea() == null) {
            return null;
        }
        
        java.awt.Polygon area = patchLocation.getArea();
        
        // Get all GameObjects and filter those within the polygon
        List<GameObject> allObjects = Rs2GameObject.getGameObjects();
        List<GameObject> objectsInArea = allObjects.stream()
            .filter(obj -> obj != null && 
                   area.contains(obj.getWorldLocation().getX(), 
                                obj.getWorldLocation().getY()))
            .collect(Collectors.toList());
        
        // Return first valid object found
        for (GameObject obj : objectsInArea) {
            if (obj != null) {
                log.debug("Found object in patch area: ID={} at {}", 
                         obj.getId(), obj.getWorldLocation());
                return obj;
            }
        }
        
        log.warn("No objects found in patch area for {}", patchLocation.getName());
        return null;
    }
    
    /**
     * Infer crop state from GameObject actions.
     * Ported from the old script's inferStateFromActions method.
     */
    private CropState inferStateFromActions(GameObject patch) {
        if (patch == null) return CropState.EMPTY;
        
        // Use ignoreImpostor=false for farming patches to get current state's actions
        ObjectComposition comp = Rs2GameObject.convertToObjectComposition(patch, false);
        if (comp == null || comp.getActions() == null) return CropState.EMPTY;
        
        // Log available actions for debugging
        log.info("inferStateFromActions - Patch ID: {}, Actions: {}", 
                patch.getId(), Arrays.toString(comp.getActions()));
        
        // Collect all non-null actions
        Set<String> actions = new HashSet<>();
        for (String action : comp.getActions()) {
            if (action != null && !action.isEmpty()) {
                actions.add(action.toLowerCase());
            }
        }
        
        // Remove "Guide" and "Inspect" as they're always present
        actions.remove("guide");
        actions.remove("inspect");
        
        // If no actions remain after removing Guide/Inspect, patch is empty
        if (actions.isEmpty()) {
            return CropState.EMPTY;
        }
        
        // If only "Rake" remains, patch is empty with weeds
        if (actions.size() == 1 && actions.contains("rake")) {
            return CropState.EMPTY;
        }
        
        // Check remaining actions for specific states
        for (String action : actions) {
            // Check-health = Tree/fruit tree/bush ready for check
            if (action.equalsIgnoreCase("check-health") || 
                action.equalsIgnoreCase("check health") || 
                action.equalsIgnoreCase("check")) {
                return CropState.UNCHECKED;
            }
            
            // Pick/Harvest = Regular crops ready to harvest
            if (action.equalsIgnoreCase("pick") || 
                action.equalsIgnoreCase("harvest") ||
                action.equalsIgnoreCase("pick-from") || 
                action.equalsIgnoreCase("pick-spine")) {
                return CropState.HARVESTABLE;
            }
            
            // Chop = Tree that needs to be removed
            if (action.contains("chop")) {
                return CropState.HARVESTABLE;
            }
            
            // Clear = Something clearable (dead plants, stumps, etc.)
            if (action.equalsIgnoreCase("clear")) {
                return CropState.DEAD;
            }
        }
        
        // If we have other actions but none match specific states, patch is growing
        return CropState.GROWING;
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
                
            case UNCHECKED:
                // Trees, bushes, cacti need check-health when fully grown
                if (hasCorrectCrop) {
                    return new PatchState(cropState, false, false, false, true, false, false, false, true,
                            "Ready for check-health");
                } else {
                    return new PatchState(cropState, false, false, false, false, true, false, false, false,
                            "Wrong crop ready for check - needs clearing");
                }
                
            case HARVESTABLE:
                if (hasCorrectCrop) {
                    // Regular harvesting for herbs, allotments, flowers
                    return new PatchState(cropState, false, false, true, false, false, false, false, true,
                            "Ready to harvest");
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