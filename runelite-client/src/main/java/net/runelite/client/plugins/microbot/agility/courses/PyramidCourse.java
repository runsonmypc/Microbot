package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.agility.courses.PyramidObstacleData.ObstacleArea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PyramidCourse implements AgilityCourseHandler {
    
    // Debug mode - set to true for verbose logging during development
    private static final boolean DEBUG = false;
    
    private static final WorldPoint START_POINT = new WorldPoint(3354, 2830, 0);
    private static final WorldPoint SIMON_LOCATION = new WorldPoint(3343, 2827, 0);
    private static final String SIMON_NAME = "Simon Templeton";
    private static final int PYRAMID_TOP_REGION = 12105;
    
    // Centralized state tracking
    private static final PyramidState state = new PyramidState();
    
    /**
     * Debug logging - only prints if DEBUG mode is enabled
     */
    private static void debugLog(String message) {
        if (DEBUG) {
            Microbot.log(message);
        }
    }
    
    // Define the strict obstacle sequence to prevent skipping ahead
    private static final List<Integer> FLOOR_2_SEQUENCE = Arrays.asList(
        10884, // Gap Cross 1
        10859, // Gap Jump  
        10861, // Gap Cross 2
        10860, // Ledge
        10865, // Low wall
        10859, // Gap jump (end)
        10857  // Stairs up
    );
    
    // Obstacle areas are now defined in PyramidObstacleData for better maintainability
    private static final List<ObstacleArea> OBSTACLE_AREAS = PyramidObstacleData.OBSTACLE_AREAS;
    
    @Override
    public WorldPoint getStartPoint() {
        return START_POINT;
    }
    
    @Override
    public List<AgilityObstacleModel> getObstacles() {
        // Return all unique obstacle IDs for compatibility
        return Arrays.asList(
            new AgilityObstacleModel(10857), // Stairs
            new AgilityObstacleModel(10865), // Low wall
            new AgilityObstacleModel(10860), // Ledge
            new AgilityObstacleModel(10867), // Plank (main object)
            new AgilityObstacleModel(10868), // Plank end (clickable)
            new AgilityObstacleModel(10859), // Gap jump
            new AgilityObstacleModel(10882), // Gap (floor 1)
            new AgilityObstacleModel(10886), // Ledge 3
            new AgilityObstacleModel(10884), // Gap (floor 2)
            new AgilityObstacleModel(10861), // Gap
            new AgilityObstacleModel(10888), // Ledge 2
            new AgilityObstacleModel(10851), // Climbing rocks
            new AgilityObstacleModel(10855)  // Doorway
        );
    }
    
    @Override
    public TileObject getCurrentObstacle() {
        WorldPoint playerPos = Rs2Player.getWorldLocation();
        
        debugLog("=== getCurrentObstacle called - Player at " + playerPos + " (plane: " + playerPos.getPlane() + ") ===");
        
        // Check if inventory is full AND we're on ground level (not inside pyramid)
        if (Rs2Inventory.isFull() && playerPos.getPlane() == 0) {
            if (Rs2Inventory.contains(ItemID.PYRAMID_TOP)) {
                // Inventory is full and has pyramid tops - handle turn-in
                if (!state.isHandlingPyramidTurnIn()) {
                    debugLog("Inventory is full with pyramid tops and on ground level - going to Simon Templeton");
                    state.startPyramidTurnIn();
                }
                
                // Handle pyramid turn-in
                if (handlePyramidTurnIn()) {
                    return null; // Return null to prevent obstacle interaction
                }
            } else {
                // Inventory is full but no pyramid tops - stop and warn
                Microbot.showMessage("Inventory is full but no pyramid tops found! Clear inventory to continue.");
                Microbot.log("WARNING: Inventory full without pyramid tops - stopping");
                return null;
            }
        } else if (!Rs2Inventory.isFull()) {
            // Reset turn-in flag when inventory is not full
            state.clearPyramidTurnIn();
        }
        
        // NEVER return an obstacle while moving or animating
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            debugLog("Player is moving/animating, returning null to prevent clicking");
            return null;
        }
        
        // Check for empty waterskins and drop them
        if (handleEmptyWaterskins()) {
            return null; // Return null to prevent obstacle interaction this cycle
        }
        
        // Special blocking for Cross Gap obstacles - don't return any obstacle while doing Cross Gap
        if (state.isDoingCrossGap()) {
            debugLog("Currently doing Cross Gap obstacle, blocking all other obstacles");
            return null;
        }
        
        // Block all obstacles while doing any XP-granting obstacle (plank, gap, ledge, etc)
        if (state.isDoingXpObstacle()) {
            debugLog("Currently doing XP-granting obstacle, blocking all other obstacles until XP received");
            return null;
        }
        
        // Additional cooldown check for Cross Gap
        if (state.isCrossGapCooldownActive()) {
            debugLog("Cross Gap cooldown active, returning null");
            return null;
        }
        
        // Double-check movement after a brief moment - animations can have pauses
        try {
            Thread.sleep(50); // Very brief check
        } catch (InterruptedException e) {
            // Ignore
        }
        
        // Recheck after the brief pause
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            debugLog("Player started moving/animating after brief pause, returning null");
            return null;
        }
        
        // Prevent getting obstacles too quickly after starting one
        if (state.isObstacleCooldownActive()) {
            debugLog("Obstacle cooldown active, returning null to prevent spam clicking");
            return null;
        }
        
        // Find the obstacle area containing the player
        ObstacleArea currentArea = null;
        
        // Debug: log areas being checked for current plane
        debugLog("Checking areas for plane " + playerPos.getPlane() + " player position " + playerPos + ":");
        for (ObstacleArea area : OBSTACLE_AREAS) {
            if (area.plane == playerPos.getPlane()) {
                boolean contains = area.containsPlayer(playerPos);
                debugLog("  - Area: " + area.name + " at (" + area.minX + "," + area.minY + ") to (" + area.maxX + "," + area.maxY + ") - contains player: " + contains);
                if (contains) {
                    debugLog("    -> Obstacle ID: " + area.obstacleId + " at location: " + area.obstacleLocation);
                }
            }
        }
        
        for (ObstacleArea area : OBSTACLE_AREAS) {
            if (area.containsPlayer(playerPos)) {
                // Special check for climbing rocks - skip if we've recently clicked them
                if (area.obstacleId == 10851 && area.name.contains("grab pyramid")) {
                    if (state.isClimbingRocksCooldownActive()) {
                        debugLog("Recently clicked climbing rocks, skipping to next area");
                        continue;
                    }
                }
                
                currentArea = area;
                debugLog("Found player in area: " + area.name + " (obstacle ID: " + area.obstacleId + ")");
                // Debug: log if this is a plank area
                if (area.obstacleId == 10868) {
                    debugLog("  Player in PLANK area - should look for plank end ground object");
                }
                break;
            }
        }
        
        if (currentArea == null) {
            debugLog("Player not in any defined obstacle area at " + playerPos + " (plane: " + playerPos.getPlane() + ")");
            
            // Special check for floor 4 start position
            if (playerPos.getPlane() == 2 && playerPos.getX() == 3041 && playerPos.getY() == 4695) {
                debugLog("SPECIAL CASE: Player at floor 4 start position (3041, 4695)");
                // Manually find the gap
                TileObject gap = findNearestObstacleWithinDistance(playerPos, 10859, 5);
                if (gap != null) {
                    debugLog("Found Gap manually at " + gap.getWorldLocation());
                    return gap;
                }
            }
            
            // Log all areas on current plane for debugging
            debugLog("Available areas on plane " + playerPos.getPlane() + ":");
            int count = 0;
            for (ObstacleArea area : OBSTACLE_AREAS) {
                if (area.plane == playerPos.getPlane()) {
                    debugLog("  - " + area.name + " at (" + area.minX + "," + area.minY + ") to (" + area.maxX + "," + area.maxY + ")");
                    count++;
                    if (count > 10) {
                        debugLog("  ... and more areas");
                        break;
                    }
                }
            }
            
            // Special case: If player just climbed to floor 1, direct them to low wall
            if (playerPos.getPlane() == 1 && playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() == 2833) {
                debugLog("Player just arrived on floor 1, looking for low wall");
                // Find the low wall obstacle
                TileObject lowWall = findNearestObstacle(playerPos, 10865);
                if (lowWall != null) {
                    return lowWall;
                }
            }
            
            // Try to find the nearest obstacle on the current plane
            debugLog("Looking for nearest pyramid obstacle...");
            return findNearestPyramidObstacle(playerPos);
        }
        
        debugLog("Player in area for: " + currentArea.name + " at " + playerPos + " (plane: " + playerPos.getPlane() + ")");
        
        // Find the specific obstacle instance
        TileObject obstacle = null;
        
        // For gaps and ledges, always find the nearest one since there can be multiple
        // Also for floor 4, always use nearest search since obstacles can be multi-tile
        if (currentArea.obstacleId == 10859 || currentArea.obstacleId == 10861 || currentArea.obstacleId == 10884 || currentArea.obstacleId == 10860 || playerPos.getPlane() == 2) {
            debugLog("Looking for nearest " + currentArea.name);
            
            // Use strict sequential checking to prevent skipping ahead
            obstacle = findNearestObstacleStrict(playerPos, currentArea.obstacleId, currentArea);
        } else {
            obstacle = findObstacleAt(currentArea.obstacleLocation, currentArea.obstacleId);
            
            if (obstacle == null) {
                debugLog("Could not find " + currentArea.name + " (ID: " + currentArea.obstacleId + ") at expected location " + currentArea.obstacleLocation);
                // Try to find any instance of this obstacle type nearby with strict checking
                obstacle = findNearestObstacleStrict(playerPos, currentArea.obstacleId, currentArea);
            }
        }
        
        if (obstacle != null) {
            debugLog("Selected obstacle: " + currentArea.name + " (ID: " + currentArea.obstacleId + ") at " + obstacle.getWorldLocation() + " for player at " + playerPos);
            
            // Track Cross Gap obstacles specifically
            if (currentArea.name.contains("Cross") || currentArea.name.contains("Gap Cross")) {
                // Cross gap time is tracked in startCrossGap
                state.startCrossGap(); // Set flag that we're doing Cross Gap
                debugLog("Detected Cross Gap obstacle - blocking all other obstacles until XP received");
            }
            
            // Track any XP-granting obstacle (gaps, planks, ledges, low walls)
            // These give XP: Low wall (8), Ledge (52), Gap/Plank (56.4)
            // These don't give XP: Stairs (0), Doorway (0), Climbing rocks (0)
            if (currentArea.obstacleId == 10865 || // Low wall
                currentArea.obstacleId == 10860 || // Ledge
                currentArea.obstacleId == 10868 || // Plank
                currentArea.obstacleId == 10859 || // Gap
                currentArea.obstacleId == 10861 || // Gap
                currentArea.obstacleId == 10882 || // Gap
                currentArea.obstacleId == 10884 || // Gap Cross
                currentArea.obstacleId == 10886 || // Ledge
                currentArea.obstacleId == 10888) { // Ledge
                state.startXpObstacle();
                debugLog("Starting XP-granting obstacle - blocking all clicks until XP received");
            }
        } else {
            Microbot.log("ERROR: Could not find any obstacle for area: " + currentArea.name + " (ID: " + currentArea.obstacleId + ")");
        }
        
        // Special handling for pyramid top region - if completed, look for stairs down
        if (obstacle == null && playerPos.getRegionID() == PYRAMID_TOP_REGION && playerPos.getPlane() == 3) {
            TileObject stairs = Rs2GameObject.getTileObject(10857);
            if (stairs != null) {
                debugLog("No obstacle found on pyramid top, found stairs to go back down");
                return stairs;
            }
        }
        
        return obstacle;
    }
    
    private TileObject findObstacleAt(WorldPoint location, int obstacleId) {
        debugLog("findObstacleAt: Looking for obstacle " + obstacleId + " at " + location);
        
        // Special handling for plank end which is a ground object
        if (obstacleId == 10868) {
            List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
            debugLog("Looking for plank end at " + location + ", checking " + groundObjects.size() + " ground objects");
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId && go.getWorldLocation().equals(location)) {
                    debugLog("Found plank end (ground object) at " + go.getWorldLocation());
                    return go;
                }
            }
            debugLog("No plank end found at expected location " + location);
            // List all plank ends found
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId) {
                    debugLog("  Found plank end at " + go.getWorldLocation() + " (not at expected location)");
                }
            }
            return null;
        }
        
        // Normal game objects
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getWorldLocation().equals(location)
        );
        
        debugLog("Found " + obstacles.size() + " obstacles with ID " + obstacleId + " at " + location);
        
        if (obstacles.isEmpty()) {
            // Log all obstacles of this type on the current plane
            List<TileObject> allObstaclesOfType = Rs2GameObject.getAll(obj -> 
                obj.getId() == obstacleId && 
                obj.getPlane() == location.getPlane()
            );
            debugLog("No obstacle found at exact location. Found " + allObstaclesOfType.size() + " obstacles with ID " + obstacleId + " on plane " + location.getPlane() + ":");
            for (TileObject obj : allObstaclesOfType) {
                debugLog("  - " + obstacleId + " at " + obj.getWorldLocation());
            }
            return null;
        }
        
        return obstacles.get(0);
    }
    
    private TileObject findNearestObstacleStrict(WorldPoint playerPos, int obstacleId, ObstacleArea currentArea) {
        debugLog("Looking for obstacle " + obstacleId + " with strict sequential checking");
        
        // Special handling for floor 4 gaps FIRST - need to select the correct one
        // Check if we're on floor 4 (plane 2) and looking for a gap, regardless of exact area name
        if (playerPos.getPlane() == 2 && obstacleId == 10859) {
            // If player is after low wall at (3043, 4701-4702), we need the second gap
            if (playerPos.getX() == 3043 && playerPos.getY() >= 4701) {
                debugLog("Player after low wall on floor 4, looking for second gap at (3048, 4695)");
                // Find the gap at (3048, 4695) specifically
                List<TileObject> gaps = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == playerPos.getPlane() &&
                    obj.getWorldLocation().getX() >= 3047 && obj.getWorldLocation().getX() <= 3049 &&
                    obj.getWorldLocation().getY() >= 4694 && obj.getWorldLocation().getY() <= 4696
                );
                
                if (!gaps.isEmpty()) {
                    TileObject secondGap = gaps.get(0);
                    debugLog("Found second gap at " + secondGap.getWorldLocation());
                    return secondGap;
                } else {
                    debugLog("Could not find second gap on floor 4!");
                }
            }
            // If player is at start of floor 4, we need the first gap
            else if (playerPos.getX() >= 3040 && playerPos.getX() <= 3042 && 
                     playerPos.getY() >= 4695 && playerPos.getY() <= 4697) {
                debugLog("Player at start of floor 4, looking for first gap");
                // Find the gap at (3040, 4697) specifically
                List<TileObject> gaps = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == playerPos.getPlane() &&
                    obj.getWorldLocation().getX() >= 3039 && obj.getWorldLocation().getX() <= 3041 &&
                    obj.getWorldLocation().getY() >= 4696 && obj.getWorldLocation().getY() <= 4698
                );
                
                if (!gaps.isEmpty()) {
                    TileObject firstGap = gaps.get(0);
                    debugLog("Found first gap at " + firstGap.getWorldLocation());
                    return firstGap;
                }
            }
        }
        
        // Special handling for floor 2 gaps to prevent skipping ahead
        if (playerPos.getPlane() == 2 && (obstacleId == 10859 || obstacleId == 10861 || obstacleId == 10884) && !currentArea.name.contains("floor 4")) {
            // Only search in a very limited area based on the current area definition
            List<TileObject> obstacles = Rs2GameObject.getAll(obj -> {
                if (obj.getId() != obstacleId || obj.getPlane() != playerPos.getPlane()) {
                    return false;
                }
                
                WorldPoint objLoc = obj.getWorldLocation();
                
                // For floor 2 gaps, use very strict position checking
                if (currentArea.name.contains("Gap Cross 1")) {
                    // First gap should be around (3356, 2835)
                    return objLoc.getX() == 3356 && objLoc.getY() >= 2835 && objLoc.getY() <= 2837;
                } else if (currentArea.name.contains("Gap Jump")) {
                    // Gap jump should be around (3356, 2841)
                    return objLoc.getX() == 3356 && objLoc.getY() >= 2838 && objLoc.getY() <= 2844;
                } else if (currentArea.name.contains("Gap Cross 2")) {
                    // Gap cross 2 should be around (3356, 2849)
                    return objLoc.getX() >= 3356 && objLoc.getX() <= 3360 && objLoc.getY() >= 2848 && objLoc.getY() <= 2850;
                } else if (currentArea.name.contains("Gap jump") && currentArea.name.contains("end")) {
                    // End gap jump should be around (3365, 2833)
                    return objLoc.getX() >= 3363 && objLoc.getX() <= 3367 && objLoc.getY() >= 2833 && objLoc.getY() <= 2834;
                }
                
                // Default: must be within 8 tiles
                return objLoc.distanceTo(playerPos) <= 8;
            });
            
            if (!obstacles.isEmpty()) {
                TileObject nearest = obstacles.stream()
                    .min((a, b) -> Integer.compare(
                        a.getWorldLocation().distanceTo(playerPos),
                        b.getWorldLocation().distanceTo(playerPos)
                    ))
                    .orElse(null);
                    
                if (nearest != null) {
                    debugLog("Found strictly checked obstacle at " + nearest.getWorldLocation());
                    return nearest;
                }
            }
        }
        
        // For floor 3 gaps, use longer distance
        if (playerPos.getPlane() == 3 && obstacleId == 10859) {
            return findNearestObstacleWithinDistance(playerPos, obstacleId, 20);
        }
        
        // For other obstacles, use normal nearest search but with distance limit
        return findNearestObstacleWithinDistance(playerPos, obstacleId, 10);
    }
    
    private TileObject findNearestObstacleWithinDistance(WorldPoint playerPos, int obstacleId, int maxDistance) {
        debugLog("Looking for obstacle " + obstacleId + " within " + maxDistance + " tiles");
        
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= maxDistance
        );
        
        if (obstacles.isEmpty()) {
            debugLog("No obstacles found within " + maxDistance + " tiles");
            return null;
        }
        
        // Log all found obstacles for debugging
        debugLog("Found " + obstacles.size() + " obstacles within " + maxDistance + " tiles:");
        for (TileObject obj : obstacles) {
            debugLog("  - " + obstacleId + " at " + obj.getWorldLocation() + " (distance: " + obj.getWorldLocation().distanceTo(playerPos) + ")");
        }
        
        return obstacles.stream()
            .min((a, b) -> Integer.compare(
                a.getWorldLocation().distanceTo(playerPos),
                b.getWorldLocation().distanceTo(playerPos)
            ))
            .orElse(null);
    }
    
    private TileObject findNearestObstacle(WorldPoint playerPos, int obstacleId) {
        // Special case for Ledge on floor 2 - different ledges based on position
        if (obstacleId == 10860 && playerPos.getPlane() == 2) {
            debugLog("Special handling for floor 2 Ledge at player position " + playerPos);
            
            // If player is anywhere in the path from Gap 10861 to Ledge, use east ledge
            if ((playerPos.getX() >= 3372 && playerPos.getX() <= 3373 && playerPos.getY() >= 2841 && playerPos.getY() <= 2850) ||
                (playerPos.getX() >= 3364 && playerPos.getX() <= 3373 && playerPos.getY() >= 2849 && playerPos.getY() <= 2850)) {
                debugLog("Player in path from Gap 10861 to Ledge, looking for east Ledge at (3372, 2839)");
                
                // Find the specific ledge at (3372, 2839)
                TileObject eastLedge = findObstacleAt(new WorldPoint(3372, 2839, 2), obstacleId);
                if (eastLedge != null) {
                    debugLog("Found east Ledge at " + eastLedge.getWorldLocation());
                    return eastLedge;
                } else {
                    debugLog("Could not find east Ledge at expected location (3372, 2839)");
                    // Try to find any ledge on east side as fallback
                    List<TileObject> eastLedges = Rs2GameObject.getAll(obj -> 
                        obj.getId() == obstacleId && 
                        obj.getPlane() == playerPos.getPlane() &&
                        obj.getWorldLocation().getX() >= 3372 && obj.getWorldLocation().getX() <= 3373 &&
                        obj.getWorldLocation().getY() >= 2837 && obj.getWorldLocation().getY() <= 2841
                    );
                    if (!eastLedges.isEmpty()) {
                        return eastLedges.get(0);
                    }
                }
            }
            
            // Default behavior - look for middle ledge
            List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
                obj.getId() == obstacleId && 
                obj.getPlane() == playerPos.getPlane() &&
                obj.getWorldLocation().getX() < 3370 && // Exclude east side ledges
                obj.getWorldLocation().getY() >= 2840 && obj.getWorldLocation().getY() <= 2851 && // Middle Y range
                obj.getWorldLocation().distanceTo(playerPos) <= 20
            );
            
            // Log all ledges found for debugging
            debugLog("Found " + obstacles.size() + " potential ledges on floor 2:");
            for (TileObject obj : obstacles) {
                debugLog("  - Ledge at " + obj.getWorldLocation());
            }
            
            // Find the ledge closest to the expected position (3364, 2841)
            WorldPoint expectedLedgePos = new WorldPoint(3364, 2841, 2);
            TileObject bestLedge = obstacles.stream()
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(expectedLedgePos),
                    b.getWorldLocation().distanceTo(expectedLedgePos)
                ))
                .orElse(null);
                
            if (bestLedge != null) {
                debugLog("Selected ledge at " + bestLedge.getWorldLocation() + " (closest to expected position " + expectedLedgePos + ")");
                return bestLedge;
            } else {
                Microbot.log("WARNING: No suitable ledge found on floor 2!");
                return null;
            }
        }
        // Special handling for plank end which is a ground object
        if (obstacleId == 10868) {
            List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
            List<GroundObject> nearbyPlanks = new ArrayList<>();
            
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId && 
                    go.getPlane() == playerPos.getPlane() &&
                    go.getWorldLocation().distanceTo(playerPos) <= 15) {
                    nearbyPlanks.add(go);
                }
            }
            
            if (nearbyPlanks.isEmpty()) {
                debugLog("No plank ends (ground objects) found nearby");
                return null;
            }
            
            debugLog("Found " + nearbyPlanks.size() + " plank ends nearby");
            for (GroundObject go : nearbyPlanks) {
                debugLog("  - Plank end at " + go.getWorldLocation() + " (distance: " + go.getWorldLocation().distanceTo(playerPos) + ")");
            }
            
            // Return closest plank end
            return nearbyPlanks.stream()
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(playerPos),
                    b.getWorldLocation().distanceTo(playerPos)
                ))
                .orElse(null);
        }
        
        // Normal game objects
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= 15
        );
        
        if (obstacles.isEmpty()) {
            return null;
        }
        
        // Log all found obstacles for debugging
        debugLog("Found " + obstacles.size() + " obstacles with ID " + obstacleId + " on plane " + playerPos.getPlane() + ":");
        for (TileObject obj : obstacles) {
            debugLog("  - " + obstacleId + " at " + obj.getWorldLocation() + " (distance: " + obj.getWorldLocation().distanceTo(playerPos) + ")");
        }
        
        // For stairs on floor 1, we need to filter out the wrong stairs
        if (obstacleId == 10857 && playerPos.getPlane() == 1) {
            // If player just climbed up and is at start position (3354-3355, 2833), we should NOT return any stairs
            // The player should go to the low wall instead
            if (playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() >= 2833 && playerPos.getY() <= 2835) {
                debugLog("Player just climbed to floor 1, should not interact with stairs yet");
                return null;
            }
            
            // Filter out stairs that are at the wrong location
            // The correct stairs to floor 2 are at (3356, 2831)
            obstacles = obstacles.stream()
                .filter(obj -> {
                    WorldPoint loc = obj.getWorldLocation();
                    // Only consider stairs in the southwest area of floor 1
                    return loc.getX() >= 3356 && loc.getX() <= 3360 && 
                           loc.getY() >= 2831 && loc.getY() <= 2833;
                })
                .collect(Collectors.toList());
                
            if (obstacles.isEmpty()) {
                debugLog("No appropriate stairs found for progression");
                return null;
            }
        }
        
        // For low wall on floor 1, make sure we get the north end
        if (obstacleId == 10865 && playerPos.getPlane() == 1 && 
            playerPos.getX() == 3354 && playerPos.getY() <= 2840) {
            // Sort by Y coordinate descending to get northernmost wall
            obstacles.sort((a, b) -> Integer.compare(
                b.getWorldLocation().getY(), 
                a.getWorldLocation().getY()
            ));
            
            // Return the northernmost low wall
            if (!obstacles.isEmpty()) {
                TileObject northWall = obstacles.get(0);
                debugLog("Selected northernmost low wall at " + northWall.getWorldLocation());
                return northWall;
            }
        }
        
        // Return closest reachable obstacle
        return obstacles.stream()
            .filter(this::isObstacleReachable)
            .min((a, b) -> Integer.compare(
                a.getWorldLocation().distanceTo(playerPos),
                b.getWorldLocation().distanceTo(playerPos)
            ))
            .orElse(obstacles.get(0));
    }
    
    private TileObject findNearestPyramidObstacle(WorldPoint playerPos) {
        List<Integer> pyramidObstacleIds = Arrays.asList(
            10857, 10865, 10860, 10867, 10868, 10859, 10882, 10886, 10884, 10861, 10888, 10851, 10855
        );
        
        // Special handling for floor 1 start position
        if (playerPos.getPlane() == 1 && playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() >= 2833 && playerPos.getY() <= 2835) {
            // Player just climbed to floor 1, exclude stairs from search
            pyramidObstacleIds = Arrays.asList(
                10865, 10860, 10867, 10868, 10859, 10882, 10886, 10884, 10861, 10888, 10851, 10855
            );
            debugLog("Excluding stairs from search at floor 1 start position");
        }
        
        List<Integer> finalObstacleIds = pyramidObstacleIds;
        
        // First check for ground objects (plank ends)
        List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
        for (GroundObject go : groundObjects) {
            if (go.getId() == 10868 && 
                go.getPlane() == playerPos.getPlane() &&
                go.getWorldLocation().distanceTo(playerPos) <= 15) {
                debugLog("Found nearby plank end (ground object) at " + go.getWorldLocation());
                return go;
            }
        }
        
        // Use longer search distance for floor 3
        int searchDistance = (playerPos.getPlane() == 3) ? 25 : 15;
        
        // Then check normal game objects
        List<TileObject> nearbyObstacles = Rs2GameObject.getAll(obj -> 
            finalObstacleIds.contains(obj.getId()) && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= searchDistance
        );
        
        if (nearbyObstacles.isEmpty()) {
            debugLog("No pyramid obstacles found within " + searchDistance + " tiles on plane " + playerPos.getPlane());
            // Try expanding search radius for floor 4 (pyramid top area)
            if (playerPos.getPlane() == 2 && playerPos.getX() >= 3040 && playerPos.getX() <= 3050) {
                debugLog("Expanding search for floor 4 pyramid top area...");
                nearbyObstacles = Rs2GameObject.getAll(obj -> 
                    finalObstacleIds.contains(obj.getId()) && 
                    obj.getPlane() == playerPos.getPlane()
                );
            }
        }
        
        debugLog("Found " + nearbyObstacles.size() + " pyramid obstacles nearby:");
        for (TileObject obj : nearbyObstacles) {
            debugLog("  - ID " + obj.getId() + " at " + obj.getWorldLocation() + " (distance: " + obj.getWorldLocation().distanceTo(playerPos) + ")");
        }
        
        return nearbyObstacles.stream()
            .filter(obj -> isObstacleReachable(obj))
            .min((a, b) -> Integer.compare(
                a.getWorldLocation().distanceTo(playerPos),
                b.getWorldLocation().distanceTo(playerPos)
            ))
            .orElse(null);
    }
    
    private boolean isObstacleReachable(TileObject obstacle) {
        if (obstacle instanceof GameObject) {
            GameObject go = (GameObject) obstacle;
            return Rs2GameObject.canReach(go.getWorldLocation(), go.sizeX() + 2, go.sizeY() + 2, 4, 4);
        } else if (obstacle instanceof GroundObject) {
            return Rs2GameObject.canReach(obstacle.getWorldLocation(), 2, 2);
        } else if (obstacle instanceof WallObject) {
            return Rs2GameObject.canReach(obstacle.getWorldLocation(), 1, 1);
        } else {
            return Rs2GameObject.canReach(obstacle.getWorldLocation(), 2, 2);
        }
    }
    
    @Override
    public boolean handleWalkToStart(WorldPoint playerLocation) {
        // Only walk to start if on ground level
        if (playerLocation.getPlane() == 0) {
            // Check if we should handle pyramid turn-in instead of walking to start
            if (Rs2Inventory.isFull() && Rs2Inventory.contains(ItemID.PYRAMID_TOP)) {
                if (!state.isHandlingPyramidTurnIn()) {
                    debugLog("Inventory is full with pyramid tops - going to Simon instead of pyramid start");
                    state.startPyramidTurnIn();
                }
                // Handle turn-in instead of walking to start
                handlePyramidTurnIn();
                return true; // Return true to prevent other actions
            }
            
            int distanceToStart = playerLocation.distanceTo(START_POINT);
            if (distanceToStart > 3) {
                // Try to directly click on the pyramid stairs if visible
                TileObject pyramidStairs = Rs2GameObject.findObjectByIdAndDistance(10857, 10);
                if (pyramidStairs != null && pyramidStairs.getWorldLocation().distanceTo(START_POINT) <= 2) {
                    debugLog("Clicking directly on pyramid stairs (distance: " + distanceToStart + ")");
                    if (Rs2GameObject.interact(pyramidStairs)) {
                        Global.sleep(600, 800); // Small delay after clicking
                        return true;
                    }
                }
                
                // Fall back to walking if stairs not found or interaction failed
                debugLog("Walking to pyramid start point (distance: " + distanceToStart + ")");
                Rs2Walker.walkTo(START_POINT, 2);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean waitForCompletion(int agilityExp, int plane) {
        // Mark that we've started an obstacle
        state.recordObstacleStart();
        
        // Note: The flags state.isDoingCrossGap() and state.isDoingXpObstacle() 
        // are set by getCurrentObstacle() and should remain set during this wait
        
        // Simplified wait logic using XP drops as primary signal
        double initialHealth = Rs2Player.getHealthPercentage();
        int timeoutMs = 8000; // 8 second timeout
        final long startTime = System.currentTimeMillis();
        
        // Track XP gains
        int lastKnownXp = agilityExp;
        boolean receivedXp = false;
        boolean hitByStoneBlock = false;
        
        // Track starting position
        WorldPoint startPos = Rs2Player.getWorldLocation();
        
        // Check if we're at the climbing rocks position (pyramid collection)
        boolean isClimbingRocksForPyramid = startPos.getPlane() == 3 && 
            startPos.getX() >= 3042 && startPos.getX() <= 3043 &&
            startPos.getY() >= 4697 && startPos.getY() <= 4698;
        
        debugLog("Starting obstacle at " + startPos + ", initial XP: " + agilityExp);
        debugLog("Flags: CrossGap=" + state.isDoingCrossGap() + ", XpObstacle=" + state.isDoingXpObstacle());
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            int currentXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
            int currentPlane = Microbot.getClient().getTopLevelWorldView().getPlane();
            double currentHealth = Rs2Player.getHealthPercentage();
            WorldPoint currentPos = Rs2Player.getWorldLocation();
            
            // Special case: Climbing rocks for pyramid collection (no XP)
            if (isClimbingRocksForPyramid) {
                if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && System.currentTimeMillis() - startTime > 1500) {
                    debugLog("Climbing rocks action completed");
                    state.recordClimbingRocks();
                    // Clear any flags that might have been set
                    if (state.isDoingXpObstacle()) {
                        debugLog("WARNING: Clearing XP obstacle flag from climbing rocks path");
                        state.clearXpObstacle();
                    }
                    if (state.isDoingCrossGap()) {
                        state.clearCrossGap();
                    }
                    Global.sleep(300, 400);
                    return true;
                }
                Global.sleep(50);
                continue;
            }
            
            // Check for XP gain
            if (currentXp != lastKnownXp) {
                int xpGained = currentXp - lastKnownXp;
                
                // Check if this is a stone block (12 XP)
                if (xpGained == 12) {
                    debugLog("Hit by stone block (12 XP) - ignoring and continuing to wait");
                    hitByStoneBlock = true;
                    lastKnownXp = currentXp;
                    continue; // Don't count stone block as completion
                }
                
                // Any other XP gain means obstacle is complete (for XP-granting obstacles)
                debugLog("Received " + xpGained + " XP - obstacle complete!");
                receivedXp = true;
                lastKnownXp = currentXp;
                
                // Check if this was a Cross Gap obstacle
                boolean wasCrossGap = state.isDoingCrossGap();
                
                // For Cross Gap, ensure minimum time has passed even with XP
                if (wasCrossGap && System.currentTimeMillis() - startTime < 3500) {
                    long waitTime = 3500 - (System.currentTimeMillis() - startTime);
                    debugLog("Cross Gap - waiting additional " + waitTime + "ms for minimum duration");
                    Global.sleep((int)waitTime);
                }
                
                // Clear flags since we received XP
                if (state.isDoingCrossGap()) {
                    debugLog("Cross Gap completed with XP - clearing flag");
                    state.clearCrossGap();
                }
                if (state.isDoingXpObstacle()) {
                    debugLog("XP obstacle completed - clearing flag");
                    state.clearXpObstacle();
                }
                
                // Add delay to ensure animation finishes
                // Cross Gap needs longer delay even after XP
                if (wasCrossGap) {
                    debugLog("Cross Gap - waiting longer for animation to fully complete");
                    Global.sleep(800, 1000);
                } else {
                    Global.sleep(200, 300);
                }
                return true;
            }
            
            // Quick checks for other completion conditions
            
            // Plane change (stairs/doorway)
            if (currentPlane != plane) {
                debugLog("Plane changed - obstacle complete");
                // Clear flags when plane changes
                if (state.isDoingCrossGap()) {
                    debugLog("Clearing Cross Gap flag due to plane change");
                    state.clearCrossGap();
                }
                if (state.isDoingXpObstacle()) {
                    debugLog("Clearing XP obstacle flag due to plane change");
                    state.clearXpObstacle();
                }
                Global.sleep(200, 300);
                return true;
            }
            
            // Health loss (failed obstacle)
            if (currentHealth < initialHealth) {
                debugLog("Failed obstacle (lost health)");
                // Clear flags if we failed
                if (state.isDoingCrossGap()) {
                    state.clearCrossGap();
                }
                if (state.isDoingXpObstacle()) {
                    state.clearXpObstacle();
                }
                return true;
            }
            
            // For non-XP obstacles (stairs, doorway), check if not moving/animating
            // Only check after at least 1 second to allow obstacle to start
            if (System.currentTimeMillis() - startTime > 1000) {
                // If we haven't received XP and are not moving/animating, check if we moved
                if (!receivedXp && !Rs2Player.isMoving() && !Rs2Player.isAnimating()) {
                    int distanceMoved = currentPos.distanceTo(startPos);
                    
                    // If we're expecting XP (flag is set), don't complete based on movement alone
                    if (state.isDoingXpObstacle()) {
                        // Special handling for Cross Gap - it moves >3 tiles but takes 6+ seconds
                        if (state.isDoingCrossGap()) {
                            // Cross Gap needs at least 6 seconds to complete
                            if (System.currentTimeMillis() - startTime < 6000) {
                                continue; // Keep waiting for Cross Gap
                            }
                            // After 6 seconds, only complete if timeout fully expires
                            // Don't use movement check for Cross Gap as it moves >3 tiles during animation
                            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                                debugLog("Cross Gap timeout after " + (System.currentTimeMillis() - startTime) + "ms - completing");
                                state.clearCrossGap();
                                state.clearXpObstacle();
                                return true;
                            }
                            // Otherwise keep waiting for XP
                            continue;
                        }
                        
                        // For non-Cross-Gap XP obstacles, use normal logic
                        // Keep waiting for XP - don't complete based on movement
                        if (System.currentTimeMillis() - startTime < 4000) {
                            continue; // Keep waiting for XP
                        }
                        // After 4 seconds without XP, check if we at least moved
                        if (distanceMoved >= 3) {
                            debugLog("WARNING: Expected XP but didn't receive it after 4s - completing based on movement");
                            // Clear flags since something went wrong
                            state.clearCrossGap();
                            state.clearXpObstacle();
                            return true;
                        }
                    }
                    
                    // For non-XP obstacles, movement indicates completion
                    if (distanceMoved >= 3 && !state.isDoingXpObstacle()) {
                        debugLog("Non-XP obstacle complete (moved " + distanceMoved + " tiles)");
                        
                        // Clear flags in case they were set
                        if (state.isDoingCrossGap()) {
                            debugLog("Clearing Cross Gap flag (movement completion)");
                            state.clearCrossGap();
                        }
                        if (state.isDoingXpObstacle()) {
                            debugLog("Clearing XP obstacle flag (movement completion)");
                            state.clearXpObstacle();
                        }
                        
                        Global.sleep(300, 400);
                        return true;
                    }
                    
                    // If we were hit by stone block and haven't received proper XP, retry
                    if (hitByStoneBlock && !receivedXp && System.currentTimeMillis() - startTime > 2000) {
                        debugLog("Stone block interrupted obstacle, no proper XP received - retrying");
                        // Clear flags since we're going to retry
                        if (state.isDoingCrossGap()) {
                            debugLog("Clearing Cross Gap flag for retry");
                            state.clearCrossGap();
                        }
                        if (state.isDoingXpObstacle()) {
                            debugLog("Clearing XP obstacle flag for retry");
                            state.clearXpObstacle();
                        }
                        Global.sleep(800, 1200);
                        return false; // Retry the obstacle
                    }
                }
            }
            
            Global.sleep(50);
        }
        
        // Timeout reached
        debugLog("Timeout after " + timeoutMs + "ms - checking if made progress");
        int distanceMoved = Rs2Player.getWorldLocation().distanceTo(startPos);
        
        // Clear flags on timeout
        if (state.isDoingCrossGap()) {
            debugLog("Clearing Cross Gap flag due to timeout");
            state.clearCrossGap();
        }
        if (state.isDoingXpObstacle()) {
            debugLog("Clearing XP obstacle flag due to timeout");
            state.clearXpObstacle();
        }
        
        // If we received XP or moved significantly, consider it successful
        if (receivedXp || distanceMoved >= 3) {
            debugLog("Made progress despite timeout (XP: " + receivedXp + ", moved: " + distanceMoved + " tiles)");
            return true;
        }
        
        debugLog("No progress made - will retry");
        return false;
    }
    
    @Override
    public Integer getRequiredLevel() {
        return 30;
    }
    
    @Override
    public boolean canBeBoosted() {
        return true;
    }
    
    @Override
    public int getLootDistance() {
        return 5; // Pyramid tops can be a bit further away
    }
    
    private boolean handlePyramidTurnIn() {
        try {
            // Check if we still have pyramid tops
            if (!Rs2Inventory.contains(ItemID.PYRAMID_TOP)) {
                debugLog("No pyramid tops found in inventory - returning to course");
                state.clearPyramidTurnIn();
                return false;
            }
            
            // Try to find Simon
            NPC simon = Rs2Npc.getNpc(SIMON_NAME);
            
            // If Simon is found and reachable, use pyramid top on him
            if (simon != null && Rs2GameObject.canReach(simon.getWorldLocation())) {
                debugLog("Simon found and reachable, using pyramid top");
                
                // Handle dialogue first if already in dialogue
                if (Rs2Dialogue.isInDialogue()) {
                    // Continue through dialogue
                    if (Rs2Dialogue.hasContinue()) {
                        Rs2Dialogue.clickContinue();
                        Global.sleep(600, 1000);
                        return true;
                    }
                    
                    // Select option to claim reward if available
                    if (Rs2Dialogue.hasDialogueOption("I've got some pyramid tops for you.")) {
                        Rs2Dialogue.clickOption("I've got some pyramid tops for you.");
                        Global.sleep(600, 1000);
                        return true;
                    }
                } else {
                    // Not in dialogue, use pyramid top on Simon
                    boolean used = Rs2Inventory.useItemOnNpc(ItemID.PYRAMID_TOP, simon);
                    if (used) {
                        debugLog("Successfully used pyramid top on Simon");
                        Global.sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
                    } else {
                        debugLog("Failed to use pyramid top on Simon");
                    }
                }
                return true;
            }
            
            // Simon not found or not reachable, walk to him
            debugLog("Simon not found or not reachable, walking to location " + SIMON_LOCATION);
            Rs2Walker.walkTo(SIMON_LOCATION, 2);
            Rs2Player.waitForWalking();
            
            // Check if we've completed the turn-in (no pyramids left and not in dialogue)
            if (!Rs2Inventory.contains(ItemID.PYRAMID_TOP) && !Rs2Dialogue.isInDialogue()) {
                debugLog("Pyramid tops turned in successfully");
                state.clearPyramidTurnIn();
                
                // Walk back towards the pyramid start
                WorldPoint currentPos = Rs2Player.getWorldLocation();
                if (currentPos.distanceTo(START_POINT) > 10) {
                    debugLog("Walking back to pyramid start");
                    Rs2Walker.walkTo(START_POINT);
                }
                return false; // Done with turn-in, can resume obstacles
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error in handlePyramidTurnIn: " + e.getMessage());
            e.printStackTrace();
            state.clearPyramidTurnIn();
            return false;
        }
    }
    
    /**
     * Checks for empty waterskins in inventory and drops them
     * @return true if waterskins were dropped, false otherwise
     */
    private boolean handleEmptyWaterskins() {
        if (Rs2Inventory.contains(ItemID.WATERSKIN0)) {
            Microbot.log("Found empty waterskin(s), dropping them");
            Rs2Inventory.drop(ItemID.WATERSKIN0);
            Global.sleep(300, 500);
            return true;
        }
        return false;
    }
    
}