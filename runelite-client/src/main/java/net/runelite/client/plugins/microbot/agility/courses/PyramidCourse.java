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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PyramidCourse implements AgilityCourseHandler {
    
    private static final WorldPoint START_POINT = new WorldPoint(3354, 2830, 0);
    private static final WorldPoint SIMON_LOCATION = new WorldPoint(3343, 2827, 0);
    private static final String SIMON_NAME = "Simon Templeton";
    private static final int PYRAMID_TOP_REGION = 12105;
    
    // Track when we started an obstacle to prevent clicking during traversal
    private static long lastObstacleStartTime = 0;
    private static final long OBSTACLE_COOLDOWN = 1500; // 1.5 seconds minimum between obstacles
    
    // Track if we've already clicked climbing rocks this session
    private static long lastClimbingRocksTime = 0;
    private static final long CLIMBING_ROCKS_COOLDOWN = 30000; // 30 seconds - pyramid respawn time
    
    // Track Cross Gap obstacles specifically
    private static long lastCrossGapTime = 0;
    private static final long CROSS_GAP_COOLDOWN = 4500; // 4.5 seconds for Cross Gap - wait for XP
    
    // Track if we're handling pyramid turn-in
    private static boolean handlingPyramidTurnIn = false;
    
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
    
    
    // Pyramid obstacle definitions based on player position
    private static class ObstacleArea {
        final int minX, minY, maxX, maxY, plane;
        final int obstacleId;
        final WorldPoint obstacleLocation;
        final String name;
        
        ObstacleArea(int minX, int minY, int maxX, int maxY, int plane, int obstacleId, WorldPoint obstacleLocation, String name) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.plane = plane;
            this.obstacleId = obstacleId;
            this.obstacleLocation = obstacleLocation;
            this.name = name;
        }
        
        boolean containsPlayer(WorldPoint playerPos) {
            return playerPos.getPlane() == plane &&
                   playerPos.getX() >= minX && playerPos.getX() <= maxX &&
                   playerPos.getY() >= minY && playerPos.getY() <= maxY;
        }
    }
    
    // Define precise obstacle areas based on logged player positions
    private static final List<ObstacleArea> OBSTACLE_AREAS = Arrays.asList(
        // Floor 0 -> 1
        new ObstacleArea(3354, 2830, 3354, 2830, 0, 10857, new WorldPoint(3354, 2831, 0), "Stairs (up)"),
        
        // Floor 1 - Clockwise path (precise positions from logs)
        // After stairs, player can land at (3354-3355, 2833)
        new ObstacleArea(3354, 2833, 3355, 2833, 1, 10865, new WorldPoint(3354, 2849, 1), "Low wall"),
        
        // Low wall has intermediate positions as player walks north
        new ObstacleArea(3354, 2834, 3354, 2848, 1, 10865, new WorldPoint(3354, 2849, 1), "Low wall"),
        
        // After low wall, player lands at (3354, 2850) or (3355, 2850)
        new ObstacleArea(3354, 2850, 3355, 2850, 1, 10860, new WorldPoint(3364, 2851, 1), "Ledge (east)"),
        
        // Full area for approaching and traversing the ledge (includes area from (3354, 2851) to (3363, 2852))
        new ObstacleArea(3354, 2851, 3363, 2852, 1, 10860, new WorldPoint(3364, 2851, 1), "Ledge (east)"),
        
        // After ledge, approaching plank from north
        new ObstacleArea(3364, 2850, 3375, 2852, 1, 10868, new WorldPoint(3368, 2845, 1), "Plank (approach)"),
        
        // East side approach to plank (if player went around)
        new ObstacleArea(3374, 2845, 3375, 2849, 1, 10868, new WorldPoint(3368, 2845, 1), "Plank (east)"),
        
        // After crossing plank, player is south/west of it
        new ObstacleArea(3368, 2834, 3375, 2844, 1, 10882, new WorldPoint(3371, 2831, 1), "Gap (floor 1)"),
        
        // After gap, player at (3371-3372, 2832)
        new ObstacleArea(3371, 2832, 3372, 2832, 1, 10886, new WorldPoint(3362, 2831, 1), "Ledge 3"),
        
        // Moving west along ledge 3
        new ObstacleArea(3362, 2832, 3370, 2832, 1, 10886, new WorldPoint(3362, 2831, 1), "Ledge 3"),
        
        // After ledge 3, player at (3361-3362, 2832)
        new ObstacleArea(3361, 2832, 3362, 2832, 1, 10857, new WorldPoint(3356, 2831, 1), "Stairs (floor 1 up)"),
        
        // Approaching stairs from west
        new ObstacleArea(3356, 2831, 3360, 2833, 1, 10857, new WorldPoint(3356, 2831, 1), "Stairs (floor 1 up)"),
        
        // Floor 2 - Three gaps in sequence
        // After stairs from floor 1, player at (3356-3357, 2835)
        new ObstacleArea(3356, 2835, 3357, 2837, 2, 10884, new WorldPoint(3356, 2835, 2), "Gap Cross 1 (floor 2)"),
        
        // After first gap cross, player at ~(3356-3357, 2838-2840)
        new ObstacleArea(3356, 2838, 3357, 2847, 2, 10859, new WorldPoint(3356, 2841, 2), "Gap Jump (floor 2)"),
        
        // After gap jump, player continues north to third gap
        new ObstacleArea(3356, 2848, 3360, 2850, 2, 10861, new WorldPoint(3356, 2849, 2), "Gap Cross 2 (floor 2)"),
        
        // After Gap 10861, player needs to go to Ledge 10860
        // Large area from north side to east side where player travels after Gap 10861
        new ObstacleArea(3372, 2841, 3373, 2850, 2, 10860, new WorldPoint(3372, 2839, 2), "Ledge (floor 2) after gap - east path"),
        new ObstacleArea(3364, 2849, 3373, 2850, 2, 10860, new WorldPoint(3372, 2839, 2), "Ledge (floor 2) after gap - south path"),
        
        // Old positions kept for other scenarios
        new ObstacleArea(3359, 2850, 3360, 2850, 2, 10860, new WorldPoint(3364, 2841, 2), "Ledge (floor 2) after gap"),
        new ObstacleArea(3361, 2849, 3363, 2850, 2, 10860, new WorldPoint(3364, 2841, 2), "Ledge (floor 2) south approach"),
        
        // After crossing the ledge - player lands near the ledge on east side
        new ObstacleArea(3370, 2834, 3373, 2840, 2, 10865, new WorldPoint(3370, 2833, 2), "Low wall (floor 2) after ledge"),
        
        // Player at (3372, 2836) after crossing wrong east ledge - redirect to correct path
        new ObstacleArea(3372, 2835, 3373, 2839, 2, 10860, new WorldPoint(3364, 2841, 2), "Ledge (floor 2) from wrong position"),
        
        // At or near the actual ledge obstacle (which is at 3364,2841 size 10x11)
        new ObstacleArea(3364, 2841, 3373, 2851, 2, 10865, new WorldPoint(3370, 2833, 2), "Low wall (floor 2)"),
        
        // After crossing ledge, player ends at (3364, 2851) or nearby
        new ObstacleArea(3364, 2851, 3365, 2851, 2, 10865, new WorldPoint(3370, 2833, 2), "Low wall (floor 2) from ledge"),
        
        // After ledge completion and walking south, player approaches low wall
        new ObstacleArea(3364, 2849, 3365, 2850, 2, 10865, new WorldPoint(3370, 2833, 2), "Low wall (floor 2) approach"),
        
        // Walking east toward low wall
        new ObstacleArea(3366, 2849, 3373, 2851, 2, 10865, new WorldPoint(3370, 2833, 2), "Low wall (floor 2) east"),
        
        // After low wall at (3369-3370, 2834)
        new ObstacleArea(3369, 2834, 3370, 2834, 2, 10859, new WorldPoint(3365, 2833, 2), "Gap jump (floor 2 end)"),
        
        // After gap jump at (3363-3365, 2834)
        new ObstacleArea(3363, 2834, 3365, 2834, 2, 10857, new WorldPoint(3358, 2833, 2), "Stairs (floor 2 up)"),
        
        // Approaching stairs
        new ObstacleArea(3358, 2833, 3362, 2834, 2, 10857, new WorldPoint(3358, 2833, 2), "Stairs (floor 2 up)"),
        
        // Floor 3 - Clockwise path (precise positions from ObstacleData)
        // After stairs from floor 2, player at (3358, 2837)
        new ObstacleArea(3358, 2837, 3359, 2838, 3, 10865, new WorldPoint(3358, 2837, 3), "Low wall (floor 3)"),
        
        // After low wall, player at (3358, 2840)
        new ObstacleArea(3358, 2840, 3359, 2842, 3, 10888, new WorldPoint(3358, 2840, 3), "Ledge 2"),
        
        // After Ledge 10888, large area before Gap jumps on floor 3
        new ObstacleArea(3358, 2847, 3371, 2848, 3, 10859, new WorldPoint(3358, 2843, 3), "Gap jump area (floor 3) after ledge"),
        new ObstacleArea(3370, 2843, 3371, 2848, 3, 10859, new WorldPoint(3358, 2843, 3), "Gap jump area (floor 3) east"),
        
        // Original gap areas for other positions
        new ObstacleArea(3358, 2843, 3362, 2846, 3, 10859, new WorldPoint(3358, 2843, 3), "Gap jump 1 (floor 3)"),
        new ObstacleArea(3363, 2843, 3367, 2846, 3, 10859, new WorldPoint(3363, 2843, 3), "Gap jump 2 (floor 3)"),
        new ObstacleArea(3368, 2843, 3369, 2846, 3, 10859, new WorldPoint(3368, 2843, 3), "Gap jump 3 (floor 3)"),
        
        // After gap jump, player on east side for plank
        new ObstacleArea(3370, 2835, 3371, 2840, 3, 10868, new WorldPoint(3370, 2835, 3), "Plank (floor 3)"),
        
        // After plank, heading to stairs
        new ObstacleArea(3360, 2835, 3369, 2836, 3, 10857, new WorldPoint(3360, 2835, 3), "Stairs (floor 3 up)"),
        
        // Floor 4 (uses special coordinate system, plane=2)
        // After stairs from floor 3, player arrives at (3041, 4695) - define exact 2x2 area
        new ObstacleArea(3040, 4695, 3041, 4696, 2, 10859, new WorldPoint(3040, 4697, 2), "Gap jump (floor 4 start)"),
        new ObstacleArea(3042, 4695, 3042, 4697, 2, 10859, new WorldPoint(3040, 4695, 2), "Gap jump (floor 4 start alt)"),
        
        // After first gap jump - wider area
        new ObstacleArea(3040, 4698, 3042, 4702, 2, 10865, new WorldPoint(3040, 4699, 2), "Low wall (floor 4)"),
        new ObstacleArea(3041, 4697, 3042, 4697, 2, 10865, new WorldPoint(3040, 4699, 2), "Low wall (floor 4 alt)"),
        
        // After low wall, player lands at (3043, 4701-4702) - need second gap
        new ObstacleArea(3043, 4701, 3043, 4702, 2, 10859, new WorldPoint(3048, 4695, 2), "Gap jump (floor 4 second)"),
        
        // Larger area for second gap
        new ObstacleArea(3043, 4695, 3049, 4700, 2, 10859, new WorldPoint(3048, 4695, 2), "Gap jump (floor 4 mid)"),
        
        // After gap jump, low wall on east side
        new ObstacleArea(3047, 4693, 3049, 4696, 2, 10865, new WorldPoint(3047, 4693, 2), "Low wall (floor 4 end)"),
        new ObstacleArea(3048, 4695, 3049, 4696, 2, 10865, new WorldPoint(3047, 4693, 2), "Low wall (floor 4 end alt)"),
        
        // After low wall, stairs to go up - expanded area
        new ObstacleArea(3042, 4693, 3047, 4695, 2, 10857, new WorldPoint(3042, 4693, 2), "Stairs (floor 4 up)"),
        
        // Floor 5 (pyramid top, plane=3)
        // After stairs from floor 4, player at (3042, 4697) - this is where we grab pyramid
        new ObstacleArea(3042, 4697, 3043, 4698, 3, 10851, new WorldPoint(3042, 4697, 3), "Climbing rocks (grab pyramid)"),
        
        // Same position after grabbing pyramid - need to jump gap
        new ObstacleArea(3042, 4697, 3043, 4698, 3, 10859, new WorldPoint(3046, 4698, 3), "Gap jump (floor 5) from pyramid spot"),
        
        // After grabbing pyramid with climbing rocks, need to jump gap
        new ObstacleArea(3044, 4697, 3046, 4700, 3, 10859, new WorldPoint(3046, 4698, 3), "Gap jump (floor 5)"),
        
        // After gap jump, use doorway to exit
        new ObstacleArea(3047, 4696, 3047, 4700, 3, 10855, new WorldPoint(3044, 4695, 3), "Doorway (floor 5)"),
        
        // Additional area for after gap but before doorway
        new ObstacleArea(3044, 4695, 3046, 4696, 3, 10855, new WorldPoint(3044, 4695, 3), "Doorway (floor 5 approach)")
    );
    
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
        
        Microbot.log("=== getCurrentObstacle called - Player at " + playerPos + " (plane: " + playerPos.getPlane() + ") ===");
        
        // Check if inventory is full AND we're on ground level (not inside pyramid)
        if (Rs2Inventory.isFull() && playerPos.getPlane() == 0) {
            if (Rs2Inventory.contains(ItemID.PYRAMID_TOP)) {
                // Inventory is full and has pyramid tops - handle turn-in
                if (!handlingPyramidTurnIn) {
                    Microbot.log("Inventory is full with pyramid tops and on ground level - going to Simon Templeton");
                    handlingPyramidTurnIn = true;
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
            handlingPyramidTurnIn = false;
        }
        
        // NEVER return an obstacle while moving or animating
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            Microbot.log("Player is moving/animating, returning null to prevent clicking");
            return null;
        }
        
        // Special blocking for Cross Gap obstacles
        if (System.currentTimeMillis() - lastCrossGapTime < CROSS_GAP_COOLDOWN) {
            Microbot.log("Cross Gap cooldown active, returning null");
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
            Microbot.log("Player started moving/animating after brief pause, returning null");
            return null;
        }
        
        // Prevent getting obstacles too quickly after starting one
        if (System.currentTimeMillis() - lastObstacleStartTime < OBSTACLE_COOLDOWN) {
            Microbot.log("Obstacle cooldown active, returning null to prevent spam clicking");
            return null;
        }
        
        // Find the obstacle area containing the player
        ObstacleArea currentArea = null;
        
        // Debug: log areas being checked for current plane
        Microbot.log("Checking areas for plane " + playerPos.getPlane() + " player position " + playerPos + ":");
        for (ObstacleArea area : OBSTACLE_AREAS) {
            if (area.plane == playerPos.getPlane()) {
                boolean contains = area.containsPlayer(playerPos);
                Microbot.log("  - Area: " + area.name + " at (" + area.minX + "," + area.minY + ") to (" + area.maxX + "," + area.maxY + ") - contains player: " + contains);
                if (contains) {
                    Microbot.log("    -> Obstacle ID: " + area.obstacleId + " at location: " + area.obstacleLocation);
                }
            }
        }
        
        for (ObstacleArea area : OBSTACLE_AREAS) {
            if (area.containsPlayer(playerPos)) {
                // Special check for climbing rocks - skip if we've recently clicked them
                if (area.obstacleId == 10851 && area.name.contains("grab pyramid")) {
                    if (System.currentTimeMillis() - lastClimbingRocksTime < CLIMBING_ROCKS_COOLDOWN) {
                        Microbot.log("Recently clicked climbing rocks, skipping to next area");
                        continue;
                    }
                }
                
                currentArea = area;
                Microbot.log("Found player in area: " + area.name + " (obstacle ID: " + area.obstacleId + ")");
                // Debug: log if this is a plank area
                if (area.obstacleId == 10868) {
                    Microbot.log("  Player in PLANK area - should look for plank end ground object");
                }
                break;
            }
        }
        
        if (currentArea == null) {
            Microbot.log("Player not in any defined obstacle area at " + playerPos + " (plane: " + playerPos.getPlane() + ")");
            
            // Special check for floor 4 start position
            if (playerPos.getPlane() == 2 && playerPos.getX() == 3041 && playerPos.getY() == 4695) {
                Microbot.log("SPECIAL CASE: Player at floor 4 start position (3041, 4695)");
                // Manually find the gap
                TileObject gap = findNearestObstacleWithinDistance(playerPos, 10859, 5);
                if (gap != null) {
                    Microbot.log("Found Gap manually at " + gap.getWorldLocation());
                    return gap;
                }
            }
            
            // Log all areas on current plane for debugging
            Microbot.log("Available areas on plane " + playerPos.getPlane() + ":");
            int count = 0;
            for (ObstacleArea area : OBSTACLE_AREAS) {
                if (area.plane == playerPos.getPlane()) {
                    Microbot.log("  - " + area.name + " at (" + area.minX + "," + area.minY + ") to (" + area.maxX + "," + area.maxY + ")");
                    count++;
                    if (count > 10) {
                        Microbot.log("  ... and more areas");
                        break;
                    }
                }
            }
            
            // Special case: If player just climbed to floor 1, direct them to low wall
            if (playerPos.getPlane() == 1 && playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() == 2833) {
                Microbot.log("Player just arrived on floor 1, looking for low wall");
                // Find the low wall obstacle
                TileObject lowWall = findNearestObstacle(playerPos, 10865);
                if (lowWall != null) {
                    return lowWall;
                }
            }
            
            // Try to find the nearest obstacle on the current plane
            Microbot.log("Looking for nearest pyramid obstacle...");
            return findNearestPyramidObstacle(playerPos);
        }
        
        Microbot.log("Player in area for: " + currentArea.name + " at " + playerPos + " (plane: " + playerPos.getPlane() + ")");
        
        // Find the specific obstacle instance
        TileObject obstacle = null;
        
        // For gaps and ledges, always find the nearest one since there can be multiple
        // Also for floor 4, always use nearest search since obstacles can be multi-tile
        if (currentArea.obstacleId == 10859 || currentArea.obstacleId == 10861 || currentArea.obstacleId == 10884 || currentArea.obstacleId == 10860 || playerPos.getPlane() == 2) {
            Microbot.log("Looking for nearest " + currentArea.name);
            
            // Use strict sequential checking to prevent skipping ahead
            obstacle = findNearestObstacleStrict(playerPos, currentArea.obstacleId, currentArea);
        } else {
            obstacle = findObstacleAt(currentArea.obstacleLocation, currentArea.obstacleId);
            
            if (obstacle == null) {
                Microbot.log("Could not find " + currentArea.name + " (ID: " + currentArea.obstacleId + ") at expected location " + currentArea.obstacleLocation);
                // Try to find any instance of this obstacle type nearby with strict checking
                obstacle = findNearestObstacleStrict(playerPos, currentArea.obstacleId, currentArea);
            }
        }
        
        if (obstacle != null) {
            Microbot.log("Selected obstacle: " + currentArea.name + " (ID: " + currentArea.obstacleId + ") at " + obstacle.getWorldLocation() + " for player at " + playerPos);
            
            // Track Cross Gap obstacles specifically
            if (currentArea.name.contains("Cross") || currentArea.name.contains("Gap Cross")) {
                lastCrossGapTime = System.currentTimeMillis();
                Microbot.log("Detected Cross Gap obstacle - setting 3.5 second cooldown");
            }
        } else {
            Microbot.log("ERROR: Could not find any obstacle for area: " + currentArea.name + " (ID: " + currentArea.obstacleId + ")");
        }
        
        // Special handling for pyramid top region - if completed, look for stairs down
        if (obstacle == null && playerPos.getRegionID() == PYRAMID_TOP_REGION && playerPos.getPlane() == 3) {
            TileObject stairs = Rs2GameObject.getTileObject(10857);
            if (stairs != null) {
                Microbot.log("No obstacle found on pyramid top, found stairs to go back down");
                return stairs;
            }
        }
        
        return obstacle;
    }
    
    private TileObject findObstacleAt(WorldPoint location, int obstacleId) {
        Microbot.log("findObstacleAt: Looking for obstacle " + obstacleId + " at " + location);
        
        // Special handling for plank end which is a ground object
        if (obstacleId == 10868) {
            List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
            Microbot.log("Looking for plank end at " + location + ", checking " + groundObjects.size() + " ground objects");
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId && go.getWorldLocation().equals(location)) {
                    Microbot.log("Found plank end (ground object) at " + go.getWorldLocation());
                    return go;
                }
            }
            Microbot.log("No plank end found at expected location " + location);
            // List all plank ends found
            for (GroundObject go : groundObjects) {
                if (go.getId() == obstacleId) {
                    Microbot.log("  Found plank end at " + go.getWorldLocation() + " (not at expected location)");
                }
            }
            return null;
        }
        
        // Normal game objects
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getWorldLocation().equals(location)
        );
        
        Microbot.log("Found " + obstacles.size() + " obstacles with ID " + obstacleId + " at " + location);
        
        if (obstacles.isEmpty()) {
            // Log all obstacles of this type on the current plane
            List<TileObject> allObstaclesOfType = Rs2GameObject.getAll(obj -> 
                obj.getId() == obstacleId && 
                obj.getPlane() == location.getPlane()
            );
            Microbot.log("No obstacle found at exact location. Found " + allObstaclesOfType.size() + " obstacles with ID " + obstacleId + " on plane " + location.getPlane() + ":");
            for (TileObject obj : allObstaclesOfType) {
                Microbot.log("  - " + obstacleId + " at " + obj.getWorldLocation());
            }
            return null;
        }
        
        return obstacles.get(0);
    }
    
    private TileObject findNearestObstacleStrict(WorldPoint playerPos, int obstacleId, ObstacleArea currentArea) {
        Microbot.log("Looking for obstacle " + obstacleId + " with strict sequential checking");
        
        // Special handling for floor 4 gaps FIRST - need to select the correct one
        // Check if we're on floor 4 (plane 2) and looking for a gap, regardless of exact area name
        if (playerPos.getPlane() == 2 && obstacleId == 10859) {
            // If player is after low wall at (3043, 4701-4702), we need the second gap
            if (playerPos.getX() == 3043 && playerPos.getY() >= 4701) {
                Microbot.log("Player after low wall on floor 4, looking for second gap at (3048, 4695)");
                // Find the gap at (3048, 4695) specifically
                List<TileObject> gaps = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == playerPos.getPlane() &&
                    obj.getWorldLocation().getX() >= 3047 && obj.getWorldLocation().getX() <= 3049 &&
                    obj.getWorldLocation().getY() >= 4694 && obj.getWorldLocation().getY() <= 4696
                );
                
                if (!gaps.isEmpty()) {
                    TileObject secondGap = gaps.get(0);
                    Microbot.log("Found second gap at " + secondGap.getWorldLocation());
                    return secondGap;
                } else {
                    Microbot.log("Could not find second gap on floor 4!");
                }
            }
            // If player is at start of floor 4, we need the first gap
            else if (playerPos.getX() >= 3040 && playerPos.getX() <= 3042 && 
                     playerPos.getY() >= 4695 && playerPos.getY() <= 4697) {
                Microbot.log("Player at start of floor 4, looking for first gap");
                // Find the gap at (3040, 4697) specifically
                List<TileObject> gaps = Rs2GameObject.getAll(obj -> 
                    obj.getId() == obstacleId && 
                    obj.getPlane() == playerPos.getPlane() &&
                    obj.getWorldLocation().getX() >= 3039 && obj.getWorldLocation().getX() <= 3041 &&
                    obj.getWorldLocation().getY() >= 4696 && obj.getWorldLocation().getY() <= 4698
                );
                
                if (!gaps.isEmpty()) {
                    TileObject firstGap = gaps.get(0);
                    Microbot.log("Found first gap at " + firstGap.getWorldLocation());
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
                    Microbot.log("Found strictly checked obstacle at " + nearest.getWorldLocation());
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
        Microbot.log("Looking for obstacle " + obstacleId + " within " + maxDistance + " tiles");
        
        List<TileObject> obstacles = Rs2GameObject.getAll(obj -> 
            obj.getId() == obstacleId && 
            obj.getPlane() == playerPos.getPlane() &&
            obj.getWorldLocation().distanceTo(playerPos) <= maxDistance
        );
        
        if (obstacles.isEmpty()) {
            Microbot.log("No obstacles found within " + maxDistance + " tiles");
            return null;
        }
        
        // Log all found obstacles for debugging
        Microbot.log("Found " + obstacles.size() + " obstacles within " + maxDistance + " tiles:");
        for (TileObject obj : obstacles) {
            Microbot.log("  - " + obstacleId + " at " + obj.getWorldLocation() + " (distance: " + obj.getWorldLocation().distanceTo(playerPos) + ")");
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
            Microbot.log("Special handling for floor 2 Ledge at player position " + playerPos);
            
            // If player is anywhere in the path from Gap 10861 to Ledge, use east ledge
            if ((playerPos.getX() >= 3372 && playerPos.getX() <= 3373 && playerPos.getY() >= 2841 && playerPos.getY() <= 2850) ||
                (playerPos.getX() >= 3364 && playerPos.getX() <= 3373 && playerPos.getY() >= 2849 && playerPos.getY() <= 2850)) {
                Microbot.log("Player in path from Gap 10861 to Ledge, looking for east Ledge at (3372, 2839)");
                
                // Find the specific ledge at (3372, 2839)
                TileObject eastLedge = findObstacleAt(new WorldPoint(3372, 2839, 2), obstacleId);
                if (eastLedge != null) {
                    Microbot.log("Found east Ledge at " + eastLedge.getWorldLocation());
                    return eastLedge;
                } else {
                    Microbot.log("Could not find east Ledge at expected location (3372, 2839)");
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
            Microbot.log("Found " + obstacles.size() + " potential ledges on floor 2:");
            for (TileObject obj : obstacles) {
                Microbot.log("  - Ledge at " + obj.getWorldLocation());
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
                Microbot.log("Selected ledge at " + bestLedge.getWorldLocation() + " (closest to expected position " + expectedLedgePos + ")");
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
                Microbot.log("No plank ends (ground objects) found nearby");
                return null;
            }
            
            Microbot.log("Found " + nearbyPlanks.size() + " plank ends nearby");
            for (GroundObject go : nearbyPlanks) {
                Microbot.log("  - Plank end at " + go.getWorldLocation() + " (distance: " + go.getWorldLocation().distanceTo(playerPos) + ")");
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
        Microbot.log("Found " + obstacles.size() + " obstacles with ID " + obstacleId + " on plane " + playerPos.getPlane() + ":");
        for (TileObject obj : obstacles) {
            Microbot.log("  - " + obstacleId + " at " + obj.getWorldLocation() + " (distance: " + obj.getWorldLocation().distanceTo(playerPos) + ")");
        }
        
        // For stairs on floor 1, we need to filter out the wrong stairs
        if (obstacleId == 10857 && playerPos.getPlane() == 1) {
            // If player just climbed up and is at start position (3354-3355, 2833), we should NOT return any stairs
            // The player should go to the low wall instead
            if (playerPos.getX() >= 3354 && playerPos.getX() <= 3355 && playerPos.getY() >= 2833 && playerPos.getY() <= 2835) {
                Microbot.log("Player just climbed to floor 1, should not interact with stairs yet");
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
                Microbot.log("No appropriate stairs found for progression");
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
                Microbot.log("Selected northernmost low wall at " + northWall.getWorldLocation());
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
            Microbot.log("Excluding stairs from search at floor 1 start position");
        }
        
        List<Integer> finalObstacleIds = pyramidObstacleIds;
        
        // First check for ground objects (plank ends)
        List<GroundObject> groundObjects = Rs2GameObject.getGroundObjects();
        for (GroundObject go : groundObjects) {
            if (go.getId() == 10868 && 
                go.getPlane() == playerPos.getPlane() &&
                go.getWorldLocation().distanceTo(playerPos) <= 15) {
                Microbot.log("Found nearby plank end (ground object) at " + go.getWorldLocation());
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
            Microbot.log("No pyramid obstacles found within " + searchDistance + " tiles on plane " + playerPos.getPlane());
            // Try expanding search radius for floor 4 (pyramid top area)
            if (playerPos.getPlane() == 2 && playerPos.getX() >= 3040 && playerPos.getX() <= 3050) {
                Microbot.log("Expanding search for floor 4 pyramid top area...");
                nearbyObstacles = Rs2GameObject.getAll(obj -> 
                    finalObstacleIds.contains(obj.getId()) && 
                    obj.getPlane() == playerPos.getPlane()
                );
            }
        }
        
        Microbot.log("Found " + nearbyObstacles.size() + " pyramid obstacles nearby:");
        for (TileObject obj : nearbyObstacles) {
            Microbot.log("  - ID " + obj.getId() + " at " + obj.getWorldLocation() + " (distance: " + obj.getWorldLocation().distanceTo(playerPos) + ")");
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
                if (!handlingPyramidTurnIn) {
                    Microbot.log("Inventory is full with pyramid tops - going to Simon instead of pyramid start");
                    handlingPyramidTurnIn = true;
                }
                // Handle turn-in instead of walking to start
                handlePyramidTurnIn();
                return true; // Return true to prevent other actions
            }
            
            int distanceToStart = playerLocation.distanceTo(START_POINT);
            if (distanceToStart > 10) {
                Microbot.log("Walking to pyramid start point");
                Rs2Walker.walkTo(START_POINT, 2);
                return true;
            }
        }
        return false;
    }
    
    @Override
    public boolean waitForCompletion(int agilityExp, int plane) {
        // Mark that we've started an obstacle
        lastObstacleStartTime = System.currentTimeMillis();
        
        // Custom wait logic for pyramid obstacles that handles stone blocks
        double initialHealth = Rs2Player.getHealthPercentage();
        int timeoutMs = 10000; // Longer timeout for pyramid
        final long startTime = System.currentTimeMillis();
        
        // Track XP gains to differentiate stone blocks from obstacles
        int totalXpGained = 0;
        int lastKnownXp = agilityExp;
        boolean hitByStoneBlock = false;
        
        // Track starting position
        WorldPoint startPos = Rs2Player.getWorldLocation();
        
        // Check if we're at the climbing rocks position (pyramid collection)
        boolean isClimbingRocksForPyramid = startPos.getPlane() == 3 && 
            startPos.getX() >= 3042 && startPos.getX() <= 3043 &&
            startPos.getY() >= 4697 && startPos.getY() <= 4698;
        
        // Log starting position for debugging planks
        Microbot.log("Starting obstacle at position " + startPos + ", waiting for completion...");
        
        // Track when we stopped moving to add a delay
        long stoppedMovingTime = 0;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            int currentXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
            int currentPlane = Microbot.getClient().getTopLevelWorldView().getPlane();
            double currentHealth = Rs2Player.getHealthPercentage();
            WorldPoint currentPos = Rs2Player.getWorldLocation();
            
            // Special check for climbing rocks pyramid collection
            if (isClimbingRocksForPyramid) {
                // Wait for animation to complete first
                if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && System.currentTimeMillis() - startTime > 1500) {
                    // The climbing rocks animation is done
                    // Either we got pyramid (dialog) or "You find nothing" message
                    // In both cases, we should move on to the gap
                    Microbot.log("Climbing rocks action completed - moving to next obstacle");
                    lastClimbingRocksTime = System.currentTimeMillis(); // Mark that we've clicked climbing rocks
                    Global.sleep(600, 800);
                    return true;
                }
                // Keep waiting while animating
                Global.sleep(50);
                continue;
            }
            
            // CRITICAL: Never return true while still moving or animating
            boolean isMoving = Rs2Player.isMoving() || Rs2Player.isAnimating();
            if (isMoving) {
                // Keep waiting - don't process any completion logic while moving
                stoppedMovingTime = 0; // Reset the stopped timer
                Global.sleep(50);
                continue;
            }
            
            // We've stopped moving/animating - track when this happened
            if (stoppedMovingTime == 0) {
                stoppedMovingTime = System.currentTimeMillis();
            }
            
            // Wait a bit after stopping to ensure animations complete
            int waitAfterStop = 400; // Default wait after stop
            
            // Check if this is a Cross Gap obstacle
            boolean isCrossGap = (startPos.getX() == 3356 && (startPos.getY() == 2835 || startPos.getY() == 2849)) ||
                                (startPos.getX() >= 3356 && startPos.getX() <= 3360 && startPos.getY() >= 2848 && startPos.getY() <= 2850);
            
            // Gap obstacles need longer wait due to animation pauses
            if (totalXpGained == 0 && (startPos.distanceTo(currentPos) < 2)) {
                // Haven't moved much and no XP yet - likely mid-animation
                if (isCrossGap) {
                    waitAfterStop = 1200; // Much longer for Cross Gap
                } else {
                    waitAfterStop = 800; // Slightly longer for other gaps
                }
            }
            
            if (System.currentTimeMillis() - stoppedMovingTime < waitAfterStop) {
                Global.sleep(50);
                continue;
            }
            
            // Check for XP gain
            if (currentXp != lastKnownXp) {
                int xpGained = currentXp - lastKnownXp;
                totalXpGained += xpGained;
                
                // Stone blocks give exactly 12 XP
                if (xpGained == 12) {
                    Microbot.log("Hit by stone block - will retry obstacle");
                    hitByStoneBlock = true;
                }
                
                lastKnownXp = currentXp;
            }
            
            // Check for plane change (successful obstacle)
            if (currentPlane != plane) {
                // Wait a bit to ensure we're completely done
                Global.sleep(300, 400);
                return true;
            }
            
            // Check for health loss (failed obstacle)
            if (currentHealth < initialHealth) {
                Microbot.log("Failed obstacle, lost health");
                return true;
            }
            
            // Only check completion conditions after we've stopped moving
            // and enough time has passed to receive XP drops
            if (System.currentTimeMillis() - startTime > 1000) {
                int distanceMoved = currentPos.distanceTo(startPos);
                
                // For low wall, we expect 8 XP - wait longer if we haven't received it yet
                if (totalXpGained == 0 && distanceMoved < 15) {
                    // Still waiting for XP drop, continue waiting
                    if (System.currentTimeMillis() - startTime < 4000) {
                        continue;
                    }
                }
                
                // If we only got stone block XP (12) and stopped moving, return false to retry
                if (hitByStoneBlock && totalXpGained == 12 && distanceMoved < 3) {
                    Microbot.log("Stone block interrupted movement, waiting before retry");
                    Global.sleep(1200, 1800); // Wait a bit before retrying
                    return false; // This will cause the script to re-click the obstacle
                }
                
                // Consider obstacle completed if we gained proper XP and moved
                // Note: We already waited 600ms after stopping, so no additional delay needed
                if (totalXpGained > 12 && distanceMoved >= 2) {
                    return true;
                }
                
                // Zero-XP obstacles (stairs, doorway) - check movement only
                if (totalXpGained == 0 && distanceMoved >= 3) {
                    return true;
                }
                
                // Low wall (8 XP) completion
                if (totalXpGained == 8 && distanceMoved >= 3) {
                    Microbot.log("Low wall completed - gained 8 XP");
                    return true;
                }
                
                // Low wall + stone block (20 XP) completion
                if (totalXpGained == 20 && distanceMoved >= 3) {
                    Microbot.log("Low wall (with stone block) completed - gained 20 XP");
                    return true;
                }
                
                // Ledge completion (52 XP)
                if (totalXpGained == 52) {
                    Microbot.log("Ledge completed - gained 52 XP");
                    return true;
                }
                
                // For Cross Gap obstacles, NEVER return true without XP
                if (isCrossGap) {
                    // Cross Gap MUST have XP to be considered complete
                    if (totalXpGained < 56) {
                        // No XP yet - keep waiting regardless of movement
                        if (System.currentTimeMillis() - startTime < 5000) {
                            Microbot.log("Cross Gap - still waiting for XP drop (current XP: " + totalXpGained + ")");
                            continue;
                        } else {
                            // Timeout after 5 seconds without XP
                            Microbot.log("Cross Gap timeout without XP - may need to retry");
                            return false;
                        }
                    }
                    
                    // We have XP for Cross Gap - verify completion conditions
                    if (totalXpGained >= 56 && totalXpGained <= 69) {
                        // Ensure we've also moved and animation is done
                        if (distanceMoved < 3) {
                            Microbot.log("Cross Gap XP received but haven't moved far enough yet (" + distanceMoved + " tiles)");
                            continue; // Keep waiting
                        }
                        
                        // Ensure minimum time has passed for animation
                        if (System.currentTimeMillis() - startTime < 3000) {
                            Microbot.log("Cross Gap - waiting for full animation completion");
                            continue;
                        }
                        
                        Microbot.log("Cross Gap completed - gained " + totalXpGained + " XP, moved " + distanceMoved + " tiles");
                        return true;
                    }
                }
                
                // Gap/Plank completion (56 XP) for non-Cross Gap obstacles
                if (totalXpGained >= 56 && totalXpGained <= 57) {
                    if (distanceMoved < 3) {
                        // Other gaps still need movement check
                        Microbot.log("Gap XP received but haven't moved far enough yet (" + distanceMoved + " tiles)");
                        continue; // Keep waiting
                    }
                    
                    Microbot.log("Gap/Plank completed - gained " + totalXpGained + " XP, moved " + distanceMoved + " tiles");
                    Microbot.log("  Started at " + startPos + ", ended at " + currentPos);
                    
                    return true;
                }
                
                // Ledge/Gap with stone block (64/68 XP)
                if (totalXpGained >= 64 && totalXpGained <= 69) {
                    Microbot.log("Obstacle (with stone block) completed - gained " + totalXpGained + " XP");
                    return true;
                }
                
                // For gaps and other XP obstacles, ensure we wait for XP
                if (totalXpGained == 0 && distanceMoved >= 2) {
                    // We moved but no XP yet - keep waiting a bit more
                    if (System.currentTimeMillis() - startTime < 3000) {
                        continue;
                    }
                }
                
                // If nothing happening for too long, timeout
                if (System.currentTimeMillis() - startTime > 4000) {
                    Microbot.log("Timeout waiting for obstacle completion after 4 seconds");
                    Microbot.log("  Total XP gained: " + totalXpGained + ", distance moved: " + distanceMoved);
                    return false;
                }
            }
            
            Global.sleep(50);
        }
        
        // Timeout - check if we made progress
        boolean madeProgress = totalXpGained > 12 || (totalXpGained > 0 && Rs2Player.getWorldLocation().distanceTo(startPos) >= 3);
        if (!madeProgress) {
            Microbot.log("No progress made, will retry");
        }
        return madeProgress;
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
                Microbot.log("No pyramid tops found in inventory - returning to course");
                handlingPyramidTurnIn = false;
                return false;
            }
            
            // Try to find Simon
            NPC simon = Rs2Npc.getNpc(SIMON_NAME);
            
            // If Simon is found and reachable, use pyramid top on him
            if (simon != null && Rs2GameObject.canReach(simon.getWorldLocation())) {
                Microbot.log("Simon found and reachable, using pyramid top");
                
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
                        Microbot.log("Successfully used pyramid top on Simon");
                        Global.sleepUntil(() -> Rs2Dialogue.isInDialogue(), 3000);
                    } else {
                        Microbot.log("Failed to use pyramid top on Simon");
                    }
                }
                return true;
            }
            
            // Simon not found or not reachable, walk to him
            Microbot.log("Simon not found or not reachable, walking to location " + SIMON_LOCATION);
            Rs2Walker.walkTo(SIMON_LOCATION, 2);
            Rs2Player.waitForWalking();
            
            // Check if we've completed the turn-in (no pyramids left and not in dialogue)
            if (!Rs2Inventory.contains(ItemID.PYRAMID_TOP) && !Rs2Dialogue.isInDialogue()) {
                Microbot.log("Pyramid tops turned in successfully");
                handlingPyramidTurnIn = false;
                
                // Walk back towards the pyramid start
                WorldPoint currentPos = Rs2Player.getWorldLocation();
                if (currentPos.distanceTo(START_POINT) > 10) {
                    Microbot.log("Walking back to pyramid start");
                    Rs2Walker.walkTo(START_POINT);
                }
                return false; // Done with turn-in, can resume obstacles
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error in handlePyramidTurnIn: " + e.getMessage());
            e.printStackTrace();
            handlingPyramidTurnIn = false;
            return false;
        }
    }
    
}