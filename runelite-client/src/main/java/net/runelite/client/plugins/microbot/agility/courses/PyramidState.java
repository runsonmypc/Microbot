package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Encapsulates state tracking for the Agility Pyramid course.
 * Centralizes all state management to avoid scattered static variables.
 */
public class PyramidState {
    
    // Timing and cooldown tracking
    private long lastObstacleStartTime = 0;
    private long lastClimbingRocksTime = 0;
    private long lastCrossGapTime = 0;
    
    // State flags  
    private boolean currentlyDoingCrossGap = false;
    private boolean currentlyDoingXpObstacle = false;
    private boolean handlingPyramidTurnIn = false;
    
    // Random turn-in threshold (4-6 pyramids)
    private int pyramidTurnInThreshold = generateNewThreshold();
    
    // Cooldown constants
    private static final long OBSTACLE_COOLDOWN = 1500; // 1.5 seconds between obstacles
    private static final long CLIMBING_ROCKS_COOLDOWN = 30000; // 30 seconds - pyramid respawn time
    private static final long CROSS_GAP_COOLDOWN = 6000; // 6 seconds for Cross Gap
    
    /**
     * Records that an obstacle was just started
     */
    public void recordObstacleStart() {
        lastObstacleStartTime = System.currentTimeMillis();
    }
    
    /**
     * Checks if enough time has passed since last obstacle
     */
    public boolean isObstacleCooldownActive() {
        return System.currentTimeMillis() - lastObstacleStartTime < OBSTACLE_COOLDOWN;
    }
    
    /**
     * Records that climbing rocks were clicked
     */
    public void recordClimbingRocks() {
        lastClimbingRocksTime = System.currentTimeMillis();
    }
    
    /**
     * Checks if climbing rocks are on cooldown
     */
    public boolean isClimbingRocksCooldownActive() {
        return System.currentTimeMillis() - lastClimbingRocksTime < CLIMBING_ROCKS_COOLDOWN;
    }
    
    /**
     * Records that a Cross Gap obstacle was started
     */
    public void startCrossGap() {
        lastCrossGapTime = System.currentTimeMillis();
        currentlyDoingCrossGap = true;
    }
    
    /**
     * Checks if Cross Gap is on cooldown
     */
    public boolean isCrossGapCooldownActive() {
        return System.currentTimeMillis() - lastCrossGapTime < CROSS_GAP_COOLDOWN;
    }
    
    /**
     * Clears the Cross Gap flag
     */
    public void clearCrossGap() {
        currentlyDoingCrossGap = false;
    }
    
    /**
     * Checks if currently doing a Cross Gap obstacle
     */
    public boolean isDoingCrossGap() {
        return currentlyDoingCrossGap;
    }
    
    /**
     * Sets the XP obstacle flag
     */
    public void startXpObstacle() {
        currentlyDoingXpObstacle = true;
    }
    
    /**
     * Clears the XP obstacle flag
     */
    public void clearXpObstacle() {
        currentlyDoingXpObstacle = false;
    }
    
    /**
     * Checks if currently doing an XP-granting obstacle
     */
    public boolean isDoingXpObstacle() {
        return currentlyDoingXpObstacle;
    }
    
    /**
     * Sets the pyramid turn-in flag
     */
    public void startPyramidTurnIn() {
        handlingPyramidTurnIn = true;
    }
    
    /**
     * Clears the pyramid turn-in flag and generates a new random threshold
     */
    public void clearPyramidTurnIn() {
        handlingPyramidTurnIn = false;
        // Generate a new random threshold for next turn-in
        pyramidTurnInThreshold = generateNewThreshold();
    }
    
    /**
     * Checks if currently handling pyramid turn-in
     */
    public boolean isHandlingPyramidTurnIn() {
        return handlingPyramidTurnIn;
    }
    
    /**
     * Gets the current pyramid turn-in threshold
     */
    public int getPyramidTurnInThreshold() {
        return pyramidTurnInThreshold;
    }
    
    /**
     * Generates a new random threshold between 4 and 6 (inclusive)
     */
    private int generateNewThreshold() {
        return Rs2Random.betweenInclusive(4, 6);
    }
    
    /**
     * Resets all state flags (useful for plugin restart)
     */
    public void reset() {
        lastObstacleStartTime = 0;
        lastClimbingRocksTime = 0;
        lastCrossGapTime = 0;
        currentlyDoingCrossGap = false;
        currentlyDoingXpObstacle = false;
        handlingPyramidTurnIn = false;
        pyramidTurnInThreshold = generateNewThreshold();
    }
}